package ru.misqog.mellhunt.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class MellstroyConfig {

    public static final Common COMMON;
    public static final ForgeConfigSpec SPEC;

    static {
        Pair<Common, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        SPEC = pair.getRight();
    }

    private MellstroyConfig() {
    }

    /* Safe accessors: config values can be queried before the config file is loaded. */
    public static boolean naturalSpawn() {
        return get(() -> COMMON.naturalSpawn.get(), true);
    }

    public static int spawnIntervalSeconds() {
        return get(() -> COMMON.spawnIntervalSeconds.get(), 60);
    }

    public static int spawnMinDistance() {
        return get(() -> COMMON.spawnMinDistance.get(), 45);
    }

    public static int spawnMaxDistance() {
        return get(() -> COMMON.spawnMaxDistance.get(), 75);
    }

    public static boolean spawnOnlyAtNight() {
        return get(() -> COMMON.spawnOnlyAtNight.get(), false);
    }

    public static int huntRange() {
        return get(() -> COMMON.huntRange.get(), 56);
    }

    public static boolean oneHitKill() {
        return get(() -> COMMON.oneHitKill.get(), true);
    }

    public static boolean redScreen() {
        return get(() -> COMMON.redScreen.get(), true);
    }

    public static boolean casinoEnabled() {
        return get(() -> COMMON.casinoEnabled.get(), true);
    }

    public static int casinoWinChanceMin() {
        return get(() -> COMMON.casinoWinChanceMin.get(), 30);
    }

    public static int casinoWinChanceMax() {
        return get(() -> COMMON.casinoWinChanceMax.get(), 75);
    }

    public static int slotMachineWinChance() {
        return get(() -> COMMON.slotMachineWinChance.get(), 35);
    }

    /**
     * The jackpot chance is rolled again every time the casino opens. The
     * configured range is inclusive.
     */
    public static int randomCasinoWinChance(net.minecraft.util.RandomSource random) {
        int min = casinoWinChanceMin();
        int max = casinoWinChanceMax();
        if (min > max) {
            int tmp = min;
            min = max;
            max = tmp;
        }
        return min + random.nextInt(max - min + 1);
    }

    private static <T> T get(java.util.function.Supplier<T> supplier, T fallback) {
        try {
            T value = supplier.get();
            return value == null ? fallback : value;
        } catch (Exception e) {
            return fallback;
        }
    }

    public static final class Common {

        public final ForgeConfigSpec.BooleanValue naturalSpawn;
        public final ForgeConfigSpec.IntValue spawnIntervalSeconds;
        public final ForgeConfigSpec.IntValue spawnMinDistance;
        public final ForgeConfigSpec.IntValue spawnMaxDistance;
        public final ForgeConfigSpec.BooleanValue spawnOnlyAtNight;
        public final ForgeConfigSpec.IntValue huntRange;
        public final ForgeConfigSpec.BooleanValue oneHitKill;
        public final ForgeConfigSpec.BooleanValue redScreen;
        public final ForgeConfigSpec.BooleanValue casinoEnabled;
        public final ForgeConfigSpec.IntValue casinoWinChanceMin;
        public final ForgeConfigSpec.IntValue casinoWinChanceMax;
        public final ForgeConfigSpec.IntValue slotMachineWinChance;

        private Common(ForgeConfigSpec.Builder builder) {
            builder.push("mellhunt");

            this.naturalSpawn = builder
                    .comment("Spawn Mellstroy automatically.")
                    .define("naturalSpawn", true);

            this.spawnIntervalSeconds = builder
                    .comment("How often (in seconds) the mod tries to spawn Mellstroy.")
                    .defineInRange("spawnIntervalSeconds", 60, 10, 3600);

            this.spawnMinDistance = builder
                    .comment("He never spawns closer than this, so you always get a head start.")
                    .defineInRange("spawnMinDistance", 45, 16, 200);

            this.spawnMaxDistance = builder
                    .comment("Maximum spawn distance.")
                    .defineInRange("spawnMaxDistance", 75, 32, 300);

            this.spawnOnlyAtNight = builder
                    .comment("Only spawn at night.")
                    .define("spawnOnlyAtNight", false);

            this.huntRange = builder
                    .comment("How far (in blocks) Mellstroy can sense a player.")
                    .defineInRange("huntRange", 56, 16, 256);

            this.oneHitKill = builder
                    .comment("Kill the player with a single hit.")
                    .define("oneHitKill", true);

            this.redScreen = builder
                    .comment("Red screen and camera shake when Mellstroy is close.")
                    .define("redScreen", true);

            this.casinoEnabled = builder
                    .comment("When Mellstroy kills you, he offers one spin of his casino.",
                            "Win - you keep your items and he leaves. Lose - you die and lose everything.")
                    .define("casinoEnabled", true);

            this.casinoWinChanceMin = builder
                    .comment("Minimum jackpot chance in percent. A new random chance is rolled every time the casino opens.")
                    .defineInRange("casinoWinChanceMin", 30, 1, 99);

            this.casinoWinChanceMax = builder
                    .comment("Maximum jackpot chance in percent. The range is inclusive.")
                    .defineInRange("casinoWinChanceMax", 75, 1, 99);

            this.slotMachineWinChance = builder
                    .comment("Win chance of the playable slot machine block, in percent.",
                            "The machine takes one gold ingot per spin (free in creative).")
                    .defineInRange("slotMachineWinChance", 35, 1, 99);

            builder.pop();
        }
    }
}
