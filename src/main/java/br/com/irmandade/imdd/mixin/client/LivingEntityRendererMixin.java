package br.com.irmandade.imdd.mixin.client;

import br.com.irmandade.imdd.relic.RelicRegistry;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Relíquia da Invisibilidade: esconde a armadura (só elytra, cabeças de mob/player e abóbora continuam). */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"), require = 0)
    private void imdd$esconderArmadura(LivingEntity entity, LivingEntityRenderState state, float tickProgress, CallbackInfo ci) {
        if (!RelicRegistry.is(entity.getEquippedStack(EquipmentSlot.FEET), "invisibilidade")) return;
        state.equippedHeadStack = ItemStack.EMPTY;
        if (!state.equippedChestStack.isOf(Items.ELYTRA)) state.equippedChestStack = ItemStack.EMPTY;
        state.equippedLegsStack = ItemStack.EMPTY;
        state.equippedFeetStack = ItemStack.EMPTY;
    }
}
