package com.ys.ys_mcbg;

import net.minecraft.util.Mth;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Smoothed client-side cache for far teammate markers.
 *
 * A small interpolation delay intentionally removes the visible jitter that
 * happened when a live PlayerRenderer was rendered a second time every frame.
 */
public final class ClientTeamMarkerCache {
    public static final class MarkerState {
        public final UUID playerId;
        private double prevX, prevY, prevZ;
        private double curX, curY, curZ;
        private float prevYaw, curYaw;
        private long receivedAtNanos;

        private MarkerState(TeamMarkerSyncPacket.Marker marker, long now) {
            this.playerId = marker.playerId;
            this.prevX = this.curX = marker.x;
            this.prevY = this.curY = marker.y;
            this.prevZ = this.curZ = marker.z;
            this.prevYaw = this.curYaw = marker.yRot;
            this.receivedAtNanos = now;
        }

        private void update(TeamMarkerSyncPacket.Marker marker, long now) {
            this.prevX = this.curX;
            this.prevY = this.curY;
            this.prevZ = this.curZ;
            this.prevYaw = this.curYaw;
            this.curX = marker.x;
            this.curY = marker.y;
            this.curZ = marker.z;
            this.curYaw = marker.yRot;
            this.receivedAtNanos = now;
        }

        public double x() {
            return Mth.lerp(interpolation(), prevX, curX);
        }

        public double y() {
            return Mth.lerp(interpolation(), prevY, curY);
        }

        public double z() {
            return Mth.lerp(interpolation(), prevZ, curZ);
        }

        public float yaw() {
            return Mth.rotLerp((float) interpolation(), prevYaw, curYaw);
        }

        private float interpolation() {
            long age = System.nanoTime() - receivedAtNanos;

            // 100 ms interpolation window. Team packets are sent every 2 ticks.
            double t = age / 100_000_000.0D;
            if (t < 0.0D) t = 0.0D;
            if (t > 1.0D) t = 1.0D;
            return (float) t;
        }
    }

    private static final Map<UUID, MarkerState> MARKERS = new HashMap<>();

    private ClientTeamMarkerCache() {}

    public static void apply(TeamMarkerSyncPacket packet) {
        long now = System.nanoTime();

        Map<UUID, TeamMarkerSyncPacket.Marker> incoming = new HashMap<>();
        for (TeamMarkerSyncPacket.Marker marker : packet.markers) {
            incoming.put(marker.playerId, marker);
        }

        MARKERS.keySet().removeIf(uuid -> !incoming.containsKey(uuid));
        for (TeamMarkerSyncPacket.Marker marker : packet.markers) {
            MarkerState existing = MARKERS.get(marker.playerId);
            if (existing == null) {
                MARKERS.put(marker.playerId, new MarkerState(marker, now));
            } else {
                existing.update(marker, now);
            }
        }
    }

    public static void clear() {
        MARKERS.clear();
    }

    public static MarkerState get(UUID id) {
        return MARKERS.get(id);
    }

    public static Iterable<MarkerState> values() {
        return Collections.unmodifiableCollection(MARKERS.values());
    }
}
