package com.mellstroy.horror.client;

import com.mellstroy.horror.MellstroyMod;
import com.mellstroy.horror.registry.ModEntities;
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
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(FatCatModel.LAYER_LOCATION, FatCatModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("mellstroy_fear", ClientFear::renderOverlay);
    }

    private ClientSetup() {
    }
}
