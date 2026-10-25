package com.mellstroy.horror.client;

import com.mellstroy.horror.MellstroyMod;
import com.mellstroy.horror.config.MellstroyConfig;
import com.mellstroy.horror.entity.MellstroyEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * The client-side "fear" system: red vignette, full-screen flash and camera
 * shake. The vignette is driven by how close Mellstroy is, while the camera
 * only shakes while he can actually see the player.
 */
@Mod.EventBusSubscriber(modid = MellstroyMod.MOD_ID, value = Dist.CLIENT)
public final class ClientFear {

    /** 0..1, how close / how much he scares the player right now. */
    private static float fear;
    /** 0..1, short full-screen red flash on being hit. */
    private static float flash;
    /** True while the nearest Mellstroy can actually see the player. */
    private static boolean seen;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            fear = 0.0F;
            flash = 0.0F;
            seen = false;
            return;
        }

        if (mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.CREATIVE) {
            fear = 0.0F;
            flash = 0.0F;
            seen = false;
            return;
        }

        if (!MellstroyConfig.redScreen()) {
            fear *= 0.8F;
            seen = false;
            return;
        }

        MellstroyEntity nearestMob = nearestMellstroy(mc);
        double nearest = nearestMob == null ? Double.MAX_VALUE : nearestMob.distanceTo(mc.player);
        // Only a Mellstroy who can actually see the player may shake the camera.
        // Being close while he is searching or wandering around does not count.
        seen = nearestMob != null && nearestMob.isSeeing(mc.player);

        float target = 0.0F;
        if (nearest < 30.0D) {
            target = (float) Math.min(1.0D, (30.0D - nearest) / 27.0D);
        }
        fear += (target - fear) * 0.12F;
        if (fear < 0.002F) {
            fear = 0.0F;
        }

        if (nearest < 4.0D && mc.player.hurtTime > 0) {
            flash = 1.0F;
        }
        flash *= 0.9F;
        if (flash < 0.02F) {
            flash = 0.0F;
        }
    }

    /** Registered in {@link ClientSetup}. */
    static void renderOverlay(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui
                || (mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.CREATIVE)) {
            return;
        }

        float pulse = 0.85F + 0.15F * (float) Math.sin((mc.player.tickCount + partialTick) * 0.3F);
        float intensity = Math.min(1.0F, fear * pulse);

        if (intensity > 0.01F) {
            int maxAlpha = (int) (intensity * 165.0F);
            int washAlpha = Math.max(0, maxAlpha / 3);

            // Whole-screen red wash.
            graphics.fill(0, 0, width, height, (washAlpha << 24) | 0x4A0000);

            // Top / bottom vignette.
            int band = Math.max(24, height / 5);
            graphics.fillGradient(0, 0, width, band, (maxAlpha << 24) | 0x2B0000, 0x00000000);
            graphics.fillGradient(0, height - band, width, height, 0x00000000, (maxAlpha << 24) | 0x2B0000);

            // Left / right vignette (built from thin strips since fillGradient is vertical only).
            int edge = Math.min(width / 4, 150);
            int steps = 20;
            int stepWidth = Math.max(1, edge / steps);
            for (int i = 0; i < steps; i++) {
                float t = 1.0F - (float) i / (float) steps;
                int alpha = (int) (maxAlpha * t * t);
                if (alpha <= 2) {
                    continue;
                }
                int color = (alpha << 24) | 0x330000;
                graphics.fill(i * stepWidth, 0, (i + 1) * stepWidth, height, color);
                graphics.fill(width - (i + 1) * stepWidth, 0, width - i * stepWidth, height, color);
            }
        }

        if (flash > 0.01F) {
            int alpha = (int) (flash * 225.0F);
            graphics.fill(0, 0, width, height, (alpha << 24) | 0xC00000);
        }
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        // No shake while he cannot see the player: the red vignette still
        // reacts to his presence, but the camera stays steady.
        if (!seen || (fear < 0.05F && flash < 0.05F)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || (mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.CREATIVE)) {
            return;
        }

        float strength = Math.max(fear, flash);
        float time = mc.player.tickCount + (float) event.getPartialTick();
        float amp = strength * strength * 2.0F;

        event.setYaw(event.getYaw() + (float) Math.sin(time * 3.3F) * amp);
        event.setPitch(event.getPitch() + (float) Math.cos(time * 4.1F) * amp * 0.5F);
        event.setRoll(event.getRoll() + (float) Math.sin(time * 2.3F) * amp);
    }

    @Nullable
    private static MellstroyEntity nearestMellstroy(Minecraft mc) {
        MellstroyEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        if (mc.level == null || mc.player == null) {
            return null;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof MellstroyEntity mob) {
                double distance = entity.distanceTo(mc.player);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = mob;
                }
            }
        }
        return best;
    }

    private ClientFear() {
    }
}
