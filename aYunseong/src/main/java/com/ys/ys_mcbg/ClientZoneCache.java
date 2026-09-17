package com.ys.ys_mcbg;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;

/**
 * Client-side cache for current zone runtime info.
 * Updated via ZoneScheduleSyncPacket.
 */
public class ClientZoneCache {
    public static boolean running;

    public static int phase;
    public static int stage;
    public static long stageStartTick;
    public static int stageDurationTicks;

    public static int phaseWaitTicks;
    public static int phaseShrinkTicks;

    public static double fromX, fromZ, toX, toZ;
    public static float fromR, toR;

    public static void apply(ZoneScheduleSyncPacket p) {
        running = p.running;
        phase = p.phase;
        stage = p.stage;
        stageStartTick = p.stageStartTick;
        stageDurationTicks = p.stageDurationTicks;

        phaseWaitTicks = p.phaseWaitTicks;
        phaseShrinkTicks = p.phaseShrinkTicks;

        fromX = p.fromX; fromZ = p.fromZ; fromR = p.fromR;
        toX = p.toX; toZ = p.toZ; toR = p.toR;
    }

    /** Current-frame “current circle(center/radius)” + HUD timers (ticks) */
    public static Sample sample() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (mc.level == null) return new Sample();

        Sample s = new Sample();
        s.running = running;
        if (!running) return s;

        long now = level.getGameTime();
        long stageEnd = stageStartTick + (long) stageDurationTicks;
        long remainStage = Math.max(0L, stageEnd - now);

        float p = stageDurationTicks <= 0 ? 1f : (float) (now - stageStartTick) / (float) stageDurationTicks;
        p = Mth.clamp(p, 0f, 1f);

        if (stage == ZoneRuntime.STAGE_SHRINK) {
            // Lerp between from -> to
            s.cx = Mth.lerp(p, fromX, toX);
            s.cz = Mth.lerp(p, fromZ, toZ);
            s.r = Mth.lerp(p, fromR, toR);

            // ✅ SHRINK 중: 다음 자기장(=축소 끝)까지 카운트
            s.nextZoneTicks = remainStage;
            s.shrinkLeftTicks = 0;
        } else {
            // WAIT: stay at from
            s.cx = fromX;
            s.cz = fromZ;
            s.r = fromR;

            // ✅ WAIT 중: 이번 자기장 축소(=시작)까지
            s.shrinkLeftTicks = remainStage;

            // ✅ WAIT 중: 다음 자기장(=축소 끝)까지 = (대기 남은 시간 + 축소 시간)
            s.nextZoneTicks = remainStage + (long) phaseShrinkTicks;
        }

        return s;
    }

    public static class Sample {
        public boolean running;
        public double cx, cz;
        public float r;
        public long nextZoneTicks;      // "다음 자기장까지" (축소 끝까지)
        public long shrinkLeftTicks;    // "이번 자기장 축소까지" (축소 시작까지)
    }
}
