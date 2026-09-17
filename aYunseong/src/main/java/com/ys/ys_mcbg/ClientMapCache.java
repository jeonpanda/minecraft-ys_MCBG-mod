package com.ys.ys_mcbg;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.ByteArrayOutputStream;
import java.util.zip.Inflater;

@Mod.EventBusSubscriber(modid = ysMcbgMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientMapCache {
    public static boolean ready = false;
    public static int mapSize = 0;

    private static byte[][] parts;
    private static int received;

    private static DynamicTexture texture;
    private static ResourceLocation texId;

    private static long cachedSeed = Long.MIN_VALUE;

    public static ResourceLocation getTexId() {
        return texId;
    }

    public static void clearAll() {
        ready = false;
        mapSize = 0;

        parts = null;
        received = 0;

        if (texture != null) {
            texture.close();
            texture = null;
        }
        texId = null;
    }

    public static void reset() {
        ready = false;
        mapSize = 0;
        parts = null;
        received = 0;
        cachedSeed = Long.MIN_VALUE;

        if (texture != null) {
            texture.close();
            texture = null;
        }
        texId = null;
    }

    // 월드 나갈 때 캐시 제거 (새 월드에서 이전 맵 뜨는 현상 방지)
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        reset();
    }

    public static void reset(int newSize, int totalParts, long worldSeed) {
        ready = false;
        mapSize = newSize;
        parts = new byte[totalParts][];
        received = 0;
        cachedSeed = worldSeed;
    }

    public static void acceptPart(MapDataPartPacket p) {
        // seed가 바뀌면 무조건 reset
        if (cachedSeed != Long.MIN_VALUE && cachedSeed != p.worldSeed) {
            reset();
        }

        if (mapSize != p.mapSize || parts == null || parts.length != p.totalParts || cachedSeed != p.worldSeed) {
            reset(p.mapSize, p.totalParts, p.worldSeed);
        }

        if (p.partIndex < 0 || p.partIndex >= parts.length) return;

        if (parts[p.partIndex] == null) {
            parts[p.partIndex] = p.payload;
            received++;
        }

        if (received == parts.length) {
            byte[] comp = join(parts);
            byte[] rgb = inflate(comp, mapSize * mapSize * 3);
            if (rgb == null) return;
            buildTexture(rgb);
            ready = true;
        }
    }

    private static byte[] join(byte[][] arr) {
        int total = 0;
        for (byte[] a : arr) total += a.length;
        byte[] out = new byte[total];
        int off = 0;
        for (byte[] a : arr) {
            System.arraycopy(a, 0, out, off, a.length);
            off += a.length;
        }
        return out;
    }

    private static byte[] inflate(byte[] comp, int expectedLen) {
        try {
            Inflater inflater = new Inflater();
            inflater.setInput(comp);

            ByteArrayOutputStream baos = new ByteArrayOutputStream(expectedLen);
            byte[] buf = new byte[8192];
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n <= 0) break;
                baos.write(buf, 0, n);
            }
            inflater.end();

            byte[] out = baos.toByteArray();
            if (out.length != expectedLen) return null;
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static void buildTexture(byte[] rgb) {
        Minecraft mc = Minecraft.getInstance();

        if (texture != null) {
            texture.close();
            texture = null;
            texId = null;
        }

        NativeImage img = new NativeImage(mapSize, mapSize, false);

        int i = 0;
        for (int z = 0; z < mapSize; z++) {
            for (int x = 0; x < mapSize; x++) {
                int r = rgb[i++] & 0xFF;
                int g = rgb[i++] & 0xFF;
                int b = rgb[i++] & 0xFF;

                // ABGR
                int abgr = (0xFF << 24) | (b << 16) | (g << 8) | r;
                img.setPixelRGBA(x, z, abgr);
            }
        }

        texture = new DynamicTexture(img);
        texId = mc.getTextureManager().register("ys_mcbg_fullmap", texture);
    }
}
