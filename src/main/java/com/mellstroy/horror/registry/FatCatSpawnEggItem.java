package com.mellstroy.horror.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/**
 * Same lazy-type trick as {@link MellstroySpawnEggItem}: items are registered
 * before entity types, so the real type can only be resolved on use.
 */
public class FatCatSpawnEggItem extends SpawnEggItem {

    public FatCatSpawnEggItem(int backgroundColor, int highlightColor, Item.Properties properties) {
        super(EntityType.PIG, backgroundColor, highlightColor, properties);
    }

    @Override
    public EntityType<? extends Mob> getType(@Nullable CompoundTag tag) {
        return ModEntities.FAT_CAT.get();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        int countBefore = stack.getCount();
        // Using an egg on a spawner only changes the spawner, it does not spawn
        // a cat, so it must not trigger the sound.
        boolean spawner = level.getBlockState(context.getClickedPos()).is(Blocks.SPAWNER);

        InteractionResult result = super.useOn(context);

        // The egg stack only shrinks when a cat was actually spawned.
        if (!level.isClientSide && !spawner && stack.getCount() < countBefore
                && context.getPlayer() instanceof ServerPlayer player) {
            long now = level.getGameTime();
            CompoundTag data = player.getPersistentData();
            long last = data.getLong("mellstroyCatEggSoundTick");
            if (last == 0L || now - last >= 1200L) {
                data.putLong("mellstroyCatEggSoundTick", now);
                BlockPos soundPos = context.getClickedPos().relative(context.getClickedFace());
                level.playSound(null, soundPos, ModSounds.MALISHOCHEK.get(),
                        SoundSource.NEUTRAL, 1.0F, 1.0F);
            }
        }

        return result;
    }
}
