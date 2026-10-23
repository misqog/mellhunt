package com.mellstroy.horror.registry;

import com.mellstroy.horror.MellstroyMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MellstroyMod.MOD_ID);

    public static final RegistryObject<Item> MELLSTROY_SPAWN_EGG = ITEMS.register("mellstroy_spawn_egg",
            () -> new MellstroySpawnEggItem(0x14141A, 0xD4AF37, new Item.Properties()));

    private ModItems() {
    }
}
