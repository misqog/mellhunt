package ru.misqog.mellhunt.entity;

import ru.misqog.mellhunt.block.CasinoChairBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The invisible seat of a {@link CasinoChairBlock}.
 *
 * <p>It exists only while somebody is sitting: an empty seat removes itself
 * after a few ticks, and so does a seat whose chair block is gone. The player
 * is dismounted to the side of the chair so they do not end up inside the
 * table in front of it.</p>
 */
public class ChairSeatEntity extends Entity {

    /** Top of the velvet cushion in the chair model (10/16 of a block). */
    private static final double SEAT_HEIGHT = 10.0D / 16.0D;

    /**
     * Distance from the seated player's feet to the top of their thighs in the
     * vanilla player model: the leg pivots sit at model height 12 px, and the
     * player model is rendered at 15/16 scale.
     */
    private static final double SEATED_HIP_HEIGHT = 12.0D / 16.0D * 15.0D / 16.0D;

    /** {@code Player#getMyRidingOffset()} - vanilla lowers the player by this much. */
    private static final double PLAYER_RIDING_OFFSET = -0.35D;

    /** Keep the player's body a hair above the cushion so the two never z-fight. */
    private static final double SEAT_CLEARANCE = 0.01D;

    /** Offset that puts the player's hips on the cushion instead of above it. */
    private static final double CUSHION_RIDING_OFFSET =
            -(SEATED_HIP_HEIGHT + PLAYER_RIDING_OFFSET) + SEAT_CLEARANCE;

    private static final int EMPTY_GRACE_TICKS = 5;

    @Nullable
    private BlockPos seatPos;
    private int life;

    public ChairSeatEntity(EntityType<? extends ChairSeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvisible(true);
    }

    public void setSeatPos(BlockPos pos) {
        this.seatPos = pos.immutable();
        this.setPos(pos.getX() + 0.5D, pos.getY() + SEAT_HEIGHT, pos.getZ() + 0.5D);
    }

    @Nullable
    public BlockPos getSeatPos() {
        return this.seatPos;
    }

    /**
     * The default (0.1 * 0.75) lifts the player above the chair. Vanilla also
     * applies {@link Player#getMyRidingOffset()} = -0.35 and renders the player
     * in the riding pose, so the hips are 0.703 blocks above the entity's feet.
     * Returning a negative offset lets the hips land on the cushion surface.
     */
    @Override
    public double getPassengersRidingOffset() {
        return CUSHION_RIDING_OFFSET;
    }

    @Override
    public void tick() {
        super.tick();
        ++this.life;

        if (this.level().isClientSide) {
            return;
        }

        boolean chairGone = this.seatPos == null
                || !(this.level().getBlockState(this.seatPos).getBlock() instanceof CasinoChairBlock);
        boolean empty = this.life > EMPTY_GRACE_TICKS && this.getPassengers().isEmpty();
        if (chairGone) {
            this.ejectPassengers();
            this.discard();
        } else if (empty) {
            this.discard();
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Direction facing = Direction.fromYRot(this.getYRot());
        Direction side = facing.getClockWise();
        return new Vec3(
                this.getX() + side.getStepX() * 0.9D,
                this.getY() - SEAT_HEIGHT,
                this.getZ() + side.getStepZ() * 0.9D);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Seat")) {
            this.seatPos = BlockPos.of(tag.getLong("Seat"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.seatPos != null) {
            tag.putLong("Seat", this.seatPos.asLong());
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
