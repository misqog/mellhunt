package ru.misqog.mellhunt.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import ru.misqog.mellhunt.casino.PokerManager;
import ru.misqog.mellhunt.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The large poker table: six blocks wide and two blocks deep.
 *
 * <p>All twelve cells are the same block distinguished by {@link #PART}. The
 * item is placed as one unit: the clicked block becomes the front-row anchor
 * part ({@link #ANCHOR_PART}) and {@link #setPlacedBy} fills the rest of the
 * footprint. Breaking any cell removes the whole table and drops exactly one
 * item, so the table always behaves as a single piece of furniture.</p>
 */
public class PokerTableBlock extends HorizontalDirectionalBlock {

    public static final int WIDTH = 6;
    public static final int DEPTH = 2;
    public static final int PART_COUNT = WIDTH * DEPTH;

    /** Clicked cell: front row, two blocks from the left end. */
    public static final int ANCHOR_COL = 2;
    public static final int ANCHOR_PART = ANCHOR_COL;

    public static final IntegerProperty PART = IntegerProperty.create("part", 0, PART_COUNT - 1);

    private static final VoxelShape SHAPE =
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 12.0D, 16.0D);

    /** Guards the recursive cleanup performed through {@link #onRemove}. */
    private static boolean removing;

    public PokerTableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos origin = originFor(context.getClickedPos(), facing);

        if (!canPlaceTable(level, origin, facing)) {
            return null;
        }

        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(PART, ANCHOR_PART);
    }

    /**
     * Places every cell of the table in one go. Called once for the anchor
     * part, immediately after the anchor block has been written to the world.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (state.getValue(PART) != ANCHOR_PART) {
            return;
        }

        Direction facing = state.getValue(FACING);
        Direction depth = facing.getOpposite();
        Direction width = facing.getClockWise();
        BlockPos origin = originFor(pos, facing);

        for (int row = 0; row < DEPTH; row++) {
            for (int col = 0; col < WIDTH; col++) {
                BlockPos target = origin.relative(depth, row).relative(width, col);
                if (target.equals(pos)) {
                    continue;
                }
                BlockState partState = defaultBlockState()
                        .setValue(FACING, facing)
                        .setValue(PART, row * WIDTH + col);
                level.setBlock(target, partState, 3);
            }
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            if (!player.isCreative()) {
                Block.popResource(level, pos, new ItemStack(this));
            }
            // Leave the broken cell itself to the vanilla destruction path;
            // this keeps its stats, tool handling and block-removal semantics.
            removeTable(level, pos, state, true);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Cleans up the other cells when a part is replaced by something else
     * (commands, explosions, pistons). Normal player breaking is handled by
     * {@link #playerWillDestroy} and drops the single table item there.
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !removing && !newState.is(state.getBlock())) {
            removeTable(level, pos, state, false);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        // Chips and shift-click are for building; an ordinary click plays.
        if (player.isShiftKeyDown() || player.getItemInHand(hand).is(ModItems.CHIP_STACK.get())) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            PokerManager.open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static BlockPos originFor(BlockPos anchor, Direction facing) {
        // The anchor sits in the front row; width runs along the facing's clockwise axis.
        return anchor.relative(facing.getClockWise(), -ANCHOR_COL);
    }

    private static boolean canPlaceTable(Level level, BlockPos origin, Direction facing) {
        Direction depth = facing.getOpposite();
        Direction width = facing.getClockWise();

        for (int row = 0; row < DEPTH; row++) {
            for (int col = 0; col < WIDTH; col++) {
                BlockPos target = origin.relative(depth, row).relative(width, col);
                if (!level.isInWorldBounds(target)) {
                    return false;
                }

                BlockState current = level.getBlockState(target);
                if (!current.isAir()
                        && !(current.canBeReplaced() && current.getFluidState().isEmpty())) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Removes every other cell of the table that belongs to the broken part. */
    private static void removeTable(Level level, BlockPos pos, BlockState state, boolean keepPos) {
        if (removing) {
            return;
        }

        Direction facing = state.getValue(FACING);
        Direction depth = facing.getOpposite();
        Direction width = facing.getClockWise();
        int part = state.getValue(PART);
        BlockPos origin = pos
                .relative(depth, -(part / WIDTH))
                .relative(width, -(part % WIDTH));

        removing = true;
        try {
            for (int row = 0; row < DEPTH; row++) {
                for (int col = 0; col < WIDTH; col++) {
                    BlockPos target = origin.relative(depth, row).relative(width, col);
                    if (keepPos && target.equals(pos)) {
                        continue;
                    }
                    BlockState current = level.getBlockState(target);
                    if (current.is(state.getBlock()) && current.getValue(FACING) == facing) {
                        level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        } finally {
            removing = false;
        }
    }
}
