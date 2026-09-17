package com.ys.ys_mcbg;

/** Client-side cache for revive ring UI. */
public class ClientReviveProgressCache {
    private static boolean active = false;
    private static int remainingTicks = 0;
    private static int totalTicks = 0;

    public static void apply(ReviveProgressPacket p) {
        active = p.active;
        remainingTicks = Math.max(0, p.remainingTicks);
        totalTicks = Math.max(0, p.totalTicks);
        if (!active) {
            remainingTicks = 0;
            totalTicks = 0;
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static int getRemainingTicks() {
        return remainingTicks;
    }

    public static int getTotalTicks() {
        return totalTicks;
    }

    /** 0..1 progress */
    public static float getProgress01() {
        if (!active || totalTicks <= 0) return 0f;
        float done = (float)(totalTicks - remainingTicks);
        return Math.min(1f, Math.max(0f, done / (float)totalTicks));
    }
}
