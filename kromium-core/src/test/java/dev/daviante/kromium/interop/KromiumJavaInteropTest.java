package dev.daviante.kromium.interop;

import dev.daviante.kromium.core.util.JvmModuleOpener;
import dev.daviante.kromium.core.util.PlatformDetector;
import dev.daviante.kromium.data.engine.EngineRegistry;
import dev.daviante.kromium.data.engine.KromiumEngine;
import dev.daviante.kromium.data.engine.KromiumEngineInfo;
import dev.daviante.kromium.domain.config.KromiumConfig;
import dev.daviante.kromium.domain.config.KromiumProxy;
import dev.daviante.kromium.domain.exception.KromiumException;
import dev.daviante.kromium.domain.exception.SslErrorPolicy;
import dev.daviante.kromium.domain.model.KromiumState;
import dev.daviante.kromium.domain.model.PlatformInfo;
import dev.daviante.kromium.presentation.browser.Kromium;
import dev.daviante.kromium.presentation.browser.KromiumBrowser;
import dev.daviante.kromium.presentation.browser.KromiumClient;
import dev.daviante.kromium.presentation.handler.KromiumAuthResponse;
import dev.daviante.kromium.presentation.handler.KromiumLoadingListener;
import dev.daviante.kromium.presentation.network.KromiumCookieManager;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.Assert.*;

/**
 * Verification test suite ensuring 100% idiomatic Java ergonomics and feature parity.
 *
 * This test class is compiled by javac (pure Java) with ZERO Kotlin runtime imports or continuations,
 * validating that any standard Java enterprise project can consume Kromium directly.
 */
public class KromiumJavaInteropTest {

    @Test
    public void testKromiumConfigBuilderErgonomics() throws Exception {
        File customDir = new File(System.getProperty("java.io.tmpdir"), "custom-kromium");

        KromiumConfig config = KromiumConfig.builder()
                .installDir(customDir)
                .userAgent("Kromium-Enterprise-Java/1.0")
                .remoteDebuggingPort(9222)
                .sandboxEnabled(true)
                .blockRegistryAndTelemetry(true)
                .proxy(KromiumProxy.direct())
                .addArgs("--test-arg-1", "--test-arg-2")
                .authServerAllowlist(List.of("*.corp.internal"))
                .build();

        assertNotNull(config);
        assertEquals(customDir.getCanonicalFile(), config.getInstallDir());
        assertEquals("Kromium-Enterprise-Java/1.0", config.getUserAgent());
        assertEquals(9222, config.getRemoteDebuggingPort());
        assertTrue(config.getSandboxEnabled());
        assertTrue(config.getBlockRegistryAndTelemetry());
        assertTrue(config.getCommandLineArgs().contains("--test-arg-1"));
        assertTrue(config.getCommandLineArgs().contains("--test-arg-2"));
        assertEquals(List.of("*.corp.internal"), config.getAuthServerAllowlist());
        config.toCefSettings();
        assertTrue(config.getCommandLineArgs().stream().anyMatch(a -> a.startsWith("--auth-server-allowlist=")));
    }

    @Test
    public void testRegistrySuppressionConstantsAccessibleFromJava() {
        List<String> flags = KromiumConfig.REGISTRY_SUPPRESSION_FLAGS;
        assertNotNull(flags);
        assertFalse(flags.isEmpty());
        assertTrue(flags.contains("--no-default-browser-check"));

        String features = KromiumConfig.REGISTRY_SUPPRESSION_FEATURES;
        assertNotNull(features);
        assertTrue(features.contains("WinNativeNotification"));
    }

    @Test
    public void testProxyFactoryMethodsAndStaticFields() {
        // Static getters
        assertEquals(KromiumProxy.System.INSTANCE, KromiumProxy.getSYSTEM());
        assertEquals(KromiumProxy.Direct.INSTANCE, KromiumProxy.getDIRECT());
        assertEquals(KromiumProxy.AutoDetect.INSTANCE, KromiumProxy.getAUTO_DETECT());

        // Factory methods
        KromiumProxy system = KromiumProxy.system();
        assertEquals(KromiumProxy.getSYSTEM(), system);

        KromiumProxy direct = KromiumProxy.direct();
        assertEquals(KromiumProxy.getDIRECT(), direct);

        KromiumProxy autoDetect = KromiumProxy.autoDetect();
        assertEquals(KromiumProxy.getAUTO_DETECT(), autoDetect);

        KromiumProxy pac = KromiumProxy.pac("http://pac.corp/wpad.dat");
        assertTrue(pac instanceof KromiumProxy.Pac);
        assertEquals("http://pac.corp/wpad.dat", ((KromiumProxy.Pac) pac).getPacUrl());

        // HTTP Proxy with @JvmOverloads
        KromiumProxy httpBasic = KromiumProxy.http("proxy.corp", 8080);
        assertTrue(httpBasic instanceof KromiumProxy.Http);
        KromiumProxy.Http http = (KromiumProxy.Http) httpBasic;
        assertEquals("proxy.corp", http.getHost());
        assertEquals(8080, http.getPort());
        assertNull(http.getUsername());
        assertFalse(http.isSecure());

        KromiumProxy httpFull = KromiumProxy.http(
                "secure-proxy.corp", 8443, "corpUser", "corpPass", true, List.of("127.0.0.1", "*.corp")
        );
        assertTrue(httpFull instanceof KromiumProxy.Http);
        KromiumProxy.Http full = (KromiumProxy.Http) httpFull;
        assertTrue(full.isSecure());
        assertEquals("corpUser", full.getUsername());
        assertEquals(2, full.getBypassList().size());

        // SOCKS5 Proxy with @JvmOverloads
        KromiumProxy socksBasic = KromiumProxy.socks5("socks.corp", 1080);
        assertTrue(socksBasic instanceof KromiumProxy.Socks5);
        KromiumProxy.Socks5 socks = (KromiumProxy.Socks5) socksBasic;
        assertEquals("socks.corp", socks.getHost());
        assertTrue(socks.getRemoteDns());

        // MultiProtocol Proxy
        KromiumProxy multi = KromiumProxy.multiProtocol("http://proxy:8080", "https://proxy:8443", null, null);
        assertTrue(multi instanceof KromiumProxy.MultiProtocol);
    }

    @Test
    public void testSslErrorPolicyJavaErgonomics() {
        assertEquals(SslErrorPolicy.Strict.INSTANCE, SslErrorPolicy.getSTRICT());
        assertEquals(SslErrorPolicy.AllowAll.INSTANCE, SslErrorPolicy.getALLOW_ALL());

        SslErrorPolicy strict = SslErrorPolicy.strict();
        assertEquals(SslErrorPolicy.getSTRICT(), strict);

        SslErrorPolicy allowAll = SslErrorPolicy.allowAll();
        assertEquals(SslErrorPolicy.getALLOW_ALL(), allowAll);

        SslErrorPolicy domains = SslErrorPolicy.allowDomains("localhost", "internal.corp");
        assertTrue(domains instanceof SslErrorPolicy.AllowDomains);
        assertTrue(((SslErrorPolicy.AllowDomains) domains).isAllowed("https://localhost:8443"));
        assertTrue(((SslErrorPolicy.AllowDomains) domains).isAllowed("https://api.internal.corp"));
        assertFalse(((SslErrorPolicy.AllowDomains) domains).isAllowed("https://external.com"));
    }

    @Test
    public void testKromiumAuthResponseFactories() {
        assertEquals(KromiumAuthResponse.Cancel.INSTANCE, KromiumAuthResponse.getCANCEL());
        assertEquals(KromiumAuthResponse.getCANCEL(), KromiumAuthResponse.cancel());

        KromiumAuthResponse proceed = KromiumAuthResponse.proceed("admin", "secret");
        assertTrue(proceed instanceof KromiumAuthResponse.Proceed);
        KromiumAuthResponse.Proceed p = (KromiumAuthResponse.Proceed) proceed;
        assertEquals("admin", p.getUsername());
        assertEquals("secret", p.getPassword());
    }

    @Test
    public void testUtilitiesJavaStaticAccess() {
        PlatformInfo platform = PlatformDetector.current();
        assertNotNull(platform);
        assertNotNull(platform.getOs());
        assertNotNull(platform.getArch());

        File defaultDir = EngineRegistry.defaultInstallDir();
        assertNotNull(defaultDir);

        KromiumEngineInfo info = KromiumEngine.getInfo();
        assertNotNull(info);
        assertEquals("150.0.14", info.getJcefVersion());

        // Ensure dynamic module opening works without exceptions
        JvmModuleOpener.ensureModulesOpened();
    }

    @Test
    public void testKromiumStateListenerAndLifecycleAccess() throws Exception {
        AtomicReference<KromiumState> observedState = new AtomicReference<>();
        AutoCloseable listenerHandle = Kromium.addStateListener(observedState::set);

        assertNotNull(listenerHandle);
        assertNotNull(observedState.get());
        assertTrue(observedState.get() instanceof KromiumState);

        // Remove listener
        listenerHandle.close();

        // Verify static access to isReady and activeProxy
        boolean ready = Kromium.isReady();
        assertFalse(ready); // Not initialized yet

        KromiumProxy proxy = Kromium.getActiveProxy();
        assertNotNull(proxy);
    }

    @Test
    public void testKromiumConfigConsumerErgonomics() {
        KromiumConfig config = new KromiumConfig();
        Consumer<KromiumConfig> configurator = cfg -> {
            cfg.setUserAgent("TestAgent/1.0");
            cfg.setRemoteDebuggingPort(9222);
        };
        configurator.accept(config);
        assertEquals("TestAgent/1.0", config.getUserAgent());
        assertEquals(9222, config.getRemoteDebuggingPort());
    }

    @Test
    public void testKromiumAwaitAsyncCancellation() {
        CompletableFuture<KromiumClient> future = Kromium.awaitClientAsync();
        assertNotNull(future);
        assertFalse(future.isDone());
        future.cancel(true);
        assertTrue(future.isCancelled());
    }

    @Test
    public void testKromiumCookieManagerJavaAsync() throws Exception {
        CompletableFuture<Map<String, String>> cookiesFuture =
                KromiumCookieManager.getCookiesAsync("about:blank");
        assertNotNull(cookiesFuture);
        Map<String, String> cookies = cookiesFuture.get(2, TimeUnit.SECONDS);
        assertNotNull(cookies);
        assertTrue(cookies.isEmpty());

        CompletableFuture<String> singleCookieFuture =
                KromiumCookieManager.getCookieAsync("about:blank", "session_id");
        assertNotNull(singleCookieFuture);
        String cookie = singleCookieFuture.get(2, TimeUnit.SECONDS);
        assertNull(cookie);
    }

    @Test
    public void testKromiumClientStaticHelpersAndLoadingListener() {
        // Test JOGL detection static accessor
        boolean jogl = KromiumClient.getHasJoglSupport();

        // Test download dir resolution static helper
        File defaultDownload = KromiumClient.resolveDefaultDownloadDirectory();
        assertNotNull(defaultDownload);
        assertTrue(defaultDownload.isAbsolute());

        // Test updateProxy (should return false gracefully when not initialized)
        boolean updated = Kromium.updateProxy(KromiumProxy.direct());
        assertFalse(updated);

        // Test SAM LoadingListener lambda creation
        KromiumLoadingListener listener = (isLoading, canGoBack, canGoForward) -> {
            assertNotNull(isLoading);
        };
        assertNotNull(listener);

        // Test catching KromiumException
        try {
            Kromium.newClient();
        } catch (KromiumException e) {
            assertNotNull(e.getMessage());
        }
    }
}
