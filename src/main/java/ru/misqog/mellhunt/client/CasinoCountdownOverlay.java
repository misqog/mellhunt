package ru.misqog.mellhunt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import ru.misqog.mellhunt.MellstroyMod;
import ru.misqog.mellhunt.world.MellstroyDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The "you lost everything" countdown, drawn in the middle of the screen.
 * The server sends the remaining seconds; zero hides it again.
 */
@Mod.EventBusSubscriber(modid = MellstroyMod.MOD_ID, value = Dist.CLIENT)
public final class CasinoCountdownOverlay {

    private static int seconds;

    public static void show(int value) {
        seconds = Math.max(0, value);
    }

    public static void hide() {
        seconds = 0;
    }

    /** Registered in {@link ClientSetup}. */
    static void renderOverlay(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        if (seconds <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        if (!MellstroyDimensions.isCasino(mc.level)) {
            return;
        }

        Component label = Component.translatable("message.mellhunt.casino.death", seconds);

        graphics.fill(0, height / 2 - 34, width, height / 2 + 26, 0x8C000000);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(width / 2.0F, height / 2.0F - 16.0F, 0.0F);
        pose.scale(2.0F, 2.0F, 1.0F);
        graphics.drawCenteredString(mc.font, label, 0, -4, 0xFFFF5555);
        pose.popPose();
    }

    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (seconds > 0 && !MellstroyDimensions.isCasino(mc.level)) {
            seconds = 0;
        }
    }

    private CasinoCountdownOverlay() {
    }
}
