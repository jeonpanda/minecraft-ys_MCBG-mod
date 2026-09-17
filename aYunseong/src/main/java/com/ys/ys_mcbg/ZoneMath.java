package com.ys.ys_mcbg;

public class ZoneMath {

    public static long totalTicks(int[] phaseMinutes) {
        long sum = 0;
        for (int m : phaseMinutes) sum += m * 60L * 20L;
        return sum;
    }

    // elapsedTicks가 현재 몇 페이즈인지(1~5). 전체 끝나면 5.
    public static int computePhaseIndex(int[] phaseMinutes, long elapsedTicks) {
        long t = elapsedTicks;
        for (int i = 0; i < 5; i++) {
            long dur = phaseMinutes[i] * 60L * 20L;
            if (t < dur) return i + 1;
            t -= dur;
        }
        return 5;
    }

    // startRadius -> finalRadius를 5페이즈 동안 기하감소로 진행
    public static float computeRadius(float startRadius, float finalRadius, int[] phaseMinutes, long elapsedTicks) {
        long t = elapsedTicks;

        float r0 = startRadius;

        for (int i = 0; i < 5; i++) {
            long dur = phaseMinutes[i] * 60L * 20L;
            float r1 = phaseEndRadius(startRadius, finalRadius, i + 1);

            if (t <= dur) {
                float alpha = dur <= 0 ? 1f : (float) t / (float) dur;
                return lerp(r0, r1, clamp01(alpha));
            }

            t -= dur;
            r0 = r1;
        }

        return finalRadius;
    }

    // i=1..5
    private static float phaseEndRadius(float startR, float finalR, int i) {
        // r_i = start * (final/start)^(i/5)
        double ratio = (finalR / startR);
        double exp = (double) i / 5.0;
        return (float) (startR * Math.pow(ratio, exp));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float t) {
        return Math.max(0f, Math.min(1f, t));
    }
}
