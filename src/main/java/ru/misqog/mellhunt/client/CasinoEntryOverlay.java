package ru.misqog.mellhunt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import ru.misqog.mellhunt.MellstroyMod;
import ru.misqog.mellhunt.world.MellstroyDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws the centred "mellstroy.game" title while the casino entry track is
 * playing. The server tells the client how many ticks to keep it visible, so
 * the overlay always matches the sound length.
 */
@Mod.EventBusSubscriber(modid = MellstroyMod.MOD_ID, value = Dist.CLIENT)
public final class CasinoEntryOverlay {

    private static final String TITLE = "mellstroy.game";

    private static int remainingTicks;
    private static int totalTicks;

    public static void show(int ticks) {
        totalTicks = Math.max(1, ticks);
        remainingTicks = totalTicks;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (remainingTicks > 0) {
            remainingTicks--;
        }
    }

    /** Registered in {@link ClientSetup}. */
    static void renderOverlay(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        if (remainingTicks <= 0 || totalTicks <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        // Only ever draw the title inside the casino itself.
        if (!MellstroyDimensions.isCasino(mc.level)) {
            return;
        }

        // Fade in quickly and fade out over the last half second.
        float progress = 1.0F - (float) remainingTicks / (float) totalTicks;
        float fadeIn = Math.min(1.0F, progress * 8.0F);
        float fadeOut = Math.min(1.0F, remainingTicks / 10.0F);
        float fade = Math.min(fadeIn, fadeOut);

        int bandAlpha = (int) (0.55F * fade * 255.0F);
        int textAlpha = (int) (fade * 255.0F);
        if (bandAlpha > 0) {
            graphics.fill(0, height / 2 - 30, width, height / 2 + 22, bandAlpha << 24);
        }

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(width / 2.0F, height / 2.0F - 10.0F, 0.0F);
        pose.scale(2.0F, 2.0F, 1.0F);
        if (textAlpha > 0) {
            graphics.drawCenteredString(mc.font, Component.literal(TITLE), 0, 0,
                    (textAlpha << 24) | 0xFFD24A);
        }
        pose.popPose();
    }

    private CasinoEntryOverlay() {
    }
}
