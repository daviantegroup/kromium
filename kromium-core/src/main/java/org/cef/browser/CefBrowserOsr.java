package org.cef.browser;

import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.callback.CefDragData;
import org.cef.handler.CefRenderHandler;
import org.cef.handler.CefScreenInfo;
import org.cef.handler.CefAcceleratedPaintInfo;

import dev.daviante.kromium.osr.awt.KromiumOSRPanel;

import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.event.*;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class CefBrowserOsr extends CefBrowser_N implements CefRenderHandler {
    private final KromiumOSRPanel canvas_;
    private boolean justCreated_ = false;
    private Rectangle browser_rect_ = new Rectangle(0, 0, 1, 1);
    private Point screenPoint_ = new Point(0, 0);
    private double scaleFactor_ = detectDefaultScaleFactor();
    private boolean autoDetectScaleFactor_ = true;
    private double scrollMultiplier_ = 1.0;
    private double scrollRemainder_ = 0.0;
    private static final boolean IS_MAC = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).contains("mac");
    private int depth = 32;
    private int depth_per_component = 8;
    private boolean isTransparent_;
    private CopyOnWriteArrayList<Consumer<CefPaintEvent>> onPaintListeners = new CopyOnWriteArrayList<>();

    private static double detectDefaultScaleFactor() {
        try {
            return GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getDefaultTransform().getScaleX();
        } catch (Throwable t) {
            return 1.0;
        }
    }

    CefBrowserOsr(CefClient client, String url, boolean transparent, CefRequestContext context, CefBrowserSettings settings) {
        this(client, url, transparent, context, null, null, settings);
    }

    private CefBrowserOsr(CefClient client, String url, boolean transparent, CefRequestContext context, CefBrowserOsr parent, Point inspectAt, CefBrowserSettings settings) {
        super(client, url, context, parent, inspectAt, settings);
        this.isTransparent_ = transparent;
        
        this.canvas_ = new KromiumOSRPanel() {
            @Override
            public void paint(Graphics g) {
                if (g instanceof Graphics2D) {
                    Graphics2D g2d = (Graphics2D) g;
                    GraphicsConfiguration config = g2d.getDeviceConfiguration();
                    depth = config.getColorModel().getPixelSize();
                    depth_per_component = config.getColorModel().getComponentSize()[0];
                    AffineTransform transform = g2d.getTransform();
                    
                    if (autoDetectScaleFactor_) {
                        double newScaleFactor = transform.getScaleX();
                        if (newScaleFactor <= 0.0) {
                            newScaleFactor = detectDefaultScaleFactor();
                        }
                        if (scaleFactor_ != newScaleFactor) {
                            scaleFactor_ = newScaleFactor;
                            notifyScreenInfoChanged();
                            wasResized(getWidth(), getHeight());
                        }
                    }
                }
            
                createBrowserIfRequired(true);
                super.paint(g);
            }
        };
        
        // Notify JCEF when the canvas size changes
        this.canvas_.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                browser_rect_.setBounds(0, 0, canvas_.getWidth(), canvas_.getHeight());
                if (getNativeRef("CefBrowser") != 0) {
                    wasResized(canvas_.getWidth(), canvas_.getHeight());
                }
            }
            @Override
            public void componentMoved(ComponentEvent e) {
                if (canvas_.isShowing()) {
                    screenPoint_ = canvas_.getLocationOnScreen();
                }
            }
        });

        // Forward Focus
        this.canvas_.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) { setFocus(true); }
            @Override
            public void focusLost(FocusEvent e) { setFocus(false); }
        });

        // Forward Mouse Events
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!canvas_.hasFocus()) {
                    canvas_.requestFocusInWindow();
                }
                sendMouseEvent(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) { sendMouseEvent(e); }
            @Override
            public void mouseEntered(MouseEvent e) { sendMouseEvent(e); }
            @Override
            public void mouseExited(MouseEvent e) { sendMouseEvent(e); }
            @Override
            public void mouseClicked(MouseEvent e) { sendMouseEvent(e); }
            @Override
            public void mouseMoved(MouseEvent e) { sendMouseEvent(e); }
            @Override
            public void mouseDragged(MouseEvent e) { sendMouseEvent(e); }
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                // High-precision scroll handling for macOS trackpads / Magic Mouse and smooth Windows scrolling.
                // On macOS, trackpads dispatch high-frequency events (~60-120Hz) with fractional preciseWheelRotation.
                // Java AWT's integer getWheelRotation() returns 0 for most of those events until thresholding,
                // which previously caused an artificial dead-zone ("stuck" / delayed response).
                double rotation = e.getPreciseWheelRotation();
                if (rotation == 0.0) {
                    rotation = e.getWheelRotation();
                }

                int scrollAmount = Math.max(1, e.getScrollAmount());
                double basePixelsPerUnit = IS_MAC ? 24.0 : 28.0;
                double totalDelta = (rotation * scrollAmount * basePixelsPerUnit * scrollMultiplier_) + scrollRemainder_;
                int deltaPixels = (int) totalDelta;
                scrollRemainder_ = totalDelta - deltaPixels;

                if (deltaPixels != 0) {
                    // JCEF's native SendMouseWheelEvent invokes getUnitsToScroll() (= scrollAmount * wheelRotation).
                    // By passing scrollAmount = 1 and wheelRotation = deltaPixels, JCEF receives exactly deltaPixels.
                    MouseWheelEvent smoothedEvent = new MouseWheelEvent(
                        (java.awt.Component) e.getSource(),
                        e.getID(),
                        e.getWhen(),
                        e.getModifiersEx(),
                        e.getX(),
                        e.getY(),
                        e.getClickCount(),
                        e.isPopupTrigger(),
                        MouseWheelEvent.WHEEL_UNIT_SCROLL,
                        1,
                        deltaPixels
                    );
                    sendMouseWheelEvent(smoothedEvent);
                }
            }
        };
        this.canvas_.addMouseListener(mouseAdapter);
        this.canvas_.addMouseMotionListener(mouseAdapter);
        this.canvas_.addMouseWheelListener(mouseAdapter);

        // Forward Keyboard Events and handle system shortcuts in OSR mode via KromiumShortcutHandler
        this.canvas_.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (dev.daviante.kromium.presentation.keyboard.KromiumShortcutHandler.handleAwtKeyEvent(CefBrowserOsr.this, e)) {
                    return;
                }
                sendKeyEvent(e);
            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (dev.daviante.kromium.presentation.keyboard.KromiumShortcutHandler.handleAwtKeyEvent(CefBrowserOsr.this, e)) {
                    return;
                }
                sendKeyEvent(e);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (dev.daviante.kromium.presentation.keyboard.KromiumShortcutHandler.handleAwtKeyEvent(CefBrowserOsr.this, e)) {
                    return;
                }
                sendKeyEvent(e);
            }
        });
        
        this.canvas_.setFocusable(true);
        this.canvas_.setRequestFocusEnabled(true);
    }

    public void setScaleFactor(double factor) {
        if (factor > 0.0 && (this.scaleFactor_ != factor || this.autoDetectScaleFactor_)) {
            this.scaleFactor_ = factor;
            this.autoDetectScaleFactor_ = false;
            notifyScreenInfoChanged();
            if (canvas_.getWidth() > 0 && canvas_.getHeight() > 0) {
                wasResized(canvas_.getWidth(), canvas_.getHeight());
            }
        }
    }

    public void setAutoDetectScaleFactor(boolean autoDetect) {
        if (this.autoDetectScaleFactor_ != autoDetect) {
            this.autoDetectScaleFactor_ = autoDetect;
            if (autoDetect) {
                this.scaleFactor_ = detectDefaultScaleFactor();
                notifyScreenInfoChanged();
                if (canvas_.getWidth() > 0 && canvas_.getHeight() > 0) {
                    wasResized(canvas_.getWidth(), canvas_.getHeight());
                }
            }
        }
    }

    public boolean isAutoDetectScaleFactor() {
        return autoDetectScaleFactor_;
    }

    public double getScaleFactor() {
        return scaleFactor_;
    }

    public KromiumOSRPanel getOSRPanel() {
        return canvas_;
    }

    public void setScrollMultiplier(double multiplier) {
        if (multiplier > 0.0) {
            this.scrollMultiplier_ = multiplier;
        }
    }

    public double getScrollMultiplier() {
        return scrollMultiplier_;
    }

    @Override
    public void createImmediately() {
        justCreated_ = true;
        createBrowserIfRequired(false);
    }

    @Override
    public Component getUIComponent() {
        return canvas_;
    }

    @Override
    public CefRenderHandler getRenderHandler() {
        return this;
    }

    @Override
    protected CefBrowser createDevToolsBrowser(CefClient client, String url, CefRequestContext context, CefBrowser parent, Point inspectAt) {
        return new CefBrowserOsr(client, url, isTransparent_, context, (CefBrowserOsr) this, inspectAt, null);
    }

    private synchronized long getWindowHandle() {
        return 0; 
    }

    @Override
    public Rectangle getViewRect(CefBrowser browser) {
        return new Rectangle(0, 0, Math.max(1, canvas_.getWidth()), Math.max(1, canvas_.getHeight()));
    }

    @Override
    public Point getScreenPoint(CefBrowser browser, Point viewPoint) {
        Point p = new Point(screenPoint_);
        p.translate(viewPoint.x, viewPoint.y);
        return p;
    }

    @Override
    public double getDeviceScaleFactor(CefBrowser browser) {
        return scaleFactor_;
    }

    @Override
    public void onPopupShow(CefBrowser browser, boolean show) {
        canvas_.setPopupVisible(show);
    }

    @Override
    public void onPopupSize(CefBrowser browser, Rectangle size) {
        canvas_.setPopupBounds(size);
    }

    @Override
    public void addOnPaintListener(Consumer<CefPaintEvent> listener) {
        onPaintListeners.add(listener);
    }

    @Override
    public void setOnPaintListener(Consumer<CefPaintEvent> listener) {
        onPaintListeners.clear();
        onPaintListeners.add(listener);
    }

    @Override
    public void removeOnPaintListener(Consumer<CefPaintEvent> listener) {
        onPaintListeners.remove(listener);
    }

    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height) {
        canvas_.onPaint(buffer, width, height, popup);
        
        if (!onPaintListeners.isEmpty()) {
            CefPaintEvent event = new CefPaintEvent(browser, popup, dirtyRects, buffer, width, height);
            for (Consumer<CefPaintEvent> listener : onPaintListeners) {
                listener.accept(event);
            }
        }
    }

    @Override
    public void onAcceleratedPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, CefAcceleratedPaintInfo sharedHandle) {}

    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) {
        return true;
    }

    @Override
    public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
        return false;
    }

    @Override
    public void updateDragCursor(CefBrowser browser, int operation) {}

    private void createBrowserIfRequired(boolean hasParent) {
        if (getNativeRef("CefBrowser") == 0) {
            if (getParentBrowser() != null) {
                createDevTools(getParentBrowser(), getClient(), 0, true, isTransparent_, null, getInspectAt());
            } else {
                createBrowser(getClient(), 0, getUrl(), true, isTransparent_, null);
            }
        } else if (hasParent && justCreated_) {
            notifyAfterParentChanged();
            setFocus(true);
            justCreated_ = false;
        }
    }

    private void notifyAfterParentChanged() {
        getClient().onAfterParentChanged(this);
    }

    @Override
    public boolean getScreenInfo(CefBrowser browser, CefScreenInfo screenInfo) {
        screenInfo.Set(scaleFactor_, depth, depth_per_component, false, browser_rect_.getBounds(), browser_rect_.getBounds());
        return true;
    }

    @Override
    public CompletableFuture<BufferedImage> createScreenshot(boolean nativeResolution) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isWindowless() {
        return true;
    }
}