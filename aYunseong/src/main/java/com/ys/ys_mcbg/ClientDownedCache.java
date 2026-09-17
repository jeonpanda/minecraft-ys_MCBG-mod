package com.ys.ys_mcbg;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientDownedCache {
    private static final Map<UUID, Boolean> MAP = new ConcurrentHashMap<>();

    public static void set(UUID id, boolean downed) {
        MAP.put(id, downed);
    }

    public static boolean isDowned(UUID id) {
        return MAP.getOrDefault(id, false);
    }
}
