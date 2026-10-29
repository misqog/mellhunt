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
 * An open MSI laptop standing on the ground.
 *
 * <p>Like the chekushka it can be turned in 16 steps of 22.5 degrees: every
 * right-click turns it a little, so it can be aimed at the viewer. The item
 * is {@link ru.misqog.mellhunt.item.LaptopItem}, which places this block on a
 * regular right-click and is dropped again when the laptop is broken.</p>
 */
public class LaptopBlock extends Block {

    /** 16 possible rotations, 22.5 degrees per click. */
    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;

    /** Chassis footprint in model units, before rotation. */
    private static final double[][] BASE_FOOTPRINT = {
            {1.5D, 2.28D}, {14.5D, 2.28D}, {1.5D, 12.2D}, {14.5D, 12.2D}
    };

    /** The screen and hinge stand at the back of the chassis. */
    private static final double[][] LID_FOOTPRINT = {
            {1.5D, 2.04D}, {14.5D, 2.04D}, {1.5D, 4.2D}, {14.5D, 4.2D}
    };

    private static final double BASE_HEIGHT = 0.98D;
    private static final double LID_HEIGHT = 9.7D;

    /** Outline/collision shapes for all 16 rotations. */
    private static final VoxelShape[] SHAPES = createShapes();

    public LaptopBlock(Properties properties) {
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
     * Rotation that turns the screen towards the player who is placing the
     * laptop.
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
            level.playSound(null, pos, SoundEvents.WOODEN_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.35F, 1.7F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static VoxelShape[] createShapes() {
        VoxelShape[] shapes = new VoxelShape[16];
        for (int i = 0; i < shapes.length; i++) {
            double angle = Math.toRadians(i * 22.5D);
            shapes[i] = Shapes.or(
                    rotatedBox(BASE_FOOTPRINT, BASE_HEIGHT, angle),
                    rotatedBox(LID_FOOTPRINT, LID_HEIGHT, angle));
        }
        return shapes;
    }

    /**
     * An axis-aligned box around the rotated footprint. The real model is a
     * little smaller, but a box per part is a good enough selection shape.
     */
    private static VoxelShape rotatedBox(double[][] footprint, double height, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;

        for (double[] corner : footprint) {
            double x = corner[0] - 8.0D;
            double z = corner[1] - 8.0D;
            double rotatedX = x * cos - z * sin;
            double rotatedZ = x * sin + z * cos;
            minX = Math.min(minX, rotatedX);
            maxX = Math.max(maxX, rotatedX);
            minZ = Math.min(minZ, rotatedZ);
            maxZ = Math.max(maxZ, rotatedZ);
        }

        return Shapes.box(
                (8.0D + minX) / 16.0D, 0.0D, (8.0D + minZ) / 16.0D,
                (8.0D + maxX) / 16.0D, height / 16.0D, (8.0D + maxZ) / 16.0D);
    }
}
