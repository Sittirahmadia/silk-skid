package dev.lvstrng.argon.gui.components;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.gui.GuiTheme;
import dev.lvstrng.argon.gui.Window;
import dev.lvstrng.argon.gui.components.settings.*;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.modules.client.ClickGUI;
import dev.lvstrng.argon.module.setting.*;
import dev.lvstrng.argon.utils.*;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static dev.lvstrng.argon.Argon.mc;

/**
 * Renders a single module row inside a Window — redesigned.
 *
 * Row layout (ROW_H = 40, more breathing room):
 *
 *  ┌────────────────────────────────────────────────────┐
 *  │  ▌   ModuleName                    [KEY]  ▸       │
 *  └────────────────────────────────────────────────────┘
 *    ↑ 4 px accent bar (enabled only, full row height)
 *        ↑ name — left-padded 18 px, vertically centred
 *                                      ↑ keybind chip
 *                                             ↑ chevron (14 px from right)
 */
public final class ModuleButton {

    // ── Settings panels ───────────────────────────────────────────────────
    public List<RenderableSetting> settings = new ArrayList<>();

    // ── Refs ──────────────────────────────────────────────────────────────
    public Window parent;
    public Module module;

    // ── Layout ────────────────────────────────────────────────────────────
    public int     offset;
    public boolean extended;
    public int     settingOffset;

    // ── Animated colours ─────────────────────────────────────────────────
    public Color currentColor;
    public Color defaultColor  = new Color(185, 185, 198);
    public Color currentAlpha;
    public Color enabledFill;

    // ── Animation (controls expand height) ───────────────────────────────
    public AnimationUtils animation = new AnimationUtils(0);

    // ── Left padding / right padding constants ────────────────────────────
    private static final int LEFT_PAD    = 18;   // module name left padding
    private static final int RIGHT_PAD   = 12;   // right-side element margin
    private static final int BAR_W       = 4;    // accent bar width (px)

    // ─────────────────────────────────────────────────────────────────────
    //  Constructor
    // ─────────────────────────────────────────────────────────────────────

    public ModuleButton(Window parent, Module module, int offset) {
        this.parent   = parent;
        this.module   = module;
        this.offset   = offset;
        this.extended = false;

        settingOffset = parent.getHeight();
        for (Setting<?> s : module.getSettings()) {
            if      (s instanceof BooleanSetting bs)  settings.add(new CheckBox(this, bs, settingOffset));
            else if (s instanceof NumberSetting  ns)  settings.add(new Slider(this, ns, settingOffset));
            else if (s instanceof ModeSetting<?> ms)  settings.add(new ModeBox(this, ms, settingOffset));
            else if (s instanceof KeybindSetting ks)  settings.add(new KeybindBox(this, ks, settingOffset));
            else if (s instanceof StringSetting  ss)  settings.add(new StringBox(this, ss, settingOffset));
            else if (s instanceof MinMaxSetting  mms) settings.add(new MinMaxSlider(this, mms, settingOffset));
            settingOffset += parent.getHeight();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Rendering
    // ─────────────────────────────────────────────────────────────────────

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (parent.getY() + offset > mc.getWindow().getFramebufferHeight()) return;

        for (RenderableSetting rs : settings) rs.onUpdate();

        // ── Animated colours ──────────────────────────────────────────────
        if (currentColor == null) currentColor = new Color(14, 14, 18, 0);
        else currentColor = new Color(14, 14, 18, currentColor.getAlpha());
        currentColor = ColorUtils.smoothAlphaTransition(0.05f, 155, currentColor);

        int idx            = Argon.INSTANCE.getModuleManager().getModulesInCategory(module.getCategory()).indexOf(module);
        int co             = GuiTheme.categoryColorOffset(module.getCategory());
        Color accentTarget = module.isEnabled() ? Utils.getMainColor(255, idx + co) : new Color(178, 178, 192);
        if (!defaultColor.equals(accentTarget))
            defaultColor = ColorUtils.smoothColorTransition(0.1f, accentTarget, defaultColor);

        // Enabled fill tint
        Color fillTarget = module.isEnabled()
                ? new Color(Utils.getMainColor(255, idx + co).getRed(),
                            Utils.getMainColor(255, idx + co).getGreen(),
                            Utils.getMainColor(255, idx + co).getBlue(), 16)
                : new Color(0, 0, 0, 0);
        if (enabledFill == null) enabledFill = new Color(0, 0, 0, 0);
        enabledFill = ColorUtils.smoothColorTransition(0.08f, fillTarget, enabledFill);

        boolean isLast = parent.moduleButtons.get(parent.moduleButtons.size() - 1) == this;
        int r  = ClickGUI.roundQuads.getValueInt();
        int px = parent.getX();
        int py = parent.getY();
        int pw = parent.getWidth();
        int ph = parent.getHeight();
        int s  = GuiTheme.SAMPLES;

        // ── Row background ────────────────────────────────────────────────
        boolean roundBottom = isLast && (int) animation.getValue() <= ph;
        if (roundBottom) {
            RenderUtils.renderRoundedQuad(context.getMatrices(), currentColor,
                    px, py + offset, px + pw, py + ph + offset,
                    0, 0, r, r, s);
        } else {
            context.fill(px, py + offset, px + pw, py + ph + offset, currentColor.getRGB());
        }

        // Subtle alternating stripe (every other module, very faint)
        if (idx % 2 == 0) {
            context.fill(px, py + offset, px + pw, py + ph + offset,
                    new Color(255, 255, 255, 4).getRGB());
        }

        // Full-row enabled tint
        if (enabledFill.getAlpha() > 2)
            context.fill(px, py + offset, px + pw, py + ph + offset, enabledFill.getRGB());

        // ── Left accent bar (4 px, inset 0 px from left edge) ────────────
        int barAlpha = module.isEnabled() ? 240 : 25;
        int barY1 = py + offset + 5;
        int barY2 = py + offset + ph - 5;
        context.fillGradient(
                px,         barY1,
                px + BAR_W, barY2,
                Utils.getMainColor(barAlpha, idx + co).getRGB(),
                Utils.getMainColor(barAlpha, idx + co + 1).getRGB());

        // ── Row top/bottom thin separator line ────────────────────────────
        context.fill(px + BAR_W + 4, py + offset + ph - 1,
                px + pw, py + offset + ph,
                new Color(255, 255, 255, 7).getRGB());

        // ── Hover overlay ─────────────────────────────────────────────────
        renderHover(context, mouseX, mouseY);

        // ── Module name (left-aligned, vertically centred) ────────────────
        int nameX = px + LEFT_PAD;
        int nameY = py + offset + ph / 2 + 4;   // +4 shifts text down from absolute centre for visual balance
        TextRenderer.drawString(module.getName(), context, nameX, nameY, defaultColor.getRGB());

        // ── Right-side indicators ─────────────────────────────────────────
        renderRightSide(context, mouseX, mouseY, idx, co, px, py, pw, ph);

        // ── Settings (expandable) ─────────────────────────────────────────
        renderSettings(context, mouseX, mouseY, delta);

        if (extended)
            for (RenderableSetting rs : settings)
                rs.renderDescription(context, mouseX, mouseY, delta);

        // ── Description tooltip ───────────────────────────────────────────
        if (isHovered(mouseX, mouseY) && !parent.dragging && module.getDescription() != null)
            renderTooltip(context, idx, co);
    }

    /**
     * Draws the expand-chevron and keybind chip on the right side of the row.
     * Elements are laid out right-to-left with consistent spacing.
     */
    private void renderRightSide(DrawContext context, int mouseX, int mouseY,
                                  int idx, int co, int px, int py, int pw, int ph) {
        int nameY     = py + offset + ph / 2 + 4;
        int rightEdge = px + pw - RIGHT_PAD;

        // ── Expand/collapse chevron (only when module has settings) ────────
        if (!settings.isEmpty()) {
            CharSequence chevron = EncryptedString.of(extended ? "\u25BE" : "\u25B8");
            int chevColor = extended
                    ? Utils.getMainColor(215, idx + co).getRGB()
                    : new Color(58, 58, 75).getRGB();
            int chevW = TextRenderer.getWidth(chevron);
            TextRenderer.drawString(chevron, context, rightEdge - chevW, nameY, chevColor);
            rightEdge -= chevW + 8;   // wider gap after chevron
        }

        // ── Keybind chip (when module has a key bound and enabled) ─────────
        int key = module.getKey();
        if (module.isEnabled() && key > 0 && key != 256) {
            CharSequence keySeq = EncryptedString.of(KeyUtils.getKey(key).toString());
            int chipW = TextRenderer.getWidth(keySeq) + 12;
            int chipH = 13;
            int chipX = rightEdge - chipW - 4;
            int chipY = py + offset + (ph - chipH) / 2;
            Color accent = Utils.getMainColor(150, idx + co);

            // Chip background
            RenderUtils.renderRoundedQuad(context.getMatrices(),
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 24),
                    chipX, chipY, chipX + chipW, chipY + chipH,
                    3, 3, 3, 3, 8);
            // Chip outline
            RenderUtils.renderRoundedOutline(context,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 90),
                    chipX, chipY, chipX + chipW, chipY + chipH,
                    3, 3, 3, 3, 1.0, 8);
            // Key text
            TextRenderer.drawString(keySeq, context,
                    chipX + chipW / 2 - TextRenderer.getWidth(keySeq) / 2,
                    chipY + chipH - 2,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 205).getRGB());
        }
    }

    /** Bottom tooltip shown when hovering a module row. */
    private void renderTooltip(DrawContext context, int idx, int co) {
        CharSequence desc = module.getDescription();
        int tw = TextRenderer.getWidth(desc);
        int fw = mc.getWindow().getFramebufferWidth();
        int fh = mc.getWindow().getFramebufferHeight();
        int tx = fw / 2 - tw / 2;
        int ty = fh - 44;
        int s  = GuiTheme.SAMPLES;

        // Tooltip pill
        RenderUtils.renderRoundedQuad(context.getMatrices(), new Color(12, 12, 17, 230),
                tx - 12, ty - 16, tx + tw + 12, ty + 16, 6, 6, 6, 6, s);
        // Top accent stripe on tooltip
        context.fillGradient(tx - 12, ty - 16, tx + tw + 12, ty - 14,
                Utils.getMainColor(160, idx + co).getRGB(),
                Utils.getMainColor(160, idx + co + 2).getRGB());
        // Description text
        TextRenderer.drawString(desc, context, tx, ty, new Color(172, 172, 188).getRGB());
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Hover overlay
    // ─────────────────────────────────────────────────────────────────────

    private void renderHover(DrawContext context, int mouseX, int mouseY) {
        if (parent.dragging) return;
        int toA = isHovered(mouseX, mouseY) ? 20 : 0;
        if (currentAlpha == null) currentAlpha = new Color(255, 255, 255, toA);
        else currentAlpha = new Color(255, 255, 255, currentAlpha.getAlpha());
        if (currentAlpha.getAlpha() != toA)
            currentAlpha = ColorUtils.smoothAlphaTransition(0.05f, toA, currentAlpha);
        context.fill(parent.getX(), parent.getY() + offset,
                parent.getX() + parent.getWidth(), parent.getY() + parent.getHeight() + offset,
                currentAlpha.getRGB());
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Settings panel (scissor-clipped expand area)
    // ─────────────────────────────────────────────────────────────────────

    private void renderSettings(DrawContext context, int mouseX, int mouseY, float delta) {
        int scissorH = (int) animation.getValue() - parent.getHeight();
        int scissorW = parent.getWidth();
        if (scissorH <= 0 || scissorW <= 0) return;

        int scissorX = parent.getX();
        // In unscaled (framebuffer) space, GL scissor Y is measured from bottom
        int topY     = parent.getY() + offset + parent.getHeight();
        int fbH      = mc.getWindow().getFramebufferHeight();
        int scissorY = Math.max(0, fbH - (topY + scissorH));
        RenderSystem.enableScissor(scissorX, scissorY, scissorW, Math.max(0, scissorH));

        if (scissorH > 0) {
            // Accent indent line spanning the settings area
            int indentX  = parent.getX() + 8;
            int indentY1 = parent.getY() + offset + parent.getHeight();
            int indentY2 = parent.getY() + offset + (int) animation.getValue();
            int co       = GuiTheme.categoryColorOffset(module.getCategory());
            context.fillGradient(indentX, indentY1, indentX + 2, indentY2,
                    Utils.getMainColor(110, co).getRGB(),
                    Utils.getMainColor(30, co + 2).getRGB());

            for (RenderableSetting rs : settings)
                rs.render(context, mouseX, mouseY, delta);

            // Slider thumb circles
            for (RenderableSetting rs : settings) {
                if (rs instanceof Slider slider) {
                    drawSliderThumb(context, slider);
                } else if (rs instanceof MinMaxSlider slider) {
                    drawMinMaxThumb(context, slider);
                }
            }
        }
        RenderSystem.disableScissor();
    }

    private void drawSliderThumb(DrawContext context, Slider s) {
        double cx = s.parentX() + Math.max(s.lerpedOffsetX, 2.5);
        double cy = s.parentY() + s.offset + s.parentOffset() + 27.5;
        RenderUtils.renderCircle(context.getMatrices(), new Color(0, 0, 0, 180), cx, cy, 6, 15);
        RenderUtils.renderCircle(context.getMatrices(), s.currentColor1.brighter(),  cx, cy, 5, 15);
    }

    private void drawMinMaxThumb(DrawContext context, MinMaxSlider s) {
        double cy = s.parentY() + s.offset + s.parentOffset() + 27.5;
        double minX = s.parentX() + Math.max(s.lerpedOffsetMinX, 2.5);
        double maxX = s.parentX() + Math.max(s.lerpedOffsetMaxX, 2.5);
        RenderUtils.renderCircle(context.getMatrices(), new Color(0, 0, 0, 180), minX, cy, 6, 15);
        RenderUtils.renderCircle(context.getMatrices(), s.currentColor1.brighter(),  minX, cy, 5, 15);
        RenderUtils.renderCircle(context.getMatrices(), new Color(0, 0, 0, 180), maxX, cy, 6, 15);
        RenderUtils.renderCircle(context.getMatrices(), s.currentColor1.brighter(),  maxX, cy, 5, 15);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Input
    // ─────────────────────────────────────────────────────────────────────

    public void onExtend() {
        for (ModuleButton mb : parent.moduleButtons) mb.extended = false;
    }

    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        for (RenderableSetting rs : settings) rs.keyPressed(keyCode, scanCode, modifiers);
    }

    public void mouseDragged(double mouseX, double mouseY, int button, double dX, double dY) {
        if (extended)
            for (RenderableSetting rs : settings) rs.mouseDragged(mouseX, mouseY, button, dX, dY);
    }

    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY)) {
            if (button == 0) module.toggle();
            if (button == 1 && !settings.isEmpty()) {
                if (!extended) onExtend();
                extended = !extended;
            }
        }
        if (extended)
            for (RenderableSetting rs : settings) rs.mouseClicked(mouseX, mouseY, button);
    }

    public void onGuiClose() {
        currentAlpha = null;
        currentColor = null;
        enabledFill  = null;
        for (RenderableSetting rs : settings) rs.onGuiClose();
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        for (RenderableSetting rs : settings) rs.mouseReleased(mouseX, mouseY, button);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────

    public boolean isHovered(double mx, double my) {
        return mx > parent.getX() && mx < parent.getX() + parent.getWidth()
                && my > parent.getY() + offset
                && my < parent.getY() + offset + parent.getHeight();
    }

    public boolean matchesSearch(String query) {
        if (query == null || query.isEmpty()) return true;
        return module.getName().toString().toLowerCase().contains(query.toLowerCase());
    }
}
