package br.com.irmandade.imdd.relic;

import br.com.irmandade.imdd.expurgo.Expurgo;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.WindChargeEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

/** Relíquias com habilidades ativas: Imortalidade, Espião, Vento, Warden, Dragão, Invisibilidade, Explorador. */
public final class RelicAbilities {
    /** ids de entidades cuja armadura deve ser escondida nos pacotes de equipamento (usado pelo mixin). */
    public static final Set<Integer> MASKED_IDS = Collections.synchronizedSet(new HashSet<>());

    private static final Map<UUID, Integer> INVIS_START = new HashMap<>();
    private static final Map<UUID, Integer> INVIS_END = new HashMap<>();
    private record Key(Identifier dim, long pos) {}
    private static final Map<Key, Integer> MAGMA = new HashMap<>();

    private RelicAbilities() {}

    public static void init() {
        UseItemCallback.EVENT.register((player, world, hand) -> onUseItem(player, world, hand));

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            // Ender chest bloqueado durante o Dia do Expurgo
            if (!world.isClient() && Expurgo.isActive() && world.getBlockState(hit.getBlockPos()).isOf(Blocks.ENDER_CHEST)) {
                player.sendMessage(Expurgo.enderChestMessage(), false);
                return ActionResult.FAIL;
            }
            // Dragão: não pode ser colocado; clicar em bloco também teleporta
            if (RelicRegistry.is(player.getStackInHand(hand), "dragao")) {
                if (!world.isClient()) dragonTeleport((ServerPlayerEntity) player);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        // Imortalidade: totem infinito com cooldown de 10s (não afeta outros totens)
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity p)) return true;
            if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
            ItemStack relic = RelicRegistry.is(p.getMainHandStack(), "imortalidade") ? p.getMainHandStack()
                    : RelicRegistry.is(p.getOffHandStack(), "imortalidade") ? p.getOffHandStack() : ItemStack.EMPTY;
            if (relic.isEmpty() || p.getItemCooldownManager().isCoolingDown(relic)) return true; // em cooldown: morre
            p.setHealth(1.0f);
            p.clearStatusEffects();
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 900, 1));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 100, 1));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 800, 0));
            p.getItemCooldownManager().set(relic, 200);
            ((ServerWorld) p.getEntityWorld()).sendEntityStatus(p, (byte) 35);
            return false;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int tick = server.getTicks();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) invisTick(p, tick);
            if (tick % 4 == 0) lavaWalk(server, tick);
        });
    }

    // ---------------- uso com botão direito ----------------

    private static ActionResult onUseItem(PlayerEntity p, World w, Hand hand) {
        ItemStack s = p.getStackInHand(hand);
        String id = RelicRegistry.idOf(s);
        if (id.isEmpty()) return ActionResult.PASS;
        boolean mine = id.equals("espiao") || id.equals("vento") || id.equals("warden") || id.equals("dragao");
        if (!mine) return ActionResult.PASS;
        if (!id.equals("dragao") && p.getItemCooldownManager().isCoolingDown(s)) return ActionResult.FAIL;
        if (w.isClient()) return ActionResult.SUCCESS;

        ServerPlayerEntity sp = (ServerPlayerEntity) p;
        ServerWorld sw = (ServerWorld) w;
        if (id.equals("espiao")) {
            // Espião nível 2 por 8 min; cooldown 20 min
            sp.addStatusEffect(new StatusEffectInstance(SpyEffect.SPY, 8 * 60 * 20, 1, false, true, true));
            sp.getItemCooldownManager().set(s, 20 * 60 * 20);
        } else if (id.equals("vento")) {
            Vec3d eye = sp.getEyePos();
            for (float off : new float[]{-4f, 4f}) {
                WindChargeEntity wc = new WindChargeEntity(sp, sw, eye.x, eye.y, eye.z);
                wc.setVelocity(sp, sp.getPitch(), sp.getYaw() + off, 0f, 1.5f, 0f);
                sw.spawnEntity(wc);
            }
            sw.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ENTITY_WIND_CHARGE_THROW, SoundCategory.PLAYERS, 0.5f, 1.0f);
            sp.getItemCooldownManager().set(s, 6);
        } else if (id.equals("warden")) {
            sonicBoom(sp, sw);
            sp.getItemCooldownManager().set(s, 200);
        } else {
            dragonTeleport(sp);
        }
        return ActionResult.SUCCESS;
    }

    private static void sonicBoom(ServerPlayerEntity p, ServerWorld w) {
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d end = eye.add(look.multiply(15));
        for (int i = 1; i <= 15; i++) {
            Vec3d pt = eye.add(look.multiply(i));
            w.spawnParticles(ParticleTypes.SONIC_BOOM, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
        }
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 3.0f, 1.0f);
        Box area = p.getBoundingBox().stretch(look.multiply(15)).expand(1.0);
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, area, x -> x != p && x.isAlive())) {
            if (e.getBoundingBox().expand(0.6).raycast(eye, end).isPresent()) {
                e.damage(w, w.getDamageSources().sonicBoom(p), 10.0f);
                double res = 1.0 - e.getAttributeValue(EntityAttributes.KNOCKBACK_RESISTANCE);
                e.addVelocity(look.x * 2.5 * res, look.y * 0.5 * res, look.z * 2.5 * res);
                if (e instanceof ServerPlayerEntity other) other.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(other));
            }
        }
    }

    /** Mesmo teleporte aleatório da chorus fruit (raio 8), sem cooldown. */
    private static void dragonTeleport(ServerPlayerEntity p) {
        ServerWorld w = (ServerWorld) p.getEntityWorld();
        double ox = p.getX(), oy = p.getY(), oz = p.getZ();
        for (int i = 0; i < 16; i++) {
            double x = ox + (p.getRandom().nextDouble() - 0.5) * 16.0;
            double y = MathHelper.clamp(oy + (p.getRandom().nextInt(16) - 8), w.getBottomY(), w.getBottomY() + w.getLogicalHeight() - 1);
            double z = oz + (p.getRandom().nextDouble() - 0.5) * 16.0;
            if (p.hasVehicle()) p.stopRiding();
            if (p.teleport(x, y, z, true)) {
                w.playSound(null, ox, oy, oz, SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                break;
            }
        }
    }

    // ---------------- Invisibilidade (nível 11 + armadura invisível) ----------------

    private static void invisTick(ServerPlayerEntity p, int tick) {
        UUID id = p.getUuid();
        boolean equipped = RelicRegistry.is(p.getEquippedStack(EquipmentSlot.FEET), "invisibilidade");
        Integer start = INVIS_START.get(id);
        if (equipped) {
            INVIS_END.remove(id);
            if (start == null) { INVIS_START.put(id, tick); return; }
            int t = tick - start;
            if (t == 10) { MASKED_IDS.add(p.getId()); broadcastEquipment(p); }   // 1º: a armadura some (delay 10 ticks)
            if (t >= 20) p.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 40, 10, true, false, false)); // 2º: o player some
            if (t > 10 && tick % 20 == 0) broadcastEquipment(p);                 // reforça contra pacotes que vazem a armadura
        } else if (start != null) {
            INVIS_START.remove(id);
            MASKED_IDS.remove(p.getId());
            broadcastEquipment(p);                                               // 1º: a armadura aparece
            INVIS_END.put(id, tick + 10);                                        // 2º: depois acaba o efeito
        }
        Integer end = INVIS_END.get(id);
        if (end != null && tick >= end) {
            INVIS_END.remove(id);
            p.removeStatusEffect(StatusEffects.INVISIBILITY);
        }
    }

    private static void broadcastEquipment(ServerPlayerEntity p) {
        List<Pair<EquipmentSlot, ItemStack>> list = new ArrayList<>();
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            list.add(Pair.of(s, p.getEquippedStack(s).copy()));
        }
        ((ServerWorld) p.getEntityWorld()).getChunkManager().sendToOtherNearbyPlayers(p, new EntityEquipmentUpdateS2CPacket(p.getId(), list));
    }

    /** Chamado pelo mixin do pacote: esconde tudo, menos elytra, cabeças de mob/player e abóbora. */
    public static List<Pair<EquipmentSlot, ItemStack>> maskEquipment(List<Pair<EquipmentSlot, ItemStack>> list) {
        List<Pair<EquipmentSlot, ItemStack>> out = new ArrayList<>();
        for (Pair<EquipmentSlot, ItemStack> pair : list) {
            EquipmentSlot slot = pair.getFirst();
            ItemStack st = pair.getSecond();
            ItemStack keep = st;
            if (slot == EquipmentSlot.HEAD) {
                if (!(st.isOf(Items.CARVED_PUMPKIN) || st.isIn(ItemTags.SKULLS))) keep = ItemStack.EMPTY;
            } else if (slot == EquipmentSlot.CHEST) {
                if (!st.isOf(Items.ELYTRA)) keep = ItemStack.EMPTY;
            } else if (slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET) {
                keep = ItemStack.EMPTY;
            }
            out.add(Pair.of(slot, keep));
        }
        return out;
    }

    // ---------------- Explorador: caminho de magma sobre a lava ----------------

    private static void lavaWalk(MinecraftServer server, int tick) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!RelicRegistry.is(p.getEquippedStack(EquipmentSlot.FEET), "explorador")) continue;
            ServerWorld w = (ServerWorld) p.getEntityWorld();
            BlockPos base = p.getBlockPos().down();
            int r = 3;
            for (BlockPos pos : BlockPos.iterate(base.add(-r, 0, -r), base.add(r, 0, r))) {
                if (pos.getSquaredDistance(base) > r * r + 1) continue;
                BlockState st = w.getBlockState(pos);
                if (st.isOf(Blocks.LAVA) && st.getFluidState().isStill() && w.getBlockState(pos.up()).isAir()) {
                    w.setBlockState(pos, Blocks.MAGMA_BLOCK.getDefaultState());
                    MAGMA.put(new Key(w.getRegistryKey().getValue(), pos.asLong()), tick + 60 + p.getRandom().nextInt(60));
                }
            }
        }
        Iterator<Map.Entry<Key, Integer>> it = MAGMA.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, Integer> e = it.next();
            if (tick < e.getValue()) continue;
            ServerWorld w = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, e.getKey().dim()));
            if (w == null) { it.remove(); continue; }
            BlockPos pos = BlockPos.fromLong(e.getKey().pos());
            if (!w.getBlockState(pos).isOf(Blocks.MAGMA_BLOCK)) { it.remove(); continue; }
            boolean someoneOn = !w.getPlayers(pl -> RelicRegistry.is(pl.getEquippedStack(EquipmentSlot.FEET), "explorador")
                    && pl.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5) < 16).isEmpty();
            if (someoneOn) { e.setValue(tick + 20); continue; }
            w.setBlockState(pos, Blocks.LAVA.getDefaultState());
            it.remove();
        }
    }
}
