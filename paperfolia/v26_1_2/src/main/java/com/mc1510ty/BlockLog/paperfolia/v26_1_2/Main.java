package com.mc1510ty.BlockLog.paperfolia.v26_1_2;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
    // 第2引数に worldName を追加
    public void showLogPage(Player player, String worldName, int x, int y, int z, int viewMode, int page) {
        // プレイヤーの現在地ではなく、引数の worldName を使用
        database.getLogs(worldName, x, y, z, viewMode, page)
                .thenAccept(logs -> {
                    player.getScheduler().execute(this, () -> {
                        if (logs.isEmpty()) {
                            player.sendMessage("§b[HS] §cこれ以上の履歴はありません。");
                            return;
                        }
                        String modeName = (viewMode == 0) ? "破壊/設置" : "操作";
                        // メッセージにもワールド名を入れておくと親切
                        player.sendMessage("§b[HS] §e" + worldName + " §f" + modeName + "履歴 (" + x + "," + y + "," + z + ") " + (page + 1) + "P:");
                        logs.forEach(player::sendMessage);

                        // クリックイベントのコマンドに worldName を追加！
                        Component nextBtn = Component.text("[ 次の5件を表示 ]")
                                .color(NamedTextColor.AQUA)
                                .clickEvent(ClickEvent.runCommand("/hs p " + worldName + " " + x + " " + y + " " + z + " " + viewMode + " " + (page + 1)));
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
                        .then(Commands.argument("world", StringArgumentType.string()) // ワールド名を最初に追加
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .then(Commands.argument("m", IntegerArgumentType.integer())
                                                                .then(Commands.argument("p", IntegerArgumentType.integer())
                                                                        .executes(ctx -> {
                                                                            if (!(ctx.getSource().getSender() instanceof Player p)) return 0;

                                                                            // ワールド名を取得
                                                                            String worldName = ctx.getArgument("world", String.class);

                                                                            showLogPage(p, worldName, // 引数に追加
                                                                                    ctx.getArgument("x", int.class),
                                                                                    ctx.getArgument("y", int.class),
                                                                                    ctx.getArgument("z", int.class),
                                                                                    ctx.getArgument("m", int.class),
                                                                                    ctx.getArgument("p", int.class));
                                                                            return Command.SINGLE_SUCCESS;
                                                                        }))))))));
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(hscommand.build());
        });
    }

    public Database getDatabase() { return database; }
    public Set<UUID> getInspectors() { return inspectors; }
}