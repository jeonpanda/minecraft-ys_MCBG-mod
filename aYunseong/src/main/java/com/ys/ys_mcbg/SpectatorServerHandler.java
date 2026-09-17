package com.ys.ys_mcbg;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 서버 측 커스텀 관전 시스템.
 *
 * 핵심 원칙
 * 1) 팀 판정은 항상 BgTeamSavedData만 사용한다.
 * 2) vanilla spectator의 자유 카메라보다 서버 판정을 우선한다.
 * 3) 서버 인원이 2명 이상이면 관전자는 자기 자신을 카메라로 둘 수 없다.
 * 4) 팀원이 살아있으면 반드시 같은 팀 생존자만 관전한다.
 * 5) 팀 전원이 사망하면 다른 생존자 전체를 관전한다.
 */
@Mod.EventBusSubscriber(modid = ysMcbgMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpectatorServerHandler {
    private static final int VALIDATION_INTERVAL_TICKS = 2;
    private static int validationCounter = 0;

    /** viewer UUID -> 현재 서버가 강제 중인 target UUID */
    private static final Map<UUID, UUID> LOCKED_TARGETS = new HashMap<>();

    private SpectatorServerHandler() {}

    @SubscribeEvent
    public static void onGameModeChanged(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        boolean entering = event.getNewGameMode() == GameType.SPECTATOR;
        boolean leaving = event.getCurrentGameMode() == GameType.SPECTATOR
                && event.getNewGameMode() != GameType.SPECTATOR;

        if (entering) {
            // spectator 진입 직후 vanilla 카메라가 자기 자신으로 남는 것을 허용하지 않는다.
            applyOrClearForcedCamera(player);
            player.displayClientMessage(Component.literal("관전 모드"), true);
        } else if (leaving) {
            LOCKED_TARGETS.remove(player.getUUID());
            player.setCamera(player);
            sendCameraLock(player, null);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        LOCKED_TARGETS.remove(player.getUUID());
        player.setCamera(player);
        sendCameraLock(player, null);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LOCKED_TARGETS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++validationCounter < VALIDATION_INTERVAL_TICKS) return;
        validationCounter = 0;

        MinecraftServer server = event.getServer();
        if (server == null) return;

        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        boolean soloServer = online.size() <= 1;

        for (ServerPlayer viewer : online) {
            if (viewer == null || !viewer.isSpectator()) {
                if (viewer != null) {
                    LOCKED_TARGETS.remove(viewer.getUUID());
                }
                continue;
            }

            // 서버에 자기 혼자만 있으면 vanilla free-spectator를 허용한다.
            if (soloServer) {
                if (viewer.getCamera() != viewer) {
                    viewer.setCamera(viewer);
                }
                sendCameraLockIfChanged(viewer, null);
                sendSpectatorInfo(viewer, null);
                continue;
            }

            applyOrClearForcedCamera(viewer);
        }
    }

    /**
     * 관전자는 서버 인원이 2명 이상이면 반드시 다른 플레이어를 카메라로 가진다.
     */
    private static void applyOrClearForcedCamera(ServerPlayer viewer) {
        if (viewer == null || !viewer.isSpectator()) return;

        MinecraftServer server = viewer.getServer();
        if (server == null) return;

        if (server.getPlayerList().getPlayers().size() <= 1) {
            LOCKED_TARGETS.remove(viewer.getUUID());
            sendCameraLockIfChanged(viewer, null);
            return;
        }

        Entity camera = viewer.getCamera();
        ServerPlayer current = camera instanceof ServerPlayer sp ? sp : null;

        // 현재 대상이 여전히 규칙상 허용된다면 그대로 유지한다.
        if (current != null && isAllowedTarget(viewer, current)) {
            LOCKED_TARGETS.put(viewer.getUUID(), current.getUUID());
            sendCameraLockIfChanged(viewer, current);
            sendSpectatorInfo(viewer, current);
            return;
        }

        // 대상이 없거나 vanilla가 자기 자신/다른 엔티티로 카메라를 돌렸다면 재지정한다.
        ServerPlayer next = getForcedTarget(viewer);
        if (next != null) {
            viewer.setCamera(next);
            LOCKED_TARGETS.put(viewer.getUUID(), next.getUUID());
            sendCameraLockIfChanged(viewer, next);
            sendSpectatorInfo(viewer, next);
        } else {
            // 이론상 2명 이상인데 target 자체가 전혀 없을 경우.
            // vanilla가 자유 비행으로 빠지지 않도록 자기 자신 고정.
            viewer.setCamera(viewer);
            LOCKED_TARGETS.remove(viewer.getUUID());
            sendCameraLockIfChanged(viewer, null);
            sendSpectatorInfo(viewer, null);
        }
    }

    public static void handleTargetRequest(ServerPlayer viewer, UUID targetId) {
        if (viewer == null || targetId == null || !viewer.isSpectator()) return;

        if (viewer.getServer().getPlayerList().getPlayers().size() <= 1) return;

        ServerPlayer target = viewer.getServer().getPlayerList().getPlayer(targetId);
        if (target == null || !isAllowedTarget(viewer, target)) {
            applyOrClearForcedCamera(viewer);
            return;
        }

        viewer.setCamera(target);
        LOCKED_TARGETS.put(viewer.getUUID(), target.getUUID());
        sendCameraLockIfChanged(viewer, target);
        sendSpectatorInfo(viewer, target);
    }

    /** 번호 1~9 요청을 현재 서버의 최신 허용 대상 목록으로 해석한다. */
    public static void handleTargetIndexRequest(ServerPlayer viewer, int index) {
        if (viewer == null || !viewer.isSpectator()) return;
        if (index < 0 || index > 8) return;
        if (viewer.getServer().getPlayerList().getPlayers().size() <= 1) return;

        List<ServerPlayer> targets = getAllowedTargets(viewer);
        if (index >= targets.size()) return;

        ServerPlayer target = targets.get(index);
        if (!isAllowedTarget(viewer, target)) return;

        viewer.setCamera(target);
        LOCKED_TARGETS.put(viewer.getUUID(), target.getUUID());
        sendCameraLockIfChanged(viewer, target);
        sendSpectatorInfo(viewer, target);
    }

    /** 서버가 직접 판정한 실제 관전 대상 목록. */
    public static List<ServerPlayer> getAllowedTargets(ServerPlayer viewer) {
        List<ServerPlayer> result = new ArrayList<>();
        if (viewer == null || !viewer.isSpectator()) return result;

        for (ServerPlayer player : viewer.getServer().getPlayerList().getPlayers()) {
            if (isAllowedTarget(viewer, player)) {
                result.add(player);
            }
        }

        result.sort(Comparator.comparing(
                p -> p.getGameProfile().getName(),
                String.CASE_INSENSITIVE_ORDER
        ));
        return result;
    }

    /**
     * 현재 규칙으로 골라낼 정상 대상.
     * 정상 대상이 하나도 없을 때만 안전용 fallback을 사용한다.
     */
    private static ServerPlayer getForcedTarget(ServerPlayer viewer) {
        List<ServerPlayer> allowed = getAllowedTargets(viewer);
        if (!allowed.isEmpty()) return allowed.get(0);

        // 서버가 2명 이상인데 생존자 자체가 없는 비정상/라운드 전환 순간에도
        // 자유 비행을 방치하지 않기 위해 다른 접속자를 임시 카메라로 묶는다.
        List<ServerPlayer> everyoneElse = new ArrayList<>();
        for (ServerPlayer player : viewer.getServer().getPlayerList().getPlayers()) {
            if (player == null || player == viewer) continue;
            everyoneElse.add(player);
        }

        everyoneElse.sort((a, b) -> {
            boolean aLiving = isLivingPlayer(a);
            boolean bLiving = isLivingPlayer(b);
            if (aLiving != bLiving) return aLiving ? -1 : 1;
            return String.CASE_INSENSITIVE_ORDER.compare(
                    a.getGameProfile().getName(), b.getGameProfile().getName());
        });

        return everyoneElse.isEmpty() ? null : everyoneElse.get(0);
    }

    /** 액션바에 서버 기준 팀/닉네임을 표시한다. */
    private static void sendSpectatorInfo(ServerPlayer viewer, ServerPlayer target) {
        if (viewer == null || !viewer.isSpectator()) return;

        String teamName = "-";
        String targetName = "없음";

        if (target != null && target != viewer) {
            BgTeamSavedData data = BgTeamSavedData.get(viewer.server);
            String team = data.getPlayerTeam().get(target.getUUID());
            if (team != null && !team.isBlank()) teamName = team;
            targetName = target.getGameProfile().getName();
        }

        viewer.displayClientMessage(
                Component.literal("현재 관전 중: [" + teamName + "] " + targetName),
                true
        );
    }

    private static void sendCameraLock(ServerPlayer viewer, ServerPlayer target) {
        ysMcbgMod.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> viewer),
                new SpectatorCameraLockPacket(target == null ? null : target.getUUID())
        );
    }

    private static void sendCameraLockIfChanged(ServerPlayer viewer, ServerPlayer target) {
        UUID old = LOCKED_TARGETS.get(viewer.getUUID());
        UUID now = target == null ? null : target.getUUID();
        if (old == null ? now == null : old.equals(now)) return;

        if (now == null) LOCKED_TARGETS.remove(viewer.getUUID());
        else LOCKED_TARGETS.put(viewer.getUUID(), now);
        sendCameraLock(viewer, target);
    }

    /** 서버 쪽에서 최종 검증하는 팀 기반 관전 규칙. */
    public static boolean isAllowedTarget(ServerPlayer viewer, ServerPlayer target) {
        if (viewer == null || target == null) return false;
        if (viewer == target) return false;
        if (!target.isAlive() || target.isSpectator()) return false;
        if (BgPlayerState.isDowned(target)) return false;
        if (viewer.serverLevel() != target.serverLevel()) return false;

        BgTeamSavedData data = BgTeamSavedData.get(viewer.server);
        String myTeam = data.getPlayerTeam().get(viewer.getUUID());
        if (myTeam == null || myTeam.isBlank()) {
            // 무소속 관전자: 생존자 전체.
            return true;
        }

        // 팀원이 단 한 명이라도 살아 있으면 무조건 같은 팀만 허용.
        boolean hasLivingTeammate = false;
        for (ServerPlayer player : viewer.getServer().getPlayerList().getPlayers()) {
            if (player == null || player == viewer) continue;
            if (!myTeam.equals(data.getPlayerTeam().get(player.getUUID()))) continue;
            if (isLivingPlayer(player)) {
                hasLivingTeammate = true;
                break;
            }
        }

        if (hasLivingTeammate) {
            return myTeam.equals(data.getPlayerTeam().get(target.getUUID()));
        }

        // 팀 전원 사망 -> 다른 생존자 전체.
        return isLivingPlayer(target);
    }

    public static boolean isLivingPlayer(ServerPlayer player) {
        return player != null
                && player.isAlive()
                && !player.isSpectator()
                && !BgPlayerState.isDowned(player);
    }
}
