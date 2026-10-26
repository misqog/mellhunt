package com.mellstroy.horror.casino;

import com.mellstroy.horror.MellstroyMod;
import com.mellstroy.horror.advancement.ModAdvancements;
import com.mellstroy.horror.config.MellstroyConfig;
import com.mellstroy.horror.entity.MellstroyEntity;
import com.mellstroy.horror.network.ModNetwork;
import com.mellstroy.horror.registry.ModItems;
import com.mellstroy.horror.registry.ModSounds;
import com.mellstroy.horror.spawn.MellstroyRespawnManager;
import com.mellstroy.horror.world.MellstroyHouseManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Mellstroy's casino": when he kills you, you get exactly one spin.
 *
 * Win  - you are revived with all your items, and he is teleported far away.
 * Lose - every item (and all XP) is deleted, and then you really die.
 *
 * The result is decided on the server the moment you spin. The outcome is only
 * applied after the win/lose sound has finished playing on the client.
 */
@Mod.EventBusSubscriber(modid = MellstroyMod.MOD_ID)
public final class CasinoManager {

    /** Used when the player does not press SPIN for a long time. */
    private static final int AUTO_SPIN_TICKS = 25 * 20;
    /** Extra server-side delay after the sound, in case the client never acks. */
    private static final int SERVER_MARGIN_TICKS = 40;
    /** Chance (percent) that a losing first spin grants one more spin. */
    private static final int SECOND_CHANCE_PERCENT = 1;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static final class Session {
        private final UUID playerId;
        private final UUID mobId;
        /** Game time at which the pending spin is auto-triggered. */
        private long autoSpinAt;
        private int winChance;
        private boolean resultSent;
        private boolean won;
        private int[] line;
        private long resolveAt;
        private boolean resolving;
        /** The current spin is the second (bonus) one and uses izi_brigi.ogg. */
        private boolean bonusRound;
        /** The current result is a losing first spin that rolled the 1% second chance. */
        private boolean secondChance;
        /** Game time when posidim.ogg has finished and SPIN may be pressed again. */
        private long bonusReadyAt;
        /** The delayed "second chance" chat line has already been shown. */
        private boolean secondChanceAnnounced;

        private Session(UUID playerId, UUID mobId, long autoSpinAt) {
            this.playerId = playerId;
            this.mobId = mobId;
            this.autoSpinAt = autoSpinAt;
        }
    }

    public static boolean isInCasino(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && !session.resolving;
    }

    /** True while any player is being handled by the casino. */
    public static boolean hasActiveSession() {
        return !SESSIONS.isEmpty();
    }

    /* ------------------------------------------------------------------ */
    /*  Death interception                                                */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof MellstroyEntity mob)) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }

        Session active = SESSIONS.get(player.getUUID());
        if (active != null) {
            // Already in the casino, or this is the real death after a lost spin.
            if (!active.resolving) {
                event.setCanceled(true);
            }
            return;
        }

        if (!MellstroyConfig.casinoEnabled()) {
            return;
        }

        // The kill sound is positional and keeps following him while it plays.
        mob.playTrackedSound(ModSounds.KILL.get(), SoundSource.HOSTILE, 1.0F, 1.0F);

        event.setCanceled(true);
        startSession(player, mob);
    }

    @SubscribeEvent
    public static void onPlayerAttacked(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Session session = SESSIONS.get(player.getUUID());
            if (session != null && !session.resolving) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * While the player is lying on the ground in the casino, mobs must not
     * acquire him as a target (and thus must not swarm or attack him). This
     * covers every newly chosen target; {@link #startSession} also clears the
     * targets mobs already had.
     */
    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof ServerPlayer player
                && SESSIONS.containsKey(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Session session = SESSIONS.remove(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player) {
            standUp(player);
            if (session != null) {
                MellstroyEntity mob = findMob(player.serverLevel(), session.mobId);
                if (mob != null) {
                    mob.unfreeze();
                    mob.giveGrace(MellstroyEntity.GRACE_TICKS);
                }
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Session flow                                                      */
    /* ------------------------------------------------------------------ */

    private static void startSession(ServerPlayer player, MellstroyEntity mob) {
        ServerLevel level = player.serverLevel();

        // The rush is over: he reached the player. Removing the house alarm here
        // keeps its "МЕЛЛСТРОЙ УЖЕ БЛИЗКО" line from overwriting the catch
        // message and prevents a new rush after a won casino.
        MellstroyHouseManager.stopRush(player.getUUID());

        player.setHealth(player.getMaxHealth());
        player.clearFire();
        player.setRemainingFireTicks(0);
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 120, 6, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20 * 120, 3, false, false, false));

        // Knocked down for the duration of the spin: he lies on the ground
        // (prone pose) and every mob ignores him until he gets up again.
        player.stopFallFlying();
        player.setDeltaMovement(Vec3.ZERO);
        player.setForcedPose(Pose.SWIMMING);
        clearMobTargets(player);

        // He stands in front of you and does not move while you spin.
        mob.freezeInPlace();
        Vec3 look = player.getLookAngle();
        double x = player.getX() + look.x * 3.0D;
        double z = player.getZ() + look.z * 3.0D;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) Math.floor(x), (int) Math.floor(z));
        mob.teleportTo(x, y, z);
        mob.lookAt(EntityAnchorArgument.Anchor.EYES, player.position().add(0.0D, player.getEyeHeight(), 0.0D));

        player.displayClientMessage(
                Component.translatable("message.mellstroy.casino")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        Session session = new Session(player.getUUID(), mob.getUUID(),
                level.getGameTime() + AUTO_SPIN_TICKS);
        session.winChance = MellstroyConfig.randomCasinoWinChance(player.getRandom());
        SESSIONS.put(player.getUUID(), session);

        ModNetwork.sendOpenCasino(player, session.winChance);
    }

    public static void handleSpin(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (!session.resultSent) {
            spin(player);
        } else if (session.secondChance
                && player.serverLevel().getGameTime() >= session.bonusReadyAt) {
            startBonusSpin(player, session);
        }
    }

    /** The player presses SPIN again after the posidim jingle: roll the bonus round. */
    private static void startBonusSpin(ServerPlayer player, Session session) {
        if (session.resolving || !session.secondChance) {
            return;
        }
        announceSecondChance(player, session);
        session.bonusRound = true;
        session.secondChance = false;
        session.resultSent = false;
        session.won = false;
        session.line = null;
        session.resolveAt = Long.MAX_VALUE;
        session.autoSpinAt = Long.MAX_VALUE;
        spin(player);
    }

    /** Shows the second-chance line exactly once, after posidim.ogg has ended. */
    private static void announceSecondChance(ServerPlayer player, Session session) {
        if (session.secondChanceAnnounced) {
            return;
        }
        session.secondChanceAnnounced = true;
        player.displayClientMessage(
                Component.translatable("message.mellstroy.casino.second_chance")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
    }

    private static void spin(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.resultSent) {
            return;
        }

        session.won = player.getRandom().nextInt(100) < session.winChance;
        session.line = session.won
                ? CasinoSymbols.randomWinning(player.getRandom())
                : CasinoSymbols.randomLosing(player.getRandom());
        session.resultSent = true;

        // 1% second chance, only after a losing first spin. The bonus spin
        // itself can never roll another one.
        session.secondChance = !session.won && !session.bonusRound
                && player.getRandom().nextInt(100) < SECOND_CHANCE_PERCENT;

        long now = player.serverLevel().getGameTime();
        int spinTicks = session.bonusRound
                ? CasinoTiming.IZI_BRIGI_SPIN_TICKS
                : CasinoTiming.SPIN_TICKS;

        if (session.secondChance) {
            // posidim.ogg plays instead of the losing sound. The server waits
            // for the player to press SPIN again; if he never does, the bonus
            // spin is auto-triggered once the jingle and the idle time pass.
            // The chat line is deliberately delayed until the jingle ends so
            // it cannot spoil the result while the reels are still spinning.
            int posidimTicks = Math.round(ModSounds.POSIDIM_SECONDS * 20.0F);
            session.bonusReadyAt = now
                    + CasinoTiming.resultStartTicks(spinTicks)
                    + posidimTicks
                    + 6;
            session.autoSpinAt = session.bonusReadyAt + AUTO_SPIN_TICKS;
            session.secondChanceAnnounced = false;
            session.resolveAt = Long.MAX_VALUE;
        } else {
            float soundSeconds = session.won
                    ? ModSounds.WIN_CASINO_SECONDS
                    : ModSounds.LOSE_CASINO_SECONDS;
            // The client first plays the full reel animation, then the result
            // sound. The fallback (in case the ack never arrives) must outlast both.
            session.resolveAt = now
                    + CasinoTiming.resultStartTicks(spinTicks)
                    + Math.round(soundSeconds * 20.0F)
                    + SERVER_MARGIN_TICKS;
        }

        ModNetwork.sendResult(player, session.won, session.line,
                session.bonusRound, session.secondChance);
    }

    public static void handleAck(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        // A second-chance result is not final: its ack would be premature.
        if (session != null && session.resultSent && !session.secondChance) {
            finish(player, session);
        }
    }

    private static void finish(ServerPlayer player, Session session) {
        if (session.resolving) {
            return;
        }
        session.resolving = true;

        // The casino is over: get up before the win/lose outcome is applied.
        standUp(player);

        ServerLevel level = player.serverLevel();
        MellstroyEntity mob = findMob(level, session.mobId);

        if (session.won) {
            player.removeAllEffects();
            player.setHealth(player.getMaxHealth());
            player.clearFire();
            player.setRemainingFireTicks(0);

            // Win or lose, he disappears and returns on his own later.
            if (mob != null) {
                mob.discard();
            }
            MellstroyRespawnManager.scheduleAfterCasino(player.getServer());

            player.displayClientMessage(
                    Component.translatable("message.mellstroy.win")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);

            // Bonus roll, only after a win: the win chance itself is untouched.
            // The exact egg chance is itself random in [5, 15] percent every win.
            int eggChance = 5 + player.getRandom().nextInt(11);
            if (player.getRandom().nextInt(100) < eggChance) {
                ItemStack egg = new ItemStack(ModItems.FAT_CAT_SPAWN_EGG.get());
                if (!player.addItem(egg)) {
                    player.drop(egg, false);
                }
                player.displayClientMessage(
                        Component.translatable("message.mellstroy.casino.cat_egg")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
            }

            SESSIONS.remove(player.getUUID());
            ModAdvancements.onCasinoWin(player);
            return;
        }

        // Lost: everything disappears, nothing is dropped, then he kills you.
        if (mob != null) {
            mob.discard();
        }
        MellstroyRespawnManager.scheduleAfterCasino(player.getServer());

        player.removeAllEffects();
        player.stopUsingItem();
        player.getInventory().clearContent();
        player.setExperienceLevels(0);
        player.experienceProgress = 0.0F;
        player.totalExperience = 0;

        player.displayClientMessage(
                Component.translatable("message.mellstroy.lose")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);

        ModAdvancements.onCasinoLoss(player);

        if (mob != null) {
            player.hurt(player.damageSources().mobAttack(mob), 4096.0F);
        } else {
            player.hurt(player.damageSources().genericKill(), 4096.0F);
        }

        SESSIONS.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SESSIONS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }

        List<Session> finished = new ArrayList<>();
        for (Session session : SESSIONS.values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
            if (player == null) {
                continue;
            }
            long now = player.serverLevel().getGameTime();

            if (!session.resultSent) {
                if (now >= session.autoSpinAt) {
                    spin(player);
                }
                continue;
            }

            if (session.secondChance) {
                // Waiting for the second SPIN after posidim.ogg. The chat line
                // is shown only once the jingle has finished.
                if (now >= session.bonusReadyAt) {
                    announceSecondChance(player, session);
                }
                if (now >= session.autoSpinAt) {
                    startBonusSpin(player, session);
                }
                continue;
            }

            if (now >= session.resolveAt) {
                finished.add(session);
            }
        }

        for (Session session : finished) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
            if (player != null) {
                finish(player, session);
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Helpers                                                           */
    /* ------------------------------------------------------------------ */

    /** Puts the player back on his feet (used when the casino ends or he logs out). */
    private static void standUp(ServerPlayer player) {
        player.setForcedPose(null);
        player.setPose(Pose.STANDING);
    }

    /**
     * Makes every mob around the player forget him: no target, no neutral
     * anger, no chase. While he lies on the ground in the casino nothing may
     * react to him.
     */
    private static void clearMobTargets(ServerPlayer player) {
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(128.0D))) {
            if (mob.getTarget() == player) {
                mob.setTarget(null);
            }
            if (mob instanceof NeutralMob neutral
                    && player.getUUID().equals(neutral.getPersistentAngerTarget())) {
                neutral.stopBeingAngry();
            }
        }
    }

    private static MellstroyEntity findMob(ServerLevel level, UUID id) {
        return level.getEntity(id) instanceof MellstroyEntity mob ? mob : null;
    }

    private CasinoManager() {
    }
}
