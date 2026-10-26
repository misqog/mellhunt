package ru.misqog.mellhunt.advancement;

import ru.misqog.mellhunt.MellstroyMod;
import net.minecraft.advancements.Advancement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Grants the achievements whose conditions cannot be expressed with vanilla
 * advancement triggers. The JSON files use {@code minecraft:impossible}, and
 * the actual checks live here.
 */
public final class ModAdvancements {

    /** Must match the criterion name in the advancement JSON files. */
    private static final String CRITERION = "code_trigger";

    private static final String CASINO_WINS = "mellhunt_casino_wins";
    private static final String CASINO_LOSSES = "mellhunt_casino_losses";
    private static final int CASINO_TARGET = 5;

    private ModAdvancements() {
    }

    public static void onEscaped(ServerPlayer player) {
        award(player, "ne_dognal");
    }

    public static void onCasinoWin(ServerPlayer player) {
        addCasinoResult(player, CASINO_WINS, "chto_za_biznes");
    }

    public static void onCasinoLoss(ServerPlayer player) {
        addCasinoResult(player, CASINO_LOSSES, "otkazano");
    }

    private static void addCasinoResult(ServerPlayer player, String key, String advancementId) {
        CompoundTag counters = counters(player);
        int value = counters.getInt(key) + 1;
        counters.putInt(key, value);
        if (value >= CASINO_TARGET) {
            award(player, advancementId);
        }
    }

    /**
     * Counters live in Forge's persistent player tag. The whole tag is saved
     * with the player, but only the {@code PlayerPersisted} child is copied on
     * respawn, so the counters must be stored there to survive the casino death.
     */
    private static CompoundTag counters(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static void award(ServerPlayer player, String id) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        Advancement advancement = server.getAdvancements()
                .getAdvancement(new ResourceLocation(MellstroyMod.MOD_ID, id));
        if (advancement != null) {
            player.getAdvancements().award(advancement, CRITERION);
        }
    }
}
