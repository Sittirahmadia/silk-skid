package dev.lvstrng.argon.module.modules.client;

import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.utils.EncryptedString;

public final class SelfDestruct extends Module {
    public static boolean destruct = false;

    public SelfDestruct() {
        super(EncryptedString.of("SelfDestruct"),
                EncryptedString.of("Destroys the client"),
                -1,
                Category.CLIENT);
    }
}
