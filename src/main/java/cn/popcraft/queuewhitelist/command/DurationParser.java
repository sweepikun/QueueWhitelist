package cn.popcraft.queuewhitelist.command;

import java.util.Locale;

final class DurationParser {
    private DurationParser() {
    }

    static Long parseExpiresAt(String input) {
        String value = input.toLowerCase(Locale.ROOT).trim();
        if (value.equals("forever") || value.equals("permanent") || value.equals("永久")) {
            return null;
        }
        if (value.length() < 2) {
            throw new IllegalArgumentException("时长格式错误");
        }

        char unit = value.charAt(value.length() - 1);
        long amount = Long.parseLong(value.substring(0, value.length() - 1));
        if (amount <= 0) {
            throw new IllegalArgumentException("时长必须大于 0");
        }

        long seconds = switch (unit) {
            case 's' -> amount;
            case 'm' -> amount * 60L;
            case 'h' -> amount * 60L * 60L;
            case 'd' -> amount * 60L * 60L * 24L;
            default -> throw new IllegalArgumentException("时长单位只能是 s、m、h、d 或 forever");
        };
        return System.currentTimeMillis() / 1000L + seconds;
    }
}
