package com.ys.ys_mcbg;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side cache for team membership + team colors.
 * Updated via TeamSyncPacket.
 */
public class ClientTeamCache {
    private static final Map<UUID, String> PLAYER_TEAM = new HashMap<>();
    private static final Map<String, Integer> TEAM_COLOR = new HashMap<>();

    public static void apply(TeamSyncPacket p) {
        PLAYER_TEAM.clear();
        TEAM_COLOR.clear();
        TEAM_COLOR.putAll(p.teamColors);
        PLAYER_TEAM.putAll(p.playerToTeam);
    }

    public static void clear() {
        PLAYER_TEAM.clear();
        TEAM_COLOR.clear();
    }

    public static String getTeam(UUID playerId) {
        return PLAYER_TEAM.get(playerId);
    }

    public static int getTeamColor(String teamName, int fallbackArgb) {
        Integer c = TEAM_COLOR.get(teamName);
        return c != null ? c : fallbackArgb;
    }

    public static Map<UUID, String> snapshotPlayerTeams() {
        return Collections.unmodifiableMap(PLAYER_TEAM);
    }
}
