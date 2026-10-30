package ru.misqog.mellhunt.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import ru.misqog.mellhunt.casino.RouletteManager;
import ru.misqog.mellhunt.network.ModNetwork;
import ru.misqog.mellhunt.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * A compact European roulette table: the wheel, the betting board, the last
 * numbers and the turbo toggle. The wheel texture is transparent around the
 * rim, so no black square corners are visible.
 */
public class RouletteScreen extends Screen {

    private static final ResourceLocation WHEEL =
            new ResourceLocation("mellhunt", "textures/gui/roulette_wheel.png");
    private static final ResourceLocation BACKGROUND =
            new ResourceLocation("mellhunt", "textures/gui/roulette_background.png");

    private static final int PANEL_W = 360;
    private static final int PANEL_H = 260;
    private static final int BOARD_COLS = 12;
    private static final int CELL_W = 14;
    private static final int CELL_H = 13;
    private static final int OUTSIDE_W = 28;
    private static final int OUTSIDE_H = 14;
    private static final float SEGMENT = 360.0F / 37.0F;

    private static final int GOLD = 0xFFFFD24A;
    private static final int GOLD_DIM = 0xFF8A6A1E;
    private static final int GREEN = 0xFF6BFF7A;
    private static final int RED_TEXT = 0xFFFF6666;
    private static final int GREY = 0xFFB7A98C;

    private final BlockPos pos;

    private int ticks;
    private int betKind = RouletteManager.RED;
    private int betNumber;
    private int[] history;

    private boolean turbo;
    private boolean spinTurbo;
    private boolean spinning;
    private boolean resultReceived;
    private boolean settled;
    private int winning = -1;
    private int payout;
    private int error;

    private float angle;
    private float spinStartAngle;
    private float spinEndAngle;
    private int resultAt = -1;
    private int settleTicks = 58;
    private SimpleSoundInstance spinSound;
    private int[] pendingHistory;

    public RouletteScreen(BlockPos pos, int[] history) {
        super(Component.translatable("roulette.mellhunt.title"));
        this.pos = pos.immutable();
        this.history = history == null ? new int[0] : history.clone();
    }

    public void receiveResult(int winning, int kind, int number, boolean won,
                              int payout, int error, int[] history) {
        if (history != null) {
            // Do not show the new number in the history strip until the wheel stops.
            this.pendingHistory = history.clone();
        }
        if (!this.spinning || this.resultReceived) {
            return;
        }
        this.winning = winning;
        this.payout = payout;
        this.error = error;
        this.betKind = kind;
        this.betNumber = number;

        if (error != 0) {
            this.spinning = false;
            this.settled = true;
            this.winning = -1;
            applyPendingHistory();
            stopSpinSound();
            return;
        }

        this.resultReceived = true;
        this.resultAt = this.ticks;
        this.spinStartAngle = this.angle;
        this.settleTicks = this.spinTurbo ? 30 : 90;

        float target = (-winning * SEGMENT) % 360.0F;
        if (target < 0.0F) {
            target += 360.0F;
        }
        float current = ((this.angle % 360.0F) + 360.0F) % 360.0F;
        float delta = target - current;
        if (delta < 0.0F) {
            delta += 360.0F;
        }
        // One extra turn with an ease-out gives an initial speed close to the
        // pre-result spin (12 deg/tick normal, ~36 deg/tick turbo), so the
        // transition does not jerk.
        this.spinEndAngle = this.angle + 360.0F + delta;
    }

    private void startSpin() {
        if (this.spinning) {
            return;
        }
        this.spinning = true;
        this.resultReceived = false;
        this.settled = false;
        this.winning = -1;
        this.payout = 0;
        this.error = 0;
        this.resultAt = -1;
        this.spinTurbo = this.turbo;
        this.pendingHistory = null;
        ModNetwork.sendRouletteSpin(this.pos, this.betKind, this.betNumber, this.spinTurbo);
        this.spinSound = SimpleSoundInstance.forUI(ModSounds.PROKRUTKA.get(),
                this.spinTurbo ? 1.3F : 1.0F);
        Minecraft.getInstance().getSoundManager().play(this.spinSound);
    }

    @Override
    public void tick() {
        ++this.ticks;
        if (!this.spinning) {
            return;
        }

        if (!this.resultReceived) {
            this.angle += this.spinTurbo ? 34.0F : 12.0F;
            return;
        }

        float t = Math.min(1.0F, (this.ticks - this.resultAt) / (float) this.settleTicks);
        float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
        this.angle = this.spinStartAngle + (this.spinEndAngle - this.spinStartAngle) * eased;

        if (t >= 1.0F) {
            this.spinning = false;
            this.settled = true;
            applyPendingHistory();
            stopSpinSound();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                    (this.payout > 0 ? ModSounds.WIN_CASINO : ModSounds.LOSE_CASINO).get(), 1.0F));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        int centerX = this.width / 2;

        graphics.fill(0, 0, this.width, this.height, 0xE0050A07);
        graphics.fill(left - 2, top - 2, left + PANEL_W + 2, top + PANEL_H + 2, GOLD_DIM);
        graphics.blit(BACKGROUND, left, top, PANEL_W, PANEL_H,
                0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
        graphics.fill(left + 1, top + 1, left + PANEL_W - 1, top + 2, GOLD);

        graphics.drawCenteredString(this.font, this.title, centerX, top + 8, GOLD);
        graphics.drawCenteredString(this.font,
                Component.translatable("roulette.mellhunt.subtitle"), centerX, top + 21, GREY);

        int wheelX = left + 8;
        int wheelY = top + 38;
        drawWheel(graphics, wheelX, wheelY);

        int boardX = left + 134;
        int boardY = top + 38;
        drawBoard(graphics, boardX, boardY, mouseX, mouseY);
        drawOutsideButtons(graphics, boardX, boardY, mouseX, mouseY);
        drawHistory(graphics, left + 8, top + 146);

        drawToggle(graphics, centerX + 65, top + 186, 50, 22,
                Component.translatable("roulette.mellhunt.turbo"), this.turbo,
                mouseX, mouseY, !this.spinning);
        drawSpinButton(graphics, centerX - 59, top + 186, 118, 22, mouseX, mouseY);

        Component status;
        int colour;
        if (this.error != 0) {
            status = Component.translatable("roulette.mellhunt.no_gold");
            colour = RED_TEXT;
        } else if (this.settled && this.winning >= 0) {
            Component number = Component.literal(Integer.toString(this.winning));
            if (this.payout > 0) {
                status = Component.translatable("roulette.mellhunt.win", number, this.payout);
                colour = GREEN;
            } else {
                status = Component.translatable("roulette.mellhunt.lose", number);
                colour = RED_TEXT;
            }
        } else if (this.spinning) {
            status = Component.translatable("roulette.mellhunt.spinning");
            colour = GREY;
        } else {
            status = Component.translatable("roulette.mellhunt.pick");
            colour = GREY;
        }
        graphics.drawCenteredString(this.font, status, centerX, top + 212, colour);
        graphics.drawCenteredString(this.font,
                Component.translatable("roulette.mellhunt.paytable"), centerX, top + 226, GOLD_DIM);
        graphics.drawCenteredString(this.font,
                Component.translatable("roulette.mellhunt.bet"), centerX, top + 240, GOLD_DIM);
    }

    private void drawWheel(GuiGraphics graphics, int x, int y) {
        int cx = x + 52;
        int cy = y + 52;

        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(this.angle));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Full 256x256 texture, so the wheel's centre (128,128) maps exactly to
        // the rotation origin and the wheel does not wobble.
        graphics.blit(WHEEL, -52, -52, 104, 104, 0, 0, 256, 256, 256, 256);
        // The small centre square rotates with the wheel.
        graphics.fill(-10, -10, 10, 10, 0xFF2A1A0E);
        graphics.fill(-9, -9, 9, 9, GOLD);
        graphics.fill(-6, -6, 6, 6, 0xFF6B4A12);
        graphics.pose().popPose();

        // Fixed pointer at 12 o'clock.
        graphics.fill(cx - 2, cy - 58, cx + 3, cy - 48, GOLD);
        graphics.fill(cx - 4, cy - 50, cx + 5, cy - 46, GOLD);

        if (this.settled && this.winning >= 0) {
            graphics.drawCenteredString(this.font, Integer.toString(this.winning),
                    cx, cy - 4, 0xFFFFFFFF);
        }
    }

    private void drawBoard(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        drawCell(graphics, x - 16, y + CELL_H, CELL_W, CELL_H, 0, 0xFF1F8A4C,
                this.betKind == RouletteManager.NUMBER && this.betNumber == 0, mouseX, mouseY);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < BOARD_COLS; col++) {
                int number = row * BOARD_COLS + col + 1;
                int cellX = x + col * CELL_W;
                int cellY = y + row * CELL_H;
                int colour = RouletteManager.isRed(number) ? 0xFFB5212B : 0xFF17171C;
                boolean selected = this.betKind == RouletteManager.NUMBER && this.betNumber == number;
                drawCell(graphics, cellX, cellY, CELL_W, CELL_H, number, colour, selected, mouseX, mouseY);
            }
        }
    }

    private void drawCell(GuiGraphics graphics, int x, int y, int w, int h, int number, int colour,
                          boolean selected, int mouseX, int mouseY) {
        graphics.fill(x, y, x + w, y + h, colour);
        boolean hover = !this.spinning && inside(mouseX, mouseY, x, y, w, h);
        int border = selected ? GOLD : (hover ? 0xFFFFF0B0 : 0x99000000);
        graphics.fill(x, y, x + w, y + 1, border);
        graphics.fill(x, y + h - 1, x + w, y + h, border);
        graphics.fill(x, y, x + 1, y + h, border);
        graphics.fill(x + w - 1, y, x + w, y + h, border);
        graphics.drawCenteredString(this.font, Integer.toString(number), x + w / 2, y + 2, 0xFFFFFFFF);
    }

    private void drawOutsideButtons(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int buttonY = y + 3 * CELL_H + 4;
        int[] kinds = {RouletteManager.RED, RouletteManager.BLACK, RouletteManager.EVEN,
                RouletteManager.ODD, RouletteManager.LOW, RouletteManager.HIGH};
        String[] keys = {"roulette.mellhunt.red", "roulette.mellhunt.black",
                "roulette.mellhunt.even", "roulette.mellhunt.odd",
                "roulette.mellhunt.low", "roulette.mellhunt.high"};
        for (int i = 0; i < kinds.length; i++) {
            int bx = x + i * OUTSIDE_W;
            boolean selected = this.betKind == kinds[i];
            boolean hover = !this.spinning
                    && inside(mouseX, mouseY, bx, buttonY, OUTSIDE_W, OUTSIDE_H);
            int fill = kinds[i] == RouletteManager.RED ? 0xFF6B1418
                    : kinds[i] == RouletteManager.BLACK ? 0xFF17171C : 0xFF2A1A0C;
            graphics.fill(bx, buttonY, bx + OUTSIDE_W, buttonY + OUTSIDE_H, selected ? GOLD_DIM : fill);
            int border = selected ? GOLD : (hover ? 0xFFFFF0B0 : 0xBB000000);
            graphics.fill(bx, buttonY, bx + OUTSIDE_W, buttonY + 1, border);
            graphics.fill(bx, buttonY + OUTSIDE_H - 1, bx + OUTSIDE_W, buttonY + OUTSIDE_H, border);
            graphics.fill(bx, buttonY, bx + 1, buttonY + OUTSIDE_H, border);
            graphics.fill(bx + OUTSIDE_W - 1, buttonY, bx + OUTSIDE_W, buttonY + OUTSIDE_H, border);
            graphics.drawCenteredString(this.font, Component.translatable(keys[i]),
                    bx + OUTSIDE_W / 2, buttonY + 3, 0xFFFFFFFF);
        }
    }

    private void drawHistory(GuiGraphics graphics, int x, int y) {
        graphics.drawString(this.font, Component.translatable("roulette.mellhunt.history"),
                x, y, GOLD_DIM, false);
        int count = Math.min(this.history.length, 10);
        for (int i = 0; i < count; i++) {
            int number = this.history[i];
            int colour = number == 0 ? 0xFF1F8A4C
                    : RouletteManager.isRed(number) ? 0xFFB5212B : 0xFF17171C;
            int cx = x + i * 16;
            int cy = y + 11;
            graphics.fill(cx, cy, cx + 14, cy + 14, colour);
            graphics.fill(cx, cy, cx + 14, cy + 1, 0xAAFFD24A);
            graphics.drawCenteredString(this.font, Integer.toString(number), cx + 7, cy + 3, 0xFFFFFFFF);
        }
    }

    private void drawSpinButton(GuiGraphics graphics, int x, int y, int w, int h,
                                int mouseX, int mouseY) {
        boolean enabled = !this.spinning;
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, w, h);
        graphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF000000);
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, enabled ? GOLD : GOLD_DIM);
        graphics.fill(x, y, x + w, y + h,
                enabled ? (hovered ? 0xFF7A5A10 : 0xFF4A350A) : 0xFF2A1A1C);
        graphics.drawCenteredString(this.font,
                Component.translatable(this.spinning
                        ? "roulette.mellhunt.spinning" : "roulette.mellhunt.spin"),
                x + w / 2, y + 7, enabled ? GOLD : GREY);
    }

    private void drawToggle(GuiGraphics graphics, int x, int y, int w, int h,
                            Component text, boolean on, int mouseX, int mouseY, boolean enabled) {
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, w, h);
        graphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF000000);
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, enabled ? GOLD_DIM : 0xFF3A2A10);
        graphics.fill(x, y, x + w, y + h,
                on ? (hovered ? 0xFF2E7A3A : 0xFF1E5A2A) : (hovered ? 0xFF2A3A2E : 0xFF16241A));
        graphics.drawCenteredString(this.font, text, x + w / 2, y + 7,
                on ? GREEN : GREY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button) || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (this.spinning) {
            return false;
        }
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        int boardX = left + 134;
        int boardY = top + 38;

        if (inside(mouseX, mouseY, boardX - 16, boardY + CELL_H, CELL_W, CELL_H)) {
            this.betKind = RouletteManager.NUMBER;
            this.betNumber = 0;
            return true;
        }
        if (inside(mouseX, mouseY, boardX, boardY, BOARD_COLS * CELL_W, 3 * CELL_H)) {
            int col = ((int) mouseX - boardX) / CELL_W;
            int row = ((int) mouseY - boardY) / CELL_H;
            this.betKind = RouletteManager.NUMBER;
            this.betNumber = row * BOARD_COLS + col + 1;
            return true;
        }

        int outsideY = boardY + 3 * CELL_H + 4;
        if (inside(mouseX, mouseY, boardX, outsideY, 6 * OUTSIDE_W, OUTSIDE_H)) {
            int index = ((int) mouseX - boardX) / OUTSIDE_W;
            int[] kinds = {RouletteManager.RED, RouletteManager.BLACK, RouletteManager.EVEN,
                    RouletteManager.ODD, RouletteManager.LOW, RouletteManager.HIGH};
            this.betKind = kinds[Math.max(0, Math.min(kinds.length - 1, index))];
            return true;
        }

        if (inside(mouseX, mouseY, this.width / 2 - 59, top + 186, 118, 22)) {
            startSpin();
            return true;
        }
        if (inside(mouseX, mouseY, this.width / 2 + 65, top + 186, 50, 22)) {
            this.turbo = !this.turbo;
            return true;
        }
        return false;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void applyPendingHistory() {
        if (this.pendingHistory != null) {
            this.history = this.pendingHistory;
            this.pendingHistory = null;
        }
    }

    private void stopSpinSound() {
        if (this.spinSound != null) {
            Minecraft.getInstance().getSoundManager().stop(this.spinSound);
            this.spinSound = null;
        }
    }

    @Override
    public void onClose() {
        stopSpinSound();
        super.onClose();
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
