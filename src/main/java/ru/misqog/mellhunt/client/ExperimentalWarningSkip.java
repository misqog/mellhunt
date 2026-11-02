package ru.misqog.mellhunt.client;

import ru.misqog.mellhunt.MellstroyMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Confirms the "experimental settings" warnings automatically.
 *
 * <p>Minecraft marks any world with more than the three vanilla dimensions as
 * experimental, so the hidden casino dimension shows two warnings: one while
 * creating a world and one while opening it. Both are recognised by their own
 * text and answered with the first button (the confirm button comes first and
 * the cancel button last), so the player never sees them.</p>
 */
@Mod.EventBusSubscriber(modid = MellstroyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class ExperimentalWarningSkip {

    private static final String[] MARKERS = {"experimental",
            "\u044d\u043a\u0441\u043f\u0435\u0440\u0438\u043c\u0435\u043d\u0442\u0430\u043b\u044c\u043d",
            "\u043d\u0435 \u043f\u043e\u0434\u0434\u0435\u0440\u0436\u0438\u0432\u0430\u044e\u0442\u0441\u044f"};

    private ExperimentalWarningSkip() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        pressIfWarning(event.getScreen());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        pressIfWarning(Minecraft.getInstance().screen);
    }

    private static void pressIfWarning(Screen screen) {
        if (screen == null) {
            return;
        }
        String text = screenText(screen).toLowerCase();
        boolean warning = false;
        for (String marker : MARKERS) {
            if (text.contains(marker)) {
                warning = true;
                break;
            }
        }
        if (!warning) {
            return;
        }
        // Never confirm while the world's session lock is still held: on Windows
        // that click fails with "Нет доступа к миру".
        if (!SessionLockWatch.releaseStaleLocks()) {
            return;
        }
        for (var child : screen.children()) {
            if (child instanceof Button button && button.visible && button.active) {
                button.onPress();
                return;
            }
        }
    }

    /** The title plus every text field the screen keeps. */
    private static String screenText(Screen screen) {
        StringBuilder builder = new StringBuilder();
        if (screen.getTitle() != null) {
            builder.append(screen.getTitle().getString()).append('\n');
        }
        try {
            for (var field : screen.getClass().getDeclaredFields()) {
                if (field.getType() != Component.class) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(screen);
                if (value instanceof Component component) {
                    builder.append(component.getString()).append('\n');
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // The title alone is often enough.
        }
        return builder.toString();
    }
}
