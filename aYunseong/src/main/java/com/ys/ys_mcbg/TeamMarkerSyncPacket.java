package com.ys.ys_mcbg;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server -> client snapshot of ONLY the receiving player's teammates.
 *
 * This intentionally carries position data so the team marker can still be
 * rendered beyond vanilla player entity tracking distance (up to 600 blocks).
 */
public final class TeamMarkerSyncPacket {
    public static final class Marker {
        public final UUID playerId;
        public final double x;
        public final double y;
        public final double z;
        public final float yRot;

        public Marker(UUID playerId, double x, double y, double z, float yRot) {
            this.playerId = playerId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yRot = yRot;
        }
    }

    public final List<Marker> markers;

    public TeamMarkerSyncPacket(List<Marker> markers) {
        this.markers = new ArrayList<>(markers);
    }

    public static void encode(TeamMarkerSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.markers.size());
        for (Marker marker : msg.markers) {
            buf.writeUUID(marker.playerId);
            buf.writeDouble(marker.x);
            buf.writeDouble(marker.y);
            buf.writeDouble(marker.z);
            buf.writeFloat(marker.yRot);
        }
    }

    public static TeamMarkerSyncPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Marker> markers = new ArrayList<>(Math.min(count, 128));

        for (int i = 0; i < count; i++) {
            markers.add(new Marker(
                    buf.readUUID(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readFloat()
            ));
        }

        return new TeamMarkerSyncPacket(markers);
    }

    public static void handle(TeamMarkerSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientTeamMarkerCache.apply(msg));
        ctx.get().setPacketHandled(true);
    }
}
