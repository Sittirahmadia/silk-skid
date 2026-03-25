package dev.lvstrng.argon.module.modules.misc;

import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.utils.EncryptedString;

public final class Freecam extends Module {
    public Freecam() {
        super(EncryptedString.of("Freecam"),
                EncryptedString.of("Allows free camera movement"),
                -1,
                Category.MISC);
    }
}
