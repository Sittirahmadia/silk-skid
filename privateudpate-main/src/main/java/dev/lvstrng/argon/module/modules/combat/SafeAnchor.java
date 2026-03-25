package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.PlayerTickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.BlockUtils;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.InventoryUtils;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class SafeAnchor extends Module implements PlayerTickListener {

    private final NumberSetting minHealth = new NumberSetting(
            EncryptedString.of("Min Health"), 0, 20, 0, 1)
            .setDescription(EncryptedString.of("Minimum health to operate"));

    private final BooleanSetting autoExplode = new BooleanSetting(
            EncryptedString.of("Auto Explode"), true)
            .setDescription(EncryptedString.of("Automatically detonate after charging"));

    private final NumberSetting placeDelay = new NumberSetting(
            EncryptedString.of("Place Delay"), 0, 10, 0, 1)
            .setDescription(EncryptedString.of("Ticks between each glowstone charge"));

    private final NumberSetting stageDelay = new NumberSetting(
            EncryptedString.of("Stage Delay"), 0, 10, 1, 1)
            .setDescription(EncryptedString.of("Ticks between phases"));

    private final NumberSetting chargeCount = new NumberSetting(
            EncryptedString.of("Charge Count"), 1, 4, 4, 1)
            .setDescription(EncryptedString.of("Glowstone charges to apply"));

    private final BooleanSetting silentSwing = new BooleanSetting(
            EncryptedString.of("Silent Swing"), true)
            .setDescription(EncryptedString.of("Suppress hand swing animation"));

    private final BooleanSetting smartProtection = new BooleanSetting(
            EncryptedString.of("Smart Protection"), true)
            .setDescription(EncryptedString.of("Place shield block intelligently between you and the anchor"));

    private final BooleanSetting autoSwitchBack = new BooleanSetting(
            EncryptedString.of("Switch Back"), true)
            .setDescription(EncryptedString.of("Return to original slot after finishing"));

    private final ModeSetting<ProtectBlock> blockMenu = new ModeSetting<>(
            EncryptedString.of("Block Menu"), ProtectBlock.GLOWSTONE, ProtectBlock.class)
            .setDescription(EncryptedString.of("Block to place as shield between you and anchor"));

    // ── State ─────────────────────────────────────────────────────────────
    private Phase phase = Phase.IDLE;
    private int delayClock = 0;
    private int chargesPlaced = 0;
    private BlockPos anchorPos = null;
    private int originalSlot = -1;

    public SafeAnchor() {
        super(EncryptedString.of("SafeAnchor"),
                EncryptedString.of("Places a shield block then charges and detonates a respawn anchor safely"),
                -1,
                Category.COMBAT);
        addSettings(minHealth, autoExplode, placeDelay, stageDelay, chargeCount,
                    silentSwing, smartProtection, autoSwitchBack, blockMenu);
    }

    @Override
    public void onEnable() {
        eventManager.add(PlayerTickListener.class, this);
        reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(PlayerTickListener.class, this);
        reset();
        super.onDisable();
    }

    private void reset() {
        phase         = Phase.IDLE;
        delayClock    = 0;
        chargesPlaced = 0;
        anchorPos     = null;
        originalSlot  = -1;
    }

    @Override
    public void onPlayerTick() {
        if (mc.player == null || mc.world == null) return;

        if (phase == Phase.IDLE) {
            // Health gate
            if (mc.player.getHealth() + mc.player.getAbsorptionAmount() < minHealth.getValue()) return;

            if (!(mc.crosshairTarget instanceof BlockHitResult bhr)) return;
            if (bhr.getType() == HitResult.Type.MISS) return;

            BlockPos hit = bhr.getBlockPos();
            double dist = mc.player.getPos().distanceTo(Vec3d.ofCenter(hit));
            if (dist > 6.0) return;

            originalSlot = mc.player.getInventory().selectedSlot;

            if (BlockUtils.isBlock(hit, Blocks.RESPAWN_ANCHOR)) {
                anchorPos = hit;
                phase = Phase.PLACE_PROTECTION;
                return;
            }
            // Crosshair on adjacent block – try to place anchor there
            BlockPos candidate = hit.offset(bhr.getSide());
            if (mc.world.getBlockState(candidate).isAir()) {
                anchorPos = candidate;
                phase = Phase.PLACE_ANCHOR;
            }
            return;
        }

        // Delay countdown
        if (delayClock > 0) { delayClock--; return; }

        switch (phase) {
            case PLACE_ANCHOR -> placeAnchor();
            case PLACE_PROTECTION -> placeProtection();
            case CHARGE_ANCHOR   -> chargeAnchor();
            case EXPLODE         -> explode();
            case DONE            -> finish();
        }
    }

    private void placeAnchor() {
        if (!InventoryUtils.selectItemFromHotbar(Items.RESPAWN_ANCHOR)) { reset(); return; }
        placeBlock(anchorPos);
        delayClock = stageDelay.getValueInt();
        phase = Phase.PLACE_PROTECTION;
    }

    private void placeProtection() {
        if (blockMenu.getMode() == ProtectBlock.OFF) {
            chargesPlaced = 0;
            phase = Phase.CHARGE_ANCHOR;
            return;
        }
        BlockPos protPos = getProtectionPos(anchorPos);
        if (protPos != null && mc.world.getBlockState(protPos).isAir()) {
            net.minecraft.item.Item shieldItem = switch (blockMenu.getMode()) {
                case OBSIDIAN    -> Items.OBSIDIAN;
                case ENDER_CHEST -> Items.ENDER_CHEST;
                default          -> Items.GLOWSTONE;
            };
            if (InventoryUtils.selectItemFromHotbar(shieldItem))
                placeBlock(protPos);
        }
        delayClock    = stageDelay.getValueInt();
        chargesPlaced = 0;
        phase         = Phase.CHARGE_ANCHOR;
    }

    private void chargeAnchor() {
        if (!InventoryUtils.selectItemFromHotbar(Items.GLOWSTONE)) {
            // No glowstone – skip straight to explode/done
            delayClock = stageDelay.getValueInt();
            phase = autoExplode.getValue() ? Phase.EXPLODE : Phase.DONE;
            return;
        }
        interactBlock(anchorPos);
        chargesPlaced++;
        if (chargesPlaced >= chargeCount.getValueInt()) {
            delayClock = stageDelay.getValueInt();
            phase = autoExplode.getValue() ? Phase.EXPLODE : Phase.DONE;
        } else {
            delayClock = placeDelay.getValueInt();
        }
    }

    private void explode() {
        // Switch to any non-glowstone, non-anchor slot
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            var s = inv.getStack(i);
            if (s.isEmpty() || (!s.isOf(Items.GLOWSTONE) && !s.isOf(Items.RESPAWN_ANCHOR))) {
                InventoryUtils.setInvSlot(i);
                break;
            }
        }
        interactBlock(anchorPos);
        delayClock = 1;
        phase = Phase.DONE;
    }

    private void finish() {
        if (autoSwitchBack.getValue() && originalSlot != -1)
            InventoryUtils.setInvSlot(originalSlot);
        reset();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void placeBlock(BlockPos pos) {
        var hit = new BlockHitResult(Vec3d.ofCenter(pos), net.minecraft.util.math.Direction.UP, pos, false);
        ActionResult r = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (r.isAccepted() && r.shouldSwingHand() && !silentSwing.getValue())
            mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void interactBlock(BlockPos pos) {
        var hit = new BlockHitResult(Vec3d.ofCenter(pos), net.minecraft.util.math.Direction.UP, pos, false);
        ActionResult r = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (r.isAccepted() && r.shouldSwingHand() && !silentSwing.getValue())
            mc.player.swingHand(Hand.MAIN_HAND);
    }

    /**
     * Finds the best position to place a protective block between the player
     * and the anchor, biased towards the horizontal direction facing the player.
     */
    private BlockPos getProtectionPos(BlockPos anchor) {
        Vec3d eye = mc.player.getEyePos();
        double dx = eye.x - (anchor.getX() + 0.5);
        double dz = eye.z - (anchor.getZ() + 0.5);

        int xOff, zOff;
        if (Math.abs(dx) >= Math.abs(dz)) {
            xOff = dx > 0 ? 1 : -1;
            zOff = 0;
        } else {
            xOff = 0;
            zOff = dz > 0 ? 1 : -1;
        }

        BlockPos primary = anchor.add(xOff, 0, zOff);
        if (!smartProtection.getValue() || mc.world.getBlockState(primary).isAir()) return primary;

        // Try secondary axis
        BlockPos secondary = Math.abs(dx) >= Math.abs(dz)
                ? anchor.add(0, 0, dz > 0 ? 1 : -1)
                : anchor.add(dx > 0 ? 1 : -1, 0, 0);

        return mc.world.getBlockState(secondary).isAir() ? secondary : primary;
    }

    private enum Phase { IDLE, PLACE_ANCHOR, PLACE_PROTECTION, CHARGE_ANCHOR, EXPLODE, DONE }
    private enum ProtectBlock { GLOWSTONE, OBSIDIAN, ENDER_CHEST, OFF }
}
