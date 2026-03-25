package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.ItemUseListener;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.mixin.MinecraftClientAccessor;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.*;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

public final class AnchorMacroV2 extends Module implements TickListener, ItemUseListener {

    private final BooleanSetting whileUse = new BooleanSetting(
            EncryptedString.of("While Use"), false);

    private final BooleanSetting lootProtect = new BooleanSetting(
            EncryptedString.of("Loot Protect"), false);

    private final BooleanSetting clickSimulation = new BooleanSetting(
            EncryptedString.of("Click Simulation"), true);

    private final BooleanSetting placer = new BooleanSetting(
            EncryptedString.of("Placer"), false)
            .setDescription(EncryptedString.of("Auto-place respawn anchors when looking at a valid surface"));

    private final BooleanSetting charger = new BooleanSetting(
            EncryptedString.of("Charger"), true)
            .setDescription(EncryptedString.of("Auto-charge anchor with glowstone"));

    private final BooleanSetting exploder = new BooleanSetting(
            EncryptedString.of("Exploder"), true)
            .setDescription(EncryptedString.of("Auto-explode charged anchor"));

    private final NumberSetting switchDelayMin = new NumberSetting(
            EncryptedString.of("Switch Delay Min"), 0.0, 20.0, 2.0, 1.0)
            .setDescription(EncryptedString.of("Min ticks before switching item"));

    private final NumberSetting switchDelayMax = new NumberSetting(
            EncryptedString.of("Switch Delay Max"), 0.0, 20.0, 4.0, 1.0)
            .setDescription(EncryptedString.of("Max ticks before switching item"));

    private final NumberSetting clickDelayMin = new NumberSetting(
            EncryptedString.of("Click Delay Min"), 0.0, 20.0, 2.0, 1.0)
            .setDescription(EncryptedString.of("Min ticks before clicking"));

    private final NumberSetting clickDelayMax = new NumberSetting(
            EncryptedString.of("Click Delay Max"), 0.0, 20.0, 4.0, 1.0)
            .setDescription(EncryptedString.of("Max ticks before clicking"));

    private final NumberSetting switchChance = new NumberSetting(
            EncryptedString.of("Switch Chance"), 0.0, 100.0, 100.0, 1.0)
            .setDescription(EncryptedString.of("Chance to switch item"));

    private final NumberSetting placeChance = new NumberSetting(
            EncryptedString.of("Place Chance"), 0.0, 100.0, 100.0, 1.0)
            .setDescription(EncryptedString.of("Chance to place glowstone"));

    private final NumberSetting glowstoneChance = new NumberSetting(
            EncryptedString.of("Glowstone Chance"), 0.0, 100.0, 100.0, 1.0)
            .setDescription(EncryptedString.of("Chance to place glowstone"));

    private final NumberSetting explodeChance = new NumberSetting(
            EncryptedString.of("Explode Chance"), 0.0, 100.0, 100.0, 1.0)
            .setDescription(EncryptedString.of("Chance to explode anchor"));

    private final NumberSetting explodeSlot = new NumberSetting(
            EncryptedString.of("Explode Slot"), 1.0, 9.0, 9.0, 1.0)
            .setDescription(EncryptedString.of("Hotbar slot to use when exploding (1-9)"));

    private final BooleanSetting onlyOwn = new BooleanSetting(
            EncryptedString.of("Only Own"), false)
            .setDescription(EncryptedString.of("Only interact with anchors you placed"));

    private final BooleanSetting onlyCharge = new BooleanSetting(
            EncryptedString.of("Only Charge"), false)
            .setDescription(EncryptedString.of("Only charge the anchor, never explode"));

    private final Set<BlockPos> ownedAnchors = new HashSet<>();
    private int switchClock    = 0;
    private int glowstoneClock = 0;
    private int explodeClock   = 0;
    private int clickClock     = 0;

    public AnchorMacroV2() {
        super(EncryptedString.of("Anchor Macro v2"),
                EncryptedString.of("Automatically blows up respawn anchors for you"),
                -1,
                Category.COMBAT);
        addSettings(whileUse, lootProtect, clickSimulation, placer, charger, exploder,
                placeChance, switchDelayMin, switchDelayMax, switchChance,
                clickDelayMin, clickDelayMax, glowstoneChance,
                explodeChance, explodeSlot, onlyOwn, onlyCharge);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        eventManager.add(ItemUseListener.class, this);
        switchClock    = 0;
        glowstoneClock = 0;
        explodeClock   = 0;
        clickClock     = 0;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        eventManager.remove(ItemUseListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.currentScreen != null) return;

        // Always tick clocks
        switchClock++;
        glowstoneClock++;
        explodeClock++;
        clickClock++;

        if (whileUse.getValue()
                || (!mc.player.isUsingItem() && !WorldUtils.isTool(mc.player.getOffHandStack()))) {

            if (!lootProtect.getValue()
                    || (!WorldUtils.isDeadBodyNearby() && !isValuableLootNearby())) {

                if (KeyUtils.isKeyPressed(GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
                    HitResult hitResult = mc.crosshairTarget;
                    if (hitResult instanceof BlockHitResult hit) {
                        BlockPos pos = hit.getBlockPos();

                        // ── Placer: place anchor on a valid surface ───────────
                        if (placer.getValue()
                                && !BlockUtils.isBlock(pos, Blocks.RESPAWN_ANCHOR)
                                && BlockUtils.canPlaceBlockClient(pos)
                                && InventoryUtils.hasItemInHotbar(item -> item == Items.RESPAWN_ANCHOR)) {
                            if (mc.player.getMainHandStack().getItem() != Items.RESPAWN_ANCHOR) {
                                if (switchClock < MathUtils.randomInt(switchDelayMin.getValueInt(), switchDelayMax.getValueInt())) return;
                                if (MathUtils.randomInt(1, 100) <= switchChance.getValueInt()) {
                                    switchClock = 0;
                                    InventoryUtils.selectItemFromHotbar(Items.RESPAWN_ANCHOR);
                                }
                            }
                            if (mc.player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR)) {
                                if (clickClock < MathUtils.randomInt(clickDelayMin.getValueInt(), clickDelayMax.getValueInt())) return;
                                mc.options.useKey.setPressed(false);
                                if (clickSimulation.getValue())
                                    MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                                ((MinecraftClientAccessor) mc).invokeDoItemUse();
                                clickClock = 0;
                            }
                            return;
                        }

                        if (BlockUtils.isBlock(pos, Blocks.RESPAWN_ANCHOR)) {
                            if (onlyOwn.getValue() && !ownedAnchors.contains(pos)) return;

                            mc.options.useKey.setPressed(false);

                            // ── Charger: fill anchor with glowstone ───────────
                            if (charger.getValue() && BlockUtils.isAnchorNotCharged(pos)) {
                                if (MathUtils.randomInt(1, 100) <= placeChance.getValueInt()) {
                                    if (mc.player.getMainHandStack().getItem() != Items.GLOWSTONE) {
                                        if (switchClock < MathUtils.randomInt(switchDelayMin.getValueInt(), switchDelayMax.getValueInt())) return;
                                        if (MathUtils.randomInt(1, 100) <= switchChance.getValueInt()) {
                                            switchClock = 0;
                                            InventoryUtils.selectItemFromHotbar(Items.GLOWSTONE);
                                        }
                                    }
                                    if (mc.player.getMainHandStack().getItem() == Items.GLOWSTONE) {
                                        if (clickClock < MathUtils.randomInt(clickDelayMin.getValueInt(), clickDelayMax.getValueInt())) return;
                                        if (MathUtils.randomInt(1, 100) <= glowstoneChance.getValueInt()) {
                                            clickClock = 0;
                                            if (clickSimulation.getValue())
                                                MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                                            ((MinecraftClientAccessor) mc).invokeDoItemUse();
                                        }
                                    }
                                }
                            }

                            // ── Exploder: detonate charged anchor ─────────────
                            if (exploder.getValue() && BlockUtils.isAnchorCharged(pos)) {
                                int slot = explodeSlot.getValueInt() - 1;
                                if (mc.player.getInventory().selectedSlot != slot) {
                                    if (switchClock < MathUtils.randomInt(switchDelayMin.getValueInt(), switchDelayMax.getValueInt())) return;
                                    if (MathUtils.randomInt(1, 100) <= switchChance.getValueInt()) {
                                        switchClock = 0;
                                        InventoryUtils.setInvSlot(slot);
                                    }
                                }
                                if (mc.player.getInventory().selectedSlot == slot) {
                                    if (clickClock < MathUtils.randomInt(clickDelayMin.getValueInt(), clickDelayMax.getValueInt())) return;
                                    if (MathUtils.randomInt(1, 100) <= explodeChance.getValueInt()) {
                                        clickClock = 0;
                                        if (!onlyCharge.getValue()) {
                                            if (clickSimulation.getValue())
                                                MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                                            ((MinecraftClientAccessor) mc).invokeDoItemUse();
                                            ownedAnchors.remove(pos);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onItemUse(ItemUseListener.ItemUseEvent event) {
        HitResult hitResult = mc.crosshairTarget;
        if (hitResult instanceof BlockHitResult hit) {
            if (hit.getType() == HitResult.Type.BLOCK) {
                if (mc.player.getMainHandStack().getItem() == Items.RESPAWN_ANCHOR) {
                    Direction dir = hit.getSide();
                    BlockPos pos  = hit.getBlockPos();
                    if (!mc.world.getBlockState(pos).isReplaceable()) {
                        ownedAnchors.add(pos.offset(dir));
                    } else {
                        ownedAnchors.add(pos);
                    }
                }
                BlockPos bp = hit.getBlockPos();
                if (BlockUtils.isAnchorCharged(bp)) {
                    ownedAnchors.remove(bp);
                }
            }
        }
    }

    /** Check whether valuable loot items are on the ground nearby. */
    private boolean isValuableLootNearby() {
        if (mc.world == null || mc.player == null) return false;
        double radius = 10.0;
        BlockPos pos  = mc.player.getBlockPos();
        Box box = new Box(
                pos.getX() - radius, pos.getY() - radius, pos.getZ() - radius,
                pos.getX() + radius, pos.getY() + radius, pos.getZ() + radius);
        for (Entity entity : mc.world.getOtherEntities(null, box)) {
            if (entity instanceof ItemEntity itemEntity) {
                ItemStack stack = itemEntity.getStack();
                if (stack.isEmpty()) continue;
                if (stack.getItem() instanceof ArmorItem)  return true;
                if (stack.getItem() instanceof SwordItem)  return true;
                if (stack.getItem() == Items.ELYTRA)       return true;
                if (stack.getItem() == Items.TOTEM_OF_UNDYING) return true;
            }
        }
        return false;
    }
}
