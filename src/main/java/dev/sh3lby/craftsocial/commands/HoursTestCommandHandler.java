package dev.sh3lby.craftsocial.commands;

import dev.sh3lby.craftsocial.CraftSocialPlugin;
import dev.sh3lby.craftsocial.config.PluginConfig;
import dev.sh3lby.craftsocial.storage.OnlineTimeTracker;
import dev.sh3lby.craftsocial.utils.MessagePalette;
import dev.sh3lby.craftsocial.utils.PlayerIdMapper;
import dev.sh3lby.craftsocial.utils.WebhookService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class HoursTestCommandHandler implements CommandExecutor, TabCompleter {

    private final CraftSocialPlugin plugin;
    private final OnlineTimeTracker tracker;
    private final PlayerIdMapper playerIdMapper;
    private final WebhookService webhookService;
    private final PluginConfig config;

    public HoursTestCommandHandler(CraftSocialPlugin plugin,
                                   OnlineTimeTracker tracker,
                                   PlayerIdMapper playerIdMapper,
                                   WebhookService webhookService,
                                   PluginConfig config) {
        this.plugin = plugin;
        this.tracker = tracker;
        this.playerIdMapper = playerIdMapper;
        this.webhookService = webhookService;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("craftsocial.admin")) {
            MessagePalette.sendRaw(sender, "§cУ вас нет прав для выполнения этой команды.");
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                MessagePalette.sendRaw(sender, "§cУкажите игрока: §6/" + label + " <player>");
                return true;
            }
            Player self = (Player) sender;
            showHoursInfo(sender, self);
            return true;
        }

        String sub = args[0].toLowerCase();
        if ("trigger".equals(sub)) {
            Player target;
            if (args.length >= 2) {
                target = Bukkit.getPlayerExact(args[1]);
                if (target == null || !target.isOnline()) {
                    MessagePalette.sendRaw(sender, "§cИгрок не найден или оффлайн: §f" + args[1]);
                    return true;
                }
            } else {
                if (!(sender instanceof Player)) {
                    MessagePalette.sendRaw(sender, "§cУкажите игрока: §6/" + label + " trigger <player>");
                    return true;
                }
                target = (Player) sender;
            }

            triggerReward(sender, target);
            return true;
        }

        // /jshours <player>
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            MessagePalette.sendRaw(sender, "§cИгрок не найден или оффлайн: §f" + args[0]);
            return true;
        }
        showHoursInfo(sender, target);
        return true;
    }

    private void showHoursInfo(CommandSender sender, Player target) {
        UUID uuid = target.getUniqueId();
        int hours = tracker.getConsecutiveHours(uuid);

        int min = config.getOnlineMinReward();
        int max = config.getOnlineMaxReward();
        if (min <= 0 || max < min) {
            min = 10;
            max = 30;
        }
        // Мультипликатор для следующей награды (следующий полный час)
        int nextHourIndex = hours; // 0 -> первый час, 1 -> второй и т.д.
        double multiplier = config.getOnlineMultiplierBase()
            + nextHourIndex * config.getOnlineMultiplierStep();
        if (multiplier < 0.0) {
            multiplier = 0.0;
        }

        int minTotal = (int) Math.round(min * multiplier);
        int maxTotal = (int) Math.round(max * multiplier);

        MessagePalette.sendRaw(sender, "§c[CraftSocial Hours] §fИгрок: §6" + target.getName());
        MessagePalette.sendRaw(sender, "§fНепрерывных часов в сессии: §6" + hours);
        MessagePalette.sendRaw(sender, "§fСледующая награда: §6random(" + min + ".." + max + ") × " +
            String.format(java.util.Locale.US, "%.2f", multiplier) +
            " = от " + minTotal + " до " + maxTotal + " вайши");
    }

    private void triggerReward(CommandSender sender, Player target) {
        UUID uuid = target.getUniqueId();
        int hours = tracker.getAndIncrementHours(uuid); // 0-based индекс текущего часа

        long playerId;
        try {
            playerId = playerIdMapper.getPlayerIdSync(target);
        } catch (Exception e) {
            MessagePalette.sendRaw(sender, "§cНе удалось получить player_id для §f" + target.getName());
            return;
        }

        int playtimeSeconds = (hours + 1) * 3600;
        if (webhookService != null) {
            webhookService.pushPlaytimeReward(playerId, playtimeSeconds);
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            MessagePalette.send(target, "§6(TEST) Час §f" + (hours + 1) + " §6отправлен боту на начисление.");
            if (!sender.equals(target)) {
                MessagePalette.sendRaw(sender, "§6Тест: игрок §f" + target.getName() + "§6, час §f" + (hours + 1) + " §6отправлен боту.");
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("craftsocial.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            String prefix = args[0].toLowerCase();
            if ("trigger".startsWith(prefix)) {
                result.add("trigger");
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(prefix)) {
                    result.add(online.getName());
                }
            }
            return result;
        }

        if (args.length == 2 && "trigger".equalsIgnoreCase(args[0])) {
            List<String> result = new ArrayList<>();
            String prefix = args[1].toLowerCase();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(prefix)) {
                    result.add(online.getName());
                }
            }
            return result;
        }

        return Collections.emptyList();
    }
}
