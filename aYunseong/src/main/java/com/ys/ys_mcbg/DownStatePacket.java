package com.ys.ys_mcbg;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class DownStatePacket {
    public final UUID playerId;
    public final boolean downed;

    public DownStatePacket(UUID playerId, boolean downed) {
        this.playerId = playerId;
        this.downed = downed;
    }

    public static void encode(DownStatePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerId);
        buf.writeBoolean(msg.downed);
    }

    public static DownStatePacket decode(FriendlyByteBuf buf) {
        return new DownStatePacket(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(DownStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientDownedCache.set(msg.playerId, msg.downed));
        ctx.get().setPacketHandled(true);
    }
}
