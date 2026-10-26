package ru.misqog.mellhunt.casino;

/**
 * Timing shared by the server session and the client animation.
 *
 * <p>Every spin is stretched to the full length of its sound: prokrutka.ogg
 * (7.745 s) for the first spin and izi_brigi.ogg (9.324 s) for the 1% bonus
 * round. The reels stop one by one during the last second, then the result
 * sound starts a little later.</p>
 */
public final class CasinoTiming {

    /** ceil(7.745 * 20) - the full length of prokrutka.ogg in ticks. */
    public static final int SPIN_TICKS = 155;

    /** ceil(9.324 * 20) - the full length of izi_brigi.ogg in ticks. */
    public static final int IZI_BRIGI_SPIN_TICKS = 187;

    /** Reels stop one by one: 5.75s, 6.75s and 7.75s of prokrutka. */
    public static final int[] REEL_STOP_TICKS = {
            reelStopTicks(SPIN_TICKS, 0),
            reelStopTicks(SPIN_TICKS, 1),
            reelStopTicks(SPIN_TICKS, 2)
    };

    /** A short pause between the last reel stopping and the win/lose sound. */
    public static final int RESULT_SOUND_DELAY_TICKS = 8;

    /** Tick after the spin start when the win/lose sound begins. */
    public static final int RESULT_START_TICKS = resultStartTicks(SPIN_TICKS);

    /** Tick at which reel {@code index} (0..2) locks to the final symbol. */
    public static int reelStopTicks(int spinTicks, int index) {
        return spinTicks - 40 + index * 20;
    }

    /** Tick after the spin start when the result sound begins. */
    public static int resultStartTicks(int spinTicks) {
        return spinTicks + RESULT_SOUND_DELAY_TICKS;
    }

    private CasinoTiming() {
    }
}
