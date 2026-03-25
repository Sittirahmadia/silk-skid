package dev.lvstrng.argon.module.modules.client;

import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;

public final class ClickGUI extends Module {

    // Color settings
    public static NumberSetting red;
    public static NumberSetting green;
    public static NumberSetting blue;
    public static BooleanSetting rainbow;
    public static BooleanSetting breathing;

    // Font / rendering
    public static BooleanSetting customFont;
    public static BooleanSetting antiAliasing;
    public static BooleanSetting background;

    // Window
    public static NumberSetting alphaWindow;
    public static NumberSetting roundQuads;

    // Animation
    public static ModeSetting<AnimationMode> animationMode;

    public ClickGUI() {
        super(EncryptedString.of("ClickGUI"),
                EncryptedString.of("Opens the click GUI"),
                344, // GLFW_KEY_RIGHT_SHIFT
                Category.CLIENT);

        red           = new NumberSetting(EncryptedString.of("Red"),    0, 255, 100, 1);
        green         = new NumberSetting(EncryptedString.of("Green"),  0, 255, 100, 1);
        blue          = new NumberSetting(EncryptedString.of("Blue"),   0, 255, 255, 1);
        rainbow       = new BooleanSetting(EncryptedString.of("Rainbow"),   false);
        breathing     = new BooleanSetting(EncryptedString.of("Breathing"), false);
        customFont    = new BooleanSetting(EncryptedString.of("Custom Font"), true);
        antiAliasing  = new BooleanSetting(EncryptedString.of("Anti Aliasing"), true);
        background    = new BooleanSetting(EncryptedString.of("Background"), true);
        alphaWindow   = new NumberSetting(EncryptedString.of("Alpha"),      0, 255, 220, 1);
        roundQuads    = new NumberSetting(EncryptedString.of("Round Quads"), 0, 10,   6, 1);
        animationMode = new ModeSetting<>(EncryptedString.of("Animation"), AnimationMode.Normal, AnimationMode.class);

        addSettings(red, green, blue, rainbow, breathing, customFont,
                    antiAliasing, background, alphaWindow, roundQuads, animationMode);
    }

    @Override
    public void onEnable() {
        dev.lvstrng.argon.Argon.mc.setScreen(dev.lvstrng.argon.Argon.INSTANCE.clickGui);
        super.onEnable();
    }

    public void setEnabledStatus(boolean status) {
        if (isEnabled() != status) toggle();
    }

    public enum AnimationMode { Normal, Positive, Off }
}
