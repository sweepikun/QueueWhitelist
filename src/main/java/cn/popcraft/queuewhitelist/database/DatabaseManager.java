package cn.popcraft.queuewhitelist.database;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class DatabaseManager {
    private final JavaPlugin plugin;
    private Connection connection;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void open() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                throw new SQLException("无法创建插件数据文件夹：" + dataFolder.getAbsolutePath());
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + new File(dataFolder, "queuewhitelist.db").getAbsolutePath());
            connection.setAutoCommit(true);
        } catch (SQLException exception) {
            throw new IllegalStateException("数据库连接失败", exception);
        }
    }

    public void initialize(int configThreshold) {
        execute("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        execute("CREATE TABLE IF NOT EXISTS whitelist (player_name TEXT PRIMARY KEY, expires_at INTEGER NULL, created_at INTEGER NOT NULL)");

        if (getSetting("threshold").isEmpty()) {
            setThreshold(configThreshold);
        }
        plugin.getLogger().info("数据库初始化完成。");
    }

    public void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            plugin.getLogger().warning("关闭数据库连接时发生错误：" + exception.getMessage());
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
        String normalizedName = normalizeName(playerName);
        long createdAt = Instant.now().getEpochSecond();
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO whitelist(player_name, expires_at, created_at) VALUES(?, ?, ?) ON CONFLICT(player_name) DO UPDATE SET expires_at = excluded.expires_at, created_at = excluded.created_at")) {
            statement.setString(1, normalizedName);
            if (expiresAt == null) {
                statement.setNull(2, java.sql.Types.INTEGER);
            } else {
                statement.setLong(2, expiresAt);
            }
            statement.setLong(3, createdAt);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("写入白名单失败", exception);
        }
    }

    public boolean removeWhitelist(String playerName) {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM whitelist WHERE player_name = ?")) {
            statement.setString(1, normalizeName(playerName));
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("删除白名单失败", exception);
        }
    }

    public Optional<WhitelistEntry> findWhitelist(String playerName) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT player_name, expires_at, created_at FROM whitelist WHERE player_name = ?")) {
            statement.setString(1, normalizeName(playerName));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                Long expiresAt = resultSet.getObject("expires_at") == null ? null : resultSet.getLong("expires_at");
                Instant createdAt = Instant.ofEpochSecond(resultSet.getLong("created_at"));
                return Optional.of(new WhitelistEntry(resultSet.getString("player_name"), expiresAt, createdAt));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("查询白名单失败", exception);
        }
    }

    public List<WhitelistEntry> listWhitelist(int page, int pageSize) {
        int offset = Math.max(0, page - 1) * pageSize;
        List<WhitelistEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT player_name, expires_at, created_at FROM whitelist ORDER BY created_at DESC LIMIT ? OFFSET ?")) {
            statement.setInt(1, pageSize);
            statement.setInt(2, offset);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Long expiresAt = resultSet.getObject("expires_at") == null ? null : resultSet.getLong("expires_at");
                    entries.add(new WhitelistEntry(resultSet.getString("player_name"), expiresAt, Instant.ofEpochSecond(resultSet.getLong("created_at"))));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("读取白名单列表失败", exception);
        }
        return entries;
    }

    public int cleanupExpired(long now) {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM whitelist WHERE expires_at IS NOT NULL AND expires_at <= ?")) {
            statement.setLong(1, now);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("清理过期白名单失败", exception);
        }
    }

    private Optional<String> getSetting(String key) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT value FROM settings WHERE key = ?")) {
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
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO settings(key, value) VALUES(?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("保存设置失败", exception);
        }
    }

    private void execute(String sql) {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException("执行数据库语句失败", exception);
        }
    }

    private String normalizeName(String playerName) {
        return playerName.toLowerCase(Locale.ROOT);
    }
}
