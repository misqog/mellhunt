package ru.misqog.mellhunt.client;

import ru.misqog.mellhunt.casino.PokerHands;
import ru.misqog.mellhunt.network.ModNetwork;
import ru.misqog.mellhunt.network.PokerStatePacket;
import ru.misqog.mellhunt.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

/**
 * Jacks-or-Better video poker. The cards are revealed a few ticks after the
 * DRAW result arrives, so the payout only reaches the inventory once the
 * hand is visible.
 */
public class PokerScreen extends Screen {

    private static final int PANEL_W = 360;
    private static final int PANEL_H = 264;
    private static final int CARD_W = 60;
    private static final int CARD_H = 80;
    private static final int CARD_GAP = 5;
    private static final String[] SUITS = {"\u2660", "\u2665", "\u2666", "\u2663"};
    private static final String[] RANKS = {
            "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"
    };

    private static final int GOLD = 0xFFFFD24A;
    private static final int GOLD_DIM = 0xFF8A6A1E;
    private static final int GREEN = 0xFF6BFF7A;
    private static final int RED = 0xFFFF6666;
    private static final int GREY = 0xFFB7A98C;
    private static final int CREAM = 0xFFF0E8D2;
    private static final int INK = 0xFF17120E;

    private final BlockPos pos;
    private int phase = PokerStatePacket.IDLE;
    private int[] cards = new int[5];
    private int held;
    private int payout;
    private int category = -1;
    private int error;
    private boolean waiting;

    private int ticks;
    private int revealAt = -1;
    private boolean resultSoundPlayed;

    public PokerScreen(BlockPos pos) {
        super(Component.translatable("poker.mellhunt.title"));
        this.pos = pos.immutable();
    }

    public void receiveState(int phase, int[] cards, int held, int payout, int category, int error) {
        this.phase = phase;
        this.cards = cards == null ? new int[5] : cards.clone();
        if (this.cards.length < 5) {
            this.cards = java.util.Arrays.copyOf(this.cards, 5);
        }
        this.held = held;
        this.payout = payout;
        this.category = category;
        this.error = error;
        this.waiting = false;
        this.resultSoundPlayed = false;

        if (phase == PokerStatePacket.RESULT) {
            this.revealAt = this.ticks + 18;
        } else {
            this.revealAt = -1;
            if (phase == PokerStatePacket.HOLD) {
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP, 1.4F));
            }
        }
    }

    @Override
    public void tick() {
        ++this.ticks;
        if (this.phase == PokerStatePacket.RESULT && !this.resultSoundPlayed
                && this.revealAt >= 0 && this.ticks >= this.revealAt) {
            this.resultSoundPlayed = true;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                    (this.payout > 0 ? ModSounds.WIN_CASINO : ModSounds.LOSE_CASINO).get(), 1.0F));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        int centerX = this.width / 2;

        graphics.fill(0, 0, this.width, this.height, 0xE0080704);
        graphics.fill(left - 2, top - 2, left + PANEL_W + 2, top + PANEL_H + 2, GOLD_DIM);
        graphics.fill(left, top, left + PANEL_W, top + PANEL_H, 0xFF0D1A12);
        graphics.fillGradient(left + 1, top + 1, left + PANEL_W - 1, top + 38, 0x556BFF7A, 0x000D1A12);
        graphics.fill(left + 1, top + 1, left + PANEL_W - 1, top + 2, GOLD);

        graphics.drawCenteredString(this.font, this.title, centerX, top + 8, GOLD);
        graphics.drawCenteredString(this.font,
                Component.translatable("poker.mellhunt.subtitle"), centerX, top + 22, GREY);

        int total = CARD_W * 5 + CARD_GAP * 4;
        int cardsX = centerX - total / 2;
        int cardsY = top + 40;
        boolean hidden = this.phase == PokerStatePacket.RESULT
                && this.revealAt >= 0 && this.ticks < this.revealAt;
        for (int i = 0; i < 5; i++) {
            drawCard(graphics, cardsX + i * (CARD_W + CARD_GAP), cardsY, i, hidden, mouseX, mouseY);
        }

        Component status;
        int color;
        if (this.error == 1) {
            status = Component.translatable("poker.mellhunt.no_gold");
            color = RED;
        } else if (this.error == 2) {
            status = Component.translatable("poker.mellhunt.no_hand");
            color = RED;
        } else if (hidden) {
            status = Component.translatable("poker.mellhunt.reveal");
            color = GOLD;
        } else if (this.phase == PokerStatePacket.RESULT) {
            Component name = Component.translatable(PokerHands.nameKey(this.category));
            if (this.payout > 0) {
                status = Component.translatable("poker.mellhunt.win", name, this.payout);
                color = GREEN;
            } else {
                status = Component.translatable("poker.mellhunt.lose", name);
                color = RED;
            }
        } else if (this.phase == PokerStatePacket.HOLD) {
            status = Component.translatable("poker.mellhunt.hold_hint");
            color = GREY;
        } else {
            status = Component.translatable("poker.mellhunt.waiting");
            color = GREY;
        }
        graphics.drawCenteredString(this.font, status, centerX, top + 128, color);

        Component button = Component.translatable(this.phase == PokerStatePacket.HOLD
                ? "poker.mellhunt.draw" : "poker.mellhunt.deal");
        int bw = 140;
        int bh = 22;
        int bx = centerX - bw / 2;
        int by = top + 146;
        boolean enabled = !this.waiting && !hidden;
        boolean hovered = enabled && inside(mouseX, mouseY, bx, by, bw, bh);
        graphics.fill(bx - 2, by - 2, bx + bw + 2, by + bh + 2, 0xFF000000);
        graphics.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, enabled ? GOLD : GOLD_DIM);
        graphics.fill(bx, by, bx + bw, by + bh,
                enabled ? (hovered ? 0xFF7A5A10 : 0xFF4A350A) : 0xFF2A1A1C);
        graphics.drawCenteredString(this.font, button, centerX, by + 7, enabled ? GOLD : GREY);

        drawPaytable(graphics, left + 14, top + 180);
        graphics.drawCenteredString(this.font,
                Component.translatable("poker.mellhunt.bet"), centerX, top + 244, GOLD_DIM);
    }

    private void drawCard(GuiGraphics graphics, int x, int y, int index, boolean hidden,
                          int mouseX, int mouseY) {
        boolean isHeld = (this.held & (1 << index)) != 0;
        int card = this.cards[index];
        boolean faceUp = this.phase != PokerStatePacket.IDLE && card != 0 && !hidden;

        graphics.fill(x - 2, y - 2, x + CARD_W + 2, y + CARD_H + 2, 0xFF000000);
        boolean hover = this.phase == PokerStatePacket.HOLD
                && !this.waiting && inside(mouseX, mouseY, x, y, CARD_W, CARD_H);
        int border = isHeld ? GOLD : (hover ? 0xFFFFF0B0 : GOLD_DIM);
        graphics.fill(x - 1, y - 1, x + CARD_W + 1, y + CARD_H + 1, border);

        if (!faceUp) {
            graphics.fill(x, y, x + CARD_W, y + CARD_H, 0xFF1A2A3A);
            graphics.fill(x + 6, y + 6, x + CARD_W - 6, y + CARD_H - 6, 0xFF223A52);
            graphics.drawCenteredString(this.font, "?", x + CARD_W / 2, y + CARD_H / 2 - 4, GOLD_DIM);
        } else {
            graphics.fill(x, y, x + CARD_W, y + CARD_H, CREAM);
            int red = PokerHands.suit(card) == 1 || PokerHands.suit(card) == 2 ? 0xFFB5212B : INK;
            String rank = RANKS[PokerHands.rank(card) - 2];
            String suit = SUITS[PokerHands.suit(card)];

            graphics.pose().pushPose();
            graphics.pose().translate(x + 6.0F, y + 6.0F, 0.0F);
            graphics.pose().scale(2.0F, 2.0F, 1.0F);
            graphics.drawString(this.font, rank, 0, 0, red, false);
            graphics.pose().popPose();

            graphics.pose().pushPose();
            graphics.pose().translate(x + CARD_W / 2.0F - 10.0F, y + CARD_H / 2.0F - 8.0F, 0.0F);
            graphics.pose().scale(3.0F, 3.0F, 1.0F);
            graphics.drawString(this.font, suit, 0, 0, red, false);
            graphics.pose().popPose();
        }

        if (isHeld) {
            graphics.fill(x, y + CARD_H - 13, x + CARD_W, y + CARD_H - 2, GOLD);
            graphics.drawCenteredString(this.font,
                    Component.translatable("poker.mellhunt.hold"),
                    x + CARD_W / 2, y + CARD_H - 11, INK);
        }
    }

    private void drawPaytable(GuiGraphics graphics, int x, int y) {
        int[] left = {PokerHands.ROYAL_FLUSH, PokerHands.STRAIGHT_FLUSH,
                PokerHands.FOUR_OF_A_KIND, PokerHands.FULL_HOUSE, PokerHands.FLUSH};
        int[] right = {PokerHands.STRAIGHT, PokerHands.THREE_OF_A_KIND,
                PokerHands.TWO_PAIR, PokerHands.JACKS_OR_BETTER, PokerHands.HIGH_CARD};
        for (int i = 0; i < left.length; i++) {
            drawPayEntry(graphics, x, y + i * 10, left[i]);
            drawPayEntry(graphics, x + 170, y + i * 10, right[i]);
        }
    }

    private void drawPayEntry(GuiGraphics graphics, int x, int y, int category) {
        graphics.drawString(this.font, Component.translatable(PokerHands.nameKey(category)),
                x, y, GREY, false);
        String pay = Integer.toString(PokerHands.payout(category));
        graphics.drawString(this.font, pay, x + 162 - this.font.width(pay), y, GOLD, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button) || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        boolean hidden = this.phase == PokerStatePacket.RESULT
                && this.revealAt >= 0 && this.ticks < this.revealAt;
        if (this.waiting || hidden) {
            return false;
        }

        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        int total = CARD_W * 5 + CARD_GAP * 4;
        int cardsX = this.width / 2 - total / 2;
        int cardsY = top + 40;

        if (this.phase == PokerStatePacket.HOLD) {
            for (int i = 0; i < 5; i++) {
                int x = cardsX + i * (CARD_W + CARD_GAP);
                if (inside(mouseX, mouseY, x, cardsY, CARD_W, CARD_H)) {
                    this.held ^= 1 << i;
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
                    return true;
                }
            }
        }

        int bw = 140;
        int bh = 22;
        int bx = this.width / 2 - bw / 2;
        int by = top + 146;
        if (inside(mouseX, mouseY, bx, by, bw, bh)) {
            this.waiting = true;
            if (this.phase == PokerStatePacket.HOLD) {
                ModNetwork.sendPokerDraw(this.pos, this.held);
            } else {
                ModNetwork.sendPokerDeal(this.pos);
            }
            return true;
        }
        return false;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
