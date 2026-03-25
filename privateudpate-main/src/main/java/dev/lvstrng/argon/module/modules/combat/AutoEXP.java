package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.MathUtils;
import dev.lvstrng.argon.mixin.MinecraftClientAccessor;

import net.minecraft.item.Items;

public final class AutoEXP extends Module implements TickListener {

    private final NumberSetting throwDelay = new NumberSetting(
            EncryptedString.of("Throw Delay"), 1, 20, 4, 1)
            .setDescription(EncryptedString.of("Ticks between each bottle throw"));

    private final NumberSetting throwChance = new NumberSetting(
            EncryptedString.of("Throw Chance"), 1, 100, 100, 1)
            .setDescription(EncryptedString.of("% chance to throw each eligible tick"));

    private final BooleanSetting onlyOnGround = new BooleanSetting(
            EncryptedString.of("Only On Ground"), false)
            .setDescription(EncryptedString.of("Only throw while standing on the ground"));

    private final BooleanSetting stopWhenFull = new BooleanSetting(
            EncryptedString.of("Stop When Full"), true)
            .setDescription(EncryptedString.of("Disable the module automatically when XP bar is full (level 30)"));

    private int clock = 0;

    public AutoEXP() {
        super(EncryptedString.of("AutoEXP"),
                EncryptedString.of("Automatically throws experience bottles"),
                -1,
                Category.COMBAT);
        addSettings(throwDelay, throwChance, onlyOnGround, stopWhenFull);
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

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        // Stop automatically when XP is capped
        if (stopWhenFull.getValue() && mc.player.experienceLevel >= 30) {
            setEnabled(false);
            return;
        }

        if (onlyOnGround.getValue() && !mc.player.isOnGround()) return;
        if (!mc.player.isHolding(Items.EXPERIENCE_BOTTLE)) return;

        if (clock > 0) { clock--; return; }
        if (MathUtils.randomInt(1, 101) > throwChance.getValueInt()) return;

        // Reset item-use cooldown so the throw always registers
        ((MinecraftClientAccessor) mc).setItemUseCooldown(0);
        ((MinecraftClientAccessor) mc).invokeDoItemUse();

        clock = throwDelay.getValueInt();
    }
}
