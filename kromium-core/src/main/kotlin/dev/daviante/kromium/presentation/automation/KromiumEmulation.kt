package dev.daviante.kromium.presentation.automation

import dev.daviante.kromium.core.logging.KromiumLogger
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame

private const val TAG = "KromiumEmulation"

/**
 * Headless environment normalization and desktop emulation utility.
 *
 * Ensures that off-screen (OSR) and headless Chromium instances provide a standard desktop
 * browser environment for high-fidelity rendering, testing, and automation:
 * - Emulates standard [navigator.webdriver] behavior consistent with interactive user sessions.
 * - Injects standard desktop [window.chrome] runtime objects.
 * - Populates [navigator.plugins] and [navigator.mimeTypes] with authentic desktop entries (e.g. PDF viewer).
 * - Configures standard desktop [navigator.languages] (e.g. `['en-US', 'en']`).
 * - Emulates standard [Notification.permission] query behavior.
 * - Standardizes WebGL vendor and renderer reporting for consistent hardware acceleration profiles.
 * - Propagates desktop environment properties to dynamically created `<iframe>` elements.
 */
object KromiumEmulation {

    /**
     * JavaScript payload injected into the top-level document and child iframes to normalize the headless environment.
     */
    val SCRIPT: String = """
        (function() {
            function patchWindow(win) {
                if (!win || !win.navigator) return;

                function makeNative(fn, name) {
                    try {
                        var str = 'function ' + (name || '') + '() { [native code] }';
                        Object.defineProperty(fn, 'toString', {
                            value: function() { return str; },
                            configurable: true,
                            writable: true
                        });
                    } catch (_) {}
                    return fn;
                }

                try {
                    // 1. Normalize navigator.webdriver for consistent interactive session behavior
                    try {
                        delete win.Navigator.prototype.webdriver;
                    } catch (_) {}
                    try {
                        Object.defineProperty(win.Navigator.prototype, 'webdriver', {
                            get: makeNative(function() { return undefined; }, 'get webdriver'),
                            configurable: true,
                            enumerable: true
                        });
                    } catch (_) {}
                    try {
                        Object.defineProperty(win.navigator, 'webdriver', {
                            get: makeNative(function() { return undefined; }, 'get webdriver'),
                            configurable: true,
                            enumerable: true
                        });
                    } catch (_) {}
                } catch (_) {}

                try {
                    // 2. Standardize window.chrome desktop runtime objects
                    if (!win.chrome) {
                        win.chrome = {};
                    }
                    if (!win.chrome.runtime) {
                        win.chrome.runtime = {};
                    }
                    if (!win.chrome.loadTimes) {
                        win.chrome.loadTimes = makeNative(function() {
                            return {
                                requestTime: Date.now() / 1000,
                                startLoadTime: Date.now() / 1000,
                                commitLoadTime: Date.now() / 1000,
                                finishDocumentLoadTime: Date.now() / 1000,
                                finishLoadTime: Date.now() / 1000,
                                firstPaintTime: Date.now() / 1000,
                                firstPaintAfterLoadTime: 0,
                                navigationType: 'Other'
                            };
                        }, 'loadTimes');
                    }
                    if (!win.chrome.csi) {
                        win.chrome.csi = makeNative(function() {
                            return {
                                startE: Date.now(),
                                onloadT: Date.now(),
                                pageT: 100,
                                tran: 15
                            };
                        }, 'csi');
                    }
                } catch (_) {}

                try {
                    // 3. Configure standard desktop navigator.languages
                    Object.defineProperty(win.navigator, 'languages', {
                        get: makeNative(function() { return ['en-US', 'en']; }, 'get languages'),
                        configurable: true,
                        enumerable: true
                    });
                } catch (_) {}

                try {
                    // 4. Standardize navigator.plugins & mimeTypes with authentic PluginArray mockup
                    function fakePlugin(name, description, filename) {
                        return {
                            name: name,
                            description: description,
                            filename: filename,
                            length: 1,
                            item: function() { return this[0]; },
                            namedItem: function(n) { return (n === name) ? this[0] : null; }
                        };
                    }
                    var pdfPlugin = fakePlugin('PDF Viewer', 'Portable Document Format', 'internal-pdf-viewer');
                    var chromePdf = fakePlugin('Chrome PDF Viewer', 'Portable Document Format', 'internal-pdf-viewer');
                    var plugins = [pdfPlugin, chromePdf];
                    plugins.item = makeNative(function(i) { return plugins[i] || null; }, 'item');
                    plugins.namedItem = makeNative(function(n) {
                        for (var i = 0; i < plugins.length; i++) {
                            if (plugins[i].name === n) return plugins[i];
                        }
                        return null;
                    }, 'namedItem');
                    plugins.refresh = makeNative(function() {}, 'refresh');

                    if (win.PluginArray && win.PluginArray.prototype) {
                        try {
                            Object.setPrototypeOf(plugins, win.PluginArray.prototype);
                        } catch (_) {}
                    }
                    try {
                        Object.defineProperty(plugins, Symbol.toStringTag, { value: 'PluginArray' });
                    } catch (_) {}

                    Object.defineProperty(win.navigator, 'plugins', {
                        get: makeNative(function() { return plugins; }, 'get plugins'),
                        configurable: true,
                        enumerable: true
                    });
                } catch (_) {}

                try {
                    // 5. Standardize navigator.permissions.query for notifications
                    if (win.navigator.permissions && win.navigator.permissions.query) {
                        var origQuery = win.navigator.permissions.query;
                        win.navigator.permissions.query = makeNative(function(parameters) {
                            if (parameters && parameters.name === 'notifications') {
                                var perm = (win.Notification && win.Notification.permission) ? win.Notification.permission : 'default';
                                return Promise.resolve({
                                    state: perm === 'granted' ? 'granted' : (perm === 'denied' ? 'denied' : 'prompt'),
                                    onchange: null
                                });
                            }
                            return origQuery.apply(this, arguments);
                        }, 'query');
                    }
                } catch (_) {}

                try {
                    // 6. Standardize WebGL Vendor and Renderer
                    var getParamVendor = 37445; // UNMASKED_VENDOR_WEBGL
                    var getParamRenderer = 37446; // UNMASKED_RENDERER_WEBGL

                    function patchContext(proto) {
                        if (!proto || !proto.getParameter) return;
                        var origGetParameter = proto.getParameter;
                        proto.getParameter = makeNative(function(param) {
                            if (param === getParamVendor) {
                                return 'Intel Inc.';
                            }
                            if (param === getParamRenderer) {
                                return 'Intel(R) Iris(TM) Plus Graphics 640';
                            }
                            return origGetParameter.apply(this, arguments);
                        }, 'getParameter');
                    }

                    if (win.WebGLRenderingContext) {
                        patchContext(win.WebGLRenderingContext.prototype);
                    }
                    if (win.WebGL2RenderingContext) {
                        patchContext(win.WebGL2RenderingContext.prototype);
                    }
                } catch (_) {}
            }

            // Apply to main window
            patchWindow(window);

            // 7. Ensure child iframes inherit desktop environment properties
            try {
                var origContentWindow = Object.getOwnPropertyDescriptor(HTMLIFrameElement.prototype, 'contentWindow');
                if (origContentWindow && origContentWindow.get) {
                    Object.defineProperty(HTMLIFrameElement.prototype, 'contentWindow', {
                        get: function() {
                            var win = origContentWindow.get.call(this);
                            if (win) {
                                try { patchWindow(win); } catch (_) {}
                            }
                            return win;
                        },
                        configurable: true,
                        enumerable: true
                    });
                }
            } catch (_) {}
        })();
    """.trimIndent()

    /**
     * Injects the desktop emulation script into the specified [CefFrame] (defaults to main frame).
     */
    @JvmStatic
    fun inject(frame: CefFrame?) {
        if (frame == null) return
        try {
            frame.executeJavaScript(SCRIPT, frame.url ?: "about:blank", 0)
        } catch (e: Throwable) {
            KromiumLogger.w(TAG, "Failed to inject desktop emulation script into frame: ${e.message}")
        }
    }

    /**
     * Injects the desktop emulation script into the active browser's main frame.
     */
    @JvmStatic
    fun inject(browser: CefBrowser?) {
        inject(browser?.mainFrame)
    }
}
