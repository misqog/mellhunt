package ru.misqog.mellhunt.client;

import ru.misqog.mellhunt.network.CashierTradePacket;
import ru.misqog.mellhunt.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The cashier's counter: nine gold nuggets for one gold ingot.
 *
 * <p>Rendered as a small casino counter instead of a chest GUI so it fits the
 * room. The exchange itself happens on the server; this screen only sends the
 * chosen amount, so nuggets that were spent meanwhile simply do not count.</p>
 */
public class CashierScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 210;
    private static final int GOLD = 0xFFFFD24A;
    private static final int GOLD_DIM = 0xFF8A6A1E;
    private static final int CREAM = 0xFFF0E8D2;
    private static final int INK = 0xFF17120E;

    private static final int[] AMOUNTS = {5, 10, 20};

    public CashierScreen() {
        super(Component.translatable("cashier.mellhunt.title"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int top = (this.height - PANEL_H) / 2;
        int buttonY = top + PANEL_H - 34;

        int buttonW = 86;
        int gap = 6;
        int total = buttonW * 3 + gap * 2;
        int startX = centerX - total / 2;

        this.addRenderableWidget(Button.builder(
                        Component.translatable("cashier.mellhunt.all"), button -> this.exchange(CashierTradePacket.ALL))
                .bounds(centerX - 61, buttonY + 24, 122, 20).build());

        for (int i = 0; i < AMOUNTS.length; i++) {
            final int exchanges = AMOUNTS[i];
            this.addRenderableWidget(Button.builder(
                            Component.translatable("cashier.mellhunt.times", exchanges),
                            button -> this.exchange(switch (exchanges) {
                                case 5 -> CashierTradePacket.FIVE;
                                case 10 -> CashierTradePacket.TEN;
                                default -> CashierTradePacket.TWENTY;
                            }))
                    .bounds(startX + i * (buttonW + gap), buttonY, buttonW, 20).build());
        }
    }

    private void exchange(int action) {
        ModNetwork.sendCashierTrade(action);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;

        // The counter itself.
        graphics.fill(left, top, left + PANEL_W, top + PANEL_H, 0xE017120E);
        graphics.fill(left, top, left + PANEL_W, top + 2, GOLD);
        graphics.fill(left, top + PANEL_H - 2, left + PANEL_W, top + PANEL_H, GOLD);
        graphics.fill(left, top, left + 2, top + PANEL_H, GOLD_DIM);
        graphics.fill(left + PANEL_W - 2, top, left + PANEL_W, top + PANEL_H, GOLD_DIM);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 12, GOLD);

        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(this.font.split(Component.translatable("cashier.mellhunt.rate"), PANEL_W - 30).get(0));
        List<FormattedCharSequence> more = this.font.split(Component.translatable("cashier.mellhunt.help"),
                PANEL_W - 40);
        lines.addAll(more);

        int y = top + 38;
        for (FormattedCharSequence line : lines) {
            graphics.drawCenteredString(this.font, line, this.width / 2, y, CREAM);
            y += 11;
        }

        // A small nugget -> ingot illustration drawn from plain rectangles.
        int iconY = top + 96;
        int iconX = this.width / 2;
        graphics.fill(iconX - 78, iconY, iconX - 62, iconY + 16, 0xFFFFC64D);
        graphics.fill(iconX - 74, iconY + 4, iconX - 66, iconY + 12, 0xFFE8A020);
        graphics.drawString(this.font, "x9", iconX - 58, iconY + 4, CREAM, false);
        graphics.drawString(this.font, "->", iconX - 8, iconY + 4, GOLD, false);
        graphics.fill(iconX + 14, iconY - 2, iconX + 34, iconY + 18, 0xFFFFD24A);
        graphics.fill(iconX + 18, iconY + 6, iconX + 30, iconY + 10, 0xFFE0A82C);
        graphics.drawString(this.font, "x1", iconX + 38, iconY + 4, CREAM, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The server is the authority; nothing here survives a dimension change. */
    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}
