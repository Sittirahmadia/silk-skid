package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.*;

import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;

public final class CrystalOptimizer extends Module implements TickListener {

    // ── Settings ──────────────────────────────────────────────────────────

    private final NumberSetting range = new NumberSetting(
            EncryptedString.of("Range"), 1, 8, 4.5, 0.5)
            .setDescription(EncryptedString.of("Max distance to break nearby crystals"));

    private final NumberSetting breakDelay = new NumberSetting(
            EncryptedString.of("Break Delay"), 0, 20, 1, 1)
            .setDescription(EncryptedString.of("Ticks between each break (0 = instant)"));

    private final NumberSetting minDamage = new NumberSetting(
            EncryptedString.of("Min Damage"), 0, 20, 0, 0.5)
            .setDescription(EncryptedString.of("Only break crystals that deal at least this much damage to the nearest enemy"));

    private final BooleanSetting smartTarget = new BooleanSetting(
            EncryptedString.of("Smart Target"), true)
            .setDescription(EncryptedString.of("Only break crystals if a player enemy is nearby"));

    private final BooleanSetting requireCrystal = new BooleanSetting(
            EncryptedString.of("Require Crystal"), false)
            .setDescription(EncryptedString.of("Only run when holding end crystals"));

    private final BooleanSetting swing = new BooleanSetting(
            EncryptedString.of("Swing"), true)
            .setDescription(EncryptedString.of("Swing hand when breaking"));

    private final BooleanSetting fastBreak = new BooleanSetting(
            EncryptedString.of("Fast Break"), false)
            .setDescription(EncryptedString.of("Send an extra attack packet before interactionManager for faster server-side registration"));

    // ── State ─────────────────────────────────────────────────────────────

    private int clock = 0;

    // ── Lifecycle ─────────────────────────────────────────────────────────

    public CrystalOptimizer() {
        super(EncryptedString.of("Crystal Optimizer"),
                EncryptedString.of("Instantly breaks nearby crystals with damage prediction"),
                -1,
                Category.COMBAT);
        addSettings(range, breakDelay, minDamage, smartTarget, requireCrystal, swing, fastBreak);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        clock = 0;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    // ── Tick ──────────────────────────────────────────────────────────────

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        if (requireCrystal.getValue() && !mc.player.isHolding(Items.END_CRYSTAL)) return;

        if (clock > 0) { clock--; return; }

        // Optional: only act if an enemy player is nearby
        PlayerEntity target = null;
        if (smartTarget.getValue()) {
            target = findNearestTarget();
            if (target == null) return;
        }

        // Find the best crystal to break
        EndCrystalEntity best    = null;
        float            bestDmg = minDamage.getValueFloat();
        double           rangeSq = range.getValue() * range.getValue();

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EndCrystalEntity crystal)) continue;

            // Fast squared-distance check — no sqrt needed
            if (mc.player.squaredDistanceTo(crystal) > rangeSq) continue;

            // Damage prediction
            float dmg = target != null
                    ? DamageUtils.crystalDamage(target, crystal.getPos())
                    : 0f;

            // When smartTarget is off we just pick nearest above minDamage threshold
            if (smartTarget.getValue() && dmg < minDamage.getValue()) continue;

            if (target == null || dmg > bestDmg) {
                bestDmg = dmg;
                best    = crystal;
                if (target == null) break; // nearest-only when no target
            }
        }

        if (best == null) return;

        // Fast break: send raw packet before attackEntity
        if (fastBreak.getValue()) {
            mc.player.networkHandler.sendPacket(
                    PlayerInteractEntityC2SPacket.attack(best, mc.player.isSneaking()));
        }

        mc.interactionManager.attackEntity(mc.player, best);
        if (swing.getValue()) mc.player.swingHand(Hand.MAIN_HAND);

        clock = breakDelay.getValueInt();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private PlayerEntity findNearestTarget() {
        PlayerEntity best   = null;
        double       bestSq = Double.MAX_VALUE;

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (p.isDead() || p.getHealth() <= 0f) continue;
            double sq = mc.player.squaredDistanceTo(p);
            if (sq < bestSq) { bestSq = sq; best = p; }
        }
        return best;
    }
}
