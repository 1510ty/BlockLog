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
                    logAsync(p, e.getClickedBlock(), 1);
                }
            }
            return;
        }

        // --- ここから調査モード (inspect) の処理 ---
        if (e.getClickedBlock() == null) return;
        e.setCancelled(true);

        // 論理モードの決定 (左クリックなら破壊/設置モード(0)、右なら操作モード(1))
        int viewMode = (e.getAction() == Action.LEFT_CLICK_BLOCK) ? 0 : 1;
        Block b = e.getClickedBlock();

        // Mainクラスの一元化メソッドを呼び出す
        plugin.showLogPage(p, b.getX(), b.getY(), b.getZ(), viewMode, 0);
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