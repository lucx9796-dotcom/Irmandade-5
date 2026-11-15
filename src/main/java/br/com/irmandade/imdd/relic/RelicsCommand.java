package br.com.irmandade.imdd.relic;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

public final class RelicsCommand {
    private RelicsCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("relics")
                // Só operadores (nível 2). Para liberar a todos, remova a linha abaixo.
                .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                .executes(ctx -> {
                    RelicsMenu.open(ctx.getSource().getPlayerOrThrow());
                    return 1;
                }));
    }
}
