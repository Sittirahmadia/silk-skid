package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.ButtonListener;
import dev.lvstrng.argon.event.events.PlayerTickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.InventoryUtils;
import dev.lvstrng.argon.utils.RotationUtils;
import dev.lvstrng.argon.utils.rotation.Rotation;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.MagmaCubeEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public final class AutoCrystal extends Module implements PlayerTickListener, ButtonListener {

    private final NumberSetting delay = new NumberSetting(
            EncryptedString.of("Delay"), 0, 500, 50, 1)
            .setDescription(EncryptedString.of("Delay in ms between each action"));

    private final BooleanSetting silentSwap = new BooleanSetting(
            EncryptedString.of("Silent Swap"), false)
            .setDescription(EncryptedString.of("Silently swap to crystals, letting you visually hold another item"));

    private final BooleanSetting headBob = new BooleanSetting(
            EncryptedString.of("Head Bob"), false)
            .setDescription(EncryptedString.of("Silently update pitch to break crystals above or below you"));

    private final BooleanSetting inAir = new BooleanSetting(
            EncryptedString.of("In Air"), false)
            .setDescription(EncryptedString.of("Place crystals while in the air"));

    private final BooleanSetting switchOnEnable = new BooleanSetting(
            EncryptedString.of("Switch On Enable"), false)
            .setDescription(EncryptedString.of("Switch to crystals when the module is enabled"));

    private final BooleanSetting damageTick = new BooleanSetting(
            EncryptedString.of("Damage Tick"), false)
            .setDescription(EncryptedString.of("Only break crystals on damage ticks"));

    private final BooleanSetting pauseOnKill = new BooleanSetting(
            EncryptedString.of("Pause On Kill"), false)
            .setDescription(EncryptedString.of("Pause when no living targets are nearby"));

    private final ModeSetting<ActivateOn> activateOn = new ModeSetting<>(
            EncryptedString.of("Activate On"), ActivateOn.ALWAYS, ActivateOn.class)
            .setDescription(EncryptedString.of("When to activate: Always, or only while Right Mouse Button is held"));

    private long lastActionTime = 0;
    private BlockPos placedPos = null;
    private boolean rightMouseHeld = false;
    private final Random random = new Random();

    public AutoCrystal() {
        super(EncryptedString.of("Auto Crystal"),
                EncryptedString.of("Automatically places and explodes end crystals"),
                -1,
                Category.COMBAT);
        addSettings(delay, silentSwap, headBob, inAir, switchOnEnable, damageTick, pauseOnKill, activateOn);
    }

    @Override
    public void onEnable() {
        eventManager.add(PlayerTickListener.class, this);
        eventManager.add(ButtonListener.class, this);
        placedPos = null;
        rightMouseHeld = false;
        lastActionTime = 0;

        if (switchOnEnable.getValue()) {
            InventoryUtils.selectItemFromHotbar(Items.END_CRYSTAL);
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(PlayerTickListener.class, this);
        eventManager.remove(ButtonListener.class, this);
        placedPos = null;
        super.onDisable();
    }

    @Override
    public void onButtonPress(ButtonEvent event) {
        if (event.button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            rightMouseHeld = (event.action == GLFW.GLFW_PRESS || event.action == GLFW.GLFW_REPEAT);
        }
    }

    @Override
    public void onPlayerTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        // Respect "Activate On" setting
        if (activateOn.isMode(ActivateOn.RIGHT_BUTTON) && !rightMouseHeld) return;

        // Pause if no nearby players alive
        if (pauseOnKill.getValue() && noLivingTargets()) return;

        // Check we have crystals or can silently swap to them
        boolean holdingCrystal = mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL;
        boolean canSilent = silentSwap.getValue() && InventoryUtils.hasItemInHotbar(i -> i == Items.END_CRYSTAL);
        if (!holdingCrystal && !canSilent) return;

        if (mc.player.isUsingItem()) return;
        if (!mc.player.isOnGround() && !inAir.getValue()) return;

        long now = System.currentTimeMillis();
        if (now - lastActionTime < delay.getValue()) return;
        lastActionTime = now;

        HitResult hitResult = mc.crosshairTarget;
        if (hitResult == null) return;

        // --- EXPLODE phase ---
        if (hitResult.getType() == HitResult.Type.ENTITY) {
            EntityHitResult ehr = (EntityHitResult) hitResult;
            if (ehr.getEntity() instanceof EndCrystalEntity
                    || ehr.getEntity() instanceof SlimeEntity
                    || ehr.getEntity() instanceof MagmaCubeEntity) {

                boolean shouldBreak = !damageTick.getValue()
                        || getClosestPlayer() == null
                        || getClosestPlayer().hurtTime > 0;

                if (shouldBreak) {
                    mc.interactionManager.attackEntity(mc.player, ehr.getEntity());
                    mc.player.swingHand(Hand.MAIN_HAND);
                    placedPos = null;
                }
                return;
            }
        }

        // --- HEAD BOB: break crystal above/below without looking directly at it ---
        if (headBob.getValue()) {
            BlockPos searchPos = placedPos;
            if (searchPos == null && hitResult.getType() == HitResult.Type.BLOCK) {
                searchPos = ((BlockHitResult) hitResult).getBlockPos();
            }
            if (searchPos != null) {
                Vec3d center = searchPos.up().toCenterPos();
                for (EndCrystalEntity crystal : mc.world.getEntitiesByClass(
                        EndCrystalEntity.class,
                        new Box(center.subtract(1, 1, 1), center.add(1, 1, 1)),
                        e -> mc.player.distanceTo(e) <= 4.5f)) {

                    Vec3d cp = crystal.getPos();
                    float rx = (float) (cp.x + randomInRange(-0.25f, 0.25f));
                    float ry = (float) (cp.y + randomInRange(0.3f, 0.6f));
                    float rz = (float) (cp.z + randomInRange(-0.25f, 0.25f));
                    Rotation rot = RotationUtils.getDirection(mc.player, new Vec3d(rx, ry, rz));
                    mc.player.setPitch((float) rot.pitch());

                    mc.interactionManager.attackEntity(mc.player, crystal);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    placedPos = null;
                    return;
                }
            }
        }

        // --- PLACE phase ---
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) hitResult;
            var block = mc.world.getBlockState(bhr.getBlockPos()).getBlock();

            if (block == Blocks.OBSIDIAN || block == Blocks.BEDROCK) {
                BlockPos upPos = bhr.getBlockPos().up();

                // Skip if something is already there
                if (!mc.world.isAir(upPos)) return;

                boolean hasCrystalAbove = !mc.world.getEntitiesByClass(
                        EndCrystalEntity.class,
                        new Box(upPos.getX(), upPos.getY(), upPos.getZ(),
                                upPos.getX() + 1, upPos.getY() + 2, upPos.getZ() + 1),
                        e -> true).isEmpty();
                if (hasCrystalAbove) return;

                // Silent swap: save slot, switch, place, restore
                int savedSlot = mc.player.getInventory().selectedSlot;
                if (canSilent && !holdingCrystal) {
                    InventoryUtils.selectItemFromHotbar(Items.END_CRYSTAL);
                }

                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                mc.player.swingHand(Hand.MAIN_HAND);
                placedPos = bhr.getBlockPos();

                if (canSilent && !holdingCrystal) {
                    InventoryUtils.setInvSlot(savedSlot);
                }
            }
        }
    }

    private PlayerEntity getClosestPlayer() {
        PlayerEntity closest = null;
        double bestDist = Double.MAX_VALUE;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            double d = mc.player.squaredDistanceTo(p);
            if (d < bestDist) { bestDist = d; closest = p; }
        }
        return closest;
    }

    private boolean noLivingTargets() {
        return mc.world.getPlayers().stream()
                .noneMatch(p -> p != mc.player && p.getHealth() > 0);
    }

    private float randomInRange(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    private enum ActivateOn { ALWAYS, RIGHT_BUTTON }
}
