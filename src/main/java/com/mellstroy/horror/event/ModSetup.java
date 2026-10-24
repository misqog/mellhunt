package com.mellstroy.horror.event;

import com.mellstroy.horror.MellstroyMod;
import com.mellstroy.horror.entity.FatCatEntity;
import com.mellstroy.horror.entity.MellstroyEntity;
import com.mellstroy.horror.registry.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MellstroyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModSetup {

    @SubscribeEvent
    public static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.MELLSTROY.get(), MellstroyEntity.createAttributes().build());
        event.put(ModEntities.FAT_CAT.get(), FatCatEntity.createAttributes().build());
    }

    private ModSetup() {
    }
}
