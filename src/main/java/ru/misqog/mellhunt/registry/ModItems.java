package ru.misqog.mellhunt.registry;

import ru.misqog.mellhunt.MellstroyMod;
import ru.misqog.mellhunt.item.ChekushkaItem;
import ru.misqog.mellhunt.item.LaptopItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
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

    public static final RegistryObject<Item> POPUDAI_SPAWN_EGG = ITEMS.register("popudai_spawn_egg",
            () -> new PopudaiSpawnEggItem(0xF8CB3A, 0x2C74D6, new Item.Properties()));

    public static final RegistryObject<Item> CHEKUSHKA = ITEMS.register("chekushka",
            () -> new ChekushkaItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> LAPTOP = ITEMS.register("laptop",
            () -> new LaptopItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    /* ------------------------------------------------------------------ */
    /*  Casino blocks                                                     */
    /* ------------------------------------------------------------------ */

    public static final RegistryObject<Item> CASINO_TILE =
            blockItem("casino_tile", ModBlocks.CASINO_TILE);
    public static final RegistryObject<Item> CASINO_FLOOR_GOLD =
            blockItem("casino_floor_gold", ModBlocks.CASINO_FLOOR_GOLD);
    public static final RegistryObject<Item> CASINO_FLOOR_GOLD_STAIRS =
            blockItem("casino_floor_gold_stairs", ModBlocks.CASINO_FLOOR_GOLD_STAIRS);
    public static final RegistryObject<Item> CASINO_CARPET =
            blockItem("casino_carpet", ModBlocks.CASINO_CARPET);
    public static final RegistryObject<Item> CASINO_WALL_WOOD =
            blockItem("casino_wall_wood", ModBlocks.CASINO_WALL_WOOD);
    public static final RegistryObject<Item> CASINO_WALLPAPER =
            blockItem("casino_wallpaper", ModBlocks.CASINO_WALLPAPER);
    public static final RegistryObject<Item> CASINO_WALL_NEON =
            blockItem("casino_wall_neon", ModBlocks.CASINO_WALL_NEON);
    public static final RegistryObject<Item> CASINO_TRIM =
            blockItem("casino_trim", ModBlocks.CASINO_TRIM);
    public static final RegistryObject<Item> SLOT_MACHINE =
            blockItem("slot_machine", ModBlocks.SLOT_MACHINE);
    public static final RegistryObject<Item> ROULETTE_TABLE =
            blockItem("roulette_table", ModBlocks.ROULETTE_TABLE);
    public static final RegistryObject<Item> POKER_TABLE =
            blockItem("poker_table", ModBlocks.POKER_TABLE);
    public static final RegistryObject<Item> CASINO_CHAIR =
            blockItem("casino_chair", ModBlocks.CASINO_CHAIR);
    public static final RegistryObject<Item> CHIP_STACK =
            blockItem("chip_stack", ModBlocks.CHIP_STACK);
    public static final RegistryObject<Item> MELLSTROY_SIGN =
            blockItem("mellstroy_sign", ModBlocks.MELLSTROY_SIGN);

    private static RegistryObject<Item> blockItem(String name, RegistryObject<Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private ModItems() {
    }
}
