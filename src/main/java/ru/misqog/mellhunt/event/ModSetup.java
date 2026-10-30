package ru.misqog.mellhunt.event;

import ru.misqog.mellhunt.MellstroyMod;
import ru.misqog.mellhunt.entity.FatCatEntity;
import ru.misqog.mellhunt.entity.MellstroyEntity;
import ru.misqog.mellhunt.entity.PopudaiEntity;
import ru.misqog.mellhunt.registry.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MellstroyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModSetup {

    @SubscribeEvent
    public static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.MELLSTROY.get(), MellstroyEntity.createAttributes().build());
        event.put(ModEntities.FAT_CAT.get(), FatCatEntity.createAttributes().build());
        event.put(ModEntities.POPUDAI.get(), PopudaiEntity.createAttributes().build());
    }

    private ModSetup() {
    }
}
