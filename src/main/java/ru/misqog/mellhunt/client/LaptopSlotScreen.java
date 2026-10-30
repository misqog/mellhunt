package ru.misqog.mellhunt.client;

import com.mojang.blaze3d.systems.RenderSystem;
import ru.misqog.mellhunt.casino.LaptopSlots;
import ru.misqog.mellhunt.network.ModNetwork;
import ru.misqog.mellhunt.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

/**
 * The laptop's five-reel, three-row slot machine.
 *
 * <p>Features: selectable number of lines (1..5), turbo mode, auto-play and a
 * win that only reaches the inventory after the reels have stopped.</p>
 */
public class LaptopSlotScreen extends Screen {

    private static final int PANEL_W = 360;
    private static final int PANEL_H = 292;
    private static final int CELL_W = 62;
    private static final int CELL_H = 58;
    private static final int GAP = 3;

    private static final ResourceLocation[] ICONS = {
            new ResourceLocation("mellhunt", "textures/gui/slot_banana.png"),
            new ResourceLocation("mellhunt", "textures/gui/slot_monkey.png"),
            new ResourceLocation("mellhunt", "textures/gui/slot_dog.png"),
            new ResourceLocation("mellhunt", "textures/gui/slot_zeus.png"),
            new ResourceLocation("mellhunt", "textures/gui/slot_coin.png"),
            new ResourceLocation("mellhunt", "textures/gui/slot_seven.png")
    };

    private static final int GOLD = 0xFFFFD24A;
    private static final int GOLD_DIM = 0xFF8A6A1E;
    private static final int GREEN = 0xFF6BFF7A;
    private static final int RED = 0xFFFF6666;
    private static final int GREY = 0xFFB7A98C;

    private static final int[] LINE_COLOURS = {
            0xFFFF5050, 0xFFFFD24A, 0xFF50DD70, 0xFF66A8FF, 0xFFDD77FF
    };

    private final BlockPos pos;
    private final int[] grid = new int[LaptopSlots.CELLS];
    private final float[] scroll = new float[LaptopSlots.REELS];
    private final boolean[] stopped = new boolean[LaptopSlots.REELS];
    private final int[] stopAt = new int[LaptopSlots.REELS];

    private int[] finalGrid;
    private int ticks;
    private boolean spinning;
    private boolean resultReceived;
    private boolean settled;
    private int resultAt = -1;
    private int payout;
    private int lineMask;
    private int error;

    private int lines = LaptopSlots.LINES.length;
    private boolean turbo;
    private boolean auto;
    private boolean spinTurbo;
    private int spinLines = LaptopSlots.LINES.length;
    private int nextAutoAt = -1;
    private SimpleSoundInstance spinSound;

    public LaptopSlotScreen(BlockPos pos) {
        super(Component.translatable("laptop.slots.title"));
        this.pos = pos.immutable();
        for (int i = 0; i < this.grid.length; i++) {
            this.grid[i] = i % LaptopSlots.COUNT;
        }
        for (int i = 0; i < this.scroll.length; i++) {
            this.scroll[i] = i * 1.7F;
        }
    }

    public void receiveResult(int[] grid, int payout, int lineMask, int error) {
        if (!this.spinning || this.resultReceived) {
            return;
        }
        this.finalGrid = grid == null ? new int[LaptopSlots.CELLS] : grid.clone();
        this.payout = payout;
        this.lineMask = lineMask;
        this.error = error;

        if (error != 0) {
            this.spinning = false;
            this.settled = true;
            this.auto = false;
            stopSpinSound();
            return;
        }

        this.resultReceived = true;
        this.resultAt = this.ticks;
        for (int reel = 0; reel < LaptopSlots.REELS; reel++) {
            this.stopAt[reel] = this.ticks
                    + (this.spinTurbo ? 18 + reel * 5 : 36 + reel * 10);
            this.stopped[reel] = false;
        }
    }

    private void startSpin() {
        if (this.spinning) {
            return;
        }
        this.spinning = true;
        this.resultReceived = false;
        this.settled = false;
        this.payout = 0;
        this.lineMask = 0;
        this.error = 0;
        this.finalGrid = null;
        this.resultAt = -1;
        this.nextAutoAt = -1;
        this.spinTurbo = this.turbo;
        this.spinLines = this.lines;
        for (int reel = 0; reel < LaptopSlots.REELS; reel++) {
            this.stopped[reel] = false;
            this.scroll[reel] = this.ticks * 0.3F + reel * 2.1F;
        }
        ModNetwork.sendLaptopSpin(this.pos, this.spinLines, this.spinTurbo);
        this.spinSound = SimpleSoundInstance.forUI(ModSounds.PROKRUTKA.get(),
                this.spinTurbo ? 1.3F : 1.0F);
        Minecraft.getInstance().getSoundManager().play(this.spinSound);
    }

    @Override
    public void tick() {
        ++this.ticks;

        if (this.auto && !this.spinning && this.settled && this.error == 0
                && this.nextAutoAt < 0) {
            this.nextAutoAt = this.ticks + 22;
        }
        if (this.auto && !this.spinning && this.nextAutoAt >= 0 && this.ticks >= this.nextAutoAt) {
            this.nextAutoAt = -1;
            startSpin();
            return;
        }

        if (!this.spinning) {
            return;
        }

        if (!this.resultReceived) {
            for (int reel = 0; reel < LaptopSlots.REELS; reel++) {
                this.scroll[reel] += (this.spinTurbo ? 0.9F : 0.5F) + reel * 0.04F;
            }
            return;
        }

        boolean allStopped = true;
        for (int reel = 0; reel < LaptopSlots.REELS; reel++) {
            if (!this.stopped[reel] && this.ticks >= this.stopAt[reel]) {
                this.stopped[reel] = true;
                for (int row = 0; row < LaptopSlots.ROWS; row++) {
                    this.grid[reel * LaptopSlots.ROWS + row] =
                            this.finalGrid[reel * LaptopSlots.ROWS + row];
                }
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.6F));
            }
            if (!this.stopped[reel]) {
                this.scroll[reel] += (this.spinTurbo ? 0.9F : 0.55F) + reel * 0.04F;
                allStopped = false;
            }
        }

        if (allStopped) {
            this.spinning = false;
            this.settled = true;
            stopSpinSound();
            boolean won = this.payout > 0;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                    (won ? ModSounds.WIN_CASINO : ModSounds.LOSE_CASINO).get(), 1.0F));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        int centerX = this.width / 2;

        graphics.fill(0, 0, this.width, this.height, 0xE006080E);
        graphics.fill(left - 2, top - 2, left + PANEL_W + 2, top + PANEL_H + 2, GOLD_DIM);
        graphics.fill(left, top, left + PANEL_W, top + PANEL_H, 0xFF0B1220);
        graphics.fillGradient(left + 1, top + 1, left + PANEL_W - 1, top + 38, 0x5550A0FF, 0x000B1220);
        graphics.fill(left + 1, top + 1, left + PANEL_W - 1, top + 2, GOLD);

        graphics.drawCenteredString(this.font, this.title, centerX, top + 9, GOLD);
        graphics.drawCenteredString(this.font,
                Component.translatable("laptop.slots.subtitle"), centerX, top + 24, GREY);

        int gridW = LaptopSlots.REELS * (CELL_W + GAP) - GAP;
        int gridH = LaptopSlots.ROWS * (CELL_H + GAP) - GAP;
        int gridX = centerX - gridW / 2;
        int gridY = top + 44;

        for (int reel = 0; reel < LaptopSlots.REELS; reel++) {
            int x = gridX + reel * (CELL_W + GAP);
            for (int row = 0; row < LaptopSlots.ROWS; row++) {
                int y = gridY + row * (CELL_H + GAP);
                graphics.fill(x - 1, y - 1, x + CELL_W + 1, y + CELL_H + 1, 0xFF000000);
                graphics.fill(x, y, x + CELL_W, y + CELL_H, 0xFF16202E);
                graphics.fill(x, y, x + CELL_W, y + 1, 0xFF3A3020);
            }
            drawReel(graphics, reel, x, gridY);
        }

        drawPaylines(graphics, gridX, gridY);
        drawLineLegend(graphics, left + 2, top + 56);

        drawLineControls(graphics, left + 10, top + 232, mouseX, mouseY);
        drawSpinButton(graphics, centerX - 48, top + 232, 96, 20, mouseX, mouseY);
        drawToggle(graphics, centerX + 54, top + 232, 44, 20, "TURBO", this.turbo,
                mouseX, mouseY, !this.spinning);
        drawToggle(graphics, centerX + 104, top + 232, 44, 20, "AUTO", this.auto,
                mouseX, mouseY, !this.spinning);

        Component status;
        int colour;
        if (this.error != 0) {
            status = Component.translatable("laptop.slots.no_gold");
            colour = RED;
        } else if (this.settled) {
            if (this.payout > 0) {
                status = Component.translatable("laptop.slots.win", this.payout);
                colour = GREEN;
            } else {
                status = Component.translatable("laptop.slots.lose");
                colour = RED;
            }
        } else if (this.spinning) {
            status = Component.translatable("laptop.slots.spinning");
            colour = GREY;
        } else {
            status = Component.translatable("laptop.slots.waiting");
            colour = GREY;
        }
        graphics.drawCenteredString(this.font, status, centerX, top + 258, colour);
        graphics.drawCenteredString(this.font,
                Component.translatable("laptop.slots.bet_lines", this.lines, this.lines),
                centerX, top + 272, GOLD_DIM);
    }

    private void drawReel(GuiGraphics graphics, int reel, int x, int gridY) {
        int height = LaptopSlots.ROWS * (CELL_H + GAP) - GAP;
        if (this.spinning && !this.stopped[reel]) {
            graphics.enableScissor(x, gridY, x + CELL_W, gridY + height);
            float value = this.scroll[reel];
            int base = (int) Math.floor(value);
            float fraction = value - base;
            for (int offset = -1; offset <= LaptopSlots.ROWS; offset++) {
                int symbol = Math.floorMod(base + offset + reel * 2, LaptopSlots.COUNT);
                int y = gridY + (int) ((offset - fraction) * (CELL_H + GAP));
                drawSymbol(graphics, symbol, x + (CELL_W - 48) / 2, y + (CELL_H - 48) / 2);
            }
            graphics.disableScissor();
        } else {
            for (int row = 0; row < LaptopSlots.ROWS; row++) {
                int y = gridY + row * (CELL_H + GAP);
                drawSymbol(graphics, this.grid[reel * LaptopSlots.ROWS + row],
                        x + (CELL_W - 48) / 2, y + (CELL_H - 48) / 2);
            }
        }
    }

    private void drawSymbol(GuiGraphics graphics, int symbol, int x, int y) {
        if (symbol < 0 || symbol >= LaptopSlots.COUNT) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(3.0F, 3.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(ICONS[symbol], 0, 0, 16, 16, 0, 0, 16, 16, 16, 16);
        graphics.pose().popPose();
    }

    private void drawPaylines(GuiGraphics graphics, int gridX, int gridY) {
        int activeLines = this.spinning || this.settled ? this.spinLines : this.lines;
        for (int line = 0; line < LaptopSlots.LINES.length; line++) {
            if (line >= activeLines) {
                continue;
            }
            boolean winning = this.settled && (this.lineMask & (1 << line)) != 0;
            int colour = winning ? LINE_COLOURS[line]
                    : (this.spinning ? 0x445A6A8A : 0x886A7A9A);
            int[] pointsX = new int[LaptopSlots.REELS];
            int[] pointsY = new int[LaptopSlots.REELS];
            for (int reel = 0; reel < LaptopSlots.REELS; reel++) {
                pointsX[reel] = gridX + reel * (CELL_W + GAP) + CELL_W / 2;
                pointsY[reel] = gridY + LaptopSlots.LINES[line][reel] * (CELL_H + GAP) + CELL_H / 2;
            }
            for (int i = 0; i < LaptopSlots.REELS - 1; i++) {
                drawLine(graphics, pointsX[i], pointsY[i], pointsX[i + 1], pointsY[i + 1],
                        colour, winning ? 2 : 1);
            }
        }
    }

    private void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int colour, int width) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) {
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            graphics.fill(x, y, x + width, y + width, colour);
        }
    }

    private void drawLineLegend(GuiGraphics graphics, int x, int y) {
        for (int line = 0; line < LaptopSlots.LINES.length; line++) {
            boolean active = line < this.lines;
            int colour = active ? LINE_COLOURS[line] : 0x445A6A8A;
            graphics.fill(x, y + line * 10, x + 6, y + line * 10 + 6, colour);
            graphics.drawString(this.font, Integer.toString(line + 1), x + 9, y + line * 10 - 1,
                    colour, false);
        }
    }

    private void drawLineControls(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        drawSmallButton(graphics, x, y, 18, 20, "-", !this.spinning
                && this.lines > 1, mouseX, mouseY);
        graphics.drawString(this.font, Component.translatable("laptop.slots.lines", this.lines),
                x + 22, y + 6, GOLD, false);
        drawSmallButton(graphics, x + 76, y, 18, 20, "+", !this.spinning
                && this.lines < LaptopSlots.LINES.length, mouseX, mouseY);
    }

    private void drawSmallButton(GuiGraphics graphics, int x, int y, int w, int h, String text,
                                 boolean enabled, int mouseX, int mouseY) {
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, w, h);
        graphics.fill(x, y, x + w, y + h, enabled
                ? (hovered ? 0xFF7A5A10 : 0xFF4A350A) : 0xFF2A1A1C);
        graphics.fill(x, y, x + w, y + 1, GOLD_DIM);
        graphics.drawCenteredString(this.font, text, x + w / 2, y + 6, enabled ? GOLD : GREY);
    }

    private void drawSpinButton(GuiGraphics graphics, int x, int y, int w, int h,
                                int mouseX, int mouseY) {
        boolean enabled = !this.spinning;
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, w, h);
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, enabled ? GOLD : GOLD_DIM);
        graphics.fill(x, y, x + w, y + h,
                enabled ? (hovered ? 0xFF7A5A10 : 0xFF4A350A) : 0xFF2A1A1C);
        graphics.drawCenteredString(this.font,
                Component.translatable(this.spinning ? "laptop.slots.spinning" : "laptop.slots.spin"),
                x + w / 2, y + 6, enabled ? GOLD : GREY);
    }

    private void drawToggle(GuiGraphics graphics, int x, int y, int w, int h, String text,
                            boolean on, int mouseX, int mouseY, boolean enabled) {
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, w, h);
        graphics.fill(x, y, x + w, y + h, on
                ? (hovered ? 0xFF2E7A3A : 0xFF1E5A2A)
                : (hovered ? 0xFF2A3A2E : 0xFF16241A));
        graphics.fill(x, y, x + w, y + 1, on ? GREEN : GOLD_DIM);
        graphics.drawCenteredString(this.font, text, x + w / 2, y + 6, on ? GREEN : GREY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button) || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        int centerX = this.width / 2;

        if (!this.spinning) {
            if (inside(mouseX, mouseY, left + 10, top + 232, 18, 20)) {
                this.lines = Math.max(1, this.lines - 1);
                return true;
            }
            if (inside(mouseX, mouseY, left + 86, top + 232, 18, 20)) {
                this.lines = Math.min(LaptopSlots.LINES.length, this.lines + 1);
                return true;
            }
            if (inside(mouseX, mouseY, centerX + 54, top + 232, 44, 20)) {
                this.turbo = !this.turbo;
                return true;
            }
            if (inside(mouseX, mouseY, centerX + 104, top + 232, 44, 20)) {
                this.auto = !this.auto;
                if (this.auto && this.error == 0) {
                    this.nextAutoAt = this.ticks + 10;
                }
                return true;
            }
        }
        if (inside(mouseX, mouseY, centerX - 48, top + 232, 96, 20)) {
            this.nextAutoAt = -1;
            startSpin();
            return true;
        }
        return false;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
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
