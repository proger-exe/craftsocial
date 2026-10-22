package dev.sh3lby.craftsocial.utils;

import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.stream.Collectors;

public final class MessagePalette {
    private static final String PRIMARY = "§c";
    private static final String SECONDARY = "§f";
    private static final String ACCENT = "§6";
    private static final String PREFIX = PRIMARY + "[CraftSocial] " + SECONDARY;

    private MessagePalette() {
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(prefixed(message));
    }

    public static void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(paint(message));
    }

    public static String prefixed(String message) {
        String normalized = message.startsWith("[CraftSocial] ")
            ? message.substring("[CraftSocial] ".length())
            : message;
        return PREFIX + paint(normalized);
    }

    public static String paint(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        return text
            .replace("§a", ACCENT)
            .replace("§b", ACCENT)
            .replace("§e", ACCENT)
            .replace("§6", ACCENT)
            .replace("§7", SECONDARY)
            .replace("§8", SECONDARY)
            .replace("§f", SECONDARY)
            .replace("§c", PRIMARY)
            .replace("§4", PRIMARY)
            .replace("§9", PRIMARY)
            .replace("§3", ACCENT)
            .replace("§2", ACCENT)
            .replace("§d", ACCENT)
            .replace("§5", PRIMARY);
    }

    public static List<String> paint(List<String> lines) {
        return lines.stream().map(MessagePalette::paint).collect(Collectors.toList());
    }
}
