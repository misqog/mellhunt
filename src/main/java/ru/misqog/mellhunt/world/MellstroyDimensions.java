package ru.misqog.mellhunt.world;

import ru.misqog.mellhunt.MellstroyMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

/**
 * Resource keys for the mod's own dimensions.
 *
 * <p>The casino is a separate void world. Keeping its key in one place avoids
 * stringly-typed dimension comparisons all over the codebase.</p>
 */
public final class MellstroyDimensions {

    /** The hidden casino dimension from {@code data/mellhunt/dimension/casino.json}. */
    public static final ResourceKey<Level> CASINO = ResourceKey.create(
            Registries.DIMENSION, new ResourceLocation(MellstroyMod.MOD_ID, "casino"));

    public static boolean isCasino(LevelAccessor level) {
        return level instanceof Level actual && isCasino(actual);
    }

    public static boolean isCasino(Level level) {
        return level != null && level.dimension().equals(CASINO);
    }

    private MellstroyDimensions() {
    }
}
