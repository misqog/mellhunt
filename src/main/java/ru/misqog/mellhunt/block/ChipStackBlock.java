package ru.misqog.mellhunt.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Small poker-chip stacks. Right-clicking cycles through eight arrangements:
 * a single chip, stacked chips and several stacks at different positions.
 *
 * <p>When placed on a poker or roulette table the block detects the table
 * below and switches to the {@link #ON_TABLE} model, which is shifted down by
 * the missing 4/16 of the table height so the chips rest on the felt instead
 * of hovering above it.</p>
 */
public class ChipStackBlock extends Block {

    public static final int VARIANT_COUNT = 8;
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, VARIANT_COUNT - 1);
    public static final BooleanProperty ON_TABLE = BooleanProperty.create("on_table");

    private static final VoxelShape FLOOR_SHAPE =
            Block.box(1.0D, 0.0D, 1.0D, 15.0D, 5.0D, 15.0D);
    /** The table top is at 12/16; the chip block sits at y+1, so shift it down 4/16. */
    private static final VoxelShape TABLE_SHAPE =
            Block.box(1.0D, -4.0D, 1.0D, 15.0D, 1.0D, 15.0D);

    public ChipStackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(VARIANT, 0)
                .setValue(ON_TABLE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VARIANT, ON_TABLE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean onTable = isTable(context.getLevel().getBlockState(context.getClickedPos().below()));
        return defaultBlockState()
                .setValue(VARIANT, 0)
                .setValue(ON_TABLE, onTable);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(ON_TABLE) ? TABLE_SHAPE : FLOOR_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        // Chips lying on a table are decorative and must not block the surface.
        return state.getValue(ON_TABLE) ? Shapes.empty() : FLOOR_SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockState next = state.cycle(VARIANT);
            level.setBlock(pos, next, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS,
                    0.35F, 1.25F + next.getValue(VARIANT) * 0.06F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockState updateShape(BlockState state, net.minecraft.core.Direction direction,
                                  BlockState neighborState, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(ON_TABLE) && !isTable(level.getBlockState(pos.below()))) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        return !state.getValue(ON_TABLE) || isTable(level.getBlockState(pos.below()));
    }

    private static boolean isTable(BlockState state) {
        return state.getBlock() instanceof CasinoTableBlock || state.getBlock() instanceof PokerTableBlock;
    }
}
