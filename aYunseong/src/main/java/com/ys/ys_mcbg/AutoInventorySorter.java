package com.ys.ys_mcbg;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 서버 권위형 자동 인벤토리 정리기.
 * 현재 프로젝트의 BlockTopInventoryRows가 정의한 실제 가방 슬롯 범위를 그대로 사용한다.
 */
public final class AutoInventorySorter {
    private static final ResourceLocation TACZ_GUN = new ResourceLocation("tacz", "modern_kinetic_gun");
    private static final ResourceLocation TACZ_AMMO = new ResourceLocation("tacz", "ammo");
    private static final ResourceLocation LRT_THROWABLE = new ResourceLocation("lrtactical", "throwable");

    private AutoInventorySorter() {}

    public static void sort(ServerPlayer player) {
        if (player == null || player.isRemoved()) return;

        Inventory inv = player.getInventory();
        List<ItemStack> original = new ArrayList<>();
        for (ItemStack stack : inv.items) {
            if (!stack.isEmpty()) original.add(stack.copy());
        }

        // 서로 정상적으로 합칠 수 있는 스택부터 최대한 합친다.
        List<ItemStack> all = mergeStacks(original);
        List<ItemStack> remaining = new ArrayList<>(all);
        List<ItemStack> dropped = new ArrayList<>();

        // 총기 예외 규칙 적용.
        GunSelection guns = selectGuns(remaining, dropped);

        // 현재 일반 인벤토리 36칸만 비운다. FEET/갑옷/헬멧 등 장착 슬롯은 건드리지 않는다.
        for (int i = 0; i < 36; i++) inv.items.set(i, ItemStack.EMPTY);

        boolean[] occupied = new boolean[36];

        // 1~2번: 단계가 낮은 주무기.
        for (int i = 0; i < guns.primary.size() && i < 2; i++) {
            inv.items.set(i, guns.primary.get(i).copy());
            occupied[i] = true;
            removeByReference(remaining, guns.primary.get(i));
        }

        // 3번: 권총, 없으면 근접무기.
        if (guns.pistol != null) {
            inv.items.set(2, guns.pistol.copy());
            occupied[2] = true;
            removeByReference(remaining, guns.pistol);
        } else {
            ItemStack melee = takeFirst(remaining, Category.MELEE);
            if (melee != null) {
                inv.items.set(2, melee.copy());
                occupied[2] = true;
                removeByReference(remaining, melee);
            }
        }

        int bagStart = BlockTopInventoryRows.getAllowedMainStart(player);
        List<Integer> bagSlots = new ArrayList<>();
        if (bagStart <= 35) {
            for (int i = bagStart; i <= 35; i++) bagSlots.add(i);
        }

        List<Integer> freeHotbarAfterMandatory = new ArrayList<>();
        for (int i = 3; i <= 8; i++) {
            if (!occupied[i]) freeHotbarAfterMandatory.add(i);
        }

        List<ItemStack> ammo = takeAll(remaining, Category.AMMO);
        ammo.sort(Comparator.comparing(AutoInventorySorter::ammoId));

        // 가방이 없으면 탄약 스택 수만큼 9번에 가까운 칸을 먼저 예약한다.
        int reservedAmmoSlots = bagSlots.isEmpty()
                ? Math.min(ammo.size(), freeHotbarAfterMandatory.size())
                : 0;

        List<Integer> combatHotbarSlots = new ArrayList<>(freeHotbarAfterMandatory.subList(0,
                Math.max(0, freeHotbarAfterMandatory.size() - reservedAmmoSlots)));

        // 4~9번: 투척물 -> 힐템 -> 플레어 -> 기타 전투 아이템.
        // 한 카테고리당 기본 최대 2스택.
        int cursor = 0;
        for (Category category : List.of(Category.THROWABLE, Category.HEAL, Category.FLARE, Category.OTHER)) {
            List<ItemStack> candidates = takeAll(remaining, category);
            candidates.sort(categoryComparator(category));
            int categoryUses = 0;
            for (ItemStack stack : candidates) {
                if (cursor < combatHotbarSlots.size() && categoryUses < 2) {
                    int slot = combatHotbarSlots.get(cursor++);
                    inv.items.set(slot, stack.copy());
                    occupied[slot] = true;
                    categoryUses++;
                } else {
                    // 가방이 있으면 뒤의 가방 영역에서 계속 보관할 수 있도록 다시 remaining에 돌려놓는다.
                    remaining.add(stack);
                }
            }
        }

        if (bagSlots.isEmpty()) {
            // 가방 없음: 예약된 슬롯을 9 -> 8 -> ... 방향으로 사용한다.
            int ammoPlaced = 0;
            for (int slot = 8; slot >= 0 && ammoPlaced < ammo.size(); slot--) {
                if (occupied[slot]) continue;
                inv.items.set(slot, ammo.get(ammoPlaced).copy());
                occupied[slot] = true;
                ammoPlaced++;
            }
            for (int i = ammoPlaced; i < ammo.size(); i++) dropped.add(ammo.get(i).copy());
        } else {
            // 가방 있음: 실제 프로젝트에서 열린 가방 영역의 앞쪽부터 탄약을 배치.
            int bagCursor = 0;
            for (ItemStack stack : ammo) {
                if (bagCursor >= bagSlots.size()) {
                    dropped.add(stack.copy());
                    continue;
                }
                int slot = bagSlots.get(bagCursor++);
                inv.items.set(slot, stack.copy());
                occupied[slot] = true;
            }

            // 탄약 이후: 힐 -> 투척 -> 파츠 -> 플레어 -> 기타 순으로 가방에 배치.
            int bagCursorAfterAmmo = bagCursor;
            for (Category category : List.of(
                    Category.HEAL,
                    Category.THROWABLE,
                    Category.ATTACHMENT,
                    Category.FLARE,
                    Category.MELEE,
                    Category.GUN,
                    Category.OTHER)) {
                List<ItemStack> candidates = takeAll(remaining, category);
                candidates.sort(categoryComparator(category));
                for (ItemStack stack : candidates) {
                    if (bagCursorAfterAmmo >= bagSlots.size()) {
                        dropped.add(stack.copy());
                        continue;
                    }
                    int slot = bagSlots.get(bagCursorAfterAmmo++);
                    inv.items.set(slot, stack.copy());
                    occupied[slot] = true;
                }
            }
        }

        // 예상치 못한/새로운 분류가 생겨도 조용히 삭제하지 않고 빈 허용 슬롯 또는 바닥으로 보낸다.
        for (ItemStack stack : new ArrayList<>(remaining)) {
            if (stack.isEmpty()) continue;
            int slot = findEmptyAllowedSlot(occupied, bagSlots);
            if (slot >= 0) {
                inv.items.set(slot, stack.copy());
                occupied[slot] = true;
            } else {
                dropped.add(stack.copy());
            }
        }
        remaining.clear();

        // 실제 월드 드롭은 총기 초과분과 수납 공간이 없는 아이템에 대해서만 발생한다.
        for (ItemStack stack : dropped) {
            if (!stack.isEmpty()) player.drop(stack.copy(), false);
        }

        inv.setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();

        if (!verifyConservation(original, inv, dropped)) {
            ysMcbgMod.LOGGER.error("Auto inventory sort conservation check failed for {}", player.getGameProfile().getName());
        }
    }

    private static List<ItemStack> mergeStacks(List<ItemStack> source) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack src : source) {
            int remaining = src.getCount();
            for (ItemStack target : merged) {
                if (!ItemStack.isSameItemSameTags(target, src)) continue;
                int max = Math.min(target.getMaxStackSize(), 64);
                int move = Math.min(remaining, Math.max(0, max - target.getCount()));
                if (move > 0) {
                    target.grow(move);
                    remaining -= move;
                    if (remaining == 0) break;
                }
            }
            while (remaining > 0) {
                int amount = Math.min(remaining, src.getMaxStackSize());
                ItemStack copy = src.copy();
                copy.setCount(amount);
                merged.add(copy);
                remaining -= amount;
            }
        }
        return merged;
    }

    private static GunSelection selectGuns(List<ItemStack> remaining, List<ItemStack> dropped) {
        List<ItemStack> primary = new ArrayList<>();
        List<ItemStack> pistols = new ArrayList<>();

        for (ItemStack stack : remaining) {
            Classification c = classify(stack);
            if (c.category != Category.GUN) continue;
            if (c.stage == 10) pistols.add(stack);
            else primary.add(stack);
        }

        primary.sort(Comparator.comparingInt(AutoInventorySorter::gunStage));

        // 단계가 높은(= 숫자가 큰) 총부터 초과분을 바닥으로 보낸다.
        while (primary.size() > 2) {
            ItemStack lowest = primary.remove(primary.size() - 1);
            removeByReference(remaining, lowest);
            dropped.add(lowest.copy());
        }

        // 권총은 개수 제한이 없다는 요청에 따라 첫 1개를 3번에 고정하고, 나머지는 가방/잔여 공간으로 보낸다.
        ItemStack pistol = pistols.isEmpty() ? null : pistols.get(0);
        return new GunSelection(primary, pistol);
    }

    private static ItemStack takeFirst(List<ItemStack> stacks, Category category) {
        for (ItemStack stack : stacks) {
            if (classify(stack).category == category) return stack;
        }
        return null;
    }

    private static List<ItemStack> takeAll(List<ItemStack> stacks, Category category) {
        List<ItemStack> result = new ArrayList<>();
        for (int i = stacks.size() - 1; i >= 0; i--) {
            if (classify(stacks.get(i)).category == category) result.add(0, stacks.remove(i));
        }
        return result;
    }

    private static void removeByReference(List<ItemStack> list, ItemStack target) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == target) {
                list.remove(i);
                return;
            }
        }
    }

    private static int findEmptyAllowedSlot(boolean[] occupied, List<Integer> bagSlots) {
        // 우선 가방 영역, 없으면 핫바.
        for (int slot : bagSlots) if (!occupied[slot]) return slot;
        for (int slot = 0; slot <= 8; slot++) if (!occupied[slot]) return slot;
        return -1;
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    private static String ammoId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString("AmmoId");
    }

    private static String throwableId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString("ThrowableId");
    }

    private static int gunStage(ItemStack stack) {
        return classify(stack).stage;
    }

    private static Classification classify(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return new Classification(Category.OTHER, Integer.MAX_VALUE);

        if (TACZ_GUN.equals(id)) {
            CompoundTag tag = stack.getTag();
            String gunId = tag == null ? "" : tag.getString("GunId");
            return new Classification(Category.GUN, gunStageFromId(gunId));
        }
        if (TACZ_AMMO.equals(id)) return new Classification(Category.AMMO, Integer.MAX_VALUE);
        if (LRT_THROWABLE.equals(id)) return new Classification(Category.THROWABLE, Integer.MAX_VALUE);

        return switch (id.toString()) {
            case "ys_mcbg:first_aid" -> new Classification(Category.HEAL, 0);
            case "ys_mcbg:bandage" -> new Classification(Category.HEAL, 1);
            case "ys_mcbg:adrenaline" -> new Classification(Category.HEAL, 2);
            case "ys_mcbg:energy_drink" -> new Classification(Category.HEAL, 3);
            case "ys_mcbg:painkiller" -> new Classification(Category.HEAL, 4);
            case "ys_mcbg:med_kit" -> new Classification(Category.HEAL, 5);
            default -> {
                if ("attachment".equals(id.getPath())) yield new Classification(Category.ATTACHMENT, Integer.MAX_VALUE);
                if ("melee".equals(id.getPath())) yield new Classification(Category.MELEE, Integer.MAX_VALUE);
                // 제공된 프로젝트 소스에는 플레어건의 실제 registry id가 없으므로 새 ID를 추측하지 않는다.
                yield new Classification(Category.OTHER, Integer.MAX_VALUE);
            }
        };
    }

    private static int gunStageFromId(String gunId) {
        return switch (gunId) {
            case "tacz:minigun", "tacz:fn_evolysa" -> 1;
            case "tacz:m16a4", "tacz:m4a1", "tacz:hk416d", "tacz:scar_l", "tacz:aug", "tacz:g36k", "tacz:ak47" -> 2;
            case "tacz:m249" -> 3;
            case "tacz:uzi", "tacz:ump45", "tacz:hk_mp5a5", "tacz:vector45" -> 4;
            case "tacz:m870", "tacz:db_long" -> 5;
            case "tacz:rpg7" -> 6;
            case "tacz:fn_fal", "tacz:mk14", "tacz:spr15hba", "tacz:skstactical" -> 7;
            case "tacz:m700", "tacz:ai_awp", "tacz:m95" -> 8;
            case "tacz:m320" -> 9;
            case "tacz:glock_17", "tacz:m1911", "tacz:timeless50", "tacz:deagle", "tacz:deagle_golden", "tacz:db_short" -> 10;
            default -> Integer.MAX_VALUE;
        };
    }

    private static Comparator<ItemStack> categoryComparator(Category category) {
        if (category == Category.HEAL) return Comparator.comparingInt(s -> classify(s).stage);
        if (category == Category.AMMO) return Comparator.comparing(AutoInventorySorter::ammoId);
        if (category == Category.THROWABLE) return Comparator.comparing(AutoInventorySorter::throwableId);
        if (category == Category.ATTACHMENT) return Comparator.comparing(AutoInventorySorter::itemId);
        return Comparator.comparing(AutoInventorySorter::itemId);
    }

    private static boolean verifyConservation(List<ItemStack> before, Inventory inv, List<ItemStack> dropped) {
        Map<String, Integer> a = countSignatures(before);
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : inv.items) if (!stack.isEmpty()) result.add(stack);
        result.addAll(dropped);
        return a.equals(countSignatures(result));
    }

    private static Map<String, Integer> countSignatures(List<ItemStack> stacks) {
        Map<String, Integer> counts = new HashMap<>();
        for (ItemStack stack : stacks) {
            String signature = itemId(stack) + "|" + (stack.getTag() == null ? "" : stack.getTag().toString());
            counts.merge(signature, stack.getCount(), Integer::sum);
        }
        return counts;
    }

    private record GunSelection(List<ItemStack> primary, ItemStack pistol) {}
    private record Classification(Category category, int stage) {}

    private enum Category {
        GUN,
        AMMO,
        THROWABLE,
        HEAL,
        ATTACHMENT,
        MELEE,
        FLARE,
        OTHER
    }
}
