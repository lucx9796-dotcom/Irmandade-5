package br.com.irmandade.imdd.client;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Esconde a armadura no render state sem depender dos nomes dos campos (que mudam entre versões):
 * varre os campos do tipo ItemStack e esvazia os que guardam armadura. Elytra, abóbora e cabeças ficam.
 */
public final class ArmorHider {
    private static final Map<Class<?>, List<Field>> CACHE = new ConcurrentHashMap<>();

    private ArmorHider() {}

    private static List<Field> stackFields(Class<?> type) {
        return CACHE.computeIfAbsent(type, k -> {
            List<Field> out = new ArrayList<>();
            for (Class<?> c = k; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType() == ItemStack.class && !Modifier.isStatic(f.getModifiers())) {
                        try { f.setAccessible(true); out.add(f); } catch (Exception ignored) {}
                    }
                }
            }
            return out;
        });
    }

    public static void hide(Object state) {
        for (Field f : stackFields(state.getClass())) {
            try {
                ItemStack cur = (ItemStack) f.get(state);
                if (cur == null || cur.isEmpty()) continue;
                if (cur.isOf(Items.ELYTRA) || cur.isOf(Items.CARVED_PUMPKIN) || cur.isIn(ItemTags.SKULLS)) continue;
                EquippableComponent eq = cur.get(DataComponentTypes.EQUIPPABLE);
                if (eq == null) continue;
                EquipmentSlot s = eq.slot();
                if (s == EquipmentSlot.HEAD || s == EquipmentSlot.CHEST || s == EquipmentSlot.LEGS || s == EquipmentSlot.FEET) {
                    f.set(state, ItemStack.EMPTY);
                }
            } catch (Exception ignored) {}
        }
    }
}
