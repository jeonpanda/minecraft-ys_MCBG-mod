package com.ys.ys_mcbg;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server -> Client: 현재 관전 시스템이 강제로 유지해야 하는 카메라 대상.
 * null(UUID 없음)이면 1인 서버 등에서 커스텀 카메라 고정을 해제한다.
 */
public class SpectatorCameraLockPacket {
    private final boolean hasTarget;
    private final UUID targetId;

    public SpectatorCameraLockPacket(UUID targetId) {
        this.hasTarget = targetId != null;
        this.targetId = targetId;
    }

    public SpectatorCameraLockPacket(FriendlyByteBuf buf) {
        this.hasTarget = buf.readBoolean();
        this.targetId = hasTarget ? buf.readUUID() : null;
    }

    public static void encode(SpectatorCameraLockPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.hasTarget);
        if (msg.hasTarget) buf.writeUUID(msg.targetId);
    }

    public static SpectatorCameraLockPacket decode(FriendlyByteBuf buf) {
        return new SpectatorCameraLockPacket(buf);
    }

    public static void handle(SpectatorCameraLockPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                SpectatorClientHandler.setLockedTarget(msg.hasTarget ? msg.targetId : null)
        ));
        ctx.get().setPacketHandled(true);
    }
}
