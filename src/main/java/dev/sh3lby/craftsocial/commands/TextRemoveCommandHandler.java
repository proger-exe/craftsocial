package dev.sh3lby.craftsocial.commands;

import dev.sh3lby.craftsocial.utils.MessagePalette;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.RayTraceResult;

/**
 * Удаляет TextDisplay или ArmorStand, на который смотрит игрок.
 */
public class TextRemoveCommandHandler implements CommandExecutor {
    private static final double MAX_DISTANCE = 10.0D;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessagePalette.sendRaw(sender, "§cЭта команда доступна только игрокам");
            return true;
        }

        RayTraceResult hit = player.getWorld().rayTraceEntities(
            player.getEyeLocation(),
            player.getEyeLocation().getDirection(),
            MAX_DISTANCE,
            0.25D,
            entity -> entity instanceof TextDisplay || entity instanceof ArmorStand
        );

        Entity entity = hit != null ? hit.getHitEntity() : null;
        if (!(entity instanceof TextDisplay) && !(entity instanceof ArmorStand)) {
            MessagePalette.sendRaw(player, "§cСмотрите на TextDisplay или ArmorStand в радиусе 10 блоков.");
            return true;
        }

        entity.remove();
        MessagePalette.sendRaw(player, "§6Сущность удалена: §f" + entity.getType().name());
        return true;
    }
}
