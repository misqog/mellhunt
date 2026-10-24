package com.mellstroy.horror;

import com.mellstroy.horror.config.MellstroyConfig;
import com.mellstroy.horror.network.ModNetwork;
import com.mellstroy.horror.registry.ModEntities;
import com.mellstroy.horror.registry.ModCreativeTabs;
import com.mellstroy.horror.registry.ModItems;
import com.mellstroy.horror.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Mellstroy - a horror mod about a man who follows you home.
 *
 * He cannot be killed. The only way to survive is to break his line of sight,
 * crouch and stay still until he loses your trail. And if he does catch you,
 * he offers you one spin of his casino.
 */
@Mod(MellstroyMod.MOD_ID)
public class MellstroyMod {

    public static final String MOD_ID = "mellstroy";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MellstroyMod(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        ModEntities.ENTITY_TYPES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModSounds.SOUNDS.register(modBus);

        modBus.addListener(MellstroyMod::onCommonSetup);

        context.registerConfig(ModConfig.Type.COMMON, MellstroyConfig.SPEC);

        LOGGER.info("[Mellstroy] He is already behind you.");
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }
}
