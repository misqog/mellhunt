package ru.misqog.mellhunt.casino;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Shared gold handling for the playable casino furniture. */
public final class CasinoMoney {

    public static boolean hasGold(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(Items.GOLD_INGOT)) {
                return true;
            }
        }
        return false;
    }

    public static int countGold(ServerPlayer player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(Items.GOLD_INGOT)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Takes one gold ingot, or returns false when the inventory has none. */
    public static boolean takeGold(ServerPlayer player) {
        return takeGold(player, 1);
    }

    /** Takes {@code count} ingots atomically, or returns false when there are not enough. */
    public static boolean takeGold(ServerPlayer player, int count) {
        if (count <= 0) {
            return true;
        }
        if (countGold(player) < count) {
            return false;
        }
        int left = count;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && left > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(Items.GOLD_INGOT)) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        player.getInventory().setChanged();
        return true;
    }

    public static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        int max = Math.max(1, stack.getMaxStackSize());
        while (stack.getCount() > max) {
            ItemStack part = stack.copy();
            part.setCount(max);
            stack.shrink(max);
            giveOne(player, part);
        }
        giveOne(player, stack);
    }

    private static void giveOne(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    public static void giveIngots(ServerPlayer player, int ingots) {
        if (ingots > 0) {
            give(player, new ItemStack(Items.GOLD_INGOT, ingots));
        }
    }

    /** Pays in nuggets, folding complete ingots together to keep the payout tidy. */
    public static void giveNuggets(ServerPlayer player, int nuggets) {
        if (nuggets <= 0) {
            return;
        }
        giveIngots(player, nuggets / 9);
        int rest = nuggets % 9;
        if (rest > 0) {
            give(player, new ItemStack(Items.GOLD_NUGGET, rest));
        }
    }

    private CasinoMoney() {
    }
}
