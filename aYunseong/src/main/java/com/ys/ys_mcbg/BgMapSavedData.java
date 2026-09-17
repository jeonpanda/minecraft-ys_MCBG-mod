package com.ys.ys_mcbg;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import static com.ys.ys_mcbg.BgMapConstants.MAP_SIZE;

public class BgMapSavedData extends SavedData {
    public static final String NAME = "ys_mcbg_fullmap";

    public boolean built = false;
    public boolean building = false;
    public int buildChunkIndex = 0;

    // RGB: 800*800*3
    public byte[] rgb = new byte[MAP_SIZE * MAP_SIZE * 3];

    // Height: 2 bytes/pixel
    public byte[] height2 = new byte[MAP_SIZE * MAP_SIZE * 2];

    // ✅ Water depth: 1 byte/pixel (0 = not water)
    public byte[] waterDepth = new byte[MAP_SIZE * MAP_SIZE];

    // ===== water depth access =====
    public int getWaterDepthAt(int idx) {
        return waterDepth[idx] & 0xFF;
    }

    public void setWaterDepthAt(int idx, int d) {
        if (d < 0) d = 0;
        if (d > 255) d = 255;
        waterDepth[idx] = (byte) d;
    }

    public static BgMapSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(BgMapSavedData::load, BgMapSavedData::new, NAME);
    }

    public void beginBuild() {
        if (built) return;
        building = true;
        buildChunkIndex = 0;

        for (int i = 0; i < MAP_SIZE * MAP_SIZE; i++) {
            int o = i * 3;
            rgb[o] = (byte) 0x22;
            rgb[o + 1] = (byte) 0x22;
            rgb[o + 2] = (byte) 0x22;

            int ho = i * 2;
            height2[ho] = 0;
            height2[ho + 1] = 0;

            waterDepth[i] = 0;
        }
        setDirty();
    }

    public void finishBuild() {
        building = false;
        built = true;
        setDirty();
    }

    // ===== height access =====
    public int getHeightAt(int idx) {
        int o = idx * 2;
        int lo = height2[o] & 0xFF;
        int hi = height2[o + 1] & 0xFF;
        return (hi << 8) | lo;
    }

    public void setHeightAt(int idx, int h) {
        int o = idx * 2;
        height2[o] = (byte) (h & 0xFF);
        height2[o + 1] = (byte) ((h >> 8) & 0xFF);
    }

    public static BgMapSavedData load(CompoundTag tag) {
        BgMapSavedData d = new BgMapSavedData();
        d.built = tag.getBoolean("built");
        d.building = tag.getBoolean("building");
        d.buildChunkIndex = tag.getInt("buildChunkIndex");

        byte[] arr = tag.getByteArray("rgb");
        if (arr.length == d.rgb.length) d.rgb = arr;

        byte[] h2 = tag.getByteArray("height2");
        if (h2.length == d.height2.length) d.height2 = h2;

        byte[] wd = tag.getByteArray("waterDepth");
        if (wd.length == d.waterDepth.length) d.waterDepth = wd;

        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("built", built);
        tag.putBoolean("building", building);
        tag.putInt("buildChunkIndex", buildChunkIndex);
        tag.putByteArray("rgb", rgb);
        tag.putByteArray("height2", height2);
        tag.putByteArray("waterDepth", waterDepth);
        return tag;
    }

    public void clearHeightsAndWater() {
        java.util.Arrays.fill(this.height2, (byte) 0);
        java.util.Arrays.fill(this.waterDepth, (byte) 0);
    }

}
