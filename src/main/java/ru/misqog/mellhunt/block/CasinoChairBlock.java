package ru.misqog.mellhunt.block;

import ru.misqog.mellhunt.entity.ChairSeatEntity;
import ru.misqog.mellhunt.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A casino chair. Right-click to sit down; shift to stand up.
 *
 * <p>The seat itself is a tiny invisible {@link ChairSeatEntity} spawned on
 * top of the block and removed as soon as the player gets up or the chair is
 * broken. The chair faces the direction the player was looking when placing
 * it, which makes it easy to line chairs up with a table.</p>
 */
public class CasinoChairBlock extends HorizontalDirectionalBlock {

    private static final VoxelShape SHAPE =
            Block.box(1.5D, 0.0D, 1.5D, 14.5D, 16.0D, 14.5D);

    public CasinoChairBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSpectator()) {
            return InteractionResult.PASS;
        }

        AABB search = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D);
        if (!level.getEntitiesOfClass(ChairSeatEntity.class, search).isEmpty()) {
            return InteractionResult.PASS;
        }

        ChairSeatEntity seat = ModEntities.CHAIR_SEAT.get().create(level);
        if (seat == null) {
            return InteractionResult.PASS;
        }

        seat.setSeatPos(pos);
        seat.setYRot(state.getValue(FACING).toYRot());
        level.addFreshEntity(seat);
        if (!player.startRiding(seat, true)) {
            seat.discard();
            return InteractionResult.PASS;
        }
        return InteractionResult.CONSUME;
    }
}
