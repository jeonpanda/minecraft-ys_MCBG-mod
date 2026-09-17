package com.ys.ys_mcbg;

import net.minecraft.util.Mth;

/**
 * Derived (runtime) zone state for a given gameTime:
 * - phase: 1..5
 * - stage: WAIT or SHRINK
 * - from/to: current stage lerp endpoints (center + radius)
 * - curX/curZ/curR: current (lerped) zone for server-side damage + vanilla border
 *
 * NOTE: All time values are in ticks (20 ticks = 1 second).
 */
public class ZoneRuntime {
    public static final int STAGE_WAIT = 0;
    public static final int STAGE_SHRINK = 1;

    public boolean running;
    public int phase;   // 1..5
    public int stage;   // WAIT/SHRINK

    /** Absolute gameTime tick when the current stage started */
    public long stageStartTick;

    /** Duration of the current stage in ticks */
    public int stageDurationTicks;

    /** Total configured wait ticks of this phase */
    public int phaseWaitTicks;

    /** Total configured shrink ticks of this phase */
    public int phaseShrinkTicks;

    // For client interpolation + render
    public double fromX, fromZ, toX, toZ;
    public float fromR, toR;

    // Current (server-side computed)
    public double curX, curZ;
    public float curR;

    public static ZoneRuntime compute(BgZoneSavedData data, long nowGameTime) {
        ZoneRuntime rt = new ZoneRuntime();
        rt.running = data.running;
        if (!data.running) return rt;

        long elapsed = Math.max(0L, nowGameTime - data.startGameTime);

        // Timeline cursor: ticks from startGameTime
        int cursor = 0;

        // "Previous" circle (start circle first)
        double prevX = data.startCx;
        double prevZ = data.startCz;
        float prevR = data.startRadius;

        for (int i = 0; i < 5; i++) {
            int waitDur = data.waitMinutes[i] * 1200;
            int shrinkDur = data.shrinkMinutes[i] * 1200;

            double nextX = data.endCx[i];
            double nextZ = data.endCz[i];
            float nextR = data.endRadius[i];

            // WAIT
            if (elapsed < cursor + (long) waitDur) {
                rt.phase = i + 1;
                rt.stage = STAGE_WAIT;
                rt.stageStartTick = data.startGameTime + cursor;
                rt.stageDurationTicks = waitDur;
                rt.phaseWaitTicks = waitDur;
                rt.phaseShrinkTicks = shrinkDur;

                // 현재 원은 prev 유지
                rt.fromX = prevX; rt.fromZ = prevZ; rt.fromR = prevR;
                rt.curX  = prevX; rt.curZ  = prevZ; rt.curR  = prevR;

                // ✅ 다음(축소 끝) 목표 원은 next로
                rt.toX = nextX; rt.toZ = nextZ; rt.toR = nextR;

                return rt;
            }
            cursor += waitDur;

            // SHRINK
            if (elapsed < cursor + (long) shrinkDur) {
                rt.phase = i + 1;
                rt.stage = STAGE_SHRINK;
                rt.stageStartTick = data.startGameTime + cursor;
                rt.stageDurationTicks = shrinkDur;
                rt.phaseWaitTicks = waitDur;
                rt.phaseShrinkTicks = shrinkDur;

                // 축소 시작/끝
                rt.fromX = prevX; rt.fromZ = prevZ; rt.fromR = prevR;
                rt.toX   = nextX; rt.toZ   = nextZ; rt.toR   = nextR;

                // ✅ 진행률 p (0..1)
                float p = shrinkDur <= 0 ? 1.0f : (float) ((elapsed - cursor) / (double) shrinkDur);
                p = Mth.clamp(p, 0f, 1f);

                // ✅ 현재 원 lerp (서버 데미지/클라 렌더 공통 기준)
                rt.curX = Mth.lerp(p, prevX, nextX);
                rt.curZ = Mth.lerp(p, prevZ, nextZ);
                rt.curR = Mth.lerp(p, prevR, nextR);

                return rt;
            }
            cursor += shrinkDur;

            // Move to next circle for next phase
            prevX = nextX;
            prevZ = nextZ;
            prevR = nextR;
        }

        // After all phases, stay on final circle
        rt.phase = 5;
        rt.stage = STAGE_WAIT;
        rt.stageStartTick = data.startGameTime + cursor;
        rt.stageDurationTicks = 0;
        rt.phaseWaitTicks = 0;
        rt.phaseShrinkTicks = 0;

        rt.fromX = prevX; rt.fromZ = prevZ; rt.fromR = prevR;
        rt.toX = prevX; rt.toZ = prevZ; rt.toR = prevR;
        rt.curX = prevX; rt.curZ = prevZ; rt.curR = prevR;
        return rt;
    }
}
