package dev.daviante.kromium.osr.awt;

import javax.swing.JPanel;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A 100% Pure Java Drop-in Panel for JCEF Off-Screen Rendering (OSR).
 * This replaces JOGL and native C bindings by drawing the Chromium ByteBuffer
 * directly into a Java2D BufferedImage, ensuring perfect Swing Z-ordering compatibility.
 */
public class KromiumOSRPanel extends JPanel {

    private BufferedImage frontBuffer;
    private BufferedImage backBuffer;
    private int[] backBufferData;
    
    // Popup support
    private BufferedImage popupBuffer;
    private int[] popupBufferData;
    private Rectangle popupRect;
    private volatile boolean isPopupVisible;

    // Configurable rendering properties with optimal defaults
    private volatile int bufferedImageType = BufferedImage.TYPE_INT_ARGB_PRE;
    private volatile ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
    private final Map<RenderingHints.Key, Object> customRenderingHints = new ConcurrentHashMap<>();

    private final Object bufferLock = new Object();

    public KromiumOSRPanel() {
        setOpaque(true);
        // We handle our own back-buffer for tearing-free rendering, so Swing's isn't strictly necessary 
        // but keeping it true helps integrate with complex Swing layouts safely.
        setDoubleBuffered(true);
    }

    /**
     * Gets the current BufferedImage type used for rasterization.
     */
    public int getBufferedImageType() {
        return bufferedImageType;
    }

    /**
     * Overrides the BufferedImage type used for the OSR raster buffer (default is BufferedImage.TYPE_INT_ARGB_PRE).
     */
    public void setBufferedImageType(int type) {
        if (this.bufferedImageType != type) {
            this.bufferedImageType = type;
            synchronized (bufferLock) {
                frontBuffer = null;
                backBuffer = null;
                popupBuffer = null;
            }
            repaint();
        }
    }

    /**
     * Gets the byte order used when interpreting the Chromium native pixel buffer.
     */
    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    /**
     * Overrides the byte order for the pixel buffer (default is ByteOrder.LITTLE_ENDIAN).
     */
    public void setByteOrder(ByteOrder byteOrder) {
        this.byteOrder = byteOrder != null ? byteOrder : ByteOrder.LITTLE_ENDIAN;
        repaint();
    }

    /**
     * Sets a custom Java2D RenderingHint on the OSR panel (e.g. KEY_INTERPOLATION, KEY_ANTIALIASING).
     * Pass null as value to remove the hint.
     */
    public void setRenderingHint(RenderingHints.Key key, Object value) {
        if (value != null) {
            customRenderingHints.put(key, value);
        } else {
            customRenderingHints.remove(key);
        }
        repaint();
    }

    /**
     * Gets a custom RenderingHint configured on this OSR panel.
     */
    public Object getRenderingHint(RenderingHints.Key key) {
        return customRenderingHints.get(key);
    }

    /**
     * Convenience method to configure the Java2D image scaling interpolation hint
     * (e.g. RenderingHints.VALUE_INTERPOLATION_BILINEAR, RenderingHints.VALUE_INTERPOLATION_BICUBIC, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR).
     */
    public void setInterpolation(Object interpolationHint) {
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolationHint);
    }

    /**
     * Connect this method to your CefRenderHandler's onPaint event.
     * 
     * @param buffer The BGRA pixel buffer from JCEF
     * @param width  The width of the frame
     * @param height The height of the frame
     * @param isPopup Whether this is a popup paint event
     */
    public void onPaint(ByteBuffer buffer, int width, int height, boolean isPopup) {
        if (isPopup) {
            handlePopupPaint(buffer, width, height);
            return;
        }

        // Reallocate the back buffer if the browser window size or image type changes
        if (backBuffer == null || backBuffer.getWidth() != width || backBuffer.getHeight() != height || backBuffer.getType() != bufferedImageType) {
            backBuffer = new BufferedImage(width, height, bufferedImageType);
            backBufferData = ((DataBufferInt) backBuffer.getRaster().getDataBuffer()).getData();
        }

        // Convert the ByteBuffer to an IntBuffer for ultra-fast CPU array copying
        IntBuffer intBuffer = buffer.order(byteOrder).asIntBuffer();
        
        // Ensure we don't read out of bounds
        int pixelsToCopy = Math.min(intBuffer.remaining(), backBufferData.length);
        intBuffer.get(backBufferData, 0, pixelsToCopy);

        // Atomically swap the fully rendered back buffer to the front buffer
        synchronized (bufferLock) {
            BufferedImage temp = frontBuffer;
            frontBuffer = backBuffer;
            // The old front buffer becomes the new back buffer to reuse its memory allocation
            backBuffer = temp;
            if (backBuffer != null) {
                backBufferData = ((DataBufferInt) backBuffer.getRaster().getDataBuffer()).getData();
            }
        }

        // Tell Swing this component needs to be redrawn
        repaint();
    }
    
    private void handlePopupPaint(ByteBuffer buffer, int width, int height) {
        synchronized (bufferLock) {
            if (popupBuffer == null || popupBuffer.getWidth() != width || popupBuffer.getHeight() != height || popupBuffer.getType() != bufferedImageType) {
                popupBuffer = new BufferedImage(width, height, bufferedImageType);
                popupBufferData = ((DataBufferInt) popupBuffer.getRaster().getDataBuffer()).getData();
            }
            
            IntBuffer intBuffer = buffer.order(byteOrder).asIntBuffer();
            int pixelsToCopy = Math.min(intBuffer.remaining(), popupBufferData.length);
            intBuffer.get(popupBufferData, 0, pixelsToCopy);
        }
        
        repaint();
    }
    
    public void setPopupBounds(Rectangle rect) {
        synchronized (bufferLock) {
            this.popupRect = rect;
        }
    }
    
    public void setPopupVisible(boolean visible) {
        this.isPopupVisible = visible;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        synchronized (bufferLock) {
            if (frontBuffer != null) {
                if (g instanceof Graphics2D g2d) {
                    // Apply baseline high-quality hints
                    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    // Apply developer custom hint overrides
                    for (Map.Entry<RenderingHints.Key, Object> entry : customRenderingHints.entrySet()) {
                        g2d.setRenderingHint(entry.getKey(), entry.getValue());
                    }
                }
                // Java2D draws the image directly into the Swing hierarchy
                g.drawImage(frontBuffer, 0, 0, getWidth(), getHeight(), null);
            }
            
            if (isPopupVisible && popupBuffer != null && popupRect != null) {
                // popupRect contains the logical coordinates and size provided by JCEF.
                // Swing Graphics 'g' operates in logical coordinates.
                // Java2D will automatically handle downscaling the high-res popupBuffer to fit the logical rect.
                g.drawImage(popupBuffer, popupRect.x, popupRect.y, popupRect.width, popupRect.height, null);
            }
        }
    }
}