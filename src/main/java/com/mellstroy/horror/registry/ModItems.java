package com.mellstroy.horror.registry;

import com.mellstroy.horror.MellstroyMod;
import com.mellstroy.horror.item.ChekushkaItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MellstroyMod.MOD_ID);

    public static final RegistryObject<Item> MELLSTROY_SPAWN_EGG = ITEMS.register("mellstroy_spawn_egg",
            () -> new MellstroySpawnEggItem(0x14141A, 0xD4AF37, new Item.Properties()));

    public static final RegistryObject<Item> FAT_CAT_SPAWN_EGG = ITEMS.register("fat_cat_spawn_egg",
            () -> new FatCatSpawnEggItem(0xEFE4D2, 0x2B2521, new Item.Properties()));

    public static final RegistryObject<Item> CHEKUSHKA = ITEMS.register("chekushka",
            () -> new ChekushkaItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));

    private ModItems() {
    }
}
