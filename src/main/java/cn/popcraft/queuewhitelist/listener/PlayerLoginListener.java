package cn.popcraft.queuewhitelist.listener;

import cn.popcraft.queuewhitelist.QueueWhitelistPlugin;
import cn.popcraft.queuewhitelist.config.QueueConfig;
import cn.popcraft.queuewhitelist.database.DatabaseManager;
import cn.popcraft.queuewhitelist.database.WhitelistEntry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.Optional;

public final class PlayerLoginListener implements Listener {
    private final QueueWhitelistPlugin plugin;
    private final DatabaseManager databaseManager;

    public PlayerLoginListener(QueueWhitelistPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerLogin(PlayerLoginEvent event) {
        int threshold = databaseManager.getThreshold();
        int onlinePlayers = plugin.getServer().getOnlinePlayers().size();
        if (onlinePlayers < threshold) {
            return;
        }

        QueueConfig config = plugin.queueConfig();
        String playerName = event.getPlayer().getName();
        if (mayBypass(event, config)) {
            return;
        }

        Optional<WhitelistEntry> optionalEntry = databaseManager.findWhitelist(playerName);
        if (optionalEntry.isEmpty()) {
            event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, format(config.kickMessage(), threshold, onlinePlayers));
            plugin.getLogger().info("玩家 " + playerName + " 进入被拒绝：当前人数 " + onlinePlayers + "，达到阈值 " + threshold + "，且没有队列白名单。");
            return;
        }

        WhitelistEntry entry = optionalEntry.get();
        long now = System.currentTimeMillis() / 1000L;
        if (entry.isExpired(now)) {
            if (config.removeExpiredOnLogin()) {
                databaseManager.removeWhitelist(playerName);
            }
            event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, format(config.expiredKickMessage(), threshold, onlinePlayers));
            plugin.getLogger().info("玩家 " + playerName + " 进入被拒绝：队列白名单已过期。");
            return;
        }

        plugin.getLogger().info("玩家 " + playerName + " 已通过队列白名单检查。当前人数：" + onlinePlayers + "，阈值：" + threshold + "。");
    }

    private boolean mayBypass(PlayerLoginEvent event, QueueConfig config) {
        if (config.bypassOp() && event.getPlayer().isOp()) {
            return true;
        }
        return config.bypassPermission() && event.getPlayer().hasPermission("queuewhitelist.bypass");
    }

    private String format(String message, int threshold, int onlinePlayers) {
        return message
                .replace("{threshold}", Integer.toString(threshold))
                .replace("{online}", Integer.toString(onlinePlayers));
    }
}
