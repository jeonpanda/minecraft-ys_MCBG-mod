package com.ys.ys_mcbg;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * Simple team system for MCBG.
 * - One team per player.
 * - Teams have a name + ARGB color.
 * - Stored as SavedData in overworld.
 */
public class BgTeamSavedData extends SavedData {
    public static final String NAME = "ys_mcbg_team";

    /** teamName -> argb */
    private final Map<String, Integer> teamColors = new HashMap<>();

    /** teamName -> members */
    private final Map<String, Set<UUID>> teamMembers = new HashMap<>();

    /** player -> teamName (reverse lookup) */
    private final Map<UUID, String> playerTeam = new HashMap<>();

    /** 항상 오버월드 기준의 SavedData를 가져오도록 보장 */
    public static BgTeamSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                BgTeamSavedData::load,
                BgTeamSavedData::new,
                NAME
        );
    }

    public static BgTeamSavedData get(MinecraftServer server) {
        return get(server.overworld());
    }

    // --------- operations ---------

    public boolean hasTeam(String name) {
        return teamColors.containsKey(name);
    }

    public int getColor(String teamName, int fallbackArgb) {
        return teamColors.getOrDefault(teamName, fallbackArgb);
    }

    public Map<String, Integer> getTeamColors() {
        return Collections.unmodifiableMap(teamColors);
    }

    public Map<UUID, String> getPlayerTeam() {
        return Collections.unmodifiableMap(playerTeam);
    }

    /** Set team color (ARGB). Creates the team if missing. */
    public void setColor(String teamName, int argb) {
        addTeam(teamName);
        teamColors.put(teamName, argb);
        setDirty();
    }

    /** Backward-compatible alias used by command code. */
    public void setTeamColor(String teamName, int argb) {
        setColor(teamName, argb);
    }

    /** Clear all teams + memberships. */
    public void resetAll() {
        teamColors.clear();
        teamMembers.clear();
        playerTeam.clear();
        setDirty();
    }

    public void resetAll(MinecraftServer server) {
        resetAll();
    }

    /** Backward-compatible alias used by command code. */
    public void clearAll() {
        resetAll();
    }

    public void clearAll(MinecraftServer server) {
        resetAll();
    }

    public void addTeam(String name) {
        if (teamColors.containsKey(name)) return;
        teamColors.put(name, pickColor(name));
        teamMembers.put(name, new HashSet<>());
        setDirty();
    }

    public void removeTeam(String name) {
        if (!teamColors.containsKey(name)) return;

        Set<UUID> members = teamMembers.getOrDefault(name, Collections.emptySet());
        for (UUID id : members) {
            playerTeam.remove(id);
        }
        teamMembers.remove(name);
        teamColors.remove(name);
        setDirty();
    }

    public void removeTeam(String name, MinecraftServer server) {
        removeTeam(name);
    }

    /** Put player into team. Removes them from previous team if any. */
    public void join(String teamName, UUID playerId) {
        addTeam(teamName);

        String prev = playerTeam.get(playerId);
        if (prev != null && !prev.equals(teamName)) {
            Set<UUID> prevSet = teamMembers.get(prev);
            if (prevSet != null) prevSet.remove(playerId);
        }

        playerTeam.put(playerId, teamName);
        teamMembers.computeIfAbsent(teamName, k -> new HashSet<>()).add(playerId);
        setDirty();
    }

    public void join(String teamName, UUID playerId, MinecraftServer server) {
        join(teamName, playerId);
    }

    /** Remove player from a team (or from whatever team they are currently in). */
    public void leave(UUID playerId) {
        String t = playerTeam.remove(playerId);
        if (t == null) return;
        Set<UUID> set = teamMembers.get(t);
        if (set != null) set.remove(playerId);
        setDirty();
    }

    public void leave(UUID playerId, MinecraftServer server) {
        leave(playerId);
    }

    // --------- serialization ---------

    public static BgTeamSavedData load(CompoundTag tag) {
        BgTeamSavedData d = new BgTeamSavedData();

        ListTag teams = tag.getList("teams", Tag.TAG_COMPOUND);
        for (int i = 0; i < teams.size(); i++) {
            CompoundTag t = teams.getCompound(i);
            String name = t.getString("name");
            int color = t.getInt("color");

            d.teamColors.put(name, color);

            Set<UUID> members = new HashSet<>();
            ListTag arr = t.getList("members", Tag.TAG_STRING);
            for (int j = 0; j < arr.size(); j++) {
                try {
                    UUID id = UUID.fromString(arr.getString(j));
                    members.add(id);
                    d.playerTeam.put(id, name);
                } catch (IllegalArgumentException ignored) {}
            }
            d.teamMembers.put(name, members);
        }

        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag teams = new ListTag();

        for (Map.Entry<String, Integer> e : teamColors.entrySet()) {
            String name = e.getKey();
            int color = e.getValue();

            CompoundTag t = new CompoundTag();
            t.putString("name", name);
            t.putInt("color", color);

            ListTag members = new ListTag();
            for (UUID id : teamMembers.getOrDefault(name, Collections.emptySet())) {
                members.add(StringTag.valueOf(id.toString()));
            }
            t.put("members", members);

            teams.add(t);
        }

        tag.put("teams", teams);
        return tag;
    }

    // --------- color picking ---------

    private static int pickColor(String name) {
        int h = name.hashCode();
        RandomSource rnd = RandomSource.create(h * 341873128712L + 132897987541L);
        int r = 80 + rnd.nextInt(176);
        int g = 80 + rnd.nextInt(176);
        int b = 80 + rnd.nextInt(176);
        int a = 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}