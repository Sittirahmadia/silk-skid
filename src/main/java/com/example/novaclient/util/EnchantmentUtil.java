package com.example.novaclient.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.World;

public class EnchantmentUtil {

    public static boolean hasEnchantment(ItemStack stack, World world, RegistryKey<Enchantment> enchantmentKey) {
        if (stack.isEmpty() || world == null) return false;

        ItemEnchantmentsComponent enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchantments == null) return false;

        return world.getRegistryManager()
                .getOrThrow(enchantmentKey.getRegistryRef())
                .getOptional(enchantmentKey.getValue())
                .map(RegistryEntry::registryKey)
                .map(key -> enchantments.getEnchantments().containsKey(key))
                .orElse(false);
    }

    public static int getEnchantmentLevel(ItemStack stack, World world, RegistryKey<Enchantment> enchantmentKey) {
        if (stack.isEmpty() || world == null) return 0;

        ItemEnchantmentsComponent enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchantments == null) return 0;

        return world.getRegistryManager()
                .getOrThrow(enchantmentKey.getRegistryRef())
                .getOptional(enchantmentKey.getValue())
                .map(entry -> enchantments.getLevel(entry))
                .orElse(0);
    }
}