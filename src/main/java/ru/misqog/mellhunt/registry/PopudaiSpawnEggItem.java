package ru.misqog.mellhunt.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import org.jetbrains.annotations.Nullable;

/**
 * Lazy-type spawn egg, same pattern as the other mob eggs: the item is
 * registered before the entity type, so the real type is resolved on use.
 */
public class PopudaiSpawnEggItem extends SpawnEggItem {

    public PopudaiSpawnEggItem(int backgroundColor, int highlightColor, Item.Properties properties) {
        super(EntityType.PIG, backgroundColor, highlightColor, properties);
    }

    @Override
    public EntityType<? extends Mob> getType(@Nullable CompoundTag tag) {
        return ModEntities.POPUDAI.get();
    }
}
