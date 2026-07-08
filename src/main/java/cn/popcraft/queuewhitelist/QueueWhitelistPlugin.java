package cn.popcraft.queuewhitelist;

import cn.popcraft.queuewhitelist.command.QueueWhitelistCommand;
import cn.popcraft.queuewhitelist.config.QueueConfig;
import cn.popcraft.queuewhitelist.database.DatabaseManager;
import cn.popcraft.queuewhitelist.listener.PlayerLoginListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class QueueWhitelistPlugin extends JavaPlugin {
    private DatabaseManager databaseManager;
    private QueueConfig queueConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        queueConfig = QueueConfig.from(this);
        databaseManager = new DatabaseManager(this);
        databaseManager.open();
        databaseManager.initialize(queueConfig.threshold());

        PlayerLoginListener listener = new PlayerLoginListener(this, databaseManager);
        getServer().getPluginManager().registerEvents(listener, this);

        QueueWhitelistCommand command = new QueueWhitelistCommand(this, databaseManager);
        PluginCommand pluginCommand = getCommand("queuewl");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        getLogger().info("队列白名单插件已启用，当前触发人数阈值：" + databaseManager.getThreshold());
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("队列白名单插件已关闭。");
    }

    public void reloadQueueConfig() {
        reloadConfig();
        queueConfig = QueueConfig.from(this);
        getLogger().info("配置已重新加载。");
    }

    public QueueConfig queueConfig() {
        return queueConfig;
    }
}
