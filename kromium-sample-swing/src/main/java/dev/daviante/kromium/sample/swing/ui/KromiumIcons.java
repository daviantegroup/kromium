package dev.daviante.kromium.sample.swing.ui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * High-DPI anti-aliased vector icons rendered in pure Java 2D.
 * Provides sharp, scalable monochrome (white / neutral) iconography matching Compose icons.
 */
public final class KromiumIcons {

    private KromiumIcons() {}

    public static Icon back(int size, Color color) {
        return new VectorIcon(size, size, (g2, w, h) -> {
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float midY = h / 2f;
            g2.draw(new Line2D.Float(w * 0.75f, midY, w * 0.25f, midY));
            Path2D.Float arrow = new Path2D.Float();
            arrow.moveTo(w * 0.50f, midY - h * 0.25f);
            arrow.lineTo(w * 0.25f, midY);
            arrow.lineTo(w * 0.50f, midY + h * 0.25f);
            g2.draw(arrow);
        });
    }

    public static Icon forward(int size, Color color) {
        return new VectorIcon(size, size, (g2, w, h) -> {
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float midY = h / 2f;
            g2.draw(new Line2D.Float(w * 0.25f, midY, w * 0.75f, midY));
            Path2D.Float arrow = new Path2D.Float();
            arrow.moveTo(w * 0.50f, midY - h * 0.25f);
            arrow.lineTo(w * 0.75f, midY);
            arrow.lineTo(w * 0.50f, midY + h * 0.25f);
            g2.draw(arrow);
        });
    }

    public static Icon reload(int size, Color color) {
        return new VectorIcon(size, size, (g2, w, h) -> {
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Arc2D.Float(w * 0.2f, h * 0.2f, w * 0.6f, h * 0.6f, 45, 270, Arc2D.OPEN));
            Path2D.Float arrow = new Path2D.Float();
            arrow.moveTo(w * 0.55f, h * 0.12f);
            arrow.lineTo(w * 0.82f, h * 0.28f);
            arrow.lineTo(w * 0.62f, h * 0.40f);
            g2.fill(arrow);
        });
    }

    public static Icon close(int size, Color color) {
        return new VectorIcon(size, size, (g2, w, h) -> {
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Line2D.Float(w * 0.25f, h * 0.25f, w * 0.75f, h * 0.75f));
            g2.draw(new Line2D.Float(w * 0.75f, h * 0.25f, w * 0.25f, h * 0.75f));
        });
    }

    public static Icon plus(int size, Color color) {
        return new VectorIcon(size, size, (g2, w, h) -> {
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Line2D.Float(w * 0.5f, h * 0.25f, w * 0.5f, h * 0.75f));
            g2.draw(new Line2D.Float(w * 0.25f, h * 0.5f, w * 0.75f, h * 0.5f));
        });
    }

    public static Icon lock(int size, Color color) {
        return new VectorIcon(size, size, (g2, w, h) -> {
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            // Shackle
            g2.draw(new Arc2D.Float(w * 0.30f, h * 0.18f, w * 0.40f, h * 0.40f, 0, 180, Arc2D.OPEN));
            // Body
            g2.fill(new RoundRectangle2D.Float(w * 0.22f, h * 0.45f, w * 0.56f, h * 0.42f, 4, 4));
        });
    }

    public static Icon download(int size, Color color) {
        return new VectorIcon(size, size, (g2, w, h) -> {
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            // Down arrow
            g2.draw(new Line2D.Float(w * 0.5f, h * 0.18f, w * 0.5f, h * 0.65f));
            Path2D.Float head = new Path2D.Float();
            head.moveTo(w * 0.32f, h * 0.48f);
            head.lineTo(w * 0.5f, h * 0.65f);
            head.lineTo(w * 0.68f, h * 0.48f);
            g2.draw(head);
            // Tray
            Path2D.Float tray = new Path2D.Float();
            tray.moveTo(w * 0.22f, h * 0.68f);
            tray.lineTo(w * 0.22f, h * 0.82f);
            tray.lineTo(w * 0.78f, h * 0.82f);
            tray.lineTo(w * 0.78f, h * 0.68f);
            g2.draw(tray);
        });
    }

    public static Icon inspect(int size, Color color) {
        return new VectorIcon(size, size, (g2, w, h) -> {
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            // Left bracket <
            Path2D.Float left = new Path2D.Float();
            left.moveTo(w * 0.38f, h * 0.28f);
            left.lineTo(w * 0.20f, h * 0.50f);
            left.lineTo(w * 0.38f, h * 0.72f);
            g2.draw(left);
            // Right bracket >
            Path2D.Float right = new Path2D.Float();
            right.moveTo(w * 0.62f, h * 0.28f);
            right.lineTo(w * 0.80f, h * 0.50f);
            right.lineTo(w * 0.62f, h * 0.72f);
            g2.draw(right);
            // Slash /
            g2.draw(new Line2D.Float(w * 0.56f, h * 0.24f, w * 0.44f, h * 0.76f));
        });
    }

    @FunctionalInterface
    public interface IconPainter {
        void paint(Graphics2D g2, int width, int height);
    }

    private static class VectorIcon implements Icon {
        private final int width;
        private final int height;
        private final IconPainter painter;

        public VectorIcon(int width, int height, IconPainter painter) {
            this.width = width;
            this.height = height;
            this.painter = painter;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            painter.paint(g2, width, height);
            g2.dispose();
        }

        @Override public int getIconWidth() { return width; }
        @Override public int getIconHeight() { return height; }
    }
}
