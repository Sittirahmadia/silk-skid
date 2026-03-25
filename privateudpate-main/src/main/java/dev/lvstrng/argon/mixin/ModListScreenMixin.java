package dev.lvstrng.argon.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Collection;

/**
 * Hides this client from every mod-list screen.
 *
 * Strategy (layered — most reliable first):
 *  1. On Screen#init  → walk ALL declared fields recursively and purge
 *     any Collection / array that contains an entry whose id matches.
 *  2. On Screen#render → repeat the purge so late-populated lists are
 *     also cleaned (ModMenu sometimes fills the list after init).
 *
 * Works with: Fabric ModMenu, FAPI mods screen, any reflective mod list.
 */
@Mixin(Screen.class)
public class ModListScreenMixin {

    /** IDs to hide — add more here if the mod ever gets a second mod-id. */
    private static final String[] HIDDEN_IDS = { "virgins", "argon", "immediatelyfast" };

    private boolean purged = false;

    @Inject(method = "init", at = @At("TAIL"))
    private void purgeOnInit(CallbackInfo ci) {
        purged = false;   // reset so render-pass also runs once
        doPurge();
    }

    @Inject(method = "renderBackground", at = @At("HEAD"))
    private void purgeOnRender(net.minecraft.client.gui.DrawContext ctx, int vOffset, int mouseY, float delta, CallbackInfo ci) {
        if (!purged) { doPurge(); purged = true; }
    }

    // ── Core purge logic ──────────────────────────────────────────────────

    private void doPurge() {
        Screen self = (Screen)(Object) this;

        // Guard: only act on mod-list-flavoured screens
        Text title = self.getTitle();
        if (title == null) return;
        String low = title.getString().toLowerCase();
        if (!low.contains("mod")) return;

        // Walk every field (including superclass fields) on the screen object
        purgeObject(self, 0);
    }

    /**
     * Recursively walk an object's fields up to {@code depth} levels deep,
     * removing any mod-entry whose id matches {@link #HIDDEN_IDS}.
     */
    private static void purgeObject(Object obj, int depth) {
        if (obj == null || depth > 5) return;

        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val == null) continue;

                    // Collection (List, Set, etc.)
                    if (val instanceof Collection<?> col) {
                        //noinspection unchecked
                        boolean removed = ((Collection<Object>) col).removeIf(
                                ModListScreenMixin::isHiddenEntry);
                        if (removed && depth < 4) {
                            // Some collections wrap a backing list — recurse
                            purgeObject(val, depth + 1);
                        }
                        // Also recurse into surviving entries in case they
                        // hold sub-lists (ModMenu's ModListWidget pattern)
                        for (Object entry : col) purgeObject(entry, depth + 1);
                        continue;
                    }

                    // Plain array
                    if (val.getClass().isArray() && !val.getClass().getComponentType().isPrimitive()) {
                        Object[] arr = (Object[]) val;
                        for (Object entry : arr) {
                            if (isHiddenEntry(entry)) {
                                // Null out — can't resize arrays, but prevents rendering
                                try {
                                    java.lang.reflect.Array.set(val,
                                            java.util.Arrays.asList(arr).indexOf(entry), null);
                                } catch (Exception ignored) {}
                            }
                        }
                        continue;
                    }

                    // Widget children (handles AbstractParentElement / ClickableWidget lists)
                    if (val instanceof net.minecraft.client.gui.Element) {
                        purgeObject(val, depth + 1);
                    }

                } catch (Exception ignored) {}
            }
            cls = cls.getSuperclass();
        }
    }

    /**
     * Returns true if {@code entry} looks like a mod-container/entry
     * whose id is in {@link #HIDDEN_IDS}.
     */
    private static boolean isHiddenEntry(Object entry) {
        if (entry == null) return false;
        try {
            // ModMenu: ModContainer  → getMetadata().getId()
            try {
                Object meta = entry.getClass().getMethod("getMetadata").invoke(entry);
                Object id   = meta.getClass().getMethod("getId").invoke(meta);
                if (matches(id)) return true;
            } catch (Exception ignored) {}

            // ModMenu: ModListWidget.Entry → getMod().getMetadata().getId()
            try {
                Object mod  = entry.getClass().getMethod("getMod").invoke(entry);
                Object meta = mod.getClass().getMethod("getMetadata").invoke(mod);
                Object id   = meta.getClass().getMethod("getId").invoke(meta);
                if (matches(id)) return true;
            } catch (Exception ignored) {}

            // Direct getId() / getId() → String
            try {
                Object id = entry.getClass().getMethod("getId").invoke(entry);
                if (matches(id)) return true;
            } catch (Exception ignored) {}

            // getModId()
            try {
                Object id = entry.getClass().getMethod("getModId").invoke(entry);
                if (matches(id)) return true;
            } catch (Exception ignored) {}

            // Field named "id" or "modId" as fallback
            for (String fname : new String[]{ "id", "modId", "MOD_ID" }) {
                try {
                    Field f = findField(entry.getClass(), fname);
                    if (f == null) continue;
                    f.setAccessible(true);
                    if (matches(f.get(entry))) return true;
                } catch (Exception ignored) {}
            }

        } catch (Exception ignored) {}
        return false;
    }

    private static boolean matches(Object id) {
        if (!(id instanceof String s)) return false;
        for (String hidden : HIDDEN_IDS) if (hidden.equalsIgnoreCase(s)) return true;
        return false;
    }

    private static Field findField(Class<?> cls, String name) {
        while (cls != null && cls != Object.class) {
            try { return cls.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
            cls = cls.getSuperclass();
        }
        return null;
    }
}
