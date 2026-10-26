package com.mellstroy.horror.client;

import com.mellstroy.horror.casino.CasinoSymbols;
import com.mellstroy.horror.casino.CasinoTiming;
import com.mellstroy.horror.network.ModNetwork;
import com.mellstroy.horror.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * Mellstroy's casino: one spin, one chance to keep your life.
 *
 * <p>The server sends the final line while the reels are already spinning. The
 * reels stop one by one, then the win/lose sound is played, and only after it
 * has finished does the screen disappear (and the server revives / kills the
 * player).</p>
 */
public class CasinoScreen extends Screen {

    private static final int PANEL_W = 360;
    private static final int PANEL_H = 232;
    private static final int BOX = 74;
    private static final int GAP = 12;

    private static final Item[] SYMBOL_ITEMS = {
            Items.AIR,                    // 0 - the "7" is drawn as text
            Items.DIAMOND,                // 1
            Items.GOLD_INGOT,             // 2
            Items.EMERALD,                // 3
            Items.NETHERITE_INGOT,        // 4
            Items.ENCHANTED_GOLDEN_APPLE  // 5
    };

    private static final int GOLD = 0xFFFFD24A;
    private static final int GOLD_DIM = 0xFF8A6A1E;
    private static final int GREEN = 0xFF6BFF7A;
    private static final int RED = 0xFFFF5555;
    private static final int GREY = 0xFFB7A98C;

    private static final int[] DEFAULT_REELS = {
            CasinoSymbols.DIAMOND,
            CasinoSymbols.GOLD,
            CasinoSymbols.EMERALD
    };
    private static final float[] DEFAULT_SCROLL = {0.0F, 1.7F, 3.4F};

    private final int winChance;
    private final ItemStack[] symbolStacks = new ItemStack[CasinoSymbols.COUNT];

    private final int[] reels = DEFAULT_REELS.clone();
    private final int[] finalLine = new int[3];
    private final boolean[] stopped = new boolean[3];
    private final float[] scroll = DEFAULT_SCROLL.clone();

    private int ticks;
    private int spinStart = -1;
    /** Length of the current spin sound in ticks (prokrutka or izi_brigi). */
    private int spinTicks = CasinoTiming.SPIN_TICKS;
    private boolean hasResult;
    private boolean won;
    /** This result is the second (bonus) spin and uses the izi_brigi sound. */
    private boolean bonusRound;
    /** A losing first spin rolled the 1%: posidim plays, then SPIN is enabled again. */
    private boolean secondChance;
    /** The posidim jingle has finished and the button is waiting for the second spin. */
    private boolean bonusOffered;
    private boolean resultSoundPlayed;
    private boolean acked;
    private int closeAt = -1;
    private int resetAt = -1;

    public CasinoScreen(int winChance) {
        super(Component.translatable("casino.mellstroy.title"));
        this.winChance = winChance;
        for (int i = 1; i < CasinoSymbols.COUNT; i++) {
            this.symbolStacks[i] = new ItemStack(SYMBOL_ITEMS[i]);
        }
    }

    public void receiveResult(boolean won, int[] line, boolean bonusRound, boolean secondChance) {
        if (this.hasResult) {
            return;
        }
        this.bonusRound = bonusRound;
        this.secondChance = secondChance;
        this.hasResult = true;
        this.won = won;
        for (int i = 0; i < 3; i++) {
            this.finalLine[i] = Math.max(0, Math.min(CasinoSymbols.COUNT - 1, line[i]));
        }

        // If the server auto-spun while the player was idle, start the full
        // animation now instead of jumping straight to the result.
        if (this.spinStart < 0) {
            this.startSpin(false);
        }
    }

    private void startSpin(boolean sendPacket) {
        if (this.spinStart >= 0) {
            return;
        }
        this.spinStart = this.ticks;
        this.bonusOffered = false;

        // The first spin uses prokrutka.ogg (7.745 s); the 1% bonus spin uses
        // izi_brigi.ogg (9.324 s). The reels are stretched to the same length.
        boolean bonus = this.bonusRound;
        this.spinTicks = bonus ? CasinoTiming.IZI_BRIGI_SPIN_TICKS : CasinoTiming.SPIN_TICKS;
        SoundEvent spinSound = (bonus ? ModSounds.IZI_BRIGI : ModSounds.PROKRUTKA).get();
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(spinSound, 1.0F));

        if (sendPacket) {
            ModNetwork.sendSpin();
        }
    }

    @Override
    public void tick() {
        ++this.ticks;

        if (this.hasResult && this.spinStart < 0) {
            this.startSpin(false);
        }

        if (this.spinStart >= 0) {
            int elapsed = this.ticks - this.spinStart;
            for (int i = 0; i < 3; ++i) {
                if (this.hasResult && elapsed >= CasinoTiming.reelStopTicks(this.spinTicks, i)) {
                    if (!this.stopped[i]) {
                        this.stopped[i] = true;
                        this.reels[i] = this.finalLine[i];
                    }
                } else if (!this.stopped[i]) {
                    // Scroll at a slightly different speed per reel so the
                    // three drums look independent.
                    this.scroll[i] += 0.72F + i * 0.06F;
                    this.reels[i] = Math.floorMod((int) Math.floor(this.scroll[i]), CasinoSymbols.COUNT);
                }
            }
        }

        if (this.hasResult && this.allStopped() && !this.resultSoundPlayed
                && this.ticks >= this.spinStart + CasinoTiming.resultStartTicks(this.spinTicks)) {
            this.playResult();
        }

        if (this.resetAt >= 0 && this.ticks >= this.resetAt) {
            this.prepareBonusSpin();
        }

        if (this.closeAt >= 0 && this.ticks >= this.closeAt) {
            if (!this.acked) {
                this.acked = true;
                ModNetwork.sendAck();
            }
            this.onClose();
        }
    }

    private boolean allStopped() {
        return this.hasResult && this.stopped[0] && this.stopped[1] && this.stopped[2];
    }

    private void playResult() {
        this.resultSoundPlayed = true;

        // The 1% second chance: no death sound, no closing. posidim.ogg plays
        // and once it ends the reels reset and SPIN becomes active again.
        if (this.secondChance) {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(ModSounds.POSIDIM.get(), 1.0F));
            this.resetAt = this.ticks + Math.round(ModSounds.POSIDIM_SECONDS * 20.0F) + 6;
            return;
        }

        SoundEvent sound = (this.won ? ModSounds.WIN_CASINO : ModSounds.LOSE_CASINO).get();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F));

        float seconds = this.won ? ModSounds.WIN_CASINO_SECONDS : ModSounds.LOSE_CASINO_SECONDS;
        this.closeAt = this.ticks + Math.round(seconds * 20.0F) + 6;
    }

    /**
     * Turns the screen back into a fresh, clickable state for the bonus spin.
     * The next press of SPIN uses izi_brigi.ogg and the usual win/lose rules.
     */
    private void prepareBonusSpin() {
        this.resetAt = -1;
        this.bonusOffered = true;
        this.bonusRound = true;
        this.hasResult = false;
        this.won = false;
        this.secondChance = false;
        this.resultSoundPlayed = false;
        this.spinStart = -1;
        this.spinTicks = CasinoTiming.IZI_BRIGI_SPIN_TICKS;
        this.closeAt = -1;
        for (int i = 0; i < 3; ++i) {
            this.stopped[i] = false;
            this.reels[i] = DEFAULT_REELS[i];
            this.scroll[i] = DEFAULT_SCROLL[i];
            this.finalLine[i] = 0;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        int centerX = this.width / 2;

        // dim the world a lot, with a red glow
        graphics.fill(0, 0, this.width, this.height, 0xE0100004);
        graphics.fillGradient(0, 0, this.width, this.height / 2, 0x44FF1E1E, 0x00FF1E1E);
        graphics.fillGradient(0, this.height / 2, this.width, this.height, 0x00FF1E1E, 0x44AA0000);

        // panel
        graphics.fill(left - 2, top - 2, left + PANEL_W + 2, top + PANEL_H + 2, GOLD_DIM);
        graphics.fill(left, top, left + PANEL_W, top + PANEL_H, 0xFF150A0C);
        graphics.fillGradient(left + 1, top + 1, left + PANEL_W - 1, top + 46, 0x55FF2A2A, 0x00150A0C);
        graphics.fill(left + 1, top + 1, left + PANEL_W - 1, top + 2, GOLD);
        graphics.fill(left + 1, top + PANEL_H - 2, left + PANEL_W - 1, top + PANEL_H - 1, GOLD_DIM);

        graphics.drawCenteredString(this.font, Component.translatable("casino.mellstroy.title"),
                centerX, top + 14, GOLD);
        graphics.drawCenteredString(this.font, Component.translatable("casino.mellstroy.subtitle"),
                centerX, top + 30, GREY);

        // reels
        int total = BOX * 3 + GAP * 2;
        int reelY = top + 50;
        int startX = centerX - total / 2;
        boolean revealed = this.allStopped();

        for (int i = 0; i < 3; ++i) {
            int x = startX + i * (BOX + GAP);
            int border = revealed ? (this.won ? GREEN : RED) : (this.stopped[i] ? GOLD : GOLD_DIM);

            graphics.fill(x - 2, reelY - 2, x + BOX + 2, reelY + BOX + 2, 0xFF000000);
            graphics.fill(x - 1, reelY - 1, x + BOX + 1, reelY + BOX + 1, border);
            graphics.fill(x, reelY, x + BOX, reelY + BOX, 0xFF1D1013);
            graphics.fillGradient(x, reelY, x + BOX, reelY + BOX, 0x33FF3A3A, 0x00000000);

            int symbolColor = revealed ? (this.won ? GREEN : RED) : GOLD;
            if (this.spinStart >= 0 && !this.stopped[i]) {
                this.drawScrollingReel(graphics, i, x, reelY);
            } else {
                this.drawSymbol(graphics, this.reels[i], x + BOX / 2.0F, reelY + BOX / 2.0F, symbolColor);
            }
        }

        // status
        Component status;
        int statusColor;
        if (revealed) {
            status = Component.translatable(this.won ? "casino.mellstroy.win" : "casino.mellstroy.lose");
            statusColor = this.won ? GREEN : RED;
        } else if (this.spinStart >= 0) {
            status = Component.translatable("casino.mellstroy.spinning");
            statusColor = GREY;
        } else if (this.bonusOffered) {
            status = Component.translatable("casino.mellstroy.second_chance");
            statusColor = GOLD;
        } else {
            status = Component.translatable("casino.mellstroy.waiting");
            statusColor = GREY;
        }
        graphics.drawCenteredString(this.font, status, centerX, top + 136, statusColor);

        // spin button
        int bw = 168;
        int bh = 26;
        int bx = centerX - bw / 2;
        int by = top + 158;
        boolean canSpin = this.spinStart < 0 && !this.hasResult;
        boolean hovered = canSpin && mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;

        graphics.fill(bx - 2, by - 2, bx + bw + 2, by + bh + 2, 0xFF000000);
        graphics.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, canSpin ? GOLD : GOLD_DIM);
        graphics.fill(bx, by, bx + bw, by + bh,
                canSpin ? (hovered ? 0xFF7A1010 : 0xFF4A0A0A) : 0xFF2A1A1C);
        graphics.fillGradient(bx + 1, by + 1, bx + bw - 1, by + bh - 1,
                canSpin ? 0x33FF6A6A : 0x11FF6A6A, 0x00000000);

        Component buttonText = canSpin
                ? Component.translatable("casino.mellstroy.spin")
                : Component.translatable("casino.mellstroy.thanks");
        graphics.drawCenteredString(this.font, buttonText, centerX, by + 9, canSpin ? GOLD : GREY);

        graphics.drawCenteredString(this.font,
                Component.translatable("casino.mellstroy.chance", this.winChance),
                centerX, top + 198, GREY);
        graphics.drawCenteredString(this.font,
                Component.translatable("casino.mellstroy.rules"),
                centerX, top + 212, GOLD_DIM);
    }

    private void drawScrollingReel(GuiGraphics graphics, int reel, int x, int reelY) {
        graphics.enableScissor(x, reelY, x + BOX, reelY + BOX);

        float value = this.scroll[reel];
        int base = Math.floorMod((int) Math.floor(value), CasinoSymbols.COUNT);
        float fraction = value - (float) Math.floor(value);
        float centerX = x + BOX / 2.0F;
        float centerY = reelY + BOX / 2.0F;

        for (int offset = -1; offset <= 1; offset++) {
            int symbol = Math.floorMod(base + offset, CasinoSymbols.COUNT);
            float symbolY = centerY + (offset - fraction) * BOX;
            this.drawSymbol(graphics, symbol, centerX, symbolY, GOLD);
        }

        graphics.disableScissor();
    }

    private void drawSymbol(GuiGraphics graphics, int symbol, float centerX, float centerY, int color) {
        var pose = graphics.pose();
        pose.pushPose();

        if (symbol == CasinoSymbols.SEVEN) {
            String seven = "7";
            float scale = 4.0F;
            pose.translate(centerX - this.font.width(seven) * scale / 2.0F,
                    centerY - 4.0F * scale, 0.0F);
            pose.scale(scale, scale, 1.0F);
            graphics.drawString(this.font, seven, 0, 0, color, true);
        } else {
            float scale = 3.4F;
            pose.translate(centerX - 8.0F * scale, centerY - 8.0F * scale, 0.0F);
            pose.scale(scale, scale, 1.0F);
            graphics.renderItem(this.symbolStacks[symbol], 0, 0);
        }

        pose.popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        int bw = 168;
        int bh = 26;
        int bx = (this.width - bw) / 2;
        int by = (this.height - PANEL_H) / 2 + 158;

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.spinStart < 0 && !this.hasResult
                && mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh) {
            this.startSpin(true);
            return true;
        }
        return false;
    }

    @Override
    protected void init() {
        super.init();
        // The player is knocked down for the whole spin: lower the local
        // camera and render him lying on the ground.
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.setForcedPose(Pose.SWIMMING);
        }
    }

    @Override
    public void removed() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.setForcedPose(null);
        }
        super.removed();
    }

    @Override
    public void onClose() {
        MellstroyAudio.stopAll();
        super.onClose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        // The world stays visible; the dim layer is drawn in render().
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
