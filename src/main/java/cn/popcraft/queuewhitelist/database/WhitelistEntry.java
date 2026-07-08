package cn.popcraft.queuewhitelist.database;

import java.time.Instant;

public record WhitelistEntry(String playerName, Long expiresAt, Instant createdAt) {
    public boolean isPermanent() {
        return expiresAt == null;
    }

    public boolean isExpired(long now) {
        return expiresAt != null && expiresAt <= now;
    }
}
