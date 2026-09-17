package com.ys.ys_mcbg;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.entity.Pose;

@Mod.EventBusSubscriber(modid = ysMcbgMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DownedServerTick {

    /** 출혈 타이머(틱). 2000틱 = 100초 */
    public static final int BLEED_TOTAL = 2000;

    private static final int REVIVE_GRACE = 3;     // 3틱 내 패킷이면 "소생 중"
    private static final int DAMAGE_INTERVAL_TICKS = 40; // 2초
    private static final float DAMAGE_AMOUNT = 2.0f;     // 2씩 감소

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!(e.player instanceof ServerPlayer sp)) return;

        long now = sp.level().getGameTime();

        // 다운이 아니면 "강제 포즈"만 정리(일반 수영/자연 포즈는 건드리지 않음)
        if (!BgPlayerState.isDowned(sp)) {
            if (sp.getForcedPose() == Pose.SWIMMING) {
                sp.setForcedPose(null);
                sp.setSwimming(false);
                sp.refreshDimensions();
            }
            return;
        }

        // ✅ 다운 상태: 엎드림(수영 포즈) 강제 + swimming 플래그 ON
        boolean changed = false;
        if (sp.getPose() != Pose.SWIMMING) {
            sp.setPose(Pose.SWIMMING);
            changed = true;
        }
        if (sp.getForcedPose() != Pose.SWIMMING) {
            sp.setForcedPose(Pose.SWIMMING);
            changed = true;
        }
        if (!sp.isSwimming()) {
            sp.setSwimming(true);
            changed = true;
        }
        if (changed) sp.refreshDimensions();

        // 소생 중이면 bleed 정지
        boolean reviving = false;
        long lastRev = BgPlayerState.getLastReviveTick(sp);
        if (lastRev > 0 && now - lastRev <= REVIVE_GRACE) {
            reviving = true;
        } else {
            // 패킷 끊기면 소생 진행 리셋 + HUD 숨김
            if (BgPlayerState.getReviveProgress(sp) > 0) {
                java.util.UUID reviverId = BgPlayerState.getReviver(sp);
                ysMcbgMod.sendReviveProgress(sp, false, 0, 0);
                if (reviverId != null && sp.getServer() != null) {
                    ServerPlayer rv = sp.getServer().getPlayerList().getPlayer(reviverId);
                    if (rv != null) ysMcbgMod.sendReviveProgress(rv, false, 0, 0);
                }
                BgPlayerState.clearRevive(sp);
            }
        }

        int bleed = BgPlayerState.getBleedTicks(sp);

        // bleed 끝 -> 사망
        if (bleed <= 0) {
            sp.kill();
            return;
        }

        // 소생 중이면 완전 정지(타이머도, 체력 감소도)
        if (reviving) return;

        // 1틱마다 남은 시간 감소 (60초 타이머)
        bleed -= 1;
        BgPlayerState.setBleedTicks(sp, bleed);

        // ✅ 2초(40틱)마다 체력 2 감소
        if ((now % DAMAGE_INTERVAL_TICKS) == 0L) {
            float cur = sp.getHealth();
            float next = cur - DAMAGE_AMOUNT;

            if (next <= 0.0f) {
                sp.kill();
                return;
            }

            sp.setHealth(next);
        }

        // bleed 0이면 확실히 사망
        if (bleed <= 0) {
            sp.kill();
        }
    }
}