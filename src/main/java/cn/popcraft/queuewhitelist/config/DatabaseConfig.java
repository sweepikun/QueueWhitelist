package cn.popcraft.queuewhitelist.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public record DatabaseConfig(
        DatabaseType type,
        String sqliteFile,
        String mysqlHost,
        int mysqlPort,
        String mysqlDatabase,
        String mysqlUsername,
        String mysqlPassword,
        String mysqlParameters,
        int maximumPoolSize,
        int minimumIdle,
        long connectionTimeout
) {
    public static DatabaseConfig from(JavaPlugin plugin) {
        DatabaseType type = DatabaseType.from(plugin.getConfig().getString("database.type", "sqlite"));
        String sqliteFile = plugin.getConfig().getString("database.sqlite.file", "queuewhitelist.db");
        String mysqlHost = plugin.getConfig().getString("database.mysql.host", "localhost");
        int mysqlPort = plugin.getConfig().getInt("database.mysql.port", 3306);
        String mysqlDatabase = plugin.getConfig().getString("database.mysql.database", "queuewhitelist");
        String mysqlUsername = plugin.getConfig().getString("database.mysql.username", "root");
        String mysqlPassword = plugin.getConfig().getString("database.mysql.password", "");
        String mysqlParameters = plugin.getConfig().getString("database.mysql.parameters", "useSSL=false&serverTimezone=UTC&characterEncoding=utf8");
        int maximumPoolSize = Math.max(1, plugin.getConfig().getInt("database.pool.maximum-pool-size", 10));
        int minimumIdle = Math.max(0, plugin.getConfig().getInt("database.pool.minimum-idle", 1));
        long connectionTimeout = Math.max(1000L, plugin.getConfig().getLong("database.pool.connection-timeout", 30000L));
        return new DatabaseConfig(type, sqliteFile, mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword, mysqlParameters, maximumPoolSize, minimumIdle, connectionTimeout);
    }

    public DatabaseConfig withType(DatabaseType targetType) {
        return new DatabaseConfig(targetType, sqliteFile, mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword, mysqlParameters, maximumPoolSize, minimumIdle, connectionTimeout);
    }

    public String jdbcUrl(JavaPlugin plugin) {
        if (type == DatabaseType.SQLITE) {
            File databaseFile = new File(plugin.getDataFolder(), sqliteFile);
            return "jdbc:sqlite:" + databaseFile.getAbsolutePath();
        }

        String parameters = mysqlParameters == null || mysqlParameters.isBlank() ? "" : "?" + mysqlParameters;
        return "jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + mysqlDatabase + parameters;
    }
}
