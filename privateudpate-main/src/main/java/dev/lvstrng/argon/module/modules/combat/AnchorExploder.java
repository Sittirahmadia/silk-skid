package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.PlayerTickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.InventoryUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public final class AnchorExploder extends Module implements PlayerTickListener {

    private final NumberSetting delay = new NumberSetting(
            EncryptedString.of("Delay"), 0, 500, 50, 1)
            .setDescription(EncryptedString.of("Delay in ms between each action"));

    private final NumberSetting switchTo = new NumberSetting(
            EncryptedString.of("Switch To"), 1, 9, 1, 1)
            .setDescription(EncryptedString.of("Hotbar slot (1-9) to switch to after exploding"));

    private long lastActionTime = 0;

    public AnchorExploder() {
        super(EncryptedString.of("Anchor Exploder"),
                EncryptedString.of("Explodes charged respawn anchors you are looking at"),
                -1,
                Category.COMBAT);
        addSettings(delay, switchTo);
    }

    @Override
    public void onEnable() {
        eventManager.add(PlayerTickListener.class, this);
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

        // Don't fire while blocking with shield
        if (mc.player.getMainHandStack().getItem() == Items.SHIELD && mc.player.isUsingItem()) return;

        HitResult result = mc.crosshairTarget;
        if (result == null || result.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult blockHitResult = (BlockHitResult) result;
        BlockState blockState = mc.world.getBlockState(blockHitResult.getBlockPos());

        if (blockState.getBlock() != Blocks.RESPAWN_ANCHOR) return;
        if (blockState.get(RespawnAnchorBlock.CHARGES) == 0) return;

        // If holding glowstone, switch away first so we can explode
        if (mc.player.getMainHandStack().getItem() == Items.GLOWSTONE) {
            InventoryUtils.setInvSlot((int) switchTo.getValue() - 1);
            lastActionTime = now;
            return;
        }

        // Right-click the charged anchor to explode it
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHitResult);
        mc.player.swingHand(Hand.MAIN_HAND);
        lastActionTime = now;
    }
}
