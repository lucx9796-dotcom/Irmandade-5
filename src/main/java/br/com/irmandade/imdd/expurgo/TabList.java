package br.com.irmandade.imdd.expurgo;

import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

/** Tab: linhas ciano, Ping/TPS, "Feito por: LucxMC_", irmandade.gg. No Expurgo o aviso fica no topo (header). */
public final class TabList {
    private TabList() {}

    public static void update(MinecraftServer server, ServerPlayerEntity p) {
        Text line = Text.literal("━".repeat(28)).formatted(Formatting.AQUA);

        MutableText header = Text.empty().append(line);
        if (Expurgo.isActive()) {
            header.append(Text.literal("\n"))
                  .append(Text.literal("☠ Dia do Expurgo ☠").styled(s -> s.withColor(0x9f0206).withBold(true)))
                  .append(Text.literal("\n"))
                  .append(Text.literal(format(Expurgo.remainingMillis())).formatted(Formatting.GRAY));
        }

        double tps = Math.min(20.0, 1000.0 / Math.max(server.getAverageTickTime(), 1.0f));
        MutableText footer = Text.empty()
                .append(line).append(Text.literal("\n"))
                .append(Text.literal("Ping: ").formatted(Formatting.GRAY))
                .append(Text.literal(p.networkHandler.getLatency() + "ms").formatted(Formatting.GREEN))
                .append(Text.literal(" – TPS: ").formatted(Formatting.GRAY))
                .append(Text.literal(String.format(Locale.ROOT, "%.1f", tps)).formatted(Formatting.GREEN))
                .append(Text.literal("\n"))
                .append(Text.literal("Feito por: ").formatted(Formatting.GRAY))
                .append(Text.literal("LucxMC_").formatted(Formatting.AQUA))
                .append(Text.literal("\n\n"))
                .append(Text.literal("irmandade.gg").formatted(Formatting.GRAY));

        p.networkHandler.sendPacket(new PlayerListHeaderS2CPacket(header, footer));
    }

    private static String format(long ms) {
        long s = ms / 1000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }
}
