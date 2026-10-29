package ru.misqog.mellhunt.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A wide freestanding "mellstroy.game" neon sign.
 *
 * <p>The sign model is two blocks wide (32 pixels) and centred on the block
 * that was placed, so it is a deliberate overhang: put it in the open or in
 * front of a wall. Only the middle of the panel has a collision shape.</p>
 */
public class MellstroySignBlock extends HorizontalDirectionalBlock {

    private static final VoxelShape SHAPE =
            Block.box(0.0D, 0.0D, 2.0D, 16.0D, 16.0D, 12.0D);

    public MellstroySignBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
