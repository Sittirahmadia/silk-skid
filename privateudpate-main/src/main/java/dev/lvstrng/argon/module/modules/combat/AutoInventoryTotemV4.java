package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.PacketReceiveListener;
import dev.lvstrng.argon.event.events.PlayerTickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.InventoryUtils;

import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class AutoInventoryTotemV4 extends Module implements PlayerTickListener, PacketReceiveListener {

    // ── Settings ──────────────────────────────────────────────────────────

    private final BooleanSetting autoSwitch = new BooleanSetting(
            EncryptedString.of("Auto Switch"), false)
            .setDescription(EncryptedString.of("Switch hotbar slot to a configured slot when inventory opens"));

    private final NumberSetting switchSlot = new NumberSetting(
            EncryptedString.of("Switch Slot"), 1, 9, 1, 1)
            .setDescription(EncryptedString.of("Hotbar slot to select when Auto Switch fires (1-9)"));

    private final BooleanSetting forceMainHand = new BooleanSetting(
            EncryptedString.of("Force Main Hand"), false)
            .setDescription(EncryptedString.of("Also fill the selected hotbar slot with a totem after offhand is covered"));

    private final BooleanSetting searchHotbar = new BooleanSetting(
            EncryptedString.of("Search Hotbar"), true)
            .setDescription(EncryptedString.of("Fall back to hotbar slots when main inventory has no totems"));

    private final BooleanSetting randomOrder = new BooleanSetting(
            EncryptedString.of("Random Order"), true)
            .setDescription(EncryptedString.of("Randomise which inventory slot is picked to look more human"));

    private final BooleanSetting healthGate = new BooleanSetting(
            EncryptedString.of("Health Gate"), false)
            .setDescription(EncryptedString.of("Only act when health is below the threshold"));

    private final NumberSetting healthThreshold = new NumberSetting(
            EncryptedString.of("Health Threshold"), 1, 20, 10, 0.5)
            .setDescription(EncryptedString.of("HP threshold at which the module fires (requires Health Gate)"));

    private final NumberSetting openDelay = new NumberSetting(
            EncryptedString.of("Open Delay"), 0, 20, 0, 1)
            .setDescription(EncryptedString.of("Ticks to wait after inventory opens before acting"));

    private final NumberSetting swapDelay = new NumberSetting(
            EncryptedString.of("Swap Delay"), 0, 10, 0, 1)
            .setDescription(EncryptedString.of("Extra ticks between each successive totem swap this session"));

    // ── Constants ─────────────────────────────────────────────────────────

    // EntityStatus 35 = totem-of-undying activation (same as V8)
    private static final byte TOTEM_POP_STATUS = 35;

    // ── State ─────────────────────────────────────────────────────────────

    private int openClock  = -1;   // -1 = screen not open this session
    private int swapClock  = 0;

    // Pre-resolved screen-handler slots — rebuilt once per need-cycle
    private int  cachedOffhandSource  = -1;
    private int  cachedMainHandSource = -1;
    private boolean cacheValid = false;

    // Written by network thread on totem pop → triggers immediate refill next tick
    private volatile boolean totemPopped = false;

    // Track whether auto-switch has fired this open session
    private boolean hasSwitched = false;

    private static final Random RNG = new Random();

    // ── Lifecycle ─────────────────────────────────────────────────────────

    public AutoInventoryTotemV4() {
        super(EncryptedString.of("AutoInventoryTotemV4"),
                EncryptedString.of("Ultra-fast totem swap while inventory is open"),
                -1,
                Category.COMBAT);
        addSettings(autoSwitch, switchSlot, forceMainHand, searchHotbar, randomOrder,
                healthGate, healthThreshold, openDelay, swapDelay);
    }

    @Override
    public void onEnable() {
        eventManager.add(PlayerTickListener.class, this);
        eventManager.add(PacketReceiveListener.class, this);
        resetState();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(PlayerTickListener.class, this);
        eventManager.remove(PacketReceiveListener.class, this);
        super.onDisable();
    }

    private void resetState() {
        openClock  = -1;
        swapClock  = 0;
        cacheValid = false;
        cachedOffhandSource  = -1;
        cachedMainHandSource = -1;
        totemPopped = false;
        hasSwitched = false;
    }

    // ── Packet hook: detect totem pop → arm instant refill ────────────────

    @Override
    public void onPacketReceive(PacketReceiveListener.PacketReceiveEvent event) {
        // EntityStatus 35 fires the instant the server processes a totem pop
        if (event.packet instanceof EntityStatusS2CPacket pkt
                && mc.player != null
                && pkt.getStatus() == TOTEM_POP_STATUS
                && pkt.getEntity(mc.world) == mc.player) {
            cacheValid  = false;   // offhand is now empty → must rescan
            totemPopped = true;    // signal the tick thread to act immediately
        }
    }

    // ── Tick ──────────────────────────────────────────────────────────────

    @Override
    public void onPlayerTick() {
        if (mc.player == null || mc.world == null) return;

        // Must be inside the player inventory screen
        if (!(mc.currentScreen instanceof InventoryScreen invScreen)) {
            if (openClock != -1) resetState();
            return;
        }

        // Health gate
        if (healthGate.getValue() && mc.player.getHealth() > (float) healthThreshold.getValue())
            return;

        // Arm open-delay on first tick the screen appears
        if (openClock == -1) {
            openClock   = openDelay.getValueInt();
            cacheValid  = false;
            hasSwitched = false;
        }
        if (openClock > 0) {
            openClock--;
            return;
        }

        // On totem pop: bypass swap delay entirely — refill must be instant
        boolean forceImmediate = totemPopped;
        totemPopped = false;

        if (!forceImmediate && swapClock > 0) {
            swapClock--;
            return;
        }

        // ── Auto-switch: once per inventory open ─────────────────────
        if (autoSwitch.getValue() && !hasSwitched) {
            InventoryUtils.setInvSlot(switchSlot.getValueInt() - 1);
            hasSwitched = true;
        }

        // ── Cache: rebuild when stale ─────────────────────────────────
        if (!cacheValid) {
            rebuildCache();
            cacheValid = true;
        }

        int syncId = invScreen.getScreenHandler().syncId;
        var inv    = mc.player.getInventory();

        // ── 1. Offhand: swap totem in immediately ─────────────────────
        if (!inv.offHand.get(0).isOf(Items.TOTEM_OF_UNDYING)) {
            if (cachedOffhandSource != -1) {
                // Button 40 = the F-key "swap with offhand" action — one packet, zero overhead
                mc.interactionManager.clickSlot(
                        syncId, cachedOffhandSource, 40,
                        SlotActionType.SWAP, mc.player);
                cachedOffhandSource = -1;
                cacheValid          = false;   // slot changed — rescan next tick
                swapClock           = swapDelay.getValueInt();
                return;
            }
        }

        // ── 2. Main hand: fill after offhand is already covered ───────
        if (forceMainHand.getValue()
                && !inv.main.get(inv.selectedSlot).isOf(Items.TOTEM_OF_UNDYING)
                && cachedMainHandSource != -1) {
            mc.interactionManager.clickSlot(
                    syncId, cachedMainHandSource,
                    inv.selectedSlot, SlotActionType.SWAP, mc.player);
            cachedMainHandSource = -1;
            cacheValid           = false;
            swapClock            = swapDelay.getValueInt();
        }
    }

    // ── Cache builder ─────────────────────────────────────────────────────

    /**
     * Scans the player inventory once and pre-resolves the best screen-handler
     * source slots for the offhand-fill and optional main-hand-fill.
     *
     * Screen-handler slot mapping (player inventory screen):
     *   Slots  9-35 → main inventory rows   (screen slot == inventory index)
     *   Slots 36-44 → hotbar                (inventory index i → screen slot i+36)
     */
    private void rebuildCache() {
        var inv    = mc.player.getInventory();
        int selHot = inv.selectedSlot;

        List<Integer> candidates = new ArrayList<>(28);

        // Prefer main inventory rows — won't disrupt the hotbar layout
        for (int i = 9; i < 36; i++) {
            if (inv.main.get(i).isOf(Items.TOTEM_OF_UNDYING))
                candidates.add(i);
        }

        // Hotbar fallback (skip currently selected slot)
        if (searchHotbar.getValue()) {
            for (int i = 0; i < 9; i++) {
                if (i == selHot) continue;
                if (inv.main.get(i).isOf(Items.TOTEM_OF_UNDYING))
                    candidates.add(i + 36);
            }
        }

        if (randomOrder.getValue())
            Collections.shuffle(candidates, RNG);

        // Offhand source: first candidate
        cachedOffhandSource = candidates.isEmpty() ? -1 : candidates.get(0);

        // Main-hand source: second candidate (or first if offhand already has totem)
        if (forceMainHand.getValue()) {
            boolean offhandFilled = inv.offHand.get(0).isOf(Items.TOTEM_OF_UNDYING);
            if (offhandFilled && !candidates.isEmpty()) {
                cachedMainHandSource = candidates.get(0);
            } else if (candidates.size() >= 2) {
                cachedMainHandSource = candidates.get(1);
            } else {
                cachedMainHandSource = -1;
            }
        } else {
            cachedMainHandSource = -1;
        }
    }
}
