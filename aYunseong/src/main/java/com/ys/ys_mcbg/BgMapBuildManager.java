package com.ys.ys_mcbg;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.world.level.block.*;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.zip.Deflater;

import static com.ys.ys_mcbg.BgMapConstants.*;

/**
 * 서버에서 800x800 고정 영역(0,0 중심)을 한 번 굽고,
 * 2차 패스에서 height 기반 hillshade + 물깊이(대충) 기반 어둡게를 적용.
 */
public class BgMapBuildManager {

    private static final int CHUNKS_PER_TICK = 8;
    private static final int PART_SIZE = 32000;

    // hillshade 강도 (0..1)
    private static final float SHADE_STRENGTH = 0.85f;

    // “빛” 방향(북서쪽에서 아래로 비춘다)
    private static final float Lx = -0.60f;
    private static final float Ly =  0.80f;
    private static final float Lz = -0.60f;

    // 물깊이 어두워지는 강도
    private static final float WATER_DARK_PER_BLOCK = 0.030f; // 1블록 깊어질 때 3% 어두워짐
    private static final int   WATER_DEPTH_SAMPLE_MAX = 24;

    private static boolean shouldIgnoreBlock(BlockState state) {
        if (state.isAir()) return true;

        Block block = state.getBlock();

        // 1. 베리어 및 구조용 투명 블록 무시
        if (block == Blocks.BARRIER || block == Blocks.LIGHT) {
            return true;
        }

        // 2. 일반 유리, 색유리, 유리판 계열 전체 무시
        if (block instanceof GlassBlock ||
                block instanceof StainedGlassBlock ||
                block instanceof StainedGlassPaneBlock ||
                block == Blocks.GLASS_PANE ||
                block == Blocks.GLASS) {
            return true;
        }

        return false;
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (e.level.isClientSide()) return;
        if (!(e.level instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;

        BgMapSavedData data = BgMapSavedData.get(level);

        if (!data.built && !data.building) data.beginBuild();
        if (!data.building) return;

        int totalChunks = CHUNKS * CHUNKS;
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        for (int n = 0; n < CHUNKS_PER_TICK && data.buildChunkIndex < totalChunks; n++) {
            int idx = data.buildChunkIndex++;

            int cx = (idx % CHUNKS) + MIN_CHUNK;
            int cz = (idx / CHUNKS) + MIN_CHUNK;

            // 강제로 로드
            level.getChunk(cx, cz);

            int baseX = cx << 4;
            int baseZ = cz << 4;

            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int wx = baseX + lx;
                    int wz = baseZ + lz;

                    int mx = wx + HALF;
                    int mz = wz + HALF;
                    if (mx < 0 || mx >= MAP_SIZE || mz < 0 || mz >= MAP_SIZE) continue;

                    int ySurface = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
                    int minY = level.getMinBuildHeight();

                    BlockState st = Blocks.AIR.defaultBlockState();
                    MapColor mapColor = MapColor.NONE;
                    int targetY = ySurface;

                    // Y축을 위에서 아래로 탐색하며 유리/배리어/공기 건너뛰기
                    for (int y = ySurface; y >= minY; y--) {
                        mpos.set(wx, y, wz);
                        BlockState state = level.getBlockState(mpos);

                        if (shouldIgnoreBlock(state)) {
                            continue;
                        }

                        MapColor mc = state.getMapColor(level, mpos);
                        if (mc != null && mc != MapColor.NONE) {
                            st = state;
                            mapColor = mc;
                            targetY = y;
                            break;
                        }
                    }

                    int col = mapColor.col; // 0xRRGGBB

                    int pixIdx = (mz * MAP_SIZE + mx);
                    int o = pixIdx * 3;
                    data.rgb[o]     = (byte) ((col >> 16) & 0xFF);
                    data.rgb[o + 1] = (byte) ((col >> 8) & 0xFF);
                    data.rgb[o + 2] = (byte) (col & 0xFF);

                    // 높이 저장 (유리/배리어가 제외된 실제 블록의 높이)
                    data.setHeightAt(pixIdx, targetY + 1);

                    // 물깊이(대충) 저장
                    if (st.getFluidState().is(FluidTags.WATER)) {
                        int depth = 0;
                        for (int d = 0; d < WATER_DEPTH_SAMPLE_MAX; d++) {
                            mpos.set(wx, targetY - d, wz);
                            BlockState s = level.getBlockState(mpos);
                            if (!s.getFluidState().is(FluidTags.WATER)) break;
                            depth++;
                        }
                        data.setWaterDepthAt(pixIdx, depth);
                    } else {
                        data.setWaterDepthAt(pixIdx, 0);
                    }
                }
            }
        }

        data.setDirty();

        if (data.buildChunkIndex >= totalChunks) {
            // 2차 패스: hillshade + water depth shading
            applyHillshade(level, data);
            data.finishBuild();
            sendToAll(level.getServer(), data);
        }
    }

    /**
     * 제대로 “높아보임/낮아보임”이 나오게 normal-dot 방식으로 음영을 만든다.
     */
    private static void applyHillshade(ServerLevel level, BgMapSavedData data) {
        // light normalize
        float ll = (float) Math.sqrt(Lx*Lx + Ly*Ly + Lz*Lz);
        float lx = Lx / ll, ly = Ly / ll, lz = Lz / ll;

        // 경계는 이웃 샘플이 없으니 패스
        for (int z = 1; z < MAP_SIZE - 1; z++) {
            for (int x = 1; x < MAP_SIZE - 1; x++) {
                int idx = z * MAP_SIZE + x;

                int hL = data.getHeightAt(idx - 1);
                int hR = data.getHeightAt(idx + 1);
                int hU = data.getHeightAt(idx - MAP_SIZE);
                int hD = data.getHeightAt(idx + MAP_SIZE);

                float dx = (hR - hL);
                float dz = (hD - hU);

                // normal ≈ normalize((-dx, k, -dz))
                float nx = -dx;
                float ny =  8.0f; // 세로 스케일(클수록 음영이 부드러워짐)
                float nz = -dz;
                float invLen = (float) (1.0 / Math.sqrt(nx*nx + ny*ny + nz*nz));
                nx *= invLen; ny *= invLen; nz *= invLen;

                float dot = nx*lx + ny*ly + nz*lz; // -1..1
                if (dot < -1f) dot = -1f;
                if (dot >  1f) dot =  1f;

                // 0..1 밝기
                float shade = 0.55f + 0.45f * dot;

                // 최종 factor
                float factor = (1.0f - SHADE_STRENGTH) + (SHADE_STRENGTH * shade);

                // 경사 큰 곳은 살짝 더 어둡게(입체감)
                float slope = (float) Math.sqrt(dx*dx + dz*dz);
                factor *= (1.0f - Math.min(0.18f, slope * 0.018f));

                // 물깊이 어둡게
                int depth = data.getWaterDepthAt(idx);
                if (depth > 0) {
                    float waterFactor = 1.0f - Math.min(0.55f, depth * WATER_DARK_PER_BLOCK);
                    factor *= waterFactor;
                }

                if (factor < 0.55f) factor = 0.55f;
                if (factor > 1.35f) factor = 1.35f;

                int o = idx * 3;
                int r = data.rgb[o] & 0xFF;
                int g = data.rgb[o + 1] & 0xFF;
                int b = data.rgb[o + 2] & 0xFF;

                r = clamp255(Math.round(r * factor));
                g = clamp255(Math.round(g * factor));
                b = clamp255(Math.round(b * factor));

                data.rgb[o]     = (byte) r;
                data.rgb[o + 1] = (byte) g;
                data.rgb[o + 2] = (byte) b;
            }
        }
        data.setDirty();
    }

    private static int clamp255(int v) {
        if (v < 0) return 0;
        return Math.min(v, 255);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        ServerLevel level = sp.serverLevel();
        if (level.dimension() != Level.OVERWORLD) return;

        BgMapSavedData data = BgMapSavedData.get(level);
        if (data.built) sendToPlayer(sp, data);
    }

    private static void sendToAll(MinecraftServer server, BgMapSavedData data) {
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) sendToPlayer(sp, data);
    }

    public static void sendToPlayer(ServerPlayer sp, BgMapSavedData data) {
        byte[] compressed = deflate(data.rgb);
        int totalParts = (compressed.length + PART_SIZE - 1) / PART_SIZE;

        long seed = sp.serverLevel().getSeed();

        for (int i = 0; i < totalParts; i++) {
            int start = i * PART_SIZE;
            int end = Math.min(compressed.length, start + PART_SIZE);
            byte[] part = Arrays.copyOfRange(compressed, start, end);

            ysMcbgMod.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> sp),
                    new MapDataPartPacket(MAP_SIZE, i, totalParts, seed, part)
            );
        }
    }

    private static byte[] deflate(byte[] input) {
        Deflater def = new Deflater(Deflater.BEST_SPEED);
        def.setInput(input);
        def.finish();

        ByteArrayOutputStream baos = new ByteArrayOutputStream(input.length / 2);
        byte[] buf = new byte[8192];
        while (!def.finished()) {
            int n = def.deflate(buf);
            if (n > 0) baos.write(buf, 0, n);
        }
        def.end();
        return baos.toByteArray();
    }

    public static void requestRebuild(ServerLevel level) {
        if (level == null || level.isClientSide()) return;

        BgMapSavedData data = BgMapSavedData.get(level);

        data.building = false;
        data.built = false;
        data.buildChunkIndex = 0;

        java.util.Arrays.fill(data.rgb, (byte) 0);
        data.clearHeightsAndWater();

        data.beginBuild();
        data.setDirty();
    }
}