package cn.popcraft.queuewhitelist.database;

import cn.popcraft.queuewhitelist.config.DatabaseConfig;
import cn.popcraft.queuewhitelist.config.DatabaseType;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class DatabaseManager {
    private final JavaPlugin plugin;
    private final DatabaseConfig config;
    private HikariDataSource dataSource;

    public DatabaseManager(JavaPlugin plugin, DatabaseConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void open() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                throw new SQLException("无法创建插件数据文件夹：" + dataFolder.getAbsolutePath());
            }

            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setPoolName("QueueWhitelist-" + config.type().configName());
            hikariConfig.setJdbcUrl(config.jdbcUrl(plugin));
            hikariConfig.setConnectionTimeout(config.connectionTimeout());
            hikariConfig.setMinimumIdle(config.minimumIdle());
            hikariConfig.setMaximumPoolSize(config.type() == DatabaseType.SQLITE ? 1 : config.maximumPoolSize());

            if (config.type() == DatabaseType.SQLITE) {
                hikariConfig.setDriverClassName("org.sqlite.JDBC");
            } else {
                hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
                hikariConfig.setUsername(config.mysqlUsername());
                hikariConfig.setPassword(config.mysqlPassword());
            }

            dataSource = new HikariDataSource(hikariConfig);
        } catch (SQLException exception) {
            throw new IllegalStateException("数据库连接池创建失败", exception);
        }
    }

    public void initialize(int configThreshold) {
        execute("CREATE TABLE IF NOT EXISTS settings (setting_key VARCHAR(64) PRIMARY KEY, value TEXT NOT NULL)");
        execute("CREATE TABLE IF NOT EXISTS whitelist (player_name VARCHAR(16) PRIMARY KEY, expires_at BIGINT NULL, created_at BIGINT NOT NULL)");

        if (getSetting("threshold").isEmpty()) {
            setThreshold(configThreshold);
        }
        plugin.getLogger().info("数据库初始化完成，当前类型：" + config.type().configName() + "。");
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    public DatabaseType type() {
        return config.type();
    }

    public int migrateTo(DatabaseType targetType, int configThreshold) {
        if (targetType == config.type()) {
            throw new IllegalArgumentException("目标数据库类型不能和当前类型相同。");
        }

        Map<String, String> settings = listSettings();
        List<WhitelistEntry> entries = listAllWhitelist();
        DatabaseManager target = new DatabaseManager(plugin, config.withType(targetType));
        target.open();
        try {
            target.initialize(configThreshold);
            for (Map.Entry<String, String> entry : settings.entrySet()) {
                target.setSetting(entry.getKey(), entry.getValue());
            }
            for (WhitelistEntry entry : entries) {
                target.upsertWhitelist(entry.playerName(), entry.expiresAt(), entry.createdAt().getEpochSecond());
            }
            plugin.getLogger().info("数据库转换完成：" + config.type().configName() + " -> " + targetType.configName() + "，白名单记录：" + entries.size() + "。");
            return entries.size();
        } finally {
            target.close();
        }
    }

    public int getThreshold() {
        return getSetting("threshold")
                .map(value -> {
                    try {
                        return Math.max(0, Integer.parseInt(value));
                    } catch (NumberFormatException ignored) {
                        return 0;
                    }
                })
                .orElse(0);
    }

    public void setThreshold(int threshold) {
        setSetting("threshold", Integer.toString(Math.max(0, threshold)));
    }

    public void addWhitelist(String playerName, Long expiresAt) {
        upsertWhitelist(normalizeName(playerName), expiresAt, Instant.now().getEpochSecond());
    }

    public boolean removeWhitelist(String playerName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM whitelist WHERE player_name = ?")) {
            statement.setString(1, normalizeName(playerName));
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("删除白名单失败", exception);
        }
    }

    public Optional<WhitelistEntry> findWhitelist(String playerName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT player_name, expires_at, created_at FROM whitelist WHERE player_name = ?")) {
            statement.setString(1, normalizeName(playerName));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readWhitelistEntry(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("查询白名单失败", exception);
        }
    }

    public List<WhitelistEntry> listWhitelist(int page, int pageSize) {
        int offset = Math.max(0, page - 1) * pageSize;
        List<WhitelistEntry> entries = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT player_name, expires_at, created_at FROM whitelist ORDER BY created_at DESC LIMIT ? OFFSET ?")) {
            statement.setInt(1, pageSize);
            statement.setInt(2, offset);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(readWhitelistEntry(resultSet));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("读取白名单列表失败", exception);
        }
        return entries;
    }

    public int cleanupExpired(long now) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM whitelist WHERE expires_at IS NOT NULL AND expires_at <= ?")) {
            statement.setLong(1, now);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("清理过期白名单失败", exception);
        }
    }

    private Map<String, String> listSettings() {
        Map<String, String> settings = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT setting_key, value FROM settings");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                settings.put(resultSet.getString("setting_key"), resultSet.getString("value"));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("读取设置列表失败", exception);
        }
        return settings;
    }

    private List<WhitelistEntry> listAllWhitelist() {
        List<WhitelistEntry> entries = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT player_name, expires_at, created_at FROM whitelist");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                entries.add(readWhitelistEntry(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("读取所有白名单失败", exception);
        }
        return entries;
    }

    private Optional<String> getSetting(String key) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT value FROM settings WHERE setting_key = ?")) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(resultSet.getString("value"));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("读取设置失败", exception);
        }
        return Optional.empty();
    }

    private void setSetting(String key, String value) {
        String sql = config.type() == DatabaseType.MYSQL
                ? "INSERT INTO settings(setting_key, value) VALUES(?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value)"
                : "INSERT INTO settings(setting_key, value) VALUES(?, ?) ON CONFLICT(setting_key) DO UPDATE SET value = excluded.value";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("保存设置失败", exception);
        }
    }

    private void upsertWhitelist(String playerName, Long expiresAt, long createdAt) {
        String sql = config.type() == DatabaseType.MYSQL
                ? "INSERT INTO whitelist(player_name, expires_at, created_at) VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE expires_at = VALUES(expires_at), created_at = VALUES(created_at)"
                : "INSERT INTO whitelist(player_name, expires_at, created_at) VALUES(?, ?, ?) ON CONFLICT(player_name) DO UPDATE SET expires_at = excluded.expires_at, created_at = excluded.created_at";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizeName(playerName));
            if (expiresAt == null) {
                statement.setNull(2, Types.BIGINT);
            } else {
                statement.setLong(2, expiresAt);
            }
            statement.setLong(3, createdAt);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("写入白名单失败", exception);
        }
    }

    private WhitelistEntry readWhitelistEntry(ResultSet resultSet) throws SQLException {
        Long expiresAt = resultSet.getObject("expires_at") == null ? null : resultSet.getLong("expires_at");
        Instant createdAt = Instant.ofEpochSecond(resultSet.getLong("created_at"));
        return new WhitelistEntry(resultSet.getString("player_name"), expiresAt, createdAt);
    }

    private void execute(String sql) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException("执行数据库语句失败", exception);
        }
    }

    private String normalizeName(String playerName) {
        return playerName.toLowerCase(Locale.ROOT);
    }
}
