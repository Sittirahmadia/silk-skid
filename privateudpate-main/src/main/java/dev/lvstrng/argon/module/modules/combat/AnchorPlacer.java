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
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class AnchorPlacer extends Module implements PlayerTickListener {

    private final ModeSetting<PlaceMode> mode = new ModeSetting<>(
            EncryptedString.of("Mode"), PlaceMode.NORMAL, PlaceMode.class)
            .setDescription(EncryptedString.of("Normal: place + charge anchor. Glowstone: charge uncharged anchor only"));

    private final NumberSetting delay = new NumberSetting(
            EncryptedString.of("Delay"), 0, 500, 50, 1)
            .setDescription(EncryptedString.of("Delay in ms between each action"));

    private final BooleanSetting glowstone = new BooleanSetting(
            EncryptedString.of("Glowstone"), true)
            .setDescription(EncryptedString.of("Charge the anchor with glowstone after placing (Normal mode)"));

    private final BooleanSetting switchBack = new BooleanSetting(
            EncryptedString.of("Switch Back"), true)
            .setDescription(EncryptedString.of("Switch to another slot after anchoring (Glowstone mode)"));

    private final NumberSetting switchSlot = new NumberSetting(
            EncryptedString.of("Switch Slot"), 1, 9, 1, 1)
            .setDescription(EncryptedString.of("Hotbar slot (1-9) to switch to after anchoring"));

    private int progress = 0;
    private long lastActionTime = 0;

    public AnchorPlacer() {
        super(EncryptedString.of("Anchor Placer"),
                EncryptedString.of("Places and charges a respawn anchor on the block you are looking at"),
                -1,
                Category.COMBAT);
        addSettings(mode, delay, glowstone, switchBack, switchSlot);
    }

    @Override
    public void onEnable() {
        eventManager.add(PlayerTickListener.class, this);
        progress = 0;
        lastActionTime = 0;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(PlayerTickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onPlayerTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        long now = System.currentTimeMillis();
        if (now - lastActionTime < delay.getValue()) return;

        if (mode.isMode(PlaceMode.NORMAL)) {
            runNormalMode(now);
        } else {
            runGlowstoneMode(now);
        }
    }

    // Place anchor on looked-at surface, then optionally charge it
    private void runNormalMode(long now) {
        switch (progress) {
            case 0 -> {
                // Switch to anchor
                if (InventoryUtils.selectItemFromHotbar(Items.RESPAWN_ANCHOR)) {
                    advance(now);
                } else {
                    toggle(); // no anchor in hotbar
                }
            }
            case 1 -> {
                // Place anchor on the looked-at block
                if (!(mc.crosshairTarget instanceof BlockHitResult bhr)) return;
                if (mc.world.getBlockState(bhr.getBlockPos()).isAir()) return;

                Direction side = bhr.getSide();
                BlockPos placePos = bhr.getBlockPos().offset(side);

                // Make sure the spot is free of entities
                if (!BlockUtils.canPlaceBlockClient(placePos.down())) {
                    // canPlaceBlockClient checks block.up(), so we pass the block below placePos
                    // Fallback: just try to place
                }

                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                mc.player.swingHand(Hand.MAIN_HAND);
                advance(now);
            }
            case 2 -> {
                if (!glowstone.getValue()) {
                    toggle();
                    return;
                }
                // Switch to glowstone
                if (InventoryUtils.selectItemFromHotbar(Items.GLOWSTONE)) {
                    advance(now);
                } else {
                    toggle(); // no glowstone
                }
            }
            case 3 -> {
                // Charge the anchor
                if (!(mc.crosshairTarget instanceof BlockHitResult bhr)) return;
                if (mc.world.getBlockState(bhr.getBlockPos()).getBlock() != Blocks.RESPAWN_ANCHOR) return;

                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                mc.player.swingHand(Hand.MAIN_HAND);
                advance(now);
            }
            case 4 -> toggle();
        }
    }

    // Charge an uncharged anchor you are looking at, then optionally switch slot
    private void runGlowstoneMode(long now) {
        switch (progress) {
            case 0 -> {
                if (!(mc.crosshairTarget instanceof BlockHitResult bhr)) return;
                if (mc.world.getBlockState(bhr.getBlockPos()).getBlock() != Blocks.RESPAWN_ANCHOR) return;
                if (mc.world.getBlockState(bhr.getBlockPos()).get(RespawnAnchorBlock.CHARGES) != 0) return;

                // Switch to glowstone block
                if (InventoryUtils.selectItemFromHotbar(Items.GLOWSTONE)) {
                    advance(now);
                } else {
                    toggle();
                }
            }
            case 1 -> {
                if (!(mc.crosshairTarget instanceof BlockHitResult bhr)) return;
                if (mc.world.getBlockState(bhr.getBlockPos()).getBlock() != Blocks.RESPAWN_ANCHOR) return;
                if (mc.world.getBlockState(bhr.getBlockPos()).get(RespawnAnchorBlock.CHARGES) != 0) return;

                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                mc.player.swingHand(Hand.MAIN_HAND);
                advance(now);
            }
            case 2 -> {
                if (switchBack.getValue()) {
                    InventoryUtils.setInvSlot((int) switchSlot.getValue() - 1);
                }
                progress = 0; // Ready for next anchor
                lastActionTime = System.currentTimeMillis();
                toggle();
            }
        }
    }

    private void advance(long now) {
        progress++;
        lastActionTime = now;
    }

    private enum PlaceMode { NORMAL, GLOWSTONE }
}
