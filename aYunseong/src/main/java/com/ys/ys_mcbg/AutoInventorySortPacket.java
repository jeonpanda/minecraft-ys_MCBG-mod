package com.ys.ys_mcbg;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AutoInventorySortPacket {
    public AutoInventorySortPacket() {}

    public AutoInventorySortPacket(FriendlyByteBuf ignored) {}

    public void toBytes(FriendlyByteBuf ignored) {}

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                AutoInventorySorter.sort(player);
            }
        });
        context.setPacketHandled(true);
        return true;
    }
}
