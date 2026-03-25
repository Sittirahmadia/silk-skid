
package dev.lvstrng.argon.module.modules.render;

import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.utils.EncryptedString;
import net.minecraft.client.MinecraftClient;

public class FPSBoosterExtreme extends Module {

    public FPSBoosterExtreme() {
        super(EncryptedString.of("FPSBoosterExtreme"), EncryptedString.of("Boost FPS"), -1, Category.RENDER);
    }

    @Override
    public void onEnable() {

        MinecraftClient mc = MinecraftClient.getInstance();

        mc.options.getViewDistance().setValue(2);
        mc.options.getSimulationDistance().setValue(3);
        mc.options.getEntityDistanceScaling().setValue(0.4);

    }

}
