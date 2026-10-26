package ru.misqog.mellhunt.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * A chekushka standing on the ground.
 *
 * <p>It can be rotated in 16 steps (22.5 degrees each) - every right-click
 * turns it a little, so it can be aimed at the label. The block only exists in
 * the world; the item itself is still {@link ru.misqog.mellhunt.item.ChekushkaItem},
 * which is what gets dropped when the bottle is broken.</p>
 */
public class ChekushkaBlock extends Block {

    /** 16 possible rotations, 22.5 degrees per click. */
    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;

    /** Bottle body footprint in model units, before the model's scale factor. */
    private static final double[][] FOOTPRINT = {
            {4.0D, 5.75D}, {12.0D, 5.75D}, {4.0D, 10.25D}, {12.0D, 10.25D}
    };

    /** The model is scaled down to half size, so a bottle is roughly half a block tall. */
    private static final double MODEL_SCALE = 0.5D;
    private static final double MODEL_HEIGHT = 7.85D;

    /** Outline shapes for all 16 rotations, so the selection box follows the bottle. */
    private static final VoxelShape[] SHAPES = createShapes();

    public ChekushkaBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ROTATION, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROTATION);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(ROTATION, rotationFor(context.getPlayer()));
    }

    /**
     * Rotation that turns the label towards the player who is placing the
     * bottle.
     */
    public static int rotationFor(@Nullable Entity entity) {
        if (entity == null) {
            return 0;
        }
        return Mth.floor((entity.getYRot() + 180.0F) * 16.0F / 360.0F + 0.5D) & 15;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(ROTATION)];
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            // One click - one 22.5 degree step. Sixteen clicks bring it back.
            level.setBlock(pos, state.cycle(ROTATION), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 0.4F, 1.8F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static VoxelShape[] createShapes() {
        VoxelShape[] shapes = new VoxelShape[16];
        for (int i = 0; i < shapes.length; i++) {
            double angle = Math.toRadians(i * 22.5D);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double minX = Double.MAX_VALUE;
            double minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxZ = -Double.MAX_VALUE;

            for (double[] corner : FOOTPRINT) {
                double x = (corner[0] - 8.0D) * MODEL_SCALE;
                double z = (corner[1] - 8.0D) * MODEL_SCALE;
                double rotatedX = x * cos - z * sin;
                double rotatedZ = x * sin + z * cos;
                minX = Math.min(minX, rotatedX);
                maxX = Math.max(maxX, rotatedX);
                minZ = Math.min(minZ, rotatedZ);
                maxZ = Math.max(maxZ, rotatedZ);
            }

            shapes[i] = Shapes.box(
                    (8.0D + minX) / 16.0D, 0.0D, (8.0D + minZ) / 16.0D,
                    (8.0D + maxX) / 16.0D, MODEL_HEIGHT / 16.0D, (8.0D + maxZ) / 16.0D);
        }
        return shapes;
    }
}
