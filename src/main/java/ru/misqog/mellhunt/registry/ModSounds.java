package ru.misqog.mellhunt.registry;

import ru.misqog.mellhunt.MellstroyMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MellstroyMod.MOD_ID);

    /** How far the positional sounds are sent to clients (blocks). */
    private static final float WORLD_RANGE = 96.0F;

    /** "I see you" - played the moment he locks onto a player. */
    public static final RegistryObject<SoundEvent> UVIDEL = registerWorld("mellhunt.uvidel");
    /** "Didn't catch you" - played 7 seconds after he loses the player. */
    public static final RegistryObject<SoundEvent> NE_DOGNAL = registerWorld("mellhunt.ne_dognal");
    /** Played when he actually kills the player. */
    public static final RegistryObject<SoundEvent> KILL = registerWorld("mellhunt.kill");
    /** Casino sounds (the client plays these over the casino screen). */
    public static final RegistryObject<SoundEvent> WIN_CASINO = register("mellhunt.win_casino");
    public static final RegistryObject<SoundEvent> LOSE_CASINO = register("mellhunt.lose_casino");
    /** Slot reels spinning. The animation is stretched to the full length of this sound. */
    public static final RegistryObject<SoundEvent> PROKRUTKA = register("mellhunt.prokrutka");
    /** Second spin of the bonus round ("посидим"). */
    public static final RegistryObject<SoundEvent> IZI_BRIGI = register("mellhunt.izi_brigi");
    /** Played instead of the losing sound when the 1% second chance is rolled. */
    public static final RegistryObject<SoundEvent> POSIDIM = register("mellhunt.posidim");
    public static final RegistryObject<SoundEvent> ARTUR = registerWorld("mellhunt.artur");
    /** Played when a one-minute chase turns into an all-out rush. */
    public static final RegistryObject<SoundEvent> SEKS = registerWorld("mellhunt.seks");
    /** The cat's "malishochek" sound: egg spawns and the first house-cat sighting. */
    public static final RegistryObject<SoundEvent> MALISHOCHEK = registerWorld("mellhunt.malishochek");

    /** Random phrases he shouts while chasing you. */
    public static final List<RegistryObject<SoundEvent>> PHRASES = List.of(
            registerWorld("mellhunt.phrase1"),
            registerWorld("mellhunt.phrase2"),
            registerWorld("mellhunt.phrase3"),
            registerWorld("mellhunt.phrase4"),
            registerWorld("mellhunt.phrase5"));

    /** Length of every phrase in seconds (measured from the original files). */
    public static final float[] PHRASE_DURATIONS = {3.69F, 10.22F, 6.94F, 16.12F, 10.43F};

    /** Length of the non-phrase sounds in seconds. */
    public static final float UVIDEL_SECONDS = 6.619F;
    public static final float WIN_CASINO_SECONDS = 2.505F;
    public static final float LOSE_CASINO_SECONDS = 1.394F;
    /** Length of prokrutka.ogg in seconds (mono, 48 kHz). */
    public static final float PROKRUTKA_SECONDS = 7.745F;
    /** Length of izi_brigi.ogg in seconds (the bonus round reels). */
    public static final float IZI_BRIGI_SECONDS = 9.324F;
    /** Length of posidim.ogg in seconds (the 1% second-chance jingle). */
    public static final float POSIDIM_SECONDS = 2.310F;

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MellstroyMod.MOD_ID, name)));
    }

    /**
     * A positional sound with a generous server-side send range. Volume stays
     * at 1.0 so the client-side attenuation is purely distance based.
     */
    private static RegistryObject<SoundEvent> registerWorld(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createFixedRangeEvent(new ResourceLocation(MellstroyMod.MOD_ID, name), WORLD_RANGE));
    }

    private ModSounds() {
    }
}
