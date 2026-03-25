package dev.lvstrng.argon.module.modules.render;

import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.utils.EncryptedString;

public final class NoBounce extends Module {
    public NoBounce() {
        super(EncryptedString.of("NoBounce"),
                EncryptedString.of("Removes crystal bounce animation"),
                -1,
                Category.RENDER);
    }
}
