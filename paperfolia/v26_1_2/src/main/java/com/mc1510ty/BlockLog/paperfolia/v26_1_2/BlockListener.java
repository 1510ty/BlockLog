package com.mc1510ty.BlockLog.paperfolia.v26_1_2;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.UUID;

public class BlockListener implements Listener {
    private final Main plugin;
    public BlockListener(Main plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        logAsync(e.getPlayer(), e.getBlock(), 0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        logAsync(e.getPlayer(), e.getBlock(), 1);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();



        // 調査モード中かチェック
        if (!plugin.getInspectors().contains(uuid)) {
            // 通常時のログ記録（チェスト開閉など）
            if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
                Material type = e.getClickedBlock().getType();
                if (Tag.BUTTONS.isTagged(type) || type == Material.LEVER || Tag.DOORS.isTagged(type) ||
                        Tag.FENCE_GATES.isTagged(type) || type == Material.CHEST || type == Material.BARREL) {
                    logAsync(p, e.getClickedBlock(), 4);
                }
            }
            return;
        }

        // --- ここから調査モード (inspect) の処理 ---
        if (e.getClickedBlock() == null) return;

        // 調査時は本来のアクション（破壊・設置・開閉）をキャンセル
        e.setCancelled(true);

        Block b = e.getClickedBlock();
        String world = b.getWorld().getName();
        int x = b.getX(), y = b.getY(), z = b.getZ();
        int initialPage = 0; // 最初のページ番号を定義


        // アクションの選別 (左: 設置破壊, 右: 操作系)
        int[] actions = (e.getAction() == Action.LEFT_CLICK_BLOCK) ? new int[]{0, 1} : new int[]{4, 5};

        // 非同期検索の開始
        plugin.getDatabase().getLogs(world, x, y, z, actions, initialPage).thenAccept(logs -> {
            p.getScheduler().execute(plugin, () -> {
                if (logs.isEmpty()) {
                    p.sendMessage("[BlockLog] 履歴は見つかりませんでした");
                } else {
                    p.sendMessage("[BlockLog] 調査結果 (" + x + ", " + y + ", " + z + "):");
                    for (String msg : logs) {
                        p.sendMessage(msg);
                    }
                    plugin.sendNextButton(p, x, y, z, initialPage + 1);
                }
            }, null, 1L);
        });
    }

    private void logAsync(Player p, Block b, int action) {
        // スナップショット（不変データ）の作成
        String uuidStr = p.getUniqueId().toString();
        String world = b.getWorld().getName();
        String type = b.getType().toString();
        String blockData = b.getBlockData().getAsString();
        int x = b.getX(), y = b.getY(), z = b.getZ();

        // Databaseクラス内で既に非同期（CompletableFuture）で動く想定
        plugin.getDatabase().log(uuidStr, world, action, type, blockData, x, y, z);
    }
}