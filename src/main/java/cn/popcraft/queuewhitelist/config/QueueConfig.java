package cn.popcraft.queuewhitelist.config;

import org.bukkit.plugin.java.JavaPlugin;

public record QueueConfig(
        int threshold,
        boolean bypassOp,
        boolean bypassPermission,
        boolean removeExpiredOnLogin,
        DatabaseConfig databaseConfig,
        String kickMessage,
        String expiredKickMessage
) {
    public static QueueConfig from(JavaPlugin plugin) {
        int threshold = Math.max(0, plugin.getConfig().getInt("threshold", 100));
        boolean bypassOp = plugin.getConfig().getBoolean("bypass-op", true);
        boolean bypassPermission = plugin.getConfig().getBoolean("bypass-permission", true);
        boolean removeExpiredOnLogin = plugin.getConfig().getBoolean("remove-expired-on-login", true);
        DatabaseConfig databaseConfig = DatabaseConfig.from(plugin);
        String kickMessage = plugin.getConfig().getString("messages.kick", "服务器当前人数已满，你需要有效的队列白名单才能进入。");
        String expiredKickMessage = plugin.getConfig().getString("messages.kick-expired", "你的队列白名单已经过期，请重新获取后再进入服务器。");
        return new QueueConfig(threshold, bypassOp, bypassPermission, removeExpiredOnLogin, databaseConfig, kickMessage, expiredKickMessage);
    }
}
