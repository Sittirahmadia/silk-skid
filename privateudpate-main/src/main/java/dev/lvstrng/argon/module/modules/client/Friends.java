package dev.lvstrng.argon.module.modules.client;

import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.utils.EncryptedString;

public final class Friends extends Module {
    public Friends() {
        super(EncryptedString.of("Friends"),
                EncryptedString.of("Manage friends list"),
                -1,
                Category.CLIENT);
    }
}
