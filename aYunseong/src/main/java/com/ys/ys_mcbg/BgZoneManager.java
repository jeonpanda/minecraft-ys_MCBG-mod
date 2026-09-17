package com.ys.ys_mcbg;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Server-side zone logic:
 * - WAIT -> SHRINK -> WAIT ... (PUBG-like)
 * - Center moves every phase, but always stays inside the previous circle
 * - Applies damage outside the current circle
 * - Syncs schedule info to clients (HUD + border renderer)
 */
@Mod.EventBusSubscriber(modid = ysMcbgMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BgZoneManager {

    /** Tick handler (server, overworld only). */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.level.isClientSide) return;
        if (event.level.dimension() != Level.OVERWORLD) return;

        ServerLevel level = (ServerLevel) event.level;
        BgZoneSavedData data = BgZoneSavedData.get(level);
        if (!data.running) return;

        long now = level.getGameTime();
        ZoneRuntime rt = ZoneRuntime.compute(data, now);

        // ✅ 축소 시작 순간 메시지
        if (rt.stage == ZoneRuntime.STAGE_SHRINK && data.lastAnnouncedShrinkPhase < rt.phase) {
            data.lastAnnouncedShrinkPhase = rt.phase;
            data.setDirty();
            level.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("[ " + rt.phase + " 페이즈 ] ")
                            .withStyle(net.minecraft.ChatFormatting.BLUE)
                            .append(
                                    Component.literal("자기장이 줄어듭니다")
                                            .withStyle(net.minecraft.ChatFormatting.RED)
                            ),
                    false
            );
        }

        // ✅ 클라에 상태 동기화(페이즈/스테이지 바뀔 때만)
        if (data.lastSentPhase != rt.phase ||
                data.lastSentStage != rt.stage ||
                data.lastSentStageStartTick != rt.stageStartTick) {

            data.lastSentPhase = rt.phase;
            data.lastSentStage = rt.stage;
            data.lastSentStageStartTick = rt.stageStartTick;
            data.setDirty();

            ysMcbgMod.CHANNEL.send(PacketDistributor.ALL.noArg(), new ZoneScheduleSyncPacket(rt));
        }



        // ✅ 바깥 데미지: 1초(20틱)마다
        if (now % 20L == 0L) {
            float dmg = damagePerSecond(rt.phase);

            for (ServerPlayer sp : level.players()) {
                if (sp.isCreative() || sp.isSpectator()) continue;

                double dx = sp.getX() - rt.curX;
                double dz = sp.getZ() - rt.curZ;
                double dist = Math.sqrt(dx * dx + dz * dz);

                if (dist >= rt.curR) {
                    sp.hurt(sp.damageSources().magic(), dmg);
                }
            }
        }
    }

    /** 플레이어가 중간에 접속해도 HUD/보더가 바로 뜨도록 1회 동기화 */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        try {
            MinecraftServer server = sp.getServer();
            if (server == null) return;

            ServerLevel level = server.getLevel(Level.OVERWORLD);
            if (level == null) return;

            BgZoneSavedData data = BgZoneSavedData.get(level);
            if (!data.running) return;

            long now = level.getGameTime();
            ZoneRuntime rt = ZoneRuntime.compute(data, now);
            ysMcbgMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new ZoneScheduleSyncPacket(rt));
        } catch (Throwable t) {
            ysMcbgMod.LOGGER.error("[MCBG] Zone sync failed on login for {}", sp.getGameProfile().getName(), t);
        }
    }

    /** 외부에서 강제 동기화(명령어로 start/stop/set 후 즉시 HUD 갱신용) */
    public static void syncToAll(MinecraftServer server) {
        if (server == null) return;
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) return;

        BgZoneSavedData data = BgZoneSavedData.get(level);
        if (!data.running) return;

        long now = level.getGameTime();
        ZoneRuntime rt = ZoneRuntime.compute(data, now);
        ysMcbgMod.CHANNEL.send(PacketDistributor.ALL.noArg(), new ZoneScheduleSyncPacket(rt));
    }


    // 페이즈별 데미지(초당). 원하는 값으로 마음대로 조절.
    private static float damagePerSecond(int phase) {
        return switch (phase) {
            case 1 -> 0.5f;
            case 2 -> 1.0f;
            case 3 -> 2.0f;
            case 4 -> 3.5f;
            case 5 -> 5.0f;
            default -> 1.0f;
        };
    }
}
