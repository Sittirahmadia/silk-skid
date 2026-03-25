package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.SwordItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

public final class TriggerBot extends Module implements TickListener {

    private final ModeSetting<Timing> timing = new ModeSetting<>(
            EncryptedString.of("Timing"), Timing.COOLDOWN, Timing.class)
            .setDescription(EncryptedString.of("Attack timing mode"));

    private final NumberSetting minDelay = new NumberSetting(
            EncryptedString.of("Min Delay"), 10, 1000, 50, 1)
            .setDescription(EncryptedString.of("Minimum ms between attacks (Delay mode)"));

    private final NumberSetting cooldownPct = new NumberSetting(
            EncryptedString.of("Cooldown %"), 0, 100, 95, 1)
            .setDescription(EncryptedString.of("Attack when cooldown reaches this % (Cooldown mode)"));

    private final NumberSetting reactionMs = new NumberSetting(
            EncryptedString.of("Reaction"), 0, 250, 0, 1)
            .setDescription(EncryptedString.of("Extra ms delay to simulate human reaction time"));

    private final ModeSetting<CritMode> crits = new ModeSetting<>(
            EncryptedString.of("Crits"), CritMode.FALLING, CritMode.class)
            .setDescription(EncryptedString.of("Critical hit requirement"));

    private final BooleanSetting onlyPlayers = new BooleanSetting(
            EncryptedString.of("Players Only"), true)
            .setDescription(EncryptedString.of("Only trigger on players"));

    private final BooleanSetting needWeapon = new BooleanSetting(
            EncryptedString.of("Need Weapon"), true)
            .setDescription(EncryptedString.of("Only trigger when holding sword/axe"));

    private final BooleanSetting needClick = new BooleanSetting(
            EncryptedString.of("Need Click"), false)
            .setDescription(EncryptedString.of("Only trigger while holding left mouse button"));

    // ── State ─────────────────────────────────────────────────────────────
    private long lastHitTime  = 0;
    private long reactStartTime = 0;
    private boolean waitingReaction = false;

    public TriggerBot() {
        super(EncryptedString.of("TriggerBot"),
                EncryptedString.of("Automatically attacks entities on crosshair"),
                -1,
                Category.COMBAT);
        addSettings(timing, minDelay, cooldownPct, reactionMs, crits, onlyPlayers, needWeapon, needClick);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        lastHitTime = 0;
        reactStartTime = 0;
        waitingReaction = false;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        if (needWeapon.getValue() && !hasWeapon()) return;
        if (needClick.getValue() &&
                GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS)
            return;

        if (!(mc.crosshairTarget instanceof EntityHitResult entityHit)) return;
        if (!(entityHit.getEntity() instanceof LivingEntity target)) return;
        if (target.getHealth() <= 0) return;
        if (onlyPlayers.getValue() && !(target instanceof PlayerEntity)) return;

        // Crit check
        switch (crits.getMode()) {
            case FALLING -> { if (!isFalling()) return; }
            case SMART   -> { if (!isFalling() && !mc.player.isOnGround()) return; }
            case OFF     -> {}
        }

        // Timing check
        long now = System.currentTimeMillis();
        boolean timingOk;
        switch (timing.getMode()) {
            case COOLDOWN -> timingOk = mc.player.getAttackCooldownProgress(0f) >= cooldownPct.getValue() / 100.0;
            case DELAY    -> timingOk = (now - lastHitTime) >= minDelay.getValue();
            default       -> timingOk = true;
        }
        if (!timingOk) return;

        // Reaction delay
        if (reactionMs.getValue() > 0) {
            if (!waitingReaction) {
                waitingReaction  = true;
                reactStartTime   = now;
                return;
            }
            if (now - reactStartTime < reactionMs.getValue()) return;
        }
        waitingReaction = false;

        // Attack
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        lastHitTime = now;
    }

    private boolean hasWeapon() {
        var item = mc.player.getMainHandStack().getItem();
        return item instanceof SwordItem || item instanceof AxeItem;
    }

    private boolean isFalling() {
        return mc.player.fallDistance > 0f && !mc.player.isOnGround()
                && !mc.player.isClimbing() && !mc.player.isTouchingWater();
    }

    private enum Timing  { COOLDOWN, DELAY }
    private enum CritMode { OFF, FALLING, SMART }
}
