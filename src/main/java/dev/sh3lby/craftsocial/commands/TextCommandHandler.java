package dev.sh3lby.craftsocial.commands;

import dev.sh3lby.craftsocial.utils.MessagePalette;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Создаёт цветной TextDisplay перед игроком или на блоке, в который он смотрит.
 */
public class TextCommandHandler implements CommandExecutor, TabCompleter {
    private static final String MANUAL_TEXT_TAG = "craftsocial_manual_text";
    private static final Map<String, NamedTextColor> COLORS = Map.ofEntries(
        Map.entry("black", NamedTextColor.BLACK),
        Map.entry("dark_blue", NamedTextColor.DARK_BLUE),
        Map.entry("dark_green", NamedTextColor.DARK_GREEN),
        Map.entry("dark_aqua", NamedTextColor.DARK_AQUA),
        Map.entry("dark_red", NamedTextColor.DARK_RED),
        Map.entry("dark_purple", NamedTextColor.DARK_PURPLE),
        Map.entry("gold", NamedTextColor.GOLD),
        Map.entry("gray", NamedTextColor.GRAY),
        Map.entry("dark_gray", NamedTextColor.DARK_GRAY),
        Map.entry("blue", NamedTextColor.BLUE),
        Map.entry("green", NamedTextColor.GREEN),
        Map.entry("aqua", NamedTextColor.AQUA),
        Map.entry("red", NamedTextColor.RED),
        Map.entry("light_purple", NamedTextColor.LIGHT_PURPLE),
        Map.entry("yellow", NamedTextColor.YELLOW),
        Map.entry("white", NamedTextColor.WHITE)
    );

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessagePalette.sendRaw(sender, "§cЭта команда доступна только игрокам");
            return true;
        }

        if (args.length < 2) {
            MessagePalette.sendRaw(player, "§cИспользование: §6/text <цвет> <текст>");
            MessagePalette.sendRaw(player, "§fПример: §6/text red Привет мир");
            return true;
        }

        String colorKey = normalizeColorKey(args[0]);
        NamedTextColor color = COLORS.get(colorKey);
        if (color == null) {
            MessagePalette.sendRaw(player, "§cНеизвестный цвет. Используйте vanilla-цвет, например: §6red, gold, yellow, white");
            return true;
        }

        String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        if (text.isEmpty()) {
            MessagePalette.sendRaw(player, "§cТекст не должен быть пустым.");
            return true;
        }

        Location spawnLocation = resolveSpawnLocation(player);
        TextDisplay display = (TextDisplay) player.getWorld().spawnEntity(spawnLocation, EntityType.TEXT_DISPLAY);
        display.text(Component.text(text, color));
        display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        display.setSeeThrough(false);
        display.setShadowed(true);
        display.setDefaultBackground(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.addScoreboardTag(MANUAL_TEXT_TAG);

        MessagePalette.sendRaw(player, "§6TextDisplay создан: §f" + text);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = normalizeColorKey(args[0]);
            return COLORS.keySet().stream()
                .filter(color -> color.startsWith(prefix))
                .sorted()
                .collect(Collectors.toList());
        }
        return List.of();
    }

    private Location resolveSpawnLocation(Player player) {
        Block targetBlock = player.getTargetBlockExact(10);
        if (targetBlock != null) {
            BlockFace face = player.getTargetBlockFace(10);
            Vector offset = face != null
                ? new Vector(face.getModX(), face.getModY(), face.getModZ()).multiply(0.6)
                : player.getEyeLocation().getDirection().normalize().multiply(0.6);
            Location base = targetBlock.getLocation().add(0.5, 0.5, 0.5).add(offset);
            base.setYaw(player.getLocation().getYaw());
            base.setPitch(0f);
            return base;
        }

        Location base = player.getEyeLocation().clone().add(player.getEyeLocation().getDirection().normalize().multiply(3.0));
        base.setPitch(0f);
        return base;
    }

    private String normalizeColorKey(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
