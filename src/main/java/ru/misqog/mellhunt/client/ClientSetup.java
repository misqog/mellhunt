package ru.misqog.mellhunt.client;

import ru.misqog.mellhunt.MellstroyMod;
import ru.misqog.mellhunt.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MellstroyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MELLSTROY.get(), MellstroyRenderer::new);
        event.registerEntityRenderer(ModEntities.FAT_CAT.get(), FatCatRenderer::new);
        event.registerEntityRenderer(ModEntities.CHAIR_SEAT.get(), ChairSeatRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(FatCatModel.LAYER_LOCATION, FatCatModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("mellhunt_fear", ClientFear::renderOverlay);
    }

    private ClientSetup() {
    }
}
