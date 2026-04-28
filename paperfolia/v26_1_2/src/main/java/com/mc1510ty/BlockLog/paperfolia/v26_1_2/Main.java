package com.mc1510ty.BlockLog.paperfolia.v26_1_2;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Main extends JavaPlugin {

    private Database database;
    private final Set<UUID> inspectors = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        database = new Database(this);
        database.initialize();

        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        registerCommands();
        getLogger().info("BlockLog Enabled (Mode-Mapped Architecture)");
    }

    // 表示ロジックの一元化メソッド
    public void showLogPage(Player player, int x, int y, int z, int viewMode, int page) {
        database.getLogs(player.getWorld().getName(), x, y, z, viewMode, page)
                .thenAccept(logs -> {
                    player.getScheduler().execute(this, () -> {
                        if (logs.isEmpty()) {
                            player.sendMessage("§b[HS] §cこれ以上の履歴はありません。");
                            return;
                        }
                        String modeName = (viewMode == 0) ? "破壊/設置" : "操作";
                        player.sendMessage("§b[HS] §f" + modeName + "履歴 (" + x + "," + y + "," + z + ") " + (page + 1) + "P:");
                        logs.forEach(player::sendMessage);

                        // 「次を表示」ボタン (viewMode を維持して生成)
                        Component nextBtn = Component.text("[ 次の5件を表示 ]")
                                .color(NamedTextColor.AQUA)
                                .clickEvent(ClickEvent.runCommand("/hs p " + x + " " + y + " " + z + " " + viewMode + " " + (page + 1)));
                        player.sendMessage(nextBtn);
                    }, null, 1L);
                });
    }

    public void registerCommands() {
        LiteralArgumentBuilder<CommandSourceStack> hscommand = Commands.literal("hs")
                .requires(s -> s.getSender().hasPermission("blocklog.admin"))
                .then(Commands.literal("i").executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player p)) return 0;
                    if (inspectors.contains(p.getUniqueId())) {
                        inspectors.remove(p.getUniqueId());
                        p.sendMessage("§b[HS] §fインスペクター: §cOFF");
                    } else {
                        inspectors.add(p.getUniqueId());
                        p.sendMessage("§b[HS] §fインスペクター: §aON");
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                // 内部・手動共用表示コマンド: /hs p <x> <y> <z> <viewMode> <page>
                .then(Commands.literal("p")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .then(Commands.argument("m", IntegerArgumentType.integer())
                                                        .then(Commands.argument("p", IntegerArgumentType.integer())
                                                                .executes(ctx -> {
                                                                    if (!(ctx.getSource().getSender() instanceof Player p)) return 0;
                                                                    showLogPage(p, ctx.getArgument("x", int.class), ctx.getArgument("y", int.class),
                                                                            ctx.getArgument("z", int.class), ctx.getArgument("m", int.class),
                                                                            ctx.getArgument("p", int.class));
                                                                    return Command.SINGLE_SUCCESS;
                                                                })))))));

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(hscommand.build());
        });
    }

    public Database getDatabase() { return database; }
    public Set<UUID> getInspectors() { return inspectors; }
}