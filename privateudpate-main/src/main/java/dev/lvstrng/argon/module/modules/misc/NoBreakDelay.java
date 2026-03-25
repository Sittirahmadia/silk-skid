package dev.lvstrng.argon.module.modules.misc;

import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.utils.EncryptedString;

public final class NoBreakDelay extends Module {
    public NoBreakDelay() {
        super(EncryptedString.of("NoBreakDelay"),
                EncryptedString.of("Removes block breaking delay"),
                -1,
                Category.MISC);
    }
}
