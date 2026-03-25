package dev.lvstrng.argon.gui;

import java.awt.*;

/**
 * Centralized theme constants for the Argon ClickGUI.
 * Redesigned with more breathing room, cleaner spacing, and improved layout.
 */
public final class GuiTheme {

    private GuiTheme() {}

    // ── Panel / Window geometry ───────────────────────────────────────────
    /** Width of each category window (module list). */
    public static final int WINDOW_W     = 220;
    /**
     * Height of the category header row and each module row.
     * Increased to 40 for more breathing room between module names.
     */
    public static final int ROW_H        = 40;
    /** Corner radius used for windows, buttons, chips. */
    public static final int RADIUS       = 8;

    // ── Colours ───────────────────────────────────────────────────────────
    /** Primary window background. */
    public static final Color BG         = new Color(11, 11, 15, 220);
    /** Slightly lighter, used for the header. */
    public static final Color BG_HEADER  = new Color(16, 16, 22, 235);
    /** Module row background. */
    public static final Color BG_ROW     = new Color(10, 10, 14, 205);
    /** Settings row background (darker indent). */
    public static final Color BG_SETTING = new Color(8,  8, 11, 200);
    /** Drop-shadow tint. */
    public static final Color SHADOW     = new Color(0, 0, 0, 90);
    /** Subtle separator / divider. */
    public static final Color DIVIDER    = new Color(255, 255, 255, 10);
    /** Text colour for labels and settings. */
    public static final Color TEXT_DIM   = new Color(175, 175, 188);
    /** Placeholder / hint text. */
    public static final Color TEXT_HINT  = new Color(65, 65, 80);

    // ── Toolbar layout ────────────────────────────────────────────────────
    /** Top margin for the toolbar (search bar + save button). */
    public static final int   TOOLBAR_Y  = 14;

    // ── Search bar ────────────────────────────────────────────────────────
    public static final int   SEARCH_W   = 250;
    public static final int   SEARCH_H   = 28;
    /** Gap between search bar and save button. */
    public static final int   SEARCH_GAP = 8;

    // ── Save button ───────────────────────────────────────────────────────
    public static final int   SAVE_W     = 72;
    public static final int   SAVE_H     = 28;

    // ── Toast notification ────────────────────────────────────────────────
    public static final long  TOAST_MS   = 1800;

    // ── Glow / shadow passes ──────────────────────────────────────────────
    /** Number of gradient samples for rounded quads. */
    public static final int   SAMPLES    = 14;

    // ── Watermark (bottom-left) ───────────────────────────────────────────
    public static final String CLIENT_NAME    = "Private Client";
    public static final Color  WATERMARK_TEXT = new Color(195, 195, 210, 155);

    // ── Category windows initial start X ─────────────────────────────────
    /** Horizontal gap between category windows. */
    public static final int   WINDOW_GAP  = 16;

    // ── Per-category accent hues (HSB-derived, layered over main accent) ──
    /**
     * Returns a per-category accent colour offset so categories can each
     * carry a slightly different hue while still respecting the user's
     * global colour setting.
     */
    public static int categoryColorOffset(dev.lvstrng.argon.module.Category cat) {
        return switch (cat) {
            case COMBAT -> 0;
            case MISC   -> 3;
            case RENDER -> 6;
            case CLIENT -> 9;
        };
    }
}
