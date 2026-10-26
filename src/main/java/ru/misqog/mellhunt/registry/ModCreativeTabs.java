package ru.misqog.mellhunt.registry;

import ru.misqog.mellhunt.MellstroyMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * A dedicated creative tab, so the mod's items are not buried among the
 * vanilla ones.
 */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MellstroyMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MELLSTROY = TABS.register("mellhunt",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.mellhunt"))
                    .icon(() -> new ItemStack(ModItems.CHEKUSHKA.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.CHEKUSHKA.get());
                        output.accept(ModItems.FAT_CAT_SPAWN_EGG.get());
                        output.accept(ModItems.MELLSTROY_SPAWN_EGG.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
