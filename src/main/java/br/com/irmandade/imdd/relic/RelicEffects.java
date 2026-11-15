package br.com.irmandade.imdd.relic;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Propriedades das relíquias (fase 1): Fazendeiro, Sorte, Nether, Invasor, Defesa, Stomper. */
public final class RelicEffects {
    private static final Map<UUID, Integer> INVASOR_NEXT = new HashMap<>();

    private RelicEffects() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int tick = server.getTicks();
            if (tick % 10 != 0) return;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) tickPlayer(p, tick);
        });

        // Stomper: na mão principal, sem dano de queda
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity p
                    && source.isIn(DamageTypeTags.IS_FALL)
                    && RelicRegistry.is(p.getMainHandStack(), "stomper")) {
                return false;
            }
            return true;
        });

        // Defesa: espinhos 360° com a relíquia em qualquer mão
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> thorns(entity, source));
    }

    private static void tickPlayer(ServerPlayerEntity p, int tick) {
        // Fazendeiro (calça equipada): Herói da Vila V
        if (RelicRegistry.is(p.getEquippedStack(EquipmentSlot.LEGS), "fazendeiro")) {
            refresh(p, StatusEffects.HERO_OF_THE_VILLAGE, 4);
        }
        // Nether (capacete equipado): Resistência ao Fogo
        if (RelicRegistry.is(p.getEquippedStack(EquipmentSlot.HEAD), "nether")) {
            refresh(p, StatusEffects.FIRE_RESISTANCE, 0);
        }
        // Sorte (em qualquer lugar do inventário): Sorte X
        for (int i = 0; i < p.getInventory().size(); i++) {
            if (RelicRegistry.is(p.getInventory().getStack(i), "sorte")) {
                refresh(p, StatusEffects.LUCK, 9);
                break;
            }
        }
        // Invasor (calça equipada): Bad Omen 5s ligado / 10s desligado; não ativa com Raid Omen/Trial Omen
        if (RelicRegistry.is(p.getEquippedStack(EquipmentSlot.LEGS), "invasor")) {
            int next = INVASOR_NEXT.getOrDefault(p.getUuid(), 0);
            if (tick >= next && !p.hasStatusEffect(StatusEffects.RAID_OMEN) && !p.hasStatusEffect(StatusEffects.TRIAL_OMEN)) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.BAD_OMEN, 100, 0, false, true, true));
                INVASOR_NEXT.put(p.getUuid(), tick + 100 + 200);
            }
        } else {
            INVASOR_NEXT.remove(p.getUuid());
        }
    }

    private static void refresh(ServerPlayerEntity p, RegistryEntry<StatusEffect> effect, int amplifier) {
        p.addStatusEffect(new StatusEffectInstance(effect, 40, amplifier, true, false, true));
    }

    private static void thorns(LivingEntity victim, DamageSource source) {
        if (!(victim instanceof ServerPlayerEntity p)) return;
        if (source.isOf(DamageTypes.THORNS)) return;
        if (!(source.getAttacker() instanceof LivingEntity attacker) || attacker == p) return;
        ItemStack main = p.getMainHandStack(), off = p.getOffHandStack();
        if (!RelicRegistry.is(main, "defesa") && !RelicRegistry.is(off, "defesa")) return;
        if (p.getRandom().nextFloat() < 0.75f) { // Thorns V = 15% x 5
            attacker.damage((ServerWorld) p.getEntityWorld(), p.getDamageSources().thorns(p), 1 + p.getRandom().nextInt(4));
        }
    }
}
