package com.mellstroy.horror.registry;

import com.mellstroy.horror.MellstroyMod;
import com.mellstroy.horror.block.ChekushkaBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
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
     * {@link com.mellstroy.horror.item.ChekushkaItem} places this block when
     * the player sneaks and right-clicks, and breaking the block drops that
     * same item.
     */
    public static final RegistryObject<Block> CHEKUSHKA = BLOCKS.register("chekushka",
            () -> new ChekushkaBlock(BlockBehaviour.Properties.of()
                    .instabreak()
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)));

    private ModBlocks() {
    }
}
