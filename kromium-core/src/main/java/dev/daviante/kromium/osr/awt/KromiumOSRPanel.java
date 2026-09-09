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
    private boolean isPopupVisible;

    private final Object bufferLock = new Object();

    public KromiumOSRPanel() {
        setOpaque(true);
        // We handle our own back-buffer for tearing-free rendering, so Swing's isn't strictly necessary 
        // but keeping it true helps integrate with complex Swing layouts safely.
        setDoubleBuffered(true);
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

        // Reallocate the back buffer if the browser window size changes
        if (backBuffer == null || backBuffer.getWidth() != width || backBuffer.getHeight() != height) {
            backBuffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
            backBufferData = ((DataBufferInt) backBuffer.getRaster().getDataBuffer()).getData();
        }

        // Convert the ByteBuffer to an IntBuffer for ultra-fast CPU array copying
        IntBuffer intBuffer = buffer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        
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
        if (popupBuffer == null || popupBuffer.getWidth() != width || popupBuffer.getHeight() != height) {
            popupBuffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
            popupBufferData = ((DataBufferInt) popupBuffer.getRaster().getDataBuffer()).getData();
        }
        
        IntBuffer intBuffer = buffer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        int pixelsToCopy = Math.min(intBuffer.remaining(), popupBufferData.length);
        intBuffer.get(popupBufferData, 0, pixelsToCopy);
        
        repaint();
    }
    
    public void setPopupBounds(Rectangle rect) {
        this.popupRect = rect;
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
                    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                }
                // Java2D draws the image directly into the Swing hierarchy
                g.drawImage(frontBuffer, 0, 0, getWidth(), getHeight(), null);
            }
        }
        
        if (isPopupVisible && popupBuffer != null && popupRect != null) {
            // popupRect contains the logical coordinates and size provided by JCEF.
            // Swing Graphics 'g' operates in logical coordinates.
            // Java2D will automatically handle downscaling the high-res popupBuffer to fit the logical rect.
            g.drawImage(popupBuffer, popupRect.x, popupRect.y, popupRect.width, popupRect.height, null);
        }
    }
}