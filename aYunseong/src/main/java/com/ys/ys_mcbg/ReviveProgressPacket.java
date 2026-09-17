package com.ys.ys_mcbg;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> Client: show/hide revive progress ring (center HUD).
 * Sent to BOTH the reviver and the downed player.
 */
public class ReviveProgressPacket {
    public final boolean active;
    public final int remainingTicks;
    public final int totalTicks;

    public ReviveProgressPacket(boolean active, int remainingTicks, int totalTicks) {
        this.active = active;
        this.remainingTicks = remainingTicks;
        this.totalTicks = totalTicks;
    }

    public static void encode(ReviveProgressPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.active);
        buf.writeVarInt(p.remainingTicks);
        buf.writeVarInt(p.totalTicks);
    }

    public static ReviveProgressPacket decode(FriendlyByteBuf buf) {
        boolean active = buf.readBoolean();
        int remain = buf.readVarInt();
        int total = buf.readVarInt();
        return new ReviveProgressPacket(active, remain, total);
    }

    public static void handle(ReviveProgressPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            ClientReviveProgressCache.apply(p);
        }));
        ctx.get().setPacketHandled(true);
    }
}
