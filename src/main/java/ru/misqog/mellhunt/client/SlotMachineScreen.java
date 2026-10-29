package ru.misqog.mellhunt.client;

import ru.misqog.mellhunt.casino.CasinoSymbols;
import ru.misqog.mellhunt.casino.CasinoTiming;
import ru.misqog.mellhunt.network.ModNetwork;
import ru.misqog.mellhunt.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * The playable "mellstroy.game" slot machine.
 *
 * <p>Visually the same cabinet as the death casino, but the stakes are just
 * the gold ingot the player paid. The server decides the result and grants the
 * prize at once; this screen only animates the reels, plays the win/lose
 * sound and closes itself afterwards. Closing early is safe - the prize is
 * already in the inventory.</p>
 */
public class SlotMachineScreen extends Screen {

    private static final int PANEL_W = 360;
    private static final int PANEL_H = 232;
    private static final int BOX = 74;
    private static final int GAP = 12;

    private static final Item[] SYMBOL_ITEMS = {
            Items.AIR,
            Items.DIAMOND,
            Items.GOLD_INGOT,
            Items.EMERALD,
            Items.NETHERITE_INGOT,
            Items.ENCHANTED_GOLDEN_APPLE
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
    private final BlockPos machinePos;
    private final ItemStack[] symbolStacks = new ItemStack[CasinoSymbols.COUNT];

    private final int[] reels = DEFAULT_REELS.clone();
    private final int[] finalLine = new int[3];
    private final boolean[] stopped = new boolean[3];
    private final float[] scroll = DEFAULT_SCROLL.clone();

    private int ticks;
    private int spinStart = -1;
    private int spinTicks = CasinoTiming.SPIN_TICKS;
    private boolean hasResult;
    private boolean won;
    private boolean resultSoundPlayed;
    private int closeAt = -1;
    private ItemStack prize = ItemStack.EMPTY;

    public SlotMachineScreen(int winChance, BlockPos machinePos) {
        super(Component.translatable("slot.mellhunt.title"));
        this.winChance = winChance;
        this.machinePos = machinePos.immutable();
        for (int i = 1; i < CasinoSymbols.COUNT; i++) {
            this.symbolStacks[i] = new ItemStack(SYMBOL_ITEMS[i]);
        }
    }

    public void receiveResult(boolean won, int[] line, String prizeId, int prizeCount) {
        if (this.hasResult) {
            return;
        }
        this.hasResult = true;
        this.won = won;
        for (int i = 0; i < 3; i++) {
            this.finalLine[i] = Math.max(0, Math.min(CasinoSymbols.COUNT - 1, line[i]));
        }

        if (prizeId != null && !prizeId.isEmpty()) {
            ResourceLocation id = ResourceLocation.tryParse(prizeId);
            if (id != null) {
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item != Items.AIR) {
                    this.prize = new ItemStack(item, Math.max(1, prizeCount));
                }
            }
        }

        if (this.spinStart < 0) {
            this.startSpin(false);
        }
    }

    private void startSpin(boolean request) {
        if (this.spinStart >= 0) {
            return;
        }
        this.spinStart = this.ticks;
        this.spinTicks = CasinoTiming.SPIN_TICKS;
        SoundEvent sound = ModSounds.PROKRUTKA.get();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F));

        if (request) {
            ModNetwork.sendSlotSpin(this.machinePos);
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
                    this.scroll[i] += 0.72F + i * 0.06F;
                    this.reels[i] = Math.floorMod((int) Math.floor(this.scroll[i]), CasinoSymbols.COUNT);
                }
            }
        }

        if (this.hasResult && this.allStopped() && !this.resultSoundPlayed
                && this.ticks >= this.spinStart + CasinoTiming.resultStartTicks(this.spinTicks)) {
            this.playResult();
        }

        if (this.closeAt >= 0 && this.ticks >= this.closeAt) {
            this.onClose();
        }

        // Safety net: if the server never answers (the block was broken, the
        // packet was lost), do not leave the player stuck on spinning reels.
        if (this.spinStart >= 0 && !this.hasResult
                && this.ticks - this.spinStart > CasinoTiming.resultStartTicks(this.spinTicks) + 200) {
            this.onClose();
        }
    }

    private boolean allStopped() {
        return this.hasResult && this.stopped[0] && this.stopped[1] && this.stopped[2];
    }

    private void playResult() {
        this.resultSoundPlayed = true;
        SoundEvent sound = (this.won ? ModSounds.WIN_CASINO : ModSounds.LOSE_CASINO).get();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F));

        float seconds = this.won ? ModSounds.WIN_CASINO_SECONDS : ModSounds.LOSE_CASINO_SECONDS;
        this.closeAt = this.ticks + Math.round(seconds * 20.0F) + 6;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        int centerX = this.width / 2;

        graphics.fill(0, 0, this.width, this.height, 0xE00A0704);
        graphics.fillGradient(0, 0, this.width, this.height / 2, 0x44FFC24A, 0x00FFC24A);
        graphics.fillGradient(0, this.height / 2, this.width, this.height, 0x00FFC24A, 0x44A06A00);

        graphics.fill(left - 2, top - 2, left + PANEL_W + 2, top + PANEL_H + 2, GOLD_DIM);
        graphics.fill(left, top, left + PANEL_W, top + PANEL_H, 0xFF150A0C);
        graphics.fillGradient(left + 1, top + 1, left + PANEL_W - 1, top + 46, 0x55FFD24A, 0x00150A0C);
        graphics.fill(left + 1, top + 1, left + PANEL_W - 1, top + 2, GOLD);
        graphics.fill(left + 1, top + PANEL_H - 2, left + PANEL_W - 1, top + PANEL_H - 1, GOLD_DIM);

        graphics.drawCenteredString(this.font, Component.translatable("slot.mellhunt.title"),
                centerX, top + 14, GOLD);
        graphics.drawCenteredString(this.font, Component.translatable("slot.mellhunt.subtitle"),
                centerX, top + 30, GREY);

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
            graphics.fillGradient(x, reelY, x + BOX, reelY + BOX, 0x33FFD24A, 0x00000000);

            int symbolColor = revealed ? (this.won ? GREEN : RED) : GOLD;
            if (this.spinStart >= 0 && !this.stopped[i]) {
                this.drawScrollingReel(graphics, i, x, reelY);
            } else {
                this.drawSymbol(graphics, this.reels[i], x + BOX / 2.0F, reelY + BOX / 2.0F, symbolColor);
            }
        }

        Component status;
        int statusColor;
        if (revealed) {
            if (this.won) {
                status = this.prize.isEmpty()
                        ? Component.translatable("slot.mellhunt.win")
                        : Component.translatable("slot.mellhunt.prize",
                                this.prize.getCount(), this.prize.getHoverName());
                statusColor = GREEN;
            } else {
                status = Component.translatable("slot.mellhunt.lose");
                statusColor = RED;
            }
        } else if (this.spinStart >= 0) {
            status = Component.translatable("casino.mellhunt.spinning");
            statusColor = GREY;
        } else {
            status = Component.translatable("slot.mellhunt.waiting");
            statusColor = GREY;
        }
        graphics.drawCenteredString(this.font, status, centerX, top + 136, statusColor);

        int bw = 168;
        int bh = 26;
        int bx = centerX - bw / 2;
        int by = top + 158;
        boolean canSpin = this.spinStart < 0 && !this.hasResult;
        boolean hovered = canSpin && mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;

        graphics.fill(bx - 2, by - 2, bx + bw + 2, by + bh + 2, 0xFF000000);
        graphics.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, canSpin ? GOLD : GOLD_DIM);
        graphics.fill(bx, by, bx + bw, by + bh,
                canSpin ? (hovered ? 0xFF7A5A10 : 0xFF4A350A) : 0xFF2A1A1C);
        graphics.fillGradient(bx + 1, by + 1, bx + bw - 1, by + bh - 1,
                canSpin ? 0x33FFD24A : 0x11FFD24A, 0x00000000);

        Component buttonText = canSpin
                ? Component.translatable("casino.mellhunt.spin")
                : Component.translatable("casino.mellhunt.thanks");
        graphics.drawCenteredString(this.font, buttonText, centerX, by + 9, canSpin ? GOLD : GREY);

        graphics.drawCenteredString(this.font,
                Component.translatable("slot.mellhunt.bet", this.winChance),
                centerX, top + 198, GREY);
        graphics.drawCenteredString(this.font,
                Component.translatable("slot.mellhunt.rules"),
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
        return true;
    }
}
