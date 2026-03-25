package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.HudListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.RotationUtils;
import dev.lvstrng.argon.utils.rotation.Rotation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.SwordItem;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Random;

public final class AimAssist extends Module implements HudListener {

    private final ModeSetting<TargetType> targets = new ModeSetting<>(
            EncryptedString.of("Targets"), TargetType.BOTH, TargetType.class)
            .setDescription(EncryptedString.of("What entities to aim at"));

    private final ModeSetting<AimAxis> mode = new ModeSetting<>(
            EncryptedString.of("Mode"), AimAxis.BOTH, AimAxis.class)
            .setDescription(EncryptedString.of("What axis to aim on"));

    private final NumberSetting smoothing = new NumberSetting(
            EncryptedString.of("Smoothing"), 0, 1, 0.5, 0.01)
            .setDescription(EncryptedString.of("Speed of aim assist (lower = smoother)"));

    private final NumberSetting fov = new NumberSetting(
            EncryptedString.of("FOV"), 0, 180, 50, 1)
            .setDescription(EncryptedString.of("FOV the target must be within"));

    private final NumberSetting range = new NumberSetting(
            EncryptedString.of("Range"), 0.1, 10, 5, 0.1)
            .setDescription(EncryptedString.of("Range the target must be within"));

    private final NumberSetting randomness = new NumberSetting(
            EncryptedString.of("Randomness"), 0, 5, 0.1, 0.01)
            .setDescription(EncryptedString.of("Randomness added to mouse movement"));

    private final ModeSetting<HitboxMode> hitbox = new ModeSetting<>(
            EncryptedString.of("Hitbox"), HitboxMode.EYE, HitboxMode.class)
            .setDescription(EncryptedString.of("Part of entity to aim at"));

    private final BooleanSetting weaponOnly = new BooleanSetting(
            EncryptedString.of("Weapon Only"), false)
            .setDescription(EncryptedString.of("Only activate when holding a sword or axe"));

    private final BooleanSetting stickyTarget = new BooleanSetting(
            EncryptedString.of("Sticky Target"), true)
            .setDescription(EncryptedString.of("Lock onto one target until it is no longer valid"));

    private Entity target = null;
    private final Random random = new Random();

    public AimAssist() {
        super(EncryptedString.of("Aim Assist"),
                EncryptedString.of("Smoothly aims toward nearby players or crystals"),
                -1,
                Category.COMBAT);
        addSettings(targets, mode, smoothing, fov, range, randomness, hitbox, weaponOnly, stickyTarget);
    }

    @Override
    public void onEnable() {
        eventManager.add(HudListener.class, this);
        target = null;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(HudListener.class, this);
        target = null;
        super.onDisable();
    }

    @Override
    public void onRenderHud(HudEvent event) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        // Don't interfere when crosshair is already on an entity
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) return;

        if (weaponOnly.getValue()) {
            var item = mc.player.getMainHandStack().getItem();
            if (!(item instanceof SwordItem) && !(item instanceof AxeItem)) return;
        }

        Rotation playerRot = new Rotation(mc.player.getYaw(), mc.player.getPitch());

        // Validate existing sticky target
        if (target != null) {
            Vec3d pos = getAimPos(target);
            double dist = Math.sqrt(mc.player.squaredDistanceTo(pos.x, pos.y, pos.z));
            if (dist > range.getValue() || !isInFov(playerRot, pos)) {
                target = null;
            }
        }

        if (!stickyTarget.getValue() || target == null) {
            target = findTarget(playerRot);
        }

        if (target == null) return;

        Vec3d aimPos = getAimPos(target);

        // Line-of-sight check
        BlockHitResult raycast = mc.world.raycast(new RaycastContext(
                mc.player.getCameraPosVec(event.delta),
                aimPos,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.ANY,
                mc.player));
        if (raycast.getType() == HitResult.Type.BLOCK) return;

        Rotation needed = RotationUtils.getDirection(mc.player, aimPos);
        float smooth = (float) smoothing.getValue();
        float rand = (randomness.getValue() > 0)
                ? (float) (-(randomness.getValue() / 2.0) + random.nextFloat() * randomness.getValue())
                : 0f;

        float newYaw   = (float) (mc.player.getYaw()   + (needed.yaw()   - mc.player.getYaw())   * smooth + rand);
        float newPitch = (float) (mc.player.getPitch() + (needed.pitch() - mc.player.getPitch()) * smooth + rand);

        switch (mode.getMode()) {
            case HORIZONTAL -> mc.player.setYaw(newYaw);
            case VERTICAL   -> mc.player.setPitch(newPitch);
            case BOTH       -> { mc.player.setYaw(newYaw); mc.player.setPitch(newPitch); }
        }
    }

    private Entity findTarget(Rotation playerRot) {
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : mc.world.getEntities()) {
            if (!isValid(e)) continue;
            Vec3d pos = getAimPos(e);
            double sqDist = mc.player.squaredDistanceTo(pos.x, pos.y, pos.z);
            if (sqDist < bestDist
                    && Math.sqrt(sqDist) <= range.getValue()
                    && isInFov(playerRot, pos)) {
                best = e;
                bestDist = sqDist;
            }
        }
        return best;
    }

    private boolean isValid(Entity e) {
        if (e == mc.player) return false;
        if (e instanceof EndCrystalEntity) return targets.isMode(TargetType.CRYSTALS) || targets.isMode(TargetType.BOTH);
        if (e instanceof PlayerEntity)     return targets.isMode(TargetType.PLAYERS)  || targets.isMode(TargetType.BOTH);
        return false;
    }

    private Vec3d getAimPos(Entity e) {
        return switch (hitbox.getMode()) {
            case EYE    -> e.getEyePos();
            case CENTER -> e.getPos().add(0, e.getHeight() / 2.0, 0);
            case BOTTOM -> e.getPos();
        };
    }

    private boolean isInFov(Rotation playerRot, Vec3d targetPos) {
        Rotation needed = RotationUtils.getDirection(mc.player, targetPos);
        double yawDiff   = Math.abs(wrapDegrees(playerRot.yaw() - needed.yaw()));
        double pitchDiff = Math.abs(playerRot.pitch() - needed.pitch());
        return yawDiff <= fov.getValue() && pitchDiff <= fov.getValue();
    }

    private double wrapDegrees(double d) {
        d = d % 360.0;
        if (d >= 180.0)  d -= 360.0;
        if (d < -180.0)  d += 360.0;
        return d;
    }

    private enum TargetType { PLAYERS, CRYSTALS, BOTH }
    private enum AimAxis    { HORIZONTAL, VERTICAL, BOTH }
    private enum HitboxMode { EYE, CENTER, BOTTOM }
}
