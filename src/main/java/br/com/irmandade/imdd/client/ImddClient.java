package br.com.irmandade.imdd.client;

import br.com.irmandade.imdd.net.ExpurgoPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Cliente: marca d'água no F3 (direita, embaixo). */
public class ImddClient implements ClientModInitializer {
    private static final String LINE1 = "Irmandade By LucxMC";
    private static final String LINE2 = "-. .. -. --. ..- . -- / . ... - .- / ... . --. ..- .-. --- /";
    private static volatile long endMs = 0;

    /** true enquanto o Dia do Expurgo está ativo (o servidor envia o tempo restante). */
    public static boolean expurgoActive() {
        return endMs > System.currentTimeMillis();
    }

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ExpurgoPayload.ID, (payload, context) ->
                endMs = payload.remainingMillis() > 0 ? System.currentTimeMillis() + payload.remainingMillis() : 0);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> endMs = 0);
        TranslucentBlocks.register();
        VanishClient.init();
        HudElementRegistry.addLast(Identifier.of("imdd", "overlay"), ImddClient::render);
    }

    private static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();

        if (overlayVisible(mc)) {
            int y = Math.min(Math.max(h / 2 + 25, 372), h - 30);   // direita, logo abaixo das linhas do F3 (que terminam por volta de y=360)
            drawLine(ctx, mc, LINE1, w, y, 0xFFFF55FF);       // Light Purple
            drawLine(ctx, mc, LINE2, w, y + 9, 0xFFFF5555);   // Light Red
        }
    }

    // ---------- só mostra com a tela F3 de verdade ----------

    private static boolean f3Resolved;
    private static Field f3ListField;
    private static Method f3Method;

    /**
     * shouldShowDebugHud() também fica true quando só uma opção de debug está ligada (F3+B = hitboxes, F3+G = bordas de chunk...),
     * mesmo sem a tela F3. Por isso confirmo com DebugHudEntryList.isF3Enabled(), resolvido em tempo de execução pelo
     * MappingResolver (funciona no dev e em produção). Se não achar, cai no comportamento antigo.
     */
    private static boolean overlayVisible(MinecraftClient mc) {
        if (!mc.getDebugHud().shouldShowDebugHud()) return false;
        Boolean f3 = f3Enabled(mc);
        return f3 == null || f3;
    }

    private static Boolean f3Enabled(MinecraftClient mc) {
        try {
            if (!f3Resolved) {
                f3Resolved = true;
                MappingResolver r = FabricLoader.getInstance().getMappingResolver();
                String fieldName = r.mapFieldName("named", "net.minecraft.client.MinecraftClient", "debugHudEntryList",
                        "Lnet/minecraft/client/gui/hud/debug/DebugHudEntryList;");
                String cls = "net.minecraft.client.gui.hud.debug.DebugHudEntryList";
                String methodName = r.mapMethodName("named", cls, "isF3Enabled", "()Z");
                Field f = MinecraftClient.class.getDeclaredField(fieldName);
                f.setAccessible(true);
                Method m = f.getType().getDeclaredMethod(methodName);
                m.setAccessible(true);
                f3ListField = f;
                f3Method = m;
            }
            if (f3ListField == null || f3Method == null) return null;
            Object list = f3ListField.get(mc);
            return list == null ? null : (Boolean) f3Method.invoke(list);
        } catch (Throwable t) {
            f3ListField = null;
            f3Method = null;
            br.com.irmandade.imdd.Imdd.LOGGER.warn("Marca d'água do F3: não consegui checar isF3Enabled ({})", t.toString());
            return null;
        }
    }

    private static void drawLine(DrawContext ctx, MinecraftClient mc, String text, int screenW, int y, int color) {
        int width = mc.textRenderer.getWidth(text);
        int x = screenW - 2 - width;
        ctx.fill(x - 1, y - 1, x + width + 1, y + 8, 0x90505050);
        ctx.drawText(mc.textRenderer, text, x, y, color, false);
    }
}
