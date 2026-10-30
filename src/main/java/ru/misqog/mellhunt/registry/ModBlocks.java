package ru.misqog.mellhunt.registry;

import ru.misqog.mellhunt.MellstroyMod;
import ru.misqog.mellhunt.block.CasinoChairBlock;
import ru.misqog.mellhunt.block.CasinoTableBlock;
import ru.misqog.mellhunt.block.ChekushkaBlock;
import ru.misqog.mellhunt.block.ChipStackBlock;
import ru.misqog.mellhunt.block.LaptopBlock;
import ru.misqog.mellhunt.block.MellstroySignBlock;
import ru.misqog.mellhunt.block.PokerTableBlock;
import ru.misqog.mellhunt.block.SlotMachineBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MellstroyMod.MOD_ID);

    /**
     * The placed chekushka. There is no separate BlockItem: the ordinary
     * {@link ru.misqog.mellhunt.item.ChekushkaItem} places this block when
     * the player sneaks and right-clicks, and breaking the block drops that
     * same item.
     */
    public static final RegistryObject<Block> CHEKUSHKA = BLOCKS.register("chekushka",
            () -> new ChekushkaBlock(BlockBehaviour.Properties.of()
                    .instabreak()
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)));

    /**
     * The placed laptop. Same idea as the chekushka: the ordinary
     * {@link ru.misqog.mellhunt.item.LaptopItem} places it, and breaking the
     * block drops that item again.
     */
    public static final RegistryObject<Block> LAPTOP = BLOCKS.register("laptop",
            () -> new LaptopBlock(BlockBehaviour.Properties.of()
                    .strength(0.6F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)));

    /* ------------------------------------------------------------------ */
    /*  Casino floors                                                     */
    /* ------------------------------------------------------------------ */

    /** Polished black-and-white checkerboard with gold grout. */
    public static final RegistryObject<Block> CASINO_TILE = BLOCKS.register("casino_tile",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(1.5F)
                    .sound(SoundType.STONE)));

    /** The VIP floor: black marble plates in a gold inlay grid. */
    public static final RegistryObject<Block> CASINO_FLOOR_GOLD = BLOCKS.register("casino_floor_gold",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(1.8F)
                    .sound(SoundType.STONE)));

    /** Stairs made of the same black-and-gold VIP floor. */
    public static final RegistryObject<Block> CASINO_FLOOR_GOLD_STAIRS = BLOCKS.register("casino_floor_gold_stairs",
            () -> new StairBlock(() -> CASINO_FLOOR_GOLD.get().defaultBlockState(),
                    BlockBehaviour.Properties.of()
                            .strength(1.8F)
                            .sound(SoundType.STONE)));

    /**
     * Deep red carpet with a gold medallion. It is a real vanilla-style carpet:
     * one pixel high, it can be placed on top of any solid block and breaks
     * when that support is removed.
     */
    public static final RegistryObject<Block> CASINO_CARPET = BLOCKS.register("casino_carpet",
            () -> new CarpetBlock(BlockBehaviour.Properties.of()
                    .strength(0.1F)
                    .sound(SoundType.WOOL)
                    .pushReaction(PushReaction.DESTROY)));

    /* ------------------------------------------------------------------ */
    /*  Casino walls                                                      */
    /* ------------------------------------------------------------------ */

    /** Mahogany panelling with two brass rails. */
    public static final RegistryObject<Block> CASINO_WALL_WOOD = BLOCKS.register("casino_wall_wood",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(2.0F)
                    .sound(SoundType.WOOD)));

    /** Burgundy wallpaper with a gold damask. */
    public static final RegistryObject<Block> CASINO_WALLPAPER = BLOCKS.register("casino_wallpaper",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(1.2F)
                    .sound(SoundType.WOOL)));

    /** Dark metal panelling with a magenta neon tube; it lights the room. */
    public static final RegistryObject<Block> CASINO_WALL_NEON = BLOCKS.register("casino_wall_neon",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(2.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 14)));

    /** Polished gold moulding to frame the walls and the floor. */
    public static final RegistryObject<Block> CASINO_TRIM = BLOCKS.register("casino_trim",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0F)
                    .sound(SoundType.METAL)));

    /* ------------------------------------------------------------------ */
    /*  Casino furniture                                                  */
    /* ------------------------------------------------------------------ */

    /** A slot cabinet: right-click to hear the reels spin. */
    public static final RegistryObject<Block> SLOT_MACHINE = BLOCKS.register("slot_machine",
            () -> new SlotMachineBlock(BlockBehaviour.Properties.of()
                    .strength(2.5F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 7)
                    .noOcclusion()));

    /** A roulette table with a felt top and a wheel painted on it. */
    public static final RegistryObject<Block> ROULETTE_TABLE = BLOCKS.register("roulette_table",
            () -> new CasinoTableBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    /**
     * One large poker table: a 6 x 2 multiblock. Placing the item fills the
     * whole footprint and breaking any part removes the table as a whole.
     */
    public static final RegistryObject<Block> POKER_TABLE = BLOCKS.register("poker_table",
            () -> new PokerTableBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    /** A velvet chair; right-click to sit down, shift to get up. */
    public static final RegistryObject<Block> CASINO_CHAIR = BLOCKS.register("casino_chair",
            () -> new CasinoChairBlock(BlockBehaviour.Properties.of()
                    .strength(1.2F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    /** Four stacks of chips on the floor. */
    public static final RegistryObject<Block> CHIP_STACK = BLOCKS.register("chip_stack",
            () -> new ChipStackBlock(BlockBehaviour.Properties.of()
                    .strength(0.5F)
                    .sound(SoundType.STONE)
                    .noOcclusion()));

    /** A wide freestanding neon sign: "mellstroy.game". */
    public static final RegistryObject<Block> MELLSTROY_SIGN = BLOCKS.register("mellstroy_sign",
            () -> new MellstroySignBlock(BlockBehaviour.Properties.of()
                    .strength(2.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 13)
                    .noOcclusion()));

    private ModBlocks() {
    }
}
