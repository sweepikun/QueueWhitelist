package cn.popcraft.queuewhitelist.command;

import cn.popcraft.queuewhitelist.QueueWhitelistPlugin;
import cn.popcraft.queuewhitelist.database.DatabaseManager;
import cn.popcraft.queuewhitelist.database.WhitelistEntry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class QueueWhitelistCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUB_COMMANDS = Arrays.asList("add", "remove", "check", "list", "threshold", "reload", "cleanup");
    private static final List<String> DURATIONS = Arrays.asList("30m", "1h", "12h", "1d", "7d", "forever");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final QueueWhitelistPlugin plugin;
    private final DatabaseManager databaseManager;

    public QueueWhitelistCommand(QueueWhitelistPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "add" -> add(sender, args);
                case "remove" -> remove(sender, args);
                case "check" -> check(sender, args);
                case "list" -> list(sender, args);
                case "threshold" -> threshold(sender, args);
                case "reload" -> reload(sender);
                case "cleanup" -> cleanup(sender);
                default -> sendHelp(sender, label);
            }
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(ChatColor.RED + exception.getMessage());
        } catch (RuntimeException exception) {
            sender.sendMessage(ChatColor.RED + "命令执行失败，请查看控制台日志。");
            plugin.getLogger().warning("命令执行失败：" + exception.getMessage());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUB_COMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ((sub.equals("add") || sub.equals("remove") || sub.equals("check")) && args.length == 2) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filter(names, args[1]);
        }
        if (sub.equals("add") && args.length == 3) {
            return filter(DURATIONS, args[2]);
        }
        if (sub.equals("threshold") && args.length == 2) {
            return filter(Arrays.asList("0", "50", "100", "150", "200"), args[1]);
        }
        return Collections.emptyList();
    }

    private void add(CommandSender sender, String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("用法：/queuewl add <玩家> <时长>");
        }
        Long expiresAt = DurationParser.parseExpiresAt(args[2]);
        databaseManager.addWhitelist(args[1], expiresAt);
        sender.sendMessage(ChatColor.GREEN + "已为玩家 " + args[1] + " 添加队列白名单，到期时间：" + formatExpires(expiresAt));
        plugin.getLogger().info("管理员 " + sender.getName() + " 为玩家 " + args[1] + " 添加队列白名单，到期时间：" + formatExpires(expiresAt));
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("用法：/queuewl remove <玩家>");
        }
        boolean removed = databaseManager.removeWhitelist(args[1]);
        sender.sendMessage(removed ? ChatColor.GREEN + "已移除玩家 " + args[1] + " 的队列白名单。" : ChatColor.YELLOW + "玩家 " + args[1] + " 不在队列白名单中。");
    }

    private void check(CommandSender sender, String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("用法：/queuewl check <玩家>");
        }
        Optional<WhitelistEntry> entry = databaseManager.findWhitelist(args[1]);
        if (entry.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "玩家 " + args[1] + " 没有队列白名单。");
            return;
        }
        WhitelistEntry whitelistEntry = entry.get();
        long now = System.currentTimeMillis() / 1000L;
        String status = whitelistEntry.isExpired(now) ? ChatColor.RED + "已过期" : ChatColor.GREEN + "有效";
        sender.sendMessage(ChatColor.AQUA + "玩家：" + whitelistEntry.playerName());
        sender.sendMessage(ChatColor.AQUA + "状态：" + status);
        sender.sendMessage(ChatColor.AQUA + "到期时间：" + formatExpires(whitelistEntry.expiresAt()));
    }

    private void list(CommandSender sender, String[] args) {
        int page = args.length >= 2 ? parsePositiveInt(args[1], "页码必须是正整数") : 1;
        List<WhitelistEntry> entries = databaseManager.listWhitelist(page, 10);
        sender.sendMessage(ChatColor.GOLD + "队列白名单列表，第 " + page + " 页：");
        if (entries.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "没有记录。");
            return;
        }
        long now = System.currentTimeMillis() / 1000L;
        for (WhitelistEntry entry : entries) {
            String status = entry.isExpired(now) ? "已过期" : "有效";
            sender.sendMessage(ChatColor.GRAY + "- " + entry.playerName() + " | " + status + " | " + formatExpires(entry.expiresAt()));
        }
    }

    private void threshold(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(ChatColor.AQUA + "当前触发人数阈值：" + databaseManager.getThreshold());
            return;
        }
        int threshold = parseNonNegativeInt(args[1], "人数阈值必须是非负整数");
        databaseManager.setThreshold(threshold);
        sender.sendMessage(ChatColor.GREEN + "已设置触发人数阈值为：" + threshold);
        plugin.getLogger().info("管理员 " + sender.getName() + " 将触发人数阈值设置为 " + threshold + "。");
    }

    private void reload(CommandSender sender) {
        plugin.reloadQueueConfig();
        sender.sendMessage(ChatColor.GREEN + "QueueWhitelist 配置已重载。");
    }

    private void cleanup(CommandSender sender) {
        int removed = databaseManager.cleanupExpired(System.currentTimeMillis() / 1000L);
        sender.sendMessage(ChatColor.GREEN + "已清理 " + removed + " 条过期队列白名单。");
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GOLD + "QueueWhitelist 命令：");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " add <玩家> <时长>" + ChatColor.GRAY + " - 添加限时白名单");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " remove <玩家>" + ChatColor.GRAY + " - 移除白名单");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " check <玩家>" + ChatColor.GRAY + " - 查看白名单状态");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " list [页码]" + ChatColor.GRAY + " - 查看白名单列表");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " threshold [人数]" + ChatColor.GRAY + " - 查看或设置触发人数");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " reload" + ChatColor.GRAY + " - 重载配置");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " cleanup" + ChatColor.GRAY + " - 清理过期记录");
    }

    private String formatExpires(Long expiresAt) {
        if (expiresAt == null) {
            return "永久";
        }
        return FORMATTER.format(Instant.ofEpochSecond(expiresAt));
    }

    private int parsePositiveInt(String value, String message) {
        int number = parseNonNegativeInt(value, message);
        if (number <= 0) {
            throw new IllegalArgumentException(message);
        }
        return number;
    }

    private int parseNonNegativeInt(String value, String message) {
        try {
            int number = Integer.parseInt(value);
            if (number < 0) {
                throw new IllegalArgumentException(message);
            }
            return number;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(message);
        }
    }

    private List<String> filter(List<String> values, String input) {
        String lowerInput = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lowerInput)) {
                result.add(value);
            }
        }
        return result;
    }
}
