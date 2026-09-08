package dev.daviante.kromium.sample.swing.theme;

import java.awt.Color;
import java.awt.Font;

/**
 * Shared design tokens matching the exact monochrome (black & white) design
 * of the Kromium Compose application.
 */
public final class KromiumTheme {

    private KromiumTheme() {}

    public static final Color BACKGROUND = new Color(0x0F, 0x0F, 0x0F);
    public static final Color SURFACE = new Color(0x18, 0x18, 0x18);
    public static final Color SURFACE_ELEVATED = new Color(0x22, 0x22, 0x22);
    public static final Color SURFACE_HIGHLIGHT = new Color(0x2D, 0x2D, 0x2D);
    public static final Color BORDER = new Color(0x33, 0x33, 0x33);
    public static final Color BORDER_SUBTLE = new Color(0x20, 0x20, 0x20);

    public static final Color TEXT_PRIMARY = Color.WHITE;
    public static final Color TEXT_SECONDARY = new Color(0xA0, 0xA0, 0xA0);
    public static final Color TEXT_MUTED = new Color(0x66, 0x66, 0x66);

    public static final Font FONT_SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    public static final Font FONT_REGULAR = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    public static final Font FONT_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    public static final Font FONT_TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 13);
}
