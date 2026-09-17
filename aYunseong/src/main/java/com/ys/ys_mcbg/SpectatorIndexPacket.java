package com.ys.ys_mcbg;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> Server: 관전 대상 번호(0~8) 요청.
 * 실제 대상 목록/팀 판정은 서버가 직접 계산한다.
 */
public class SpectatorIndexPacket {
    public final int index;

    public SpectatorIndexPacket(int index) {
        this.index = index;
    }

    public SpectatorIndexPacket(FriendlyByteBuf buf) {
        this.index = buf.readVarInt();
    }

    public static void encode(SpectatorIndexPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.index);
    }

    public static void handle(SpectatorIndexPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                SpectatorServerHandler.handleTargetIndexRequest(player, msg.index);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
