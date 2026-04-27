package com.mc1510ty.BlockLog.paperfolia.v26_1_2;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class Main extends JavaPlugin implements Listener {

    private Database database;
    private final Set<UUID> inspectors = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public void onEnable() {
        // 1. プラグインフォルダの作成 (無いとSQLiteが死ぬので確実に)
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // 2. データベースの初期化
        database = new Database(this);
        database.initialize();

        // 3. イベントリスナーの登録
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);

        // 4. コマンドの登録
        registerCommands();

        getLogger().info("BlockLog has been enabled! (Async, Fast and Free)");
    }

    @Override
    public void onDisable() {
        // 4. データベース接続の安全なクローズ
        if (database != null) {
            database.close();
        }
        inspectors.clear();
        getLogger().info("BlockLog has been disabled.");
    }

    public Database getDatabase() {
        return database;
    }
    public Set<UUID> getInspectors() {
        return inspectors;
    }

    public void registerCommands() {
        LiteralArgumentBuilder<CommandSourceStack> hscommand = Commands.literal("hs")
                .requires(source -> source.getSender().hasPermission("blocklog.inspector"))
                // メインのトグルコマンド: /hs i
                .then(Commands.literal("i")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                            UUID uuid = player.getUniqueId();

                            if (inspectors.contains(uuid)) {
                                inspectors.remove(uuid);
                                player.sendMessage("[BlockLog] インスペクターを§c無効化§rしました");
                            } else {
                                inspectors.add(uuid);
                                player.sendMessage("[BlockLog] インスペクターを§a有効化§rしました");
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                )
                // ボタン用コマンド: /hs page <x> <y> <z> <page>
                .then(Commands.literal("page")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .then(Commands.argument("p", IntegerArgumentType.integer())
                                                        .executes(ctx -> {
                                                            if (!(ctx.getSource().getSender() instanceof Player player)) return 0;

                                                            int x = ctx.getArgument("x", Integer.class);
                                                            int y = ctx.getArgument("y", Integer.class);
                                                            int z = ctx.getArgument("z", Integer.class);
                                                            int page = ctx.getArgument("p", Integer.class);

                                                            // Databaseから次のページを取得して表示 (BlockListenerと同様の処理を呼ぶ)
                                                            // ここはアクション 0,1,4 をまとめて検索する仕様にすると楽です
                                                            this.getDatabase().getLogs(player.getWorld().getName(), x, y, z, new int[]{0, 1, 4}, page)
                                                                    .thenAccept(logs -> {
                                                                        player.getScheduler().execute(this, () -> {
                                                                            if (logs.isEmpty()) {
                                                                                player.sendMessage("[BlockLog] 履歴はありません");
                                                                            } else {
                                                                                player.sendMessage("[BlockLog] 調査結果 (" + x + ", " + y + ", " + z + ") " + (page + 1) + "ページ目:");
                                                                                logs.forEach(player::sendMessage);
                                                                                sendNextButton(player, x, y, z, page + 1);
                                                                            }
                                                                        }, null, 1L);
                                                                    });
                                                            return Command.SINGLE_SUCCESS;
                                                        })
                                                ))))
                );

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(hscommand.build());
        });
    }

    // ボタン送信用の共通メソッド
    public void sendNextButton(Player p, int x, int y, int z, int nextPage) {
        p.sendMessage(net.kyori.adventure.text.Component.text("§b次の5件を表示")
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/hs page " + x + " " + y + " " + z + " " + nextPage)));
    }
}
