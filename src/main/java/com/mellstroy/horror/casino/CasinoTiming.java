package com.mellstroy.horror.casino;

/**
 * Timing shared by the server session and the client animation.
 *
 * <p>prokrutka.ogg is 7.745 seconds long. The reels therefore spin for the
 * whole sound: they stop one by one at 5.75s, 6.75s and 7.75s. The result sound
 * starts a little after the last reel has stopped.</p>
 */
public final class CasinoTiming {

    /** ceil(7.745 * 20) - the full length of prokrutka.ogg in ticks. */
    public static final int SPIN_TICKS = 155;

    /** Reels stop one by one: 5.75s, 6.75s, 7.75s. */
    public static final int[] REEL_STOP_TICKS = {
            SPIN_TICKS - 40,
            SPIN_TICKS - 20,
            SPIN_TICKS
    };

    /** A short pause between the last reel stopping and the win/lose sound. */
    public static final int RESULT_SOUND_DELAY_TICKS = 8;

    /** Tick after the spin start when the win/lose sound begins. */
    public static final int RESULT_START_TICKS =
            REEL_STOP_TICKS[2] + RESULT_SOUND_DELAY_TICKS;

    private CasinoTiming() {
    }
}
