package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.KeybindSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.*;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public final class DoubleAnchor extends Module implements TickListener {

    // ── Settings ──────────────────────────────────────────────────────────

    private final KeybindSetting triggerKey = new KeybindSetting(
            EncryptedString.of("Trigger Key"), 71, false)
            .setDescription(EncryptedString.of("Hold this key to trigger the double-anchor sequence (71 = G)"));

    private final NumberSetting stepDelay = new NumberSetting(
            EncryptedString.of("Step Delay"), 0, 5, 1, 1)
            .setDescription(EncryptedString.of("Ticks to wait between each step in the sequence"));

    private final BooleanSetting lootProtect = new BooleanSetting(
            EncryptedString.of("Loot Protect"), true)
            .setDescription(EncryptedString.of("Abort sequence if valuable loot is on the ground nearby"));

    // ── State ─────────────────────────────────────────────────────────────

    private int  step        = 0;
    private int  stepClock   = 0;
    private int  savedSlot   = 0;
    private boolean active   = false;

    // ── Lifecycle ─────────────────────────────────────────────────────────

    public DoubleAnchor() {
        super(EncryptedString.of("Double Anchor"),
                EncryptedString.of("Places and explodes two respawn anchors in sequence"),
                -1,
                Category.COMBAT);
        addSettings(triggerKey, stepDelay, lootProtect);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        resetSequence();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        resetSequence();
        super.onDisable();
    }

    // ── Tick ──────────────────────────────────────────────────────────────

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        // Trigger key gate
        int key = triggerKey.getKey();
        boolean keyHeld = key != -1 && KeyUtils.isKeyPressed(key);

        // Start new sequence when key is pressed and we have required items
        if (!active) {
            if (keyHeld && hasRequiredItems()) {
                active    = true;
                savedSlot = mc.player.getInventory().selectedSlot;
                step      = 0;
                stepClock = 0;
            }
            return;
        }

        // Abort if key released before sequence finishes
        if (!keyHeld) { abort(); return; }

        // Validate crosshair target
        if (!(mc.crosshairTarget instanceof BlockHitResult bhr)
                || bhr.getType() != HitResult.Type.BLOCK
                || BlockUtils.isBlock(bhr.getBlockPos(), Blocks.AIR)) {
            abort();
            return;
        }

        // Loot protect: abort if loot is nearby
        if (lootProtect.getValue() && isValuableLootNearby(bhr.getBlockPos())) {
            abort();
            return;
        }

        // Step delay
        if (stepClock > 0) { stepClock--; return; }

        runStep(bhr);
    }

    // ── Sequence ──────────────────────────────────────────────────────────
    //
    //  Anchor 1:
    //    0 → select anchor   1 → place anchor
    //    2 → select glowstone  3 → charge anchor
    //  Anchor 2:
    //    4 → select anchor   5 → place anchor
    //    6 → select glowstone  7 → charge anchor
    //  Explode:
    //    8 → select explode slot   9 → explode
    //   10 → restore slot, done
    //
    private void runStep(BlockHitResult bhr) {
        switch (step) {
            // ── Anchor 1 ──────────────────────────────────────────────────
            case 0 -> {
                if (!InventoryUtils.selectItemFromHotbar(Items.RESPAWN_ANCHOR)) { abort(); return; }
            }
            case 1 -> {
                if (!interact(bhr)) { abort(); return; }
            }
            case 2 -> {
                if (!InventoryUtils.selectItemFromHotbar(Items.GLOWSTONE)) { abort(); return; }
            }
            case 3 -> {
                if (!interact(bhr)) { abort(); return; }
            }
            // ── Anchor 2 ──────────────────────────────────────────────────
            case 4 -> {
                if (!InventoryUtils.selectItemFromHotbar(Items.RESPAWN_ANCHOR)) { abort(); return; }
            }
            case 5 -> {
                // The second anchor is placed on the same block face, but the
                // block state may have changed (first anchor is now there).
                // We retry the placement one step up if the original pos is occupied.
                BlockPos pos = bhr.getBlockPos();
                BlockPos alt = pos.up();
                BlockHitResult target = BlockUtils.canPlaceBlockClient(pos)
                        ? bhr
                        : new BlockHitResult(bhr.getPos().add(0, 1, 0), bhr.getSide(), alt, false);
                if (!interact(target)) { abort(); return; }
            }
            case 6 -> {
                if (!InventoryUtils.selectItemFromHotbar(Items.GLOWSTONE)) { abort(); return; }
            }
            case 7 -> {
                // Charge whichever anchor is on top (second placed)
                if (!interact(bhr)) { abort(); return; }
            }
            // ── Explode ───────────────────────────────────────────────────
            case 8 -> {
                // Switch to any non-anchor, non-glowstone slot for the explosion
                InventoryUtils.setInvSlot(savedSlot);
            }
            case 9 -> {
                // Interact triggers the explosion on both charged anchors
                interact(bhr);
            }
            case 10 -> {
                // Restore slot and finish
                InventoryUtils.setInvSlot(savedSlot);
                active = false;
                resetSequence();
                return;
            }
        }

        step++;
        stepClock = stepDelay.getValueInt();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private boolean interact(BlockHitResult hit) {
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (result.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
        return result != ActionResult.FAIL;
    }

    private void abort() {
        InventoryUtils.setInvSlot(savedSlot);
        active = false;
        resetSequence();
    }

    private void resetSequence() {
        step      = 0;
        stepClock = 0;
        active    = false;
    }

    private boolean hasRequiredItems() {
        boolean hasAnchor = false, hasGlowstone = false;
        for (int i = 0; i < 9; i++) {
            Item item = mc.player.getInventory().getStack(i).getItem();
            if (item == Items.RESPAWN_ANCHOR) hasAnchor    = true;
            if (item == Items.GLOWSTONE)      hasGlowstone = true;
        }
        return hasAnchor && hasGlowstone;
    }

    private boolean isValuableLootNearby(BlockPos origin) {
        if (mc.world == null) return false;
        double r = 10.0;
        Box box = new Box(origin.getX() - r, origin.getY() - r, origin.getZ() - r,
                origin.getX() + r, origin.getY() + r, origin.getZ() + r);
        for (Entity entity : mc.world.getOtherEntities(null, box)) {
            if (!(entity instanceof ItemEntity ie)) continue;
            ItemStack stack = ie.getStack();
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof ArmorItem)     return true;
            if (stack.getItem() instanceof SwordItem)     return true;
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) return true;
            if (stack.getItem() == Items.ELYTRA)           return true;
        }
        return false;
    }

    public boolean isActive() { return active; }
}
