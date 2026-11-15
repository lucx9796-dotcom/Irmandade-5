package br.com.irmandade.imdd.expurgo;

import br.com.irmandade.imdd.tab.TabConfig;
import br.com.irmandade.imdd.tab.TabText;
import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

/** Monta o tab a partir de TabConfig (editável pelo comando /tab). */
public final class TabList {
    private TabList() {}

    public static void updateAll(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) update(server, p);
    }

    public static void update(MinecraftServer server, ServerPlayerEntity p) {
        p.networkHandler.sendPacket(new PlayerListHeaderS2CPacket(
                build(server, p, TabConfig.HEADER), build(server, p, TabConfig.FOOTER)));
    }

    private static Text build(MinecraftServer server, ServerPlayerEntity p, List<String> lines) {
        MutableText out = Text.empty();
        boolean first = true;
        for (String raw : lines) {
            List<String> expanded;
            if (raw.trim().equals("%expurgo%")) {
                if (!Expurgo.isActive()) continue; // sem Expurgo, a linha some
                expanded = List.of("&#9f0206&lDia do Expurgo", "&7" + format(Expurgo.remainingMillis()));
            } else {
                expanded = List.of(replace(server, p, raw));
            }
            for (String line : expanded) {
                if (!first) out.append(Text.literal("\n"));
                first = false;
                out.append(TabText.parse(line));
            }
        }
        return out;
    }

    private static String replace(MinecraftServer server, ServerPlayerEntity p, String s) {
        double tps = Math.min(20.0, 1000.0 / Math.max(server.getAverageTickTime(), 1.0f));
        return s.replace("%ping%", String.valueOf(p.networkHandler.getLatency()))
                .replace("%tps%", String.format(Locale.ROOT, "%.1f", tps))
                .replace("%player%", p.getName().getString())
                .replace("%online%", String.valueOf(server.getPlayerManager().getPlayerList().size()))
                .replace("%expurgo_time%", format(Expurgo.remainingMillis()));
    }

    private static String format(long ms) {
        long s = ms / 1000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }
}
