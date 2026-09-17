package com.ys.ys_mcbg;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client -> Server: 관전 대상 변경 요청. 서버에서 대상 유효성을 반드시 재검증한다. */
public class SpectatorTargetPacket {
    private final UUID targetId;

    public SpectatorTargetPacket(UUID targetId) {
        this.targetId = targetId;
    }

    public static void encode(SpectatorTargetPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.targetId);
    }

    public static SpectatorTargetPacket decode(FriendlyByteBuf buf) {
        return new SpectatorTargetPacket(buf.readUUID());
    }

    public static void handle(SpectatorTargetPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> {
            if (sender == null || sender.server == null) return;
            SpectatorServerHandler.handleTargetRequest(sender, msg.targetId);
        });
        context.setPacketHandled(true);
    }
}
