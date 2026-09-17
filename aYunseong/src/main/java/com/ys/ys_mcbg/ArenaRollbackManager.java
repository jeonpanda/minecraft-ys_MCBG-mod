package com.ys.ys_mcbg;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores the original arena blocks at game start and restores them at game end.
 * Optimized with Direct Section Access and BlockEntity Map Iteration for Ultra-Fast Capture.
 */
public final class ArenaRollbackManager {

    public static final BlockPos DEFAULT_MIN = new BlockPos(-380, 60, -380);
    public static final BlockPos DEFAULT_MAX = new BlockPos(380, 186, 380);

    // Fast Restore: Flag 2 (SEND_TO_CLIENTS without neighbor physics updates)
    private static final int RESTORE_FLAGS = 2;

    private static final Map<Long, ArenaChunkSnapshot> SNAPSHOT = new HashMap<>();

    // 게임 진행 중 블록이 변경된 청크만 추적하는 집합
    private static final Set<Long> MODIFIED_CHUNKS = ConcurrentHashMap.newKeySet();

    private static BlockPos snapshotMin = DEFAULT_MIN;
    private static BlockPos snapshotMax = DEFAULT_MAX;
    private static int minChunkX;
    private static int maxChunkX;
    private static int minChunkZ;
    private static int maxChunkZ;
    private static boolean captured;

    private ArenaRollbackManager() {
    }

    /**
     * 블록 파괴/변형 이벤트 발생 시 해당 청크를 변경 청크 목록에 등록
     */
    public static void markChunkModified(BlockPos pos) {
        if (!captured || pos == null) return;
        long key = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        MODIFIED_CHUNKS.add(key);
    }

    /**
     * Captures every block position in the supplied inclusive area (Ultra-Fast Version).
     */
    public static void captureArena(ServerLevel level, BlockPos min, BlockPos max) {
        if (level == null || level.isClientSide()) return;

        // 안내 메시지 전송
        broadcastMessage(level, "[MCBG] 맵 데이터 저장 중...");

        int minX = Math.min(min.getX(), max.getX());
        int minY = Math.min(min.getY(), max.getY());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxX = Math.max(min.getX(), max.getX());
        int maxY = Math.max(min.getY(), max.getY());
        int maxZ = Math.max(min.getZ(), max.getZ());

        clearSnapshot();

        snapshotMin = new BlockPos(minX, minY, minZ);
        snapshotMax = new BlockPos(maxX, maxY, maxZ);

        minChunkX = minX >> 4;
        maxChunkX = maxX >> 4;
        minChunkZ = minZ >> 4;
        maxChunkZ = maxZ >> 4;

        BlockState airState = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                ArenaChunkSnapshot chunkSnapshot = new ArenaChunkSnapshot(minY, maxY);
                long chunkKey = chunkKey(chunkX, chunkZ);

                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                LevelChunkSection[] sections = chunk.getSections();

                int fromX = Math.max(minX, chunkX << 4);
                int toX = Math.min(maxX, (chunkX << 4) + 15);
                int fromZ = Math.max(minZ, chunkZ << 4);
                int toZ = Math.min(maxZ, (chunkZ << 4) + 15);

                // 1. 고속 블록 상태 캡처 (LevelChunkSection 직접 접근)
                for (int y = minY; y <= maxY; y++) {
                    int sectionIndex = chunk.getSectionIndex(y);
                    LevelChunkSection section = null;
                    if (sectionIndex >= 0 && sectionIndex < sections.length) {
                        section = sections[sectionIndex];
                    }

                    boolean isSectionEmpty = (section == null || section.hasOnlyAir());
                    int sectionLocalY = y & 15;
                    int localY = y - minY;

                    for (int z = fromZ; z <= toZ; z++) {
                        int localZ = z & 15;
                        for (int x = fromX; x <= toX; x++) {
                            int localX = x & 15;
                            int index = localIndex(localX, localY, localZ);

                            BlockState state;
                            if (isSectionEmpty) {
                                state = airState;
                            } else {
                                state = section.getBlockState(localX, sectionLocalY, localZ);
                            }
                            chunkSnapshot.setState(index, state);
                        }
                    }
                }

                // 2. 고속 BlockEntity 수집 (개별 블록 검사 없이 맵에서 직접 추출)
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    BlockPos bePos = entry.getKey();
                    int bx = bePos.getX();
                    int by = bePos.getY();
                    int bz = bePos.getZ();

                    if (bx >= fromX && bx <= toX && by >= minY && by <= maxY && bz >= fromZ && bz <= toZ) {
                        int index = localIndex(bx & 15, by - minY, bz & 15);
                        chunkSnapshot.blockEntityNbt.put(index, entry.getValue().saveWithFullMetadata().copy());
                    }
                }

                SNAPSHOT.put(chunkKey, chunkSnapshot);
            }
        }

        captured = true;
    }

    /**
     * Restores the supplied level to the last captured snapshot in a single high-speed pass.
     */
    public static void resetArena(ServerLevel level) {
        if (level == null || level.isClientSide() || !captured) return;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // 감지된 변경 청크가 없는 경우 전체 청크를 대상으로 설정
        Set<Long> targetChunks = MODIFIED_CHUNKS.isEmpty() ? SNAPSHOT.keySet() : MODIFIED_CHUNKS;

        // Flag 2 단일 패스로 고속 일괄 복구
        restoreTargetPositions(level, pos, targetChunks, RESTORE_FLAGS);

        MODIFIED_CHUNKS.clear();

        // 완료 메시지 전송
        broadcastMessage(level, "[MCBG] 맵 초기화 완료!");
    }

    public static void clearSnapshot() {
        SNAPSHOT.clear();
        MODIFIED_CHUNKS.clear();
        captured = false;
        snapshotMin = DEFAULT_MIN;
        snapshotMax = DEFAULT_MAX;
        minChunkX = maxChunkX = minChunkZ = maxChunkZ = 0;
    }

    public static boolean hasSnapshot() {
        return captured && !SNAPSHOT.isEmpty();
    }

    public static BlockPos getSnapshotMin() {
        return snapshotMin;
    }

    public static BlockPos getSnapshotMax() {
        return snapshotMax;
    }

    public static int getSnapshotChunkCount() {
        return SNAPSHOT.size();
    }

    private static void broadcastMessage(ServerLevel level, String text) {
        Component message = Component.literal(text);
        for (ServerPlayer player : level.players()) {
            if (ysMcbgMod.isAdminPlayer(player)) {
                player.sendSystemMessage(message);
            }
        }
    }

    private static void restoreTargetPositions(ServerLevel level, BlockPos.MutableBlockPos pos, Set<Long> targetChunkKeys, int restoreFlags) {
        BlockState airState = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

        for (long chunkKey : targetChunkKeys) {
            ArenaChunkSnapshot chunkSnapshot = SNAPSHOT.get(chunkKey);
            if (chunkSnapshot == null) continue;

            int chunkX = (int) chunkKey;
            int chunkZ = (int) (chunkKey >> 32);

            LevelChunk chunk = level.getChunk(chunkX, chunkZ);
            LevelChunkSection[] sections = chunk.getSections();

            int fromX = Math.max(snapshotMin.getX(), chunkX << 4);
            int toX = Math.min(snapshotMax.getX(), (chunkX << 4) + 15);
            int fromZ = Math.max(snapshotMin.getZ(), chunkZ << 4);
            int toZ = Math.min(snapshotMax.getZ(), (chunkZ << 4) + 15);
            int minY = snapshotMin.getY();
            int maxY = snapshotMax.getY();

            boolean hasBE = chunkSnapshot.hasBlockEntities();

            // 복구할 때도 Section에 직접 접근하여 비교 속도 극대화
            for (int y = minY; y <= maxY; y++) {
                int sectionIndex = chunk.getSectionIndex(y);
                LevelChunkSection section = null;
                if (sectionIndex >= 0 && sectionIndex < sections.length) {
                    section = sections[sectionIndex];
                }

                boolean isSectionEmpty = (section == null || section.hasOnlyAir());
                int sectionLocalY = y & 15;
                int localY = y - minY;

                for (int z = fromZ; z <= toZ; z++) {
                    int localZ = z & 15;
                    for (int x = fromX; x <= toX; x++) {
                        int localX = x & 15;
                        int index = localIndex(localX, localY, localZ);

                        BlockState savedState = chunkSnapshot.getState(index);

                        BlockState currentState;
                        if (isSectionEmpty) {
                            currentState = airState;
                        } else {
                            currentState = section.getBlockState(localX, sectionLocalY, localZ);
                        }

                        // 참조 주소 비교(==)를 통해 상태가 다를 때만 연산 수행
                        if (currentState != savedState) {

                            // [추가된 로직] 레드스톤 블록은 복구 대상에서 완전히 제외
                            if (currentState.getBlock() == net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK ||
                                    savedState.getBlock() == net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK) {
                                continue;
                            }

                            pos.set(x, y, z);
                            level.setBlock(pos, savedState, restoreFlags);

                            if (hasBE) {
                                CompoundTag savedBlockEntityNbt = chunkSnapshot.getBlockEntityNbt(index);
                                if (savedBlockEntityNbt != null) {
                                    BlockEntity currentBlockEntity = chunk.getBlockEntity(pos);
                                    if (currentBlockEntity != null) {
                                        currentBlockEntity.load(savedBlockEntityNbt.copy());
                                        currentBlockEntity.setChanged();
                                        level.sendBlockUpdated(pos, savedState, savedState, restoreFlags);
                                    } else {
                                        BlockEntity loaded = BlockEntity.loadStatic(pos, savedState, savedBlockEntityNbt.copy());
                                        if (loaded != null) {
                                            level.setBlockEntity(loaded);
                                            loaded.setChanged();
                                            level.sendBlockUpdated(pos, savedState, savedState, restoreFlags);
                                        }
                                    }
                                }
                            }
                        } else if (hasBE && savedState.hasBlockEntity()) {
                            // 블록 상태는 같으나 NBT(상자 내용물 등)가 변경되었을 가능성에 대한 복구
                            CompoundTag savedBlockEntityNbt = chunkSnapshot.getBlockEntityNbt(index);
                            if (savedBlockEntityNbt != null) {
                                pos.set(x, y, z);
                                BlockEntity currentBlockEntity = chunk.getBlockEntity(pos);
                                if (currentBlockEntity != null) {
                                    currentBlockEntity.load(savedBlockEntityNbt.copy());
                                    currentBlockEntity.setChanged();
                                    level.sendBlockUpdated(pos, savedState, savedState, restoreFlags);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static int localIndex(int localX, int localY, int localZ) {
        return (localY * 256) + (localZ * 16) + localX;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xffffffffL) | (((long) chunkZ & 0xffffffffL) << 32);
    }

    private static final class ArenaChunkSnapshot {
        private final Map<Integer, CompoundTag> blockEntityNbt = new HashMap<>();
        private final int minY;
        private final int maxY;

        private BlockState[] palette = new BlockState[8];
        private int paletteSize;

        private byte[] paletteIndices;
        private BlockState[] directStates;

        private ArenaChunkSnapshot(int minY, int maxY) {
            this.minY = minY;
            this.maxY = maxY;
            int total = (maxY - minY + 1) * 256;
            this.paletteIndices = new byte[total];

            addToPalette(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }

        private void setState(int index, BlockState state) {
            if (directStates != null) {
                directStates[index] = state;
                return;
            }

            int paletteIndex = paletteIndexOf(state);
            if (paletteIndex < 0) {
                paletteIndex = addToPalette(state);
            }

            if (paletteIndex <= 255) {
                paletteIndices[index] = (byte) paletteIndex;
                return;
            }

            // 팔레트 256개 초과 시 직렬 배열로 전환
            directStates = new BlockState[paletteIndices.length];
            for (int i = 0; i < paletteIndices.length; i++) {
                directStates[i] = palette[paletteIndices[i] & 0xFF];
            }
            palette = null;
            paletteSize = 0;
            paletteIndices = null;
            directStates[index] = state;
        }

        private BlockState getState(int index) {
            if (directStates != null) {
                return directStates[index];
            }
            return palette[paletteIndices[index] & 0xFF];
        }

        private boolean hasBlockEntities() {
            return !blockEntityNbt.isEmpty();
        }

        private CompoundTag getBlockEntityNbt(int index) {
            return blockEntityNbt.get(index);
        }

        private int paletteIndexOf(BlockState state) {
            for (int i = 0; i < paletteSize; i++) {
                if (palette[i] == state) return i;
            }
            return -1;
        }

        private int addToPalette(BlockState state) {
            if (paletteSize == palette.length) {
                palette = Arrays.copyOf(palette, palette.length * 2);
            }
            palette[paletteSize] = state;
            return paletteSize++;
        }
    }
}