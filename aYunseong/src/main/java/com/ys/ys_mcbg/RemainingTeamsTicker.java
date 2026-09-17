package com.ys.ys_mcbg;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/**
 * 실시간으로 '생존자가 존재하는 유효 팀' 수를 계산하여
 * mcbg_teams_left 스코어보드에 반영합니다.
 */
@Mod.EventBusSubscriber(modid = ysMcbgMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RemainingTeamsTicker {

    private static final String OBJ_NAME = "mcbg_teams_left";
    private static final String SCORE_HOLDER = "teams_left";

    private RemainingTeamsTicker() {}

    /**
     * 현재 서버에서 생존 중인 플레이어가 속한 유효 팀 개수를 계산합니다.
     */
    public static int countRemainingTeams(MinecraftServer server) {
        if (server == null) return 0;

        BgTeamSavedData teamData = BgTeamSavedData.get(server);
        Set<String> aliveTeamKeys = new HashSet<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // 관전자이거나 사망한 플레이어 제외
            if (player.isSpectator() || !player.isAlive()) continue;

            String teamName = teamData.getPlayerTeam().get(player.getUUID());

            // 팀에 속해 있고 해당 팀이 유효하게 존재할 때만 추가 (무소속은 카운트 제외)
            if (teamName != null && !teamName.isEmpty() && teamData.hasTeam(teamName)) {
                aliveTeamKeys.add(teamName);
            }
        }

        return aliveTeamKeys.size();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = event.getServer();
        if (server == null) return;

        // 1초(20틱)마다 실시간 생존 팀 수 갱신
        if (server.getTickCount() % 20 != 0) return;

        int teamsLeft = countRemainingTeams(server);

        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJ_NAME);

        if (objective == null) {
            objective = scoreboard.addObjective(
                    OBJ_NAME,
                    ObjectiveCriteria.DUMMY,
                    Component.literal("Teams Left"),
                    ObjectiveCriteria.RenderType.INTEGER
            );
        }

        scoreboard.getOrCreatePlayerScore(SCORE_HOLDER, objective).setScore(teamsLeft);
    }
}