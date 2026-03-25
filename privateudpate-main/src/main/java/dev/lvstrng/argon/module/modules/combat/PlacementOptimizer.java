package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;

public final class PlacementOptimizer extends Module implements TickListener {

    private final BooleanSetting excludeAnchors = new BooleanSetting(
            EncryptedString.of("Exclude Anchors/Glowstone"), true)
            .setDescription(EncryptedString.of("Exclude anchors and glowstone from delay reduction"));

    private final NumberSetting blockDelay = new NumberSetting(
            EncryptedString.of("Block Delay"), 0.0, 5.0, 3.0, 0.1)
            .setDescription(EncryptedString.of("Block placement delay in ticks"));

    private final NumberSetting crystalDelay = new NumberSetting(
            EncryptedString.of("Crystal Delay"), 0.0, 2.0, 0.0, 1.0)
            .setDescription(EncryptedString.of("Crystal placement delay in ticks"));

    public PlacementOptimizer() {
        super(EncryptedString.of("Placement Optimizer"),
                EncryptedString.of("Adjusts block/crystal placement delays"),
                -1,
                Category.COMBAT);
        addSettings(excludeAnchors, blockDelay, crystalDelay);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        // Queried externally by other modules via the getters below
    }

    public boolean shouldExcludeAnchors() {
        return excludeAnchors.getValue();
    }

    public int getBlockDelay() {
        return blockDelay.getValueInt();
    }

    public int getCrystalDelay() {
        return crystalDelay.getValueInt();
    }
}
