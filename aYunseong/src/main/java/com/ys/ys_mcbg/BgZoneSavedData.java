package com.ys.ys_mcbg;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

public class BgZoneSavedData extends SavedData {
    public static final String NAME = "ys_mcbg_zone";

    public boolean running = false;

    // 시작 시각(서버 gameTime tick)
    public long startGameTime = 0L;

    // 시작 원 (PUBG 1페이즈 시작 원)
    public double startCx = 0.0;
    public double startCz = 0.0;
    public float startRadius = 500.0f; // 1000x1000

    // 페이즈 설정 (분)
    public int[] waitMinutes = new int[]{10, 15, 20, 25, 30};
    public int[] shrinkMinutes = new int[]{2, 2, 2, 2, 2};

    // 페이즈 끝(축소 완료 후) 반지름(원하는 값으로 조절 가능)
    // 5페이즈 끝은 0.1
    public float[] endRadius = new float[]{350f, 250f, 180f, 120f, 0.1f};

    // “페이즈 중심 이동”을 재현 가능하게 하려고 seed + 목표 중심들을 저장
    public long seed = 0L;
    public double[] endCx = new double[5];
    public double[] endCz = new double[5];

    // 공지/동기화용
    public int lastAnnouncedShrinkPhase = 0;
    public int lastSentPhase = 0;
    public int lastSentStage = -1; // 0 WAIT, 1 SHRINK

    public static BgZoneSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                BgZoneSavedData::load,
                BgZoneSavedData::new,
                NAME
        );
    }

    public long lastSentStageStartTick = -1L;


    /** 시작 or seed 변경 or 반지름 변경 시 호출: 각 페이즈 목표 중심(endCx/endCz)을 미리 만든다 */
    public void rebuildPlan() {
        RandomSource rnd = RandomSource.create(seed);

        double curX = startCx;
        double curZ = startCz;
        float curR = startRadius;

        for (int i = 0; i < 5; i++) {
            float nextR = endRadius[i];

            // 다음 원이 현재 원 안에 완전히 들어가려면 중심 이동 거리 <= (curR - nextR)
            double maxOffset = Math.max(0.0, (double)curR - (double)nextR);

            // 원판 내부 균등 분포
            double angle = rnd.nextDouble() * Math.PI * 2.0;
            double dist = Math.sqrt(rnd.nextDouble()) * maxOffset;

            double nx = curX + Math.cos(angle) * dist;
            double nz = curZ + Math.sin(angle) * dist;

            endCx[i] = nx;
            endCz[i] = nz;

            // 다음 단계의 “현재 원” 갱신
            curX = nx;
            curZ = nz;
            curR = nextR;
        }

        setDirty();
    }

    // ================== 저장/로드 ==================
    public static BgZoneSavedData load(CompoundTag tag) {
        BgZoneSavedData d = new BgZoneSavedData();

        d.lastSentStageStartTick = tag.getLong("lastSentStageStartTick");

        d.running = tag.getBoolean("running");
        d.startGameTime = tag.getLong("startGameTime");

        d.startCx = tag.getDouble("startCx");
        d.startCz = tag.getDouble("startCz");
        d.startRadius = tag.getFloat("startRadius");

        d.seed = tag.getLong("seed");

        d.lastAnnouncedShrinkPhase = tag.getInt("lastAnnouncedShrinkPhase");
        d.lastSentPhase = tag.getInt("lastSentPhase");
        d.lastSentStage = tag.getInt("lastSentStage");

        // 배열들
        for (int i = 0; i < 5; i++) {
            d.waitMinutes[i] = tag.getInt("waitM" + i);
            d.shrinkMinutes[i] = tag.getInt("shrinkM" + i);
            d.endRadius[i] = tag.getFloat("endR" + i);
            d.endCx[i] = tag.getDouble("endCx" + i);
            d.endCz[i] = tag.getDouble("endCz" + i);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("lastSentStageStartTick", lastSentStageStartTick);

        tag.putBoolean("running", running);
        tag.putLong("startGameTime", startGameTime);

        tag.putDouble("startCx", startCx);
        tag.putDouble("startCz", startCz);
        tag.putFloat("startRadius", startRadius);

        tag.putLong("seed", seed);

        tag.putInt("lastAnnouncedShrinkPhase", lastAnnouncedShrinkPhase);
        tag.putInt("lastSentPhase", lastSentPhase);
        tag.putInt("lastSentStage", lastSentStage);

        for (int i = 0; i < 5; i++) {
            tag.putInt("waitM" + i, waitMinutes[i]);
            tag.putInt("shrinkM" + i, shrinkMinutes[i]);
            tag.putFloat("endR" + i, endRadius[i]);
            tag.putDouble("endCx" + i, endCx[i]);
            tag.putDouble("endCz" + i, endCz[i]);
        }
        return tag;
    }
}
