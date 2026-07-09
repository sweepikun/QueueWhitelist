package cn.popcraft.queuewhitelist.config;

import java.util.Locale;

public enum DatabaseType {
    SQLITE,
    MYSQL;

    public static DatabaseType from(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "sqlite" -> SQLITE;
            case "mysql" -> MYSQL;
            default -> throw new IllegalArgumentException("不支持的数据库类型：" + value);
        };
    }

    public String configName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
