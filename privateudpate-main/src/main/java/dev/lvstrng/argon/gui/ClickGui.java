package dev.lvstrng.argon.gui;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.modules.client.ClickGUI;
import dev.lvstrng.argon.utils.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static dev.lvstrng.argon.Argon.mc;

/**
 * Main ClickGUI screen — redesigned with a top panel bar that contains
 * the client name, search field, and save button, plus wider / taller
 * category windows with generous row height so text never feels cramped.
 */
public final class ClickGui extends Screen {

    // ── Windows (one per category) ────────────────────────────────────────
    public List<Window> windows = new ArrayList<>();

    // ── Background dim colour (animated) ─────────────────────────────────
    public Color currentColor;

    // ── Search state ──────────────────────────────────────────────────────
    public String  searchQuery   = "";
    public boolean searchFocused = false;

    // ── Toast state ───────────────────────────────────────────────────────
    private long saveNotifTime = 0;

    // ── Interactive colours (animated) ────────────────────────────────────
    private Color saveButtonColor = new Color(18, 18, 24, 200);

    // ── Top panel bar ─────────────────────────────────────────────────────
    /** Height of the decorative top panel (houses watermark + search + save). */
    private static final int TOP_BAR_H = 56;

    // ── Hint fade-in ──────────────────────────────────────────────────────
    private float hintAlpha = 0f;

    // ─────────────────────────────────────────────────────────────────────
    //  Constructor
    // ─────────────────────────────────────────────────────────────────────

    public ClickGui() {
        super(Text.empty());
        // Position windows below the top panel with generous gap
        int offsetX = 28;
        int startY  = TOP_BAR_H + 14;
        for (Category category : Category.values()) {
            windows.add(new Window(offsetX, startY, GuiTheme.WINDOW_W, GuiTheme.ROW_H, category, this));
            offsetX += GuiTheme.WINDOW_W + GuiTheme.WINDOW_GAP;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Rendering
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (mc.currentScreen != this) return;

        if (Argon.INSTANCE.previousScreen != null)
            Argon.INSTANCE.previousScreen.render(context, 0, 0, delta);

        // Animated background dim
        if (currentColor == null) currentColor = new Color(0, 0, 0, 0);
        else currentColor = new Color(0, 0, 0, currentColor.getAlpha());
        int targetAlpha = ClickGUI.background.getValue() ? 160 : 0;
        if (currentColor.getAlpha() != targetAlpha)
            currentColor = ColorUtils.smoothAlphaTransition(0.05f, targetAlpha, currentColor);
        if (currentColor.getAlpha() > 0)
            context.fill(0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight(), currentColor.getRGB());

        // Switch to framebuffer-pixel space
        RenderUtils.unscaledProjection();
        int smx = (int)(mouseX * mc.getWindow().getScaleFactor());
        int smy = (int)(mouseY * mc.getWindow().getScaleFactor());
        super.render(context, mouseX, mouseY, delta);

        int fw = mc.getWindow().getFramebufferWidth();
        int fh = mc.getWindow().getFramebufferHeight();

        // Top panel bar (full-width)
        renderTopBar(context, smx, smy, fw);

        // Category windows
        for (Window w : windows) {
            w.render(context, smx, smy, delta);
            w.updatePosition(smx, smy, delta);
        }

        // Keyboard hints (bottom-right)
        renderHints(context, fw, fh, delta);

        // Save toast (bottom-centre)
        renderToast(context, fw, fh);

        // Search result count (below search bar)
        renderSearchCount(context, fw);

        RenderUtils.scaledProjection();
    }

    // ── Top Bar ───────────────────────────────────────────────────────────

    /**
     * Full-width frosted-glass panel at the top.
     * Left  → client name watermark
     * Centre→ search field
     * Right → save button
     */
    private void renderTopBar(DrawContext context, int mouseX, int mouseY, int fw) {
        int fh  = TOP_BAR_H;
        int r   = GuiTheme.RADIUS;
        int s   = GuiTheme.SAMPLES;

        // Panel background (full-width, not rounded — blends with edges)
        context.fill(0, 0, fw, fh, new Color(13, 13, 18, 230).getRGB());

        // Subtle bottom border line (accent gradient)
        context.fillGradient(0, fh - 2, fw / 2, fh,
                Utils.getMainColor(90, 0).getRGB(),
                Utils.getMainColor(90, 5).getRGB());
        context.fillGradient(fw / 2, fh - 2, fw, fh,
                Utils.getMainColor(90, 5).getRGB(),
                Utils.getMainColor(90, 10).getRGB());

        // ── Watermark (left side of top bar) ──────────────────────────────
        renderWatermarkInBar(context, fh);

        // ── Search + Save (centred in top bar) ────────────────────────────
        int totalW = GuiTheme.SEARCH_W + GuiTheme.SEARCH_GAP + GuiTheme.SAVE_W;
        int sx     = fw / 2 - totalW / 2;
        int sy     = fh / 2 - GuiTheme.SEARCH_H / 2;
        renderSearchBar(context, mouseX, mouseY, sx, sy);
        renderSaveButton(context, mouseX, mouseY, sx + GuiTheme.SEARCH_W + GuiTheme.SEARCH_GAP, sy);
    }

    // ── Watermark (embedded in top bar) ──────────────────────────────────

    private void renderWatermarkInBar(DrawContext context, int barH) {
        CharSequence name = EncryptedString.of(GuiTheme.CLIENT_NAME);
        CharSequence dot  = EncryptedString.of(" \u00B7 ");
        CharSequence ver  = EncryptedString.of("client");

        int nw  = TextRenderer.getWidth(name);
        int dw  = TextRenderer.getWidth(dot);
        int vw  = TextRenderer.getWidth(ver);

        int px  = 22;
        int py  = barH / 2 + 4;   // vertically centred in bar

        // Subtle pill background behind the watermark text
        int padX = 10, padY = 6;
        RenderUtils.renderRoundedQuad(context.getMatrices(), new Color(8, 8, 12, 120),
                px - padX, py - padY - 4,
                px + nw + dw + vw + padX, py + padY - 2,
                5, 5, 5, 5, GuiTheme.SAMPLES);

        // Accent left stripe on pill
        context.fillGradient(
                px - padX, py - padY - 4,
                px - padX + 2, py + padY - 2,
                Utils.getMainColor(180, 0).getRGB(),
                Utils.getMainColor(180, 3).getRGB());

        TextRenderer.drawString(name, context, px, py, Utils.getMainColor(220, 0).getRGB());
        TextRenderer.drawString(dot,  context, px + nw, py, new Color(60, 60, 75, 220).getRGB());
        TextRenderer.drawString(ver,  context, px + nw + dw, py, GuiTheme.WATERMARK_TEXT.getRGB());
    }

    // ── Search Bar ────────────────────────────────────────────────────────

    private void renderSearchBar(DrawContext context, int mouseX, int mouseY, int sx, int sy) {
        int sw = GuiTheme.SEARCH_W;
        int sh = GuiTheme.SEARCH_H;
        int r  = GuiTheme.RADIUS;
        int s  = GuiTheme.SAMPLES;

        // Shadow
        RenderUtils.renderRoundedQuad(context.getMatrices(), new Color(0, 0, 0, 60),
                sx - 4, sy - 3, sx + sw + 4, sy + sh + 4, r + 1, r + 1, r + 1, r + 1, s);

        // Background
        RenderUtils.renderRoundedQuad(context.getMatrices(), new Color(20, 20, 28, 240),
                sx, sy, sx + sw, sy + sh, r, r, r, r, s);

        // Outline — glows when focused
        if (searchFocused)
            RenderUtils.renderRoundedOutline(context, Utils.getMainColor(200, 0),
                    sx, sy, sx + sw, sy + sh, r, r, r, r, 1.6, s);
        else
            RenderUtils.renderRoundedOutline(context, new Color(38, 38, 50, 130),
                    sx, sy, sx + sw, sy + sh, r, r, r, r, 1.0, s);

        // Search icon — vertically centred, padded from left
        CharSequence icon = EncryptedString.of("\u2315 ");
        int iconColor = searchFocused
                ? Utils.getMainColor(215, 1).getRGB()
                : GuiTheme.TEXT_HINT.getRGB();
        int textMidY = sy + sh / 2 + 4;
        TextRenderer.drawString(icon, context, sx + 10, textMidY, iconColor);

        // Vertical separator after icon
        int iconW = TextRenderer.getWidth(icon);
        context.fillGradient(
                sx + 10 + iconW + 2, sy + 6,
                sx + 10 + iconW + 3, sy + sh - 6,
                new Color(50, 50, 65, 0).getRGB(),
                new Color(50, 50, 65, 130).getRGB());
        context.fillGradient(
                sx + 10 + iconW + 2, sy + sh / 2,
                sx + 10 + iconW + 3, sy + sh - 6,
                new Color(50, 50, 65, 130).getRGB(),
                new Color(50, 50, 65, 0).getRGB());

        // Text / placeholder — starts after icon + separator
        int inputX = sx + 10 + iconW + 8;
        boolean cur = searchFocused && (System.currentTimeMillis() % 800 < 400);
        CharSequence display = (searchQuery.isEmpty() && !searchFocused)
                ? EncryptedString.of("Search modules...")
                : EncryptedString.of(searchQuery + (cur ? "|" : ""));
        int textColor = (searchQuery.isEmpty() && !searchFocused)
                ? GuiTheme.TEXT_HINT.getRGB()
                : Color.WHITE.getRGB();
        TextRenderer.drawString(display, context, inputX, textMidY, textColor);

        // Clear (×) button when query is non-empty
        if (!searchQuery.isEmpty()) {
            CharSequence x = EncryptedString.of("\u00D7");
            int xw = TextRenderer.getWidth(x);
            TextRenderer.drawString(x, context, sx + sw - xw - 9, textMidY,
                    new Color(100, 100, 118, 210).getRGB());
        }
    }

    // ── Save Button ───────────────────────────────────────────────────────

    private void renderSaveButton(DrawContext context, int mouseX, int mouseY, int bx, int sy) {
        int bw = GuiTheme.SAVE_W;
        int bh = GuiTheme.SAVE_H;
        int r  = GuiTheme.RADIUS;
        int s  = GuiTheme.SAMPLES;
        boolean hov = mouseX >= bx && mouseX <= bx + bw && mouseY >= sy && mouseY <= sy + bh;

        Color target = hov ? new Color(24, 24, 34, 245) : new Color(20, 20, 28, 240);
        saveButtonColor = ColorUtils.smoothColorTransition(0.14f, target, saveButtonColor);

        // Shadow
        RenderUtils.renderRoundedQuad(context.getMatrices(), new Color(0, 0, 0, 60),
                bx - 4, sy - 3, bx + bw + 4, sy + bh + 4, r + 1, r + 1, r + 1, r + 1, s);

        // Background
        RenderUtils.renderRoundedQuad(context.getMatrices(), saveButtonColor,
                bx, sy, bx + bw, sy + bh, r, r, r, r, s);

        // Accent fill on hover
        if (hov) {
            Color ac = Utils.getMainColor(18, 5);
            context.fill(bx, sy, bx + bw, sy + bh, ac.getRGB());
        }

        // Outline
        RenderUtils.renderRoundedOutline(context,
                hov ? Utils.getMainColor(210, 5) : new Color(38, 38, 50, 130),
                bx, sy, bx + bw, sy + bh, r, r, r, r, 1.0, s);

        // Label — centred both axes
        CharSequence label = EncryptedString.of("\u2713  Save");
        int lw = TextRenderer.getWidth(label);
        int lc = hov ? Utils.getMainColor(245, 4).getRGB() : new Color(155, 155, 172).getRGB();
        TextRenderer.drawString(label, context, bx + bw / 2 - lw / 2, sy + bh / 2 + 4, lc);
    }

    // ── Hints ─────────────────────────────────────────────────────────────

    private void renderHints(DrawContext context, int fw, int fh, float delta) {
        hintAlpha = Math.min(1f, hintAlpha + 0.018f * delta);
        if (hintAlpha < 0.01f) return;
        CharSequence h1 = EncryptedString.of("Ctrl+S");
        CharSequence h2 = EncryptedString.of("  \u00B7  Esc");
        CharSequence h3 = EncryptedString.of("  \u00B7  RMB");
        int baseAlpha = (int)(165 * hintAlpha);
        int dimAlpha  = (int)(90  * hintAlpha);
        int h1w = TextRenderer.getWidth(h1);
        int h2w = TextRenderer.getWidth(h2);
        int h3w = TextRenderer.getWidth(h3);
        int totalW = h1w + h2w + h3w;
        int tx = fw - totalW - 16;
        int ty = fh - 16;
        TextRenderer.drawString(h1, context, tx,           ty, new Color(130, 130, 148, baseAlpha).getRGB());
        TextRenderer.drawString(h2, context, tx + h1w,     ty, new Color(55, 55, 70,  dimAlpha).getRGB());
        TextRenderer.drawString(h3, context, tx + h1w + h2w, ty, new Color(55, 55, 70, dimAlpha).getRGB());
    }

    // ── Toast ─────────────────────────────────────────────────────────────

    private void renderToast(DrawContext context, int fw, int fh) {
        long elapsed = System.currentTimeMillis() - saveNotifTime;
        if (saveNotifTime <= 0 || elapsed >= GuiTheme.TOAST_MS) return;

        float p = (float) elapsed / GuiTheme.TOAST_MS;
        int alpha = p < 0.12f ? (int)(p / 0.12f * 215)
                  : p > 0.72f ? (int)((1f - (p - 0.72f) / 0.28f) * 215)
                  : 215;

        CharSequence text = EncryptedString.of("\u2713  Config Saved");
        int tw = TextRenderer.getWidth(text) + 32;
        int th = 30;
        int nx = fw / 2 - tw / 2;
        int ny = fh - 56;

        RenderUtils.renderRoundedQuad(context.getMatrices(), new Color(12, 12, 17, Math.min(alpha, 215)),
                nx, ny, nx + tw, ny + th, 6, 6, 6, 6, GuiTheme.SAMPLES);
        context.fillGradient(nx + 6, ny, nx + tw - 6, ny + 2,
                Utils.getMainColor(alpha, 0).getRGB(), Utils.getMainColor(alpha, 4).getRGB());
        TextRenderer.drawString(text, context,
                nx + tw / 2 - TextRenderer.getWidth(text) / 2, ny + th / 2 + 4,
                new Color(195, 195, 210, alpha).getRGB());
    }

    // ── Search result count ───────────────────────────────────────────────

    private void renderSearchCount(DrawContext context, int fw) {
        if (searchQuery.isEmpty()) return;
        int total = windows.stream()
                .mapToInt(w -> (int) w.moduleButtons.stream()
                        .filter(mb -> mb.matchesSearch(searchQuery)).count())
                .sum();
        CharSequence label = EncryptedString.of(total + " result" + (total != 1 ? "s" : ""));
        int lw = TextRenderer.getWidth(label);
        TextRenderer.drawString(label, context,
                fw / 2 - lw / 2, GuiTheme.TOOLBAR_Y + GuiTheme.SEARCH_H + 8,
                new Color(72, 72, 90, 205).getRGB());
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Config
    // ─────────────────────────────────────────────────────────────────────

    private void saveConfig() {
        try {
            Argon.INSTANCE.getProfileManager().saveProfile();
            saveNotifTime = System.currentTimeMillis();
        } catch (Exception ignored) {}
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────

    public boolean isDraggingAlready() {
        for (Window w : windows) if (w.dragging) return true;
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────────────────────────────────

    @Override protected void setInitialFocus() { if (client != null) super.setInitialFocus(); }
    @Override public boolean shouldPause()      { return false; }

    @Override
    public void close() {
        saveConfig();
        var clickGUIMod = Argon.INSTANCE.getModuleManager().getModule(ClickGUI.class);
        if (clickGUIMod != null) clickGUIMod.setEnabledStatus(false);
        onGuiClose();
    }

    public void onGuiClose() {
        mc.setScreenAndRender(Argon.INSTANCE.previousScreen);
        currentColor  = null;
        searchQuery   = "";
        searchFocused = false;
        hintAlpha     = 0f;
        for (Window w : windows) w.onGuiClose();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Input
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchFocused && chr >= 32 && chr != 127) { searchQuery += chr; return true; }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_S && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            saveConfig(); return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (searchFocused && !searchQuery.isEmpty()) { searchQuery  = ""; return true; }
            if (searchFocused)                          { searchFocused = false; return true; }
        }
        if (searchFocused && keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1); return true;
        }
        for (Window w : windows) w.keyPressed(keyCode, scanCode, modifiers);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double sx = mouseX * mc.getWindow().getScaleFactor();
        double sy = mouseY * mc.getWindow().getScaleFactor();

        int fw      = mc.getWindow().getFramebufferWidth();
        int totalW  = GuiTheme.SEARCH_W + GuiTheme.SEARCH_GAP + GuiTheme.SAVE_W;
        int searchX = fw / 2 - totalW / 2;
        int barY    = TOP_BAR_H / 2 - GuiTheme.SEARCH_H / 2;
        int saveX   = searchX + GuiTheme.SEARCH_W + GuiTheme.SEARCH_GAP;

        // Search bar click
        if (sx >= searchX && sx <= searchX + GuiTheme.SEARCH_W
                && sy >= barY && sy <= barY + GuiTheme.SEARCH_H) {
            searchFocused = true; return true;
        }
        // Save button click
        if (sx >= saveX && sx <= saveX + GuiTheme.SAVE_W
                && sy >= barY && sy <= barY + GuiTheme.SAVE_H) {
            saveConfig(); return true;
        }

        // Only unfocus search if clicking outside top bar entirely
        if (sy > TOP_BAR_H) searchFocused = false;

        for (Window w : windows) w.mouseClicked(sx, sy, button);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dX, double dY) {
        mouseX *= mc.getWindow().getScaleFactor();
        mouseY *= mc.getWindow().getScaleFactor();
        for (Window w : windows) w.mouseDragged(mouseX, mouseY, button, dX, dY);
        return super.mouseDragged(mouseX, mouseY, button, dX, dY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmt, double vAmt) {
        mouseY *= mc.getWindow().getScaleFactor();
        for (Window w : windows) w.mouseScrolled(mouseX, mouseY, hAmt, vAmt);
        return super.mouseScrolled(mouseX, mouseY, hAmt, vAmt);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mouseX *= mc.getWindow().getScaleFactor();
        mouseY *= mc.getWindow().getScaleFactor();
        for (Window w : windows) w.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
