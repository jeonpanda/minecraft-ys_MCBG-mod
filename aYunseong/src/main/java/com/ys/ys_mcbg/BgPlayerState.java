package com.ys.ys_mcbg;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

public class BgPlayerState {
    private static final String K_BOOST = "mcbg_boost";
    private static final String K_LOCKED_SLOT = "mcbg_locked_slot";
    private static final String K_PENDING_HEAL = "mcbg_pending_heal";
    private static final String K_PENDING_TICKS = "mcbg_pending_ticks";
    private static final String K_HAD_BOOST_EFFECT = "mcbg_had_boost_effect";

    private static CompoundTag tag(Player p) {
        return p.getPersistentData();
    }

    // ---- BOOST ----
    public static float getBoost(Player p) {
        return tag(p).getFloat(K_BOOST);
    }

    public static void setBoost(Player p, float v) {
        tag(p).putFloat(K_BOOST, v);
    }

    public static boolean hadBoostEffect(Player p) {
        return tag(p).getBoolean(K_HAD_BOOST_EFFECT);
    }

    public static void setHadBoostEffect(Player p, boolean v) {
        tag(p).putBoolean(K_HAD_BOOST_EFFECT, v);
    }

    // ---- 잠금 슬롯(사용중 핫바 고정) ----
    public static int getLockedSlot(Player p) {
        return tag(p).getInt(K_LOCKED_SLOT);
    }

    public static void setLockedSlot(Player p, int slot) {
        tag(p).putInt(K_LOCKED_SLOT, slot);
    }

    // ---- Pending Heal(사용 끝난 뒤 서서히 회복) ----
    public static void startPendingHeal(Player p, float healAmount, int ticks) {
        CompoundTag t = tag(p);
        t.putFloat(K_PENDING_HEAL, healAmount);
        t.putInt(K_PENDING_TICKS, ticks);
    }

    public static float getPendingHeal(Player p) {
        return tag(p).getFloat(K_PENDING_HEAL);
    }

    public static int getPendingTicks(Player p) {
        return tag(p).getInt(K_PENDING_TICKS);
    }

    public static void clearPendingHeal(Player p) {
        CompoundTag t = tag(p);
        t.putFloat(K_PENDING_HEAL, 0f);
        t.putInt(K_PENDING_TICKS, 0);
    }

    // BgPlayerState.java

    private static final String K_DOWNED = "mcbg_downed";
    private static final String K_BLEED_TICKS = "mcbg_bleed_ticks";          // 남은 출혈 틱 (60초=1200)
    private static final String K_REVIVE_PROGRESS = "mcbg_revive_progress";  // 소생 진행 틱 (10초=200)
    private static final String K_REVIVER = "mcbg_reviver";                  // UUID string
    private static final String K_LAST_REVIVE_TICK = "mcbg_last_revive_tick";
    private static final String K_DOWNED_BY = "mcbg_downed_by";              // UUID string (who knocked)

    // 다운 상태에서 "피 100"을 맞추기 위한 MAX_HEALTH 임시 확장
    // - 평소 20(10하트) 기준이라면, 다운 시 100(50하트)로 확장
    // - 해제 시 원복
    public static final float DOWNED_MAX_HP = 100.0f;
    private static final UUID DOWNED_MAXHP_UUID = UUID.fromString("9d2d39b9-1d24-4c0b-9a19-2d3f3d0f61a1");
    private static final String K_MAXHP_DELTA = "mcbg_downed_maxhp_delta";

    public static boolean isDowned(Player p) {
        return tag(p).getBoolean(K_DOWNED);
    }

    public static void setDowned(Player p, boolean v) {
        tag(p).putBoolean(K_DOWNED, v);
        if (!v) {
            clearRevive(p);
            clearDownedBy(p);
            tag(p).putInt(K_BLEED_TICKS, 0);
        }
    }

    // ---- Knock / kill credit ----
    public static UUID getDownedBy(Player p) {
        String s = tag(p).getString(K_DOWNED_BY);
        if (s == null || s.isEmpty()) return null;
        try { return java.util.UUID.fromString(s); } catch (Exception e) { return null; }
    }

    public static void setDownedBy(Player p, java.util.UUID id) {
        tag(p).putString(K_DOWNED_BY, id == null ? "" : id.toString());
    }

    public static void clearDownedBy(Player p) {
        setDownedBy(p, null);
    }

    public static int getBleedTicks(Player p) {
        return tag(p).getInt(K_BLEED_TICKS);
    }

    public static void setBleedTicks(Player p, int v) {
        tag(p).putInt(K_BLEED_TICKS, Math.max(0, v));
    }

    public static int getReviveProgress(Player p) {
        return tag(p).getInt(K_REVIVE_PROGRESS);
    }

    public static void setReviveProgress(Player p, int v) {
        tag(p).putInt(K_REVIVE_PROGRESS, Math.max(0, v));
    }

    public static UUID getReviver(Player p) {
        String s = tag(p).getString(K_REVIVER);
        if (s == null || s.isEmpty()) return null;
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }

    public static void setReviver(Player p, UUID id) {
        tag(p).putString(K_REVIVER, id == null ? "" : id.toString());
    }

    public static long getLastReviveTick(Player p) {
        return tag(p).getLong(K_LAST_REVIVE_TICK);
    }

    public static void setLastReviveTick(Player p, long t) {
        tag(p).putLong(K_LAST_REVIVE_TICK, t);
    }

    public static void clearRevive(Player p) {
        setReviveProgress(p, 0);
        setReviver(p, null);
        setLastReviveTick(p, 0L);
    }

    /** 다운 진입 시 MAX_HEALTH를 100으로 맞추기 위한 임시 보정(트랜지언트 Modifier). */
    public static void applyDownedMaxHealth(Player p) {
        AttributeInstance inst = p.getAttribute(Attributes.MAX_HEALTH);
        if (inst == null) return;

        // 이미 적용되어 있으면 스킵
        if (inst.getModifier(DOWNED_MAXHP_UUID) != null) return;

        double cur = inst.getValue();
        double delta = (double) DOWNED_MAX_HP - cur;

        // 이미 100이면 굳이 적용하지 않음
        if (Math.abs(delta) < 0.0001) {
            tag(p).putDouble(K_MAXHP_DELTA, 0.0);
            return;
        }

        inst.addTransientModifier(new AttributeModifier(
                DOWNED_MAXHP_UUID,
                "mcbg_downed_maxhp",
                delta,
                AttributeModifier.Operation.ADDITION
        ));
        tag(p).putDouble(K_MAXHP_DELTA, delta);
    }

    /** 다운 해제/부활/명령어 해제 시 MAX_HEALTH 보정 제거. */
    public static void clearDownedMaxHealth(Player p) {
        AttributeInstance inst = p.getAttribute(Attributes.MAX_HEALTH);
        if (inst != null && inst.getModifier(DOWNED_MAXHP_UUID) != null) {
            inst.removeModifier(DOWNED_MAXHP_UUID);
        }
        tag(p).remove(K_MAXHP_DELTA);
    }

}
