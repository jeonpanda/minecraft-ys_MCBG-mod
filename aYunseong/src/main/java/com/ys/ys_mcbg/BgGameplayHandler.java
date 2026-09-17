package com.ys.ys_mcbg;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ysMcbgMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BgGameplayHandler {

    // 100% -> 0% 120초
    private static final float BOOST_DECAY_PER_TICK = 1.0f / 2400.0f;

    // 부스트 60%가 줄어드는 동안 HP 40 회복
    private static final float HEAL_PER_BOOST_1 = 40.0f / 0.60f;

    // ✅ 잔값 스냅(이 값 이하면 0으로 강제)
    private static final float BOOST_SNAP_EPS = 0.01f; // 1% 이하 잔값 제거

    // 사용중 이속 감소(디버프)
    private static final UUID USING_SLOW_UUID = UUID.fromString("b4c6d0a5-0a27-4b5a-9a1c-9b2c58a6b0e1");
    private static final AttributeModifier USING_SLOW =
            new AttributeModifier(USING_SLOW_UUID, "mcbg_using_slow", -0.15, AttributeModifier.Operation.MULTIPLY_TOTAL);

    private static final Map<UUID, Float> lastSentBoost = new HashMap<>();

    private static boolean isUsingBgItem(Player p) {
        return p.isUsingItem() && (p.getUseItem().getItem() instanceof BgConsumableItem);
    }

    @SubscribeEvent
    public static void onUseStart(LivingEntityUseItemEvent.Start e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (p.level().isClientSide) return;

        if (!(e.getItem().getItem() instanceof BgConsumableItem)) return;
        BgPlayerState.setLockedSlot(p, p.getInventory().selected);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Player p = e.player;
        if (p.level().isClientSide) return;

        Inventory inv = p.getInventory();

        // ===== 오프핸드 슬롯 사용 금지 =====
        // 모든 플레이어가 오프핸드에 아이템을 유지할 수 없도록 하고,
        // 들어온 아이템은 오프핸드를 제외한 일반 인벤토리로 이동시킨다.
        ItemStack offhand = p.getOffhandItem();
        if (!offhand.isEmpty()) {
            ItemStack moving = offhand.copy();
            p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

            if (!moveOutOfOffhand(inv, moving)) {
                p.drop(moving, false);
            }
        }

        // ===== 사용중: 핫바 고정 + 오프핸드 스왑 방지 + 이속 감소 =====
        if (isUsingBgItem(p)) {
            int locked = BgPlayerState.getLockedSlot(p);
            if (locked < 0 || locked > 8) locked = inv.selected;
            inv.selected = locked;

            ItemStack off = p.getOffhandItem();
            if (!off.isEmpty()) {
                ItemStack moving = off.copy();
                p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

                if (!tryMoveToAllowed(inv, moving)) {
                    p.drop(moving, false);
                }
            }

            var inst = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (inst != null && inst.getModifier(USING_SLOW_UUID) == null) {
                inst.addTransientModifier(USING_SLOW);
            }
        } else {
            var inst = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (inst != null && inst.getModifier(USING_SLOW_UUID) != null) {
                inst.removeModifier(USING_SLOW_UUID);
            }
        }

        // ===== Pending Heal 처리 =====
        int ticks = BgPlayerState.getPendingTicks(p);
        float amt = BgPlayerState.getPendingHeal(p);
        if (ticks > 0 && amt > 0f) {
            float perTick = amt / (float) ticks;

            float cur = p.getHealth();
            float max = p.getMaxHealth();
            p.setHealth(Mth.clamp(cur + perTick, 0f, max));

            ticks--;
            amt -= perTick;

            if (ticks <= 0 || amt <= 0.001f) {
                BgPlayerState.clearPendingHeal(p);
            } else {
                BgPlayerState.startPendingHeal(p, amt, ticks);
            }
        }

        // ===== BOOST 감소 + 회복 + 야간투시/스피드 =====
        float boost = BgPlayerState.getBoost(p);

        if (boost > 0f) {
            float consume = Math.min(boost, BOOST_DECAY_PER_TICK);
            boost -= consume;

            // ✅ 잔값 스냅
            if (boost < BOOST_SNAP_EPS) boost = 0f;
            BgPlayerState.setBoost(p, boost);

            float heal = consume * HEAL_PER_BOOST_1;
            float cur = p.getHealth();
            float max = p.getMaxHealth();
            if (cur < max) p.setHealth(Mth.clamp(cur + heal, 0f, max));

            if (boost > 0f) {
                // ✅ 야간투시 유지 (반짝임 최소화: 짧게 갱신)
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, false));
                // ✅ 스피드도 동일 방식으로 안정적으로 유지
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 260, 0, true, false, false));

                BgPlayerState.setHadBoostEffect(p, true);
            }
        }

        // ✅ 부스트 끝: NV/Speed 즉시 제거(반짝임 방지)
        if (BgPlayerState.getBoost(p) <= 0f && BgPlayerState.hadBoostEffect(p)) {
            p.removeEffect(MobEffects.NIGHT_VISION);
            p.removeEffect(MobEffects.MOVEMENT_SPEED);
            BgPlayerState.setHadBoostEffect(p, false);
        }

        // ===== HUD 동기화 =====
        if (p instanceof ServerPlayer sp) {
            syncBoost(sp, false);
        }
    }

    private static void syncBoost(ServerPlayer sp, boolean force) {
        float now = BgPlayerState.getBoost(sp);
        if (now < BOOST_SNAP_EPS) now = 0f;

        Float prev = lastSentBoost.get(sp.getUUID());
        boolean changed = (prev == null) || Math.abs(prev - now) >= 0.01f;

        if (force || changed) {
            lastSentBoost.put(sp.getUUID(), now);
            ysMcbgMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new BoostSyncPacket(now));
        }
    }

    // ===== 사용중 “다른 동작” 차단 =====
    @SubscribeEvent public static void onRightClickItem(PlayerInteractEvent.RightClickItem e) { if (!e.getEntity().level().isClientSide && isUsingBgItem(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) { if (!e.getEntity().level().isClientSide && isUsingBgItem(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void onRightClickEntity(PlayerInteractEvent.EntityInteract e) { if (!e.getEntity().level().isClientSide && isUsingBgItem(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock e) { if (!e.getEntity().level().isClientSide && isUsingBgItem(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void onAttackEntity(AttackEntityEvent e) { if (!e.getEntity().level().isClientSide && isUsingBgItem(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void onDrop(ItemTossEvent e) { if (!e.getPlayer().level().isClientSide && isUsingBgItem(e.getPlayer())) e.setCanceled(true); }

    // ===== 허용 슬롯 이동 =====
    private static boolean moveOutOfOffhand(Inventory inv, ItemStack stack) {
        // PlayerInventory.items 는 일반 인벤토리 36칸이며 오프핸드는 별도 목록이다.
        if (moveIntoRange(inv, stack, 0, 35)) return true;
        return stack.isEmpty();
    }

    private static boolean tryMoveToAllowed(Inventory inv, ItemStack stack) {
        if (moveIntoRange(inv, stack, 27, 35)) return true;
        if (moveIntoRange(inv, stack, 0, 8)) return true;
        return stack.isEmpty();
    }

    private static boolean moveIntoRange(Inventory inv, ItemStack stack, int start, int end) {
        for (int i = start; i <= end; i++) {
            ItemStack target = inv.items.get(i);
            if (!target.isEmpty() && ItemStack.isSameItemSameTags(target, stack)) {
                int max = Math.min(target.getMaxStackSize(), inv.getMaxStackSize());
                int canMove = Math.min(stack.getCount(), max - target.getCount());
                if (canMove > 0) {
                    target.grow(canMove);
                    stack.shrink(canMove);
                    if (stack.isEmpty()) return true;
                }
            }
        }
        for (int i = start; i <= end; i++) {
            if (inv.items.get(i).isEmpty()) {
                inv.items.set(i, stack.copy());
                stack.setCount(0);
                return true;
            }
        }
        return false;
    }
}
