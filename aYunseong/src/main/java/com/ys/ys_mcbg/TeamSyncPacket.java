package com.ys.ys_mcbg;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server -> Client: team membership + team colors.
 */
public class TeamSyncPacket {
    /** teamName -> argb */
    public final Map<String, Integer> teamColors = new HashMap<>();

    /** playerUUID -> teamName */
    public final Map<UUID, String> playerToTeam = new HashMap<>();

    public TeamSyncPacket() {}

    public static TeamSyncPacket fromServer(BgTeamSavedData data) {
        TeamSyncPacket p = new TeamSyncPacket();
        p.teamColors.putAll(data.getTeamColors());
        p.playerToTeam.putAll(data.getPlayerTeam());
        return p;
    }

    public static void encode(TeamSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.teamColors.size());
        for (var e : msg.teamColors.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeInt(e.getValue());
        }

        buf.writeVarInt(msg.playerToTeam.size());
        for (var e : msg.playerToTeam.entrySet()) {
            buf.writeUUID(e.getKey());
            buf.writeUtf(e.getValue());
        }
    }

    public static TeamSyncPacket decode(FriendlyByteBuf buf) {
        TeamSyncPacket p = new TeamSyncPacket();

        int teamCount = buf.readVarInt();
        for (int i = 0; i < teamCount; i++) {
            String name = buf.readUtf();
            int color = buf.readInt();
            p.teamColors.put(name, color);
        }

        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            UUID id = buf.readUUID();
            String team = buf.readUtf();
            p.playerToTeam.put(id, team);
        }

        return p;
    }

    public static void handle(TeamSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientTeamCache.apply(msg));
        ctx.get().setPacketHandled(true);
    }
}
