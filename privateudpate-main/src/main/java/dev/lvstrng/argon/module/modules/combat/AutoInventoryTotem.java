package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.PlayerTickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.InventoryUtils;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class AutoInventoryTotem extends Module implements PlayerTickListener {

    private final BooleanSetting autoSwitch = new BooleanSetting(
            EncryptedString.of("Auto Switch"), false)
            .setDescription(EncryptedString.of("Switch hotbar selection to totem slot when inventory opens"));

    private final NumberSetting delay = new NumberSetting(
            EncryptedString.of("Delay"), 0, 20, 0, 1)
            .setDescription(EncryptedString.of("Ticks to wait before acting after inventory opens"));

    private final NumberSetting totemSlot = new NumberSetting(
            EncryptedString.of("Totem Slot"), 0, 8, 0, 1)
            .setDescription(EncryptedString.of("Hotbar slot to switch to when Auto Switch is on"));

    private final BooleanSetting forceTotem = new BooleanSetting(
            EncryptedString.of("Force Totem"), false)
            .setDescription(EncryptedString.of("Also fill main hand slot with a totem (only after offhand is covered)"));

    private final BooleanSetting searchHotbar = new BooleanSetting(
            EncryptedString.of("Search Hotbar"), true)
            .setDescription(EncryptedString.of("Also search hotbar slots for totems when main inventory is empty"));

    private final BooleanSetting healthGate = new BooleanSetting(
            EncryptedString.of("Health Gate"), false)
            .setDescription(EncryptedString.of("Only act when player health is below the threshold"));

    private final NumberSetting healthThreshold = new NumberSetting(
            EncryptedString.of("Health Threshold"), 1, 20, 10, 0.5)
            .setDescription(EncryptedString.of("Hearts below which the module acts (requires Health Gate)"));

    private final BooleanSetting activateOnKey = new BooleanSetting(
            EncryptedString.of("Activate On Key"), false)
            .setDescription(EncryptedString.of("Only run while the configured keybind is held"));

    private final NumberSetting activateKey = new NumberSetting(
            EncryptedString.of("Activate Key"), -1, 348, 67, 1)  // default: C
            .setDescription(EncryptedString.of("GLFW key code for activation (default 67 = C)"));

    // ── State ─────────────────────────────────────────────────────────────
    private int invClock = -1;
    private static final Random RNG = new Random();

    public AutoInventoryTotem() {
        super(EncryptedString.of("AutoInventoryTotem"),
                EncryptedString.of("Swaps totem into offhand / hotbar slot while inventory is open"),
                -1,
                Category.COMBAT);
        addSettings(autoSwitch, delay, totemSlot, forceTotem, searchHotbar, healthGate, healthThreshold, activateOnKey, activateKey);
    }

    @Override
    public void onEnable() {
        eventManager.add(PlayerTickListener.class, this);
        invClock = -1;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(PlayerTickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onPlayerTick() {
        if (mc.player == null) return;

        // Only active while inventory screen is open
        if (!(mc.currentScreen instanceof InventoryScreen invScreen)) {
            invClock = -1;
            return;
        }

        // Health gate: skip if player is above threshold
        if (healthGate.getValue() && mc.player.getHealth() > healthThreshold.getValue())
            return;

        // Start delay countdown when inventory first opens
        if (invClock == -1)
            invClock = delay.getValueInt();
        if (invClock > 0) {
            invClock--;
            return;
        }

        // Key-gate: only proceed if activation key is held
        if (activateOnKey.getValue()) {
            int key = activateKey.getValueInt();
            if (key >= 0 && org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getHandle(), key) != org.lwjgl.glfw.GLFW.GLFW_PRESS)
                return;
        }

        var inv = mc.player.getInventory();

        // Auto-switch selected hotbar slot
        if (autoSwitch.getValue())
            InventoryUtils.setInvSlot(totemSlot.getValueInt());

        int syncId = invScreen.getScreenHandler().syncId;

        // ── Offhand: put totem there if missing ──────────────────────────
        if (!inv.offHand.get(0).isOf(Items.TOTEM_OF_UNDYING)) {
            int slot = findRandomTotemSlot(inv.selectedSlot);
            if (slot != -1) {
                // Button 40 = swap with offhand (F key action in inventory)
                mc.interactionManager.clickSlot(syncId, slot, 40, SlotActionType.SWAP, mc.player);
                return;
            }
        }

        // ── Main hand: put totem there only after offhand is already covered ─
        if (forceTotem.getValue()) {
            var mainHand = inv.main.get(inv.selectedSlot);
            if (!mainHand.isOf(Items.TOTEM_OF_UNDYING)) {
                int slot = findRandomTotemSlot(inv.selectedSlot);
                if (slot != -1) {
                    // Swap inventory slot ↔ selected hotbar slot
                    mc.interactionManager.clickSlot(syncId, slot, inv.selectedSlot, SlotActionType.SWAP, mc.player);
                }
            }
        }
    }

    /**
     * Finds a random inventory slot containing a totem.
     * Searches main inventory (9–35) first, then hotbar (0–8) if searchHotbar is on,
     * skipping the currently selected hotbar slot to avoid self-swapping.
     *
     * @param excludeHotbarSlot hotbar index (0–8) to skip when scanning hotbar
     */
    private int findRandomTotemSlot(int excludeHotbarSlot) {
        var inv = mc.player.getInventory();
        List<Integer> candidates = new ArrayList<>();

        // Main inventory rows (slots 9–35) — preferred; won't disrupt hotbar layout
        for (int i = 9; i < 36; i++) {
            if (inv.main.get(i).isOf(Items.TOTEM_OF_UNDYING))
                candidates.add(i);
        }

        // Hotbar fallback (slots 0–8)
        if (candidates.isEmpty() && searchHotbar.getValue()) {
            for (int i = 0; i < 9; i++) {
                if (i == excludeHotbarSlot) continue; // don't swap slot with itself
                if (inv.main.get(i).isOf(Items.TOTEM_OF_UNDYING))
                    candidates.add(i);
            }
        }

        if (candidates.isEmpty()) return -1;
        Collections.shuffle(candidates, RNG);
        return candidates.get(0);
    }
}
