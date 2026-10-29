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
                        output.accept(ModItems.LAPTOP.get());

                        // Floors
                        output.accept(ModItems.CASINO_TILE.get());
                        output.accept(ModItems.CASINO_FLOOR_GOLD.get());
                        output.accept(ModItems.CASINO_CARPET.get());

                        // Walls
                        output.accept(ModItems.CASINO_WALL_WOOD.get());
                        output.accept(ModItems.CASINO_WALLPAPER.get());
                        output.accept(ModItems.CASINO_WALL_NEON.get());
                        output.accept(ModItems.CASINO_TRIM.get());

                        // Furniture
                        output.accept(ModItems.SLOT_MACHINE.get());
                        output.accept(ModItems.ROULETTE_TABLE.get());
                        output.accept(ModItems.POKER_TABLE.get());
                        output.accept(ModItems.CASINO_CHAIR.get());
                        output.accept(ModItems.CHIP_STACK.get());
                        output.accept(ModItems.MELLSTROY_SIGN.get());

                        output.accept(ModItems.FAT_CAT_SPAWN_EGG.get());
                        output.accept(ModItems.MELLSTROY_SPAWN_EGG.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
