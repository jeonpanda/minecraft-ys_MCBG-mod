package com.ys.ys_mcbg;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;


import java.util.function.Supplier;

public class BoostSyncPacket {
    private final float boost;

    public BoostSyncPacket(float boost) {
        this.boost = boost;
    }

    public BoostSyncPacket(FriendlyByteBuf buf) {
        this.boost = buf.readFloat();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeFloat(boost);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientBoostCache.boost = boost;
            });
        });
        ctx.setPacketHandled(true);
        return true;
    }

}
