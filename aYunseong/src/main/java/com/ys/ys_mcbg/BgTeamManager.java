package com.ys.ys_mcbg;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Team sync helper.
 */
@Mod.EventBusSubscriber(modid = ysMcbgMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BgTeamManager {

    /** 로그인 시 팀 정보 클라이언트로 동기화 */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        try {
            MinecraftServer server = sp.getServer();
            if (server == null) return;
            ServerLevel level = server.getLevel(Level.OVERWORLD);
            if (level == null) return;

            BgTeamSavedData teamData = BgTeamSavedData.get(level);

            // 클라이언트 데이터 동기화 패킷 전송
            ysMcbgMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), TeamSyncPacket.fromServer(teamData));
        } catch (Throwable t) {
            ysMcbgMod.LOGGER.error("[MCBG] Team sync failed on login for {}", sp.getGameProfile().getName(), t);
        }
    }

    /** 리스폰 후에도 팀 정보를 다시 보장 */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        MinecraftServer server = sp.getServer();
        if (server == null) return;
        syncPlayer(server, sp);
    }

    /** 차원 이동 후에도 팀 정보를 다시 보장 */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        MinecraftServer server = sp.getServer();
        if (server == null) return;
        syncPlayer(server, sp);
    }

    private static void syncPlayer(MinecraftServer server, ServerPlayer sp) {
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) return;
        BgTeamSavedData teamData = BgTeamSavedData.get(level);
        ysMcbgMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), TeamSyncPacket.fromServer(teamData));
    }

    /** 외부(명령어)에서 호출: 전체 패킷 동기화 */
    public static void syncToAll(MinecraftServer server) {
        if (server == null) return;
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) return;

        BgTeamSavedData teamData = BgTeamSavedData.get(level);
        ysMcbgMod.CHANNEL.send(PacketDistributor.ALL.noArg(), TeamSyncPacket.fromServer(teamData));
    }

    public static Component teamPrefix(String name) {
        return Component.literal("[TEAM] ").withStyle(net.minecraft.ChatFormatting.AQUA)
                .append(Component.literal(name).withStyle(net.minecraft.ChatFormatting.WHITE));
    }

    public static String getTeamName(ServerPlayer sp) {
        MinecraftServer server = sp.getServer();
        if (server == null) return null;
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) return null;

        BgTeamSavedData teamData = BgTeamSavedData.get(level);
        return teamData.getPlayerTeam().get(sp.getUUID()); // UUID -> teamName
    }

    public static boolean sameTeam(ServerPlayer a, ServerPlayer b) {
        String ta = getTeamName(a);
        String tb = getTeamName(b);
        return ta != null && ta.equals(tb);
    }
}