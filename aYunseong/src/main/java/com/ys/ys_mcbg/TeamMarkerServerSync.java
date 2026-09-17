package com.ys.ys_mcbg;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends far teammate positions so the client-side indicator remains usable
 * up to 600 blocks even when the normal player entity is no longer tracked.
 */
@Mod.EventBusSubscriber(modid = ysMcbgMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TeamMarkerServerSync {
    private static final double MAX_DISTANCE_SQR = 600.0D * 600.0D;
    private static final int UPDATE_EVERY_TICKS = 2;

    private static long tickCounter;

    private TeamMarkerServerSync() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickCounter++;
        if (tickCounter % UPDATE_EVERY_TICKS != 0) return;

        MinecraftServer server = event.getServer();
        if (server == null) return;

        BgTeamSavedData teamData = BgTeamSavedData.get(server);
        List<ServerPlayer> players = server.getPlayerList().getPlayers();

        for (ServerPlayer viewer : players) {
            String myTeam = teamData.getPlayerTeam().get(viewer.getUUID());
            List<TeamMarkerSyncPacket.Marker> markers = new ArrayList<>();

            if (myTeam != null && viewer.level() != null) {
                for (ServerPlayer target : players) {
                    if (target == viewer) continue;
                    if (!target.isAlive() || target.isSpectator()) continue;
                    if (target.level() != viewer.level()) continue;

                    String targetTeam = teamData.getPlayerTeam().get(target.getUUID());
                    if (!myTeam.equals(targetTeam)) continue;
                    if (target.distanceToSqr(viewer) > MAX_DISTANCE_SQR) continue;

                    markers.add(new TeamMarkerSyncPacket.Marker(
                            target.getUUID(),
                            target.getX(),
                            target.getY(),
                            target.getZ(),
                            target.getYRot()
                    ));
                }
            }

            ysMcbgMod.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> viewer),
                    new TeamMarkerSyncPacket(markers)
            );
        }
    }
}
