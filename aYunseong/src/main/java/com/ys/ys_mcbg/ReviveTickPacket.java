package com.ys.ys_mcbg;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ReviveTickPacket {
    public final UUID targetId;

    public ReviveTickPacket(UUID targetId) {
        this.targetId = targetId;
    }

    public static void encode(ReviveTickPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.targetId);
    }

    public static ReviveTickPacket decode(FriendlyByteBuf buf) {
        return new ReviveTickPacket(buf.readUUID());
    }

    public static void handle(ReviveTickPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer reviver = ctx.get().getSender();
            if (reviver == null) return;

            var target = reviver.level().getPlayerByUUID(msg.targetId);
            if (!(target instanceof ServerPlayer downed)) return;

            if (!BgPlayerState.isDowned(downed)) return;
            if (BgPlayerState.isDowned(reviver)) return;

            // 팀 체크(서버)
            if (!BgTeamManager.sameTeam(reviver, downed)) return;

            // 거리 제한
            if (reviver.distanceTo(downed) > 2.3f) return;

            // reviver 변경되면 진행 리셋 + 이전 reviver HUD 끄기
            UUID cur = BgPlayerState.getReviver(downed);
            if (cur == null || !cur.equals(reviver.getUUID())) {
                if (cur != null && downed.getServer() != null) {
                    ServerPlayer old = downed.getServer().getPlayerList().getPlayer(cur);
                    if (old != null) ysMcbgMod.sendReviveProgress(old, false, 0, 0);
                }
                BgPlayerState.setReviver(downed, reviver.getUUID());
                BgPlayerState.setReviveProgress(downed, 0);
            }

            // "소생 중" 표시/타이머 정지용(서버 틱에서 이 값을 보고 bleed 정지)
            BgPlayerState.setLastReviveTick(downed, downed.level().getGameTime());

            // 소생 진행
            final int REQUIRED = 200; // 10초
            int prog = BgPlayerState.getReviveProgress(downed) + 1;
            if (prog > REQUIRED) prog = REQUIRED;
            BgPlayerState.setReviveProgress(downed, prog);

            int remain = Math.max(0, REQUIRED - prog);

            // ✅ HUD(원형 링 + 시간) : 살리는 사람 + 살아나는 사람 둘 다
            ysMcbgMod.sendReviveProgress(reviver, true, remain, REQUIRED);
            ysMcbgMod.sendReviveProgress(downed, true, remain, REQUIRED);

            if (prog >= REQUIRED) {
                // 소생 완료: 다운 해제 + 즉시 체력 10 + 상태/속성 정리 + 클라이언트 동기화
                ysMcbgMod.clearDowned(downed, true);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
