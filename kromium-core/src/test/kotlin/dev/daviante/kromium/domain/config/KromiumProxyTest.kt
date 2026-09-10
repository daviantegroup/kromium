package dev.daviante.kromium.domain.config

import dev.daviante.kromium.domain.exception.KromiumException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KromiumProxyTest {

    @Test
    fun testSystemProxyStrategy() {
        val proxy = KromiumProxy.System
        assertTrue(proxy.toCommandLineArgs().isEmpty())
        assertEquals(mapOf("mode" to "system"), proxy.toPreferenceMap())
        assertNull(proxy.getCredentials("proxy.internal", 8080))
    }

    @Test
    fun testDirectProxyStrategy() {
        val proxy = KromiumProxy.Direct
        assertEquals(listOf("--no-proxy-server"), proxy.toCommandLineArgs())
        assertEquals(mapOf("mode" to "direct"), proxy.toPreferenceMap())
    }

    @Test
    fun testAutoDetectWpadStrategy() {
        val proxy = KromiumProxy.AutoDetect
        assertEquals(listOf("--proxy-auto-detect"), proxy.toCommandLineArgs())
        assertEquals(mapOf("mode" to "auto_detect"), proxy.toPreferenceMap())
    }

    @Test
    fun testPacProxyStrategy() {
        val pac = KromiumProxy.Pac("http://pac.corp.internal/wpad.dat")
        assertEquals(listOf("--proxy-pac-url=http://pac.corp.internal/wpad.dat"), pac.toCommandLineArgs())
        assertEquals(
            mapOf("mode" to "pac_script", "pac_url" to "http://pac.corp.internal/wpad.dat"),
            pac.toPreferenceMap()
        )

        assertFailsWith<KromiumException.InvalidConfig> {
            KromiumProxy.Pac("").validate()
        }
    }

    @Test
    fun testHttpProxyWithBypassListAndCredentials() {
        val proxy = KromiumProxy.Http(
            host = "proxy.corp.com",
            port = 8080,
            username = "domain\\user",
            password = "SecurePassword123",
            bypassList = listOf("<local>", "127.0.0.1", "*.internal.corp")
        )

        val args = proxy.toCommandLineArgs()
        assertTrue(args.contains("--proxy-server=http://proxy.corp.com:8080"))
        assertTrue(args.contains("--proxy-bypass-list=<local>;127.0.0.1;*.internal.corp"))

        val pref = proxy.toPreferenceMap()
        assertEquals("fixed_servers", pref["mode"])
        assertEquals("http://proxy.corp.com:8080", pref["server"])
        assertEquals("<local>;127.0.0.1;*.internal.corp", pref["bypass_list"])

        val creds = proxy.getCredentials("proxy.corp.com", 8080)
        assertNotNull(creds)
        assertEquals("domain\\user", creds.first)
        assertEquals("SecurePassword123", creds.second)

        // Mismatched host or port
        assertNull(proxy.getCredentials("other.host.com", 8080))
        assertNull(proxy.getCredentials("proxy.corp.com", 9090))
    }

    @Test
    fun testSecureHttpsProxyTunnel() {
        val proxy = KromiumProxy.Http(
            host = "secure-egress.corp.com",
            port = 8443,
            isSecure = true
        )

        val args = proxy.toCommandLineArgs()
        assertTrue(args.contains("--proxy-server=https://secure-egress.corp.com:8443"))

        val pref = proxy.toPreferenceMap()
        assertEquals("https://secure-egress.corp.com:8443", pref["server"])
    }

    @Test
    fun testSocks5ProxyStrategy() {
        val proxy = KromiumProxy.Socks5(
            host = "10.0.0.1",
            port = 1080,
            username = "socksuser",
            password = "sockspassword",
            remoteDns = true,
            bypassList = listOf("localhost", "127.0.0.1")
        )

        val args = proxy.toCommandLineArgs()
        assertTrue(args.contains("--proxy-server=socks5://10.0.0.1:1080"))
        assertTrue(args.contains("--proxy-bypass-list=localhost;127.0.0.1"))

        val creds = proxy.getCredentials("10.0.0.1", 1080)
        assertNotNull(creds)
        assertEquals("socksuser", creds.first)
        assertEquals("sockspassword", creds.second)
    }

    @Test
    fun testMultiProtocolProxyStrategy() {
        val proxy = KromiumProxy.MultiProtocol(
            http = "http://http-proxy.corp:8080",
            https = "https://https-proxy.corp:8443",
            socks = "socks5://socks-proxy.corp:1080",
            bypassList = listOf("<local>", "*.internal")
        )

        val args = proxy.toCommandLineArgs()
        assertTrue(args.contains("--proxy-server=http=http://http-proxy.corp:8080;https=https://https-proxy.corp:8443;socks=socks5://socks-proxy.corp:1080"))
        assertTrue(args.contains("--proxy-bypass-list=<local>;*.internal"))

        val pref = proxy.toPreferenceMap()
        assertEquals("fixed_servers", pref["mode"])
        assertEquals("http=http://http-proxy.corp:8080;https=https://https-proxy.corp:8443;socks=socks5://socks-proxy.corp:1080", pref["server"])
    }

    @Test
    fun testInvalidProxyValidation() {
        assertFailsWith<KromiumException.InvalidConfig> {
            KromiumProxy.Http("", 8080).validate()
        }
        assertFailsWith<KromiumException.InvalidConfig> {
            KromiumProxy.Http("proxy.com", 70000).validate()
        }
        assertFailsWith<KromiumException.InvalidConfig> {
            KromiumProxy.Socks5("proxy.com", -1).validate()
        }
        assertFailsWith<KromiumException.InvalidConfig> {
            KromiumProxy.MultiProtocol().validate()
        }
    }

    @Test
    fun testKromiumConfigProxyAndEnterpriseAllowlists() {
        val config = KromiumConfig().apply {
            proxy = KromiumProxy.Http(
                host = "proxy.enterprise.internal",
                port = 8080,
                bypassList = listOf("<local>", "127.0.0.1")
            )
            authServerAllowlist = listOf("*.enterprise.internal", "sso.corp.com")
            authNegotiateDelegateAllowlist = listOf("sso.corp.com")
        }

        config.validate()
        config.toCefSettings()

        assertTrue(config.commandLineArgs.contains("--proxy-server=http://proxy.enterprise.internal:8080"))
        assertTrue(config.commandLineArgs.contains("--proxy-bypass-list=<local>;127.0.0.1"))
        assertTrue(config.commandLineArgs.contains("--auth-server-allowlist=*.enterprise.internal,sso.corp.com"))
        assertTrue(config.commandLineArgs.contains("--auth-negotiate-delegate-allowlist=sso.corp.com"))
    }

    @Test
    fun testDynamicProxyWhenNotInitializedReturnsFailureGracefully() {
        val result = dev.daviante.kromium.presentation.browser.Kromium.setProxy(KromiumProxy.Direct)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is KromiumException.NotInitialized)
    }

    @Test
    fun testHttpsCompanionFactory() {
        val proxy = KromiumProxy.https("secure-proxy.corp.internal", 8443, "user", "pass", listOf("<local>"))
        assertTrue(proxy is KromiumProxy.Http)
        assertTrue(proxy.isSecure)
        assertEquals("https://secure-proxy.corp.internal:8443", proxy.serverSpec)
        val creds = proxy.getCredentials("secure-proxy.corp.internal", 8443)
        assertNotNull(creds)
        assertEquals("user", creds.first)
        assertEquals("pass", creds.second)
    }

    @Test
    fun testMultiProtocolWithEmbeddedCredentials() {
        val proxy = KromiumProxy.MultiProtocol(
            http = "http://httpuser:httppass@http-proxy.corp:8080",
            https = "https://ssluser:sslpass@ssl-proxy.corp:8443",
            socks = "socks5://socksuser:sockspass@socks-proxy.corp:1080"
        )

        // Ensure userinfo is stripped from serverSpec and command line args
        val spec = proxy.serverSpec
        assertTrue(spec.contains("http=http://http-proxy.corp:8080"))
        assertTrue(spec.contains("https=https://ssl-proxy.corp:8443"))
        assertTrue(spec.contains("socks=socks5://socks-proxy.corp:1080"))
        assertTrue(!spec.contains("httpuser"))
        assertTrue(!spec.contains("ssluser"))
        assertTrue(!spec.contains("socksuser"))

        // Ensure getCredentials retrieves credentials for corresponding hosts
        val httpCreds = proxy.getCredentials("http-proxy.corp", 8080)
        assertNotNull(httpCreds)
        assertEquals("httpuser", httpCreds.first)
        assertEquals("httppass", httpCreds.second)

        val sslCreds = proxy.getCredentials("ssl-proxy.corp", 8443)
        assertNotNull(sslCreds)
        assertEquals("ssluser", sslCreds.first)
        assertEquals("sslpass", sslCreds.second)

        val socksCreds = proxy.getCredentials("socks-proxy.corp", 1080)
        assertNotNull(socksCreds)
        assertEquals("socksuser", socksCreds.first)
        assertEquals("sockspass", socksCreds.second)

        // Non-matching endpoint
        assertNull(proxy.getCredentials("other.corp", 8080))
    }

    @Test
    fun testCachePathResolutionWithRelativeInstallDir() {
        val config = KromiumConfig()
        config.installDir = java.io.File("local_test_jcef")
        val settings = config.toCefSettings()

        assertNotNull(settings.cache_path)
        assertTrue(settings.cache_path.contains("cache"))
    }
}
