package com.ys.ys_mcbg;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> Client: current zone stage info.
 * Client uses this for:
 * - HUD timers
 * - Border render interpolation
 */
public class ZoneScheduleSyncPacket {

    public boolean running;
    public int phase;                 // 1..5
    public int stage;                 // WAIT/SHRINK
    public long stageStartTick;       // absolute gameTime
    public int stageDurationTicks;    // ticks

    public int phaseWaitTicks;        // ticks
    public int phaseShrinkTicks;      // ticks

    public double fromX, fromZ, toX, toZ;
    public float fromR, toR;

    public ZoneScheduleSyncPacket() {}

    public ZoneScheduleSyncPacket(ZoneRuntime rt) {
        this.running = rt.running;
        this.phase = rt.phase;
        this.stage = rt.stage;
        this.stageStartTick = rt.stageStartTick;
        this.stageDurationTicks = rt.stageDurationTicks;
        this.phaseWaitTicks = rt.phaseWaitTicks;
        this.phaseShrinkTicks = rt.phaseShrinkTicks;

        this.fromX = rt.fromX;
        this.fromZ = rt.fromZ;
        this.toX = rt.toX;
        this.toZ = rt.toZ;
        this.fromR = rt.fromR;
        this.toR = rt.toR;
    }

    public static void encode(ZoneScheduleSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.running);
        buf.writeInt(msg.phase);
        buf.writeInt(msg.stage);
        buf.writeLong(msg.stageStartTick);
        buf.writeInt(msg.stageDurationTicks);
        buf.writeInt(msg.phaseWaitTicks);
        buf.writeInt(msg.phaseShrinkTicks);

        buf.writeDouble(msg.fromX);
        buf.writeDouble(msg.fromZ);
        buf.writeDouble(msg.toX);
        buf.writeDouble(msg.toZ);
        buf.writeFloat(msg.fromR);
        buf.writeFloat(msg.toR);
    }

    public static ZoneScheduleSyncPacket decode(FriendlyByteBuf buf) {
        ZoneScheduleSyncPacket msg = new ZoneScheduleSyncPacket();
        msg.running = buf.readBoolean();
        msg.phase = buf.readInt();
        msg.stage = buf.readInt();
        msg.stageStartTick = buf.readLong();
        msg.stageDurationTicks = buf.readInt();
        msg.phaseWaitTicks = buf.readInt();
        msg.phaseShrinkTicks = buf.readInt();

        msg.fromX = buf.readDouble();
        msg.fromZ = buf.readDouble();
        msg.toX = buf.readDouble();
        msg.toZ = buf.readDouble();
        msg.fromR = buf.readFloat();
        msg.toR = buf.readFloat();
        return msg;
    }

    public static void handle(ZoneScheduleSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientZoneCache.apply(msg));
        ctx.get().setPacketHandled(true);
    }
}
