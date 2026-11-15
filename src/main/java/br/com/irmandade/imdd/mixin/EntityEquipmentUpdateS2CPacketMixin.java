package br.com.irmandade.imdd.mixin;

import br.com.irmandade.imdd.relic.RelicAbilities;
import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/** Esconde a armadura de quem usa a Relíquia da Invisibilidade em todos os pacotes de equipamento. */
@Mixin(EntityEquipmentUpdateS2CPacket.class)
public abstract class EntityEquipmentUpdateS2CPacketMixin {

    @ModifyVariable(method = "<init>(ILjava/util/List;)V", at = @At("HEAD"), argsOnly = true, require = 0)
    private static List<Pair<EquipmentSlot, ItemStack>> imdd$mascarar(List<Pair<EquipmentSlot, ItemStack>> list, int id) {
        return RelicAbilities.MASKED_IDS.contains(id) ? RelicAbilities.maskEquipment(list) : list;
    }
}
