package com.ys.ys_mcbg;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MapDataPartPacket {
    public int mapSize;
    public int partIndex;
    public int totalParts;
    public long worldSeed;
    public byte[] payload;

    public MapDataPartPacket(int mapSize, int partIndex, int totalParts, long worldSeed, byte[] payload) {
        this.mapSize = mapSize;
        this.partIndex = partIndex;
        this.totalParts = totalParts;
        this.worldSeed = worldSeed;
        this.payload = payload;
    }

    public static void encode(MapDataPartPacket p, FriendlyByteBuf buf) {
        buf.writeInt(p.mapSize);
        buf.writeInt(p.partIndex);
        buf.writeInt(p.totalParts);
        buf.writeLong(p.worldSeed);
        buf.writeInt(p.payload.length);
        buf.writeByteArray(p.payload);
    }

    public static MapDataPartPacket decode(FriendlyByteBuf buf) {
        int mapSize = buf.readInt();
        int partIndex = buf.readInt();
        int totalParts = buf.readInt();
        long seed = buf.readLong();
        int len = buf.readInt();
        byte[] payload = buf.readByteArray(len);
        return new MapDataPartPacket(mapSize, partIndex, totalParts, seed, payload);
    }

    public static void handle(MapDataPartPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientMapCache.acceptPart(p));
        ctx.get().setPacketHandled(true);
    }
}
