package ru.misqog.mellhunt.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Four stacks of poker chips standing on the floor. */
public class ChipStackBlock extends Block {

    private static final VoxelShape SHAPE =
            Block.box(1.0D, 0.0D, 1.0D, 15.0D, 6.0D, 15.0D);

    public ChipStackBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
