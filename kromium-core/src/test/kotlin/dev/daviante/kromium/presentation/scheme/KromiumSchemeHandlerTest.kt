package dev.daviante.kromium.presentation.scheme

import dev.daviante.kromium.domain.config.KromiumConfig
import dev.daviante.kromium.domain.model.KromiumCustomScheme
import org.cef.callback.CefCallback
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KromiumSchemeHandlerTest {

    @Test
    fun testMimeTypeResolution() {
        assertEquals("text/html; charset=utf-8", MimeTypes.lookup("index.html"))
        assertEquals("text/css; charset=utf-8", MimeTypes.lookup("style.css"))
        assertEquals("application/javascript; charset=utf-8", MimeTypes.lookup("bundle.js"))
        assertEquals("application/javascript; charset=utf-8", MimeTypes.lookup("module.mjs"))
        assertEquals("application/wasm", MimeTypes.lookup("engine.wasm"))
        assertEquals("application/json; charset=utf-8", MimeTypes.lookup("data.json"))
        assertEquals("image/svg+xml", MimeTypes.lookup("logo.svg"))
        assertEquals("image/png", MimeTypes.lookup("icon.png"))
        assertEquals("image/webp", MimeTypes.lookup("photo.webp"))
        assertEquals("font/woff2", MimeTypes.lookup("font.woff2"))
        assertEquals("video/mp4", MimeTypes.lookup("video.mp4"))
        assertEquals("application/pdf", MimeTypes.lookup("document.pdf"))

        assertTrue(MimeTypes.hasExtension("file.html"))
        assertTrue(MimeTypes.hasExtension("/path/to/script.js"))
        assertFalse(MimeTypes.hasExtension("/path/to/dashboard"))
        assertFalse(MimeTypes.hasExtension("/"))
    }

    @Test
    fun testPathSanitizationPreventsTraversal() {
        assertEquals("/index.html", KromiumSchemeHandler.sanitizePath("/index.html"))
        assertEquals("/css/style.css", KromiumSchemeHandler.sanitizePath("/css/style.css"))
        assertEquals("/assets/data.json", KromiumSchemeHandler.sanitizePath("\\assets\\data.json"))

        // Path traversal attempts must return null
        assertNull(KromiumSchemeHandler.sanitizePath("/../secret.txt"))
        assertNull(KromiumSchemeHandler.sanitizePath("/assets/../../etc/passwd"))
        assertNull(KromiumSchemeHandler.sanitizePath("/%2e%2e/secret"))
        assertNull(KromiumSchemeHandler.sanitizePath("/index.html\u0000.png"))
    }

    @Test
    fun testKromiumAssetRequestParsing() {
        val request = KromiumAssetRequest(
            url = "app://myapp/dashboard?user=kiran&tab=settings",
            method = "GET",
            scheme = "app",
            domain = "myapp",
            path = "/dashboard",
            queryString = "user=kiran&tab=settings",
            queryParameters = mapOf("user" to "kiran", "tab" to "settings"),
            headers = mapOf("Authorization" to "Bearer token123", "User-Agent" to "KromiumTest")
        )

        assertEquals("app", request.scheme)
        assertEquals("myapp", request.domain)
        assertEquals("/dashboard", request.path)
        assertEquals("kiran", request.getQueryParam("user"))
        assertEquals("settings", request.getQueryParam("tab"))
        assertEquals("Bearer token123", request.getHeader("authorization"))
        assertEquals("KromiumTest", request.getHeader("USER-AGENT"))
        assertNull(request.getHeader("Non-Existent"))
    }

    @Test
    fun testKromiumAssetResponseFactoriesAndHeaders() {
        val jsonRes = KromiumAssetResponse.json("""{"status":"ok"}""")
            .withCors("https://example.com")
            .withCacheControl(3600)

        assertEquals(200, jsonRes.statusCode)
        assertEquals("application/json; charset=utf-8", jsonRes.mimeType)
        assertEquals("https://example.com", jsonRes.headers["Access-Control-Allow-Origin"])
        assertEquals("public, max-age=3600", jsonRes.headers["Cache-Control"])

        val stream = jsonRes.openStream()
        val text = stream.bufferedReader().readText()
        assertEquals("""{"status":"ok"}""", text)

        val notFoundRes = KromiumAssetResponse.notFound()
        assertEquals(404, notFoundRes.statusCode)
        assertEquals("Not Found", notFoundRes.statusText)

        val forbiddenRes = KromiumAssetResponse.forbidden()
        assertEquals(403, forbiddenRes.statusCode)
        assertEquals("Forbidden", forbiddenRes.statusText)
    }

    @Test
    fun testDirectoryAssetHandlerWithSpaFallback() {
        val tempDir = File.createTempFile("kromium_test_web_", "").apply {
            delete()
            mkdirs()
        }

        try {
            File(tempDir, "index.html").writeText("<h1>Home</h1>")
            File(tempDir, "app.js").writeText("console.log('loaded');")
            val subDir = File(tempDir, "css").apply { mkdirs() }
            File(subDir, "main.css").writeText("body { color: black; }")

            val handler = KromiumSchemeHandler.fromDirectory(
                directory = tempDir,
                spaFallback = "index.html",
                defaultHeaders = mapOf("X-Powered-By" to "Kromium")
            )

            // 1. Serving root file
            val rootReq = KromiumAssetRequest(url = "app://ui/", path = "/")
            val rootRes = handler.handle(rootReq)
            assertNotNull(rootRes)
            assertEquals(200, rootRes.statusCode)
            assertEquals("text/html; charset=utf-8", rootRes.mimeType)
            assertEquals("<h1>Home</h1>", rootRes.openStream().bufferedReader().readText())
            assertEquals("Kromium", rootRes.headers["X-Powered-By"])

            // 2. Serving JS asset
            val jsReq = KromiumAssetRequest(url = "app://ui/app.js", path = "/app.js")
            val jsRes = handler.handle(jsReq)
            assertNotNull(jsRes)
            assertEquals("application/javascript; charset=utf-8", jsRes.mimeType)
            assertEquals("console.log('loaded');", jsRes.openStream().bufferedReader().readText())

            // 3. Serving CSS asset in subfolder
            val cssReq = KromiumAssetRequest(url = "app://ui/css/main.css", path = "/css/main.css")
            val cssRes = handler.handle(cssReq)
            assertNotNull(cssRes)
            assertEquals("text/css; charset=utf-8", cssRes.mimeType)

            // 4. SPA Fallback: GET request to extensionless route returns index.html
            val routeReq = KromiumAssetRequest(url = "app://ui/dashboard/profile", path = "/dashboard/profile")
            val routeRes = handler.handle(routeReq)
            assertNotNull(routeRes)
            assertEquals(200, routeRes.statusCode)
            assertEquals("text/html; charset=utf-8", routeRes.mimeType)
            assertEquals("<h1>Home</h1>", routeRes.openStream().bufferedReader().readText())

            // 5. Missing static asset must strictly return 404 (never masked by index.html)
            val missingAssetReq = KromiumAssetRequest(url = "app://ui/missing.png", path = "/missing.png")
            val missingRes = handler.handle(missingAssetReq)
            assertNotNull(missingRes)
            assertEquals(404, missingRes.statusCode)

            // 6. Directory traversal attempt must return 403 Forbidden
            val traversalReq = KromiumAssetRequest(url = "app://ui/../etc/passwd", path = "/../etc/passwd")
            val traversalRes = handler.handle(traversalReq)
            assertNotNull(traversalRes)
            assertEquals(403, traversalRes.statusCode)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testClasspathAssetHandlerWithMockLoader() {
        val mockLoader = object : ClassLoader() {
            override fun getResourceAsStream(name: String): InputStream? {
                return when (name) {
                    "web/index.html" -> ByteArrayInputStream("<html>SPA App</html>".toByteArray(StandardCharsets.UTF_8))
                    "web/wasm/calc.wasm" -> ByteArrayInputStream(byteArrayOf(0x00, 0x61, 0x73, 0x6d))
                    else -> null
                }
            }
        }

        val handler = KromiumSchemeHandler.fromClasspath(
            resourcePath = "web",
            classLoader = mockLoader,
            spaFallback = "index.html"
        )

        // Existing resource
        val wasmReq = KromiumAssetRequest(url = "app://myapp/wasm/calc.wasm", path = "/wasm/calc.wasm")
        val wasmRes = handler.handle(wasmReq)
        assertNotNull(wasmRes)
        assertEquals("application/wasm", wasmRes.mimeType)

        // SPA Route fallback
        val spaReq = KromiumAssetRequest(url = "app://myapp/settings", path = "/settings")
        val spaRes = handler.handle(spaReq)
        assertNotNull(spaRes)
        assertEquals(200, spaRes.statusCode)
        assertEquals("text/html; charset=utf-8", spaRes.mimeType)
        assertEquals("<html>SPA App</html>", spaRes.openStream().bufferedReader().readText())

        // Missing JS asset -> 404 (not fallback)
        val missingJsReq = KromiumAssetRequest(url = "app://myapp/nonexistent.js", path = "/nonexistent.js")
        val missingRes = handler.handle(missingJsReq)
        assertNotNull(missingRes)
        assertEquals(404, missingRes.statusCode)
    }

    @Test
    fun testKromiumCustomSchemeValidationAndPresets() {
        val secure = KromiumCustomScheme.secure("app")
        assertEquals("app", secure.schemeName)
        assertTrue(secure.isStandard)
        assertTrue(secure.isLocal)
        assertTrue(secure.isSecure)
        assertTrue(secure.isCorsEnabled)
        assertTrue(secure.isFetchEnabled)
        assertFalse(secure.isDisplayIsolated)

        val standard = KromiumCustomScheme.standard("myproto")
        assertEquals("myproto", standard.schemeName)
        assertTrue(standard.isStandard)
        assertFalse(standard.isLocal)
        assertFalse(standard.isSecure)

        // Invalid names
        assertFailsWith<IllegalArgumentException> {
            KromiumCustomScheme("   ")
        }
        assertFailsWith<IllegalArgumentException> {
            KromiumCustomScheme("app://")
        }
    }

    @Test
    fun testKromiumConfigSchemeRegistration() {
        val config = KromiumConfig()
        assertEquals(0, config.customSchemes.size)
        assertEquals(0, config.schemeHandlers.size)

        config.registerCustomScheme("app")
        assertEquals(1, config.customSchemes.size)
        assertEquals("app", config.customSchemes[0].schemeName)

        // Duplicate scheme name ignored
        config.registerCustomScheme("app")
        assertEquals(1, config.customSchemes.size)

        config.registerCustomScheme("kromium", "assets") {
            KromiumAssetResponse.text("ok")
        }
        assertEquals(2, config.customSchemes.size)
        assertEquals(1, config.schemeHandlers.size)
        assertEquals("kromium", config.schemeHandlers[0].schemeName)
        assertEquals("assets", config.schemeHandlers[0].domainName)
    }

    @Test
    fun testKromiumResourceHandlerChunkedStreaming() {
        val payload = "Hello Kromium Virtual Protocol!".toByteArray(StandardCharsets.UTF_8)
        val assetHandler = KromiumAssetHandler {
            KromiumAssetResponse.ok(payload, "text/plain; charset=utf-8")
                .withHeader("X-Custom", "TestValue")
        }

        val resourceHandler = KromiumResourceHandler(assetHandler)

        // Process request
        val request = object : CefRequest() {
            override fun dispose() {}
            override fun getIdentifier(): Long = 1
            override fun isReadOnly(): Boolean = true
            override fun getURL(): String = "app://myapp/hello"
            override fun setURL(url: String?) {}
            override fun getMethod(): String = "GET"
            override fun setMethod(method: String?) {}
            override fun setReferrer(referrerUrl: String?, policy: ReferrerPolicy?) {}
            override fun getReferrerURL(): String = ""
            override fun getReferrerPolicy(): ReferrerPolicy = ReferrerPolicy.REFERRER_POLICY_DEFAULT
            override fun getPostData(): org.cef.network.CefPostData? = null
            override fun setPostData(postData: org.cef.network.CefPostData?) {}
            override fun getHeaderByName(name: String?): String? = null
            override fun setHeaderByName(name: String?, value: String?, overwrite: Boolean) {}
            override fun getHeaderMap(headerMap: MutableMap<String, String>?) {}
            override fun setHeaderMap(headerMap: MutableMap<String, String>?) {}
            override fun set(url: String?, method: String?, postData: org.cef.network.CefPostData?, headerMap: MutableMap<String, String>?) {}
            override fun getFlags(): Int = 0
            override fun setFlags(flags: Int) {}
            override fun getFirstPartyForCookies(): String = ""
            override fun setFirstPartyForCookies(url: String?) {}
            override fun getResourceType(): ResourceType = ResourceType.RT_MAIN_FRAME
            override fun getTransitionType(): TransitionType = TransitionType.TT_EXPLICIT
        }

        var continued = false
        val callback = object : CefCallback {
            override fun Continue() { continued = true }
            override fun cancel() {}
        }

        val processed = resourceHandler.processRequest(request, callback)
        assertTrue(processed)
        assertTrue(continued)

        // Verify headers
        var capturedStatus = 0
        var capturedMime = ""
        val response = object : CefResponse() {
            override fun dispose() {}
            override fun isReadOnly(): Boolean = false
            override fun getError(): org.cef.handler.CefLoadHandler.ErrorCode = org.cef.handler.CefLoadHandler.ErrorCode.ERR_NONE
            override fun setError(errorCode: org.cef.handler.CefLoadHandler.ErrorCode?) {}
            override fun getStatus(): Int = capturedStatus
            override fun setStatus(status: Int) { capturedStatus = status }
            override fun getStatusText(): String = "OK"
            override fun setStatusText(statusText: String?) {}
            override fun getMimeType(): String = capturedMime
            override fun setMimeType(mimeType: String?) { capturedMime = mimeType ?: "" }
            override fun getHeaderByName(name: String?): String? = null
            override fun setHeaderByName(name: String?, value: String?, overwrite: Boolean) {}
            override fun getHeaderMap(headerMap: MutableMap<String, String>?) {}
            override fun setHeaderMap(headerMap: MutableMap<String, String>?) {}
        }

        val lengthRef = IntRef()
        val redirectRef = StringRef()
        resourceHandler.getResponseHeaders(response, lengthRef, redirectRef)
        assertEquals(200, capturedStatus)
        assertEquals("text/plain; charset=utf-8", capturedMime)
        assertEquals(payload.size, lengthRef.get())

        // Read in small chunks of 10 bytes
        val buffer = ByteArray(10)
        val bytesRead = IntRef()
        val readBytes = mutableListOf<Byte>()

        while (true) {
            val hasMore = resourceHandler.readResponse(buffer, buffer.size, bytesRead, callback)
            val count = bytesRead.get()
            if (count > 0) {
                for (i in 0 until count) {
                    readBytes.add(buffer[i])
                }
            }
            if (!hasMore || count == 0) break
        }

        assertEquals(String(payload, StandardCharsets.UTF_8), String(readBytes.toByteArray(), StandardCharsets.UTF_8))
        resourceHandler.cancel()
    }
}
