package com.ys.ys_mcbg;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public class BgConsumableItem extends Item {

    public enum Kind {
        BANDAGE,
        FIRST_AID,
        MED_KIT,
        ENERGY_DRINK,
        PAINKILLER,
        ADRENALINE
    }

    private final Kind kind;
    private final int useTicks;
    private final float boostAdd;
    private final int pendingHealTicks;

    // ✅ 사용중 반복 재생 간격(틱) : 20틱 = 1초
    private static final int LOOP_INTERVAL_TICKS = 20;

    // ✅ 반복 사운드 마지막 재생 시각 저장 키
    private static final String NBT_LAST_LOOP_TICK = "mcbg_last_loop_tick";

    public BgConsumableItem(Kind kind, Properties props, int useTicks, float boostAdd, int pendingHealTicks) {
        super(props);
        this.kind = kind;
        this.useTicks = useTicks;
        this.boostAdd = boostAdd;
        this.pendingHealTicks = pendingHealTicks;
    }

    public Kind getKind() {
        return kind;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return useTicks;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    private static boolean isHealth75OrMore(Player p) {
        float cur = p.getHealth();
        float max = p.getMaxHealth();
        if (max <= 0f) return true;
        return (cur / max) >= 0.75f;
    }

    // =========================================================
    // ✅ 본인에게만 들리게(서버 -> 해당 플레이어에게만)
    // =========================================================
    private static void playToSelf(Level level, Player p, SoundEvent sound, float volume, float pitch) {
        if (level.isClientSide) return;
        if (p instanceof ServerPlayer sp) {
            sp.playNotifySound(sound, SoundSource.PLAYERS, volume, pitch);
        }
    }

    // 시작/완료 볼륨
    private void playStartSound(Level level, Player p) {
        switch (kind) {
            case BANDAGE -> {
                playToSelf(level, p, SoundEvents.WOOL_HIT, 0.9f, 1.00f);
                playToSelf(level, p, SoundEvents.ARMOR_EQUIP_LEATHER, 0.8f, 1.15f);
            }
            case FIRST_AID -> {
                playToSelf(level, p, SoundEvents.BUNDLE_INSERT, 0.9f, 1.05f);
                playToSelf(level, p, SoundEvents.ARMOR_EQUIP_LEATHER, 0.8f, 1.05f);
            }
            case MED_KIT -> {
                playToSelf(level, p, SoundEvents.CHEST_OPEN, 1.0f, 1.20f);
                playToSelf(level, p, SoundEvents.BUNDLE_DROP_CONTENTS, 0.9f, 1.00f);
            }
            case ENERGY_DRINK -> {
                playToSelf(level, p, SoundEvents.BOTTLE_EMPTY, 0.8f, 1.35f);
                playToSelf(level, p, SoundEvents.GENERIC_DRINK, 1.0f, 1.25f);
            }
            case PAINKILLER -> {
                playToSelf(level, p, SoundEvents.ITEM_PICKUP, 0.7f, 1.60f);
                playToSelf(level, p, SoundEvents.HONEY_DRINK, 1.0f, 1.05f);
            }
            case ADRENALINE -> {
                playToSelf(level, p, SoundEvents.BOTTLE_FILL, 0.9f, 1.35f);
                playToSelf(level, p, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.80f);
            }
        }
    }

    private void playFinishSound(Level level, Player p) {
        switch (kind) {
            case BANDAGE, FIRST_AID -> {
                playToSelf(level, p, SoundEvents.ITEM_PICKUP, 1.0f, 1.25f);
                playToSelf(level, p, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.60f);
            }
            case MED_KIT -> {
                playToSelf(level, p, SoundEvents.PLAYER_LEVELUP, 1.2f, 1.85f);
            }
            case ENERGY_DRINK, PAINKILLER, ADRENALINE -> {
                playToSelf(level, p, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.1f, 1.90f);
            }
        }
    }


    // ✅ 사용중 반복 사운드(1초마다)
    private void playLoopSound(Level level, Player p) {
        switch (kind) {
            case BANDAGE ->
                    playToSelf(level, p, SoundEvents.WOOL_HIT, 0.8f, 0.95f);

            case FIRST_AID ->
                    playToSelf(level, p, SoundEvents.BUNDLE_INSERT, 0.8f, 1.00f);

            case MED_KIT ->
                    playToSelf(level, p, SoundEvents.BUNDLE_DROP_CONTENTS, 0.9f, 0.95f);

            case ENERGY_DRINK ->
                    playToSelf(level, p, SoundEvents.GENERIC_DRINK, 0.9f, 1.15f);

            case PAINKILLER ->
                    playToSelf(level, p, SoundEvents.HONEY_DRINK, 0.8f, 1.00f);

            case ADRENALINE ->
                    playToSelf(level, p, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.70f);
        }
    }


    // =========================================================
    // ✅ 핵심: 사용 중 매 틱 호출됨 -> 20틱마다 반복 재생
    // =========================================================
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide) return;
        if (!(entity instanceof Player p)) return;

        long now = level.getGameTime();
        long last = p.getPersistentData().getLong(NBT_LAST_LOOP_TICK);

        if (now - last >= LOOP_INTERVAL_TICKS) {
            playLoopSound(level, p);
            p.getPersistentData().putLong(NBT_LAST_LOOP_TICK, now);
        }
    }

    // ✅ 사용 취소/중단 시 타이머 정리
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeCharged) {
        if (!level.isClientSide && entity instanceof Player p) {
            p.getPersistentData().remove(NBT_LAST_LOOP_TICK);
        }
        super.releaseUsing(stack, level, entity, timeCharged);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (kind == Kind.BANDAGE || kind == Kind.FIRST_AID) {
            if (isHealth75OrMore(player)) {
                player.displayClientMessage(Component.literal("체력이 75% 이상이라 사용할 수 없습니다."), true);
                return InteractionResultHolder.fail(stack);
            }
        }

        // ✅ 시작 사운드 1회
        playStartSound(level, player);

        // ✅ 반복 타이머 초기화(바로 다음 1초 후부터 루프)
        if (!level.isClientSide) {
            player.getPersistentData().putLong(NBT_LAST_LOOP_TICK, level.getGameTime());
        }

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player p)) return stack;

        if (!level.isClientSide) {
            // ✅ 루프 타이머 정리
            p.getPersistentData().remove(NBT_LAST_LOOP_TICK);

            switch (kind) {
                case BANDAGE -> {
                    BgPlayerState.startPendingHeal(p, 10.0f, pendingHealTicks);
                    stack.shrink(1);
                }
                case FIRST_AID -> {
                    float max = p.getMaxHealth();
                    float target = max * 0.75f;
                    float cur = p.getHealth();
                    if (cur < target) {
                        float amt = target - cur;
                        BgPlayerState.startPendingHeal(p, amt, pendingHealTicks);
                    }
                    stack.shrink(1);
                }
                case MED_KIT -> {
                    p.setHealth(p.getMaxHealth());
                    stack.shrink(1);
                }
                case ENERGY_DRINK, PAINKILLER, ADRENALINE -> {
                    float now = BgPlayerState.getBoost(p);
                    float next = Math.min(1.0f, now + boostAdd);
                    BgPlayerState.setBoost(p, next);
                    stack.shrink(1);
                }
            }

            // ✅ 완료 사운드 1회
            playFinishSound(level, p);
        }

        return stack;
    }
}
