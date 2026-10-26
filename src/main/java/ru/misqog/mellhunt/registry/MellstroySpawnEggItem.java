package ru.misqog.mellhunt.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import org.jetbrains.annotations.Nullable;

/**
 * Items are registered before entity types, so the real entity type cannot be
 * resolved in the constructor. {@link SpawnEggItem} only needs it lazily.
 */
public class MellstroySpawnEggItem extends SpawnEggItem {

    public MellstroySpawnEggItem(int backgroundColor, int highlightColor, Item.Properties properties) {
        super(EntityType.PIG, backgroundColor, highlightColor, properties);
    }

    @Override
    public EntityType<? extends Mob> getType(@Nullable CompoundTag tag) {
        return ModEntities.MELLSTROY.get();
    }
}
