package dev.sh3lby.craftsocial.commands;

import dev.sh3lby.craftsocial.handlers.EconomyHandler;
import dev.sh3lby.craftsocial.utils.PlayerIdMapper;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Обработчик команд для экономики.
 */
public class EconomyCommandHandler implements CommandExecutor, TabCompleter {
    private static final Logger logger = LoggerFactory.getLogger(EconomyCommandHandler.class);
    
    private final EconomyHandler economyHandler;
    private final PlayerIdMapper playerIdMapper;
    
    public EconomyCommandHandler(EconomyHandler economyHandler, PlayerIdMapper playerIdMapper) {
        this.economyHandler = economyHandler;
        this.playerIdMapper = playerIdMapper;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Эта команда доступна только игрокам");
            return true;
        }
        
        Player player = (Player) sender;
        long playerId = playerIdMapper.getPlayerIdSync(player);
        
        if (args.length == 0) {
            sendUsage(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        try {
            switch (subCommand) {
                case "balance":
                    player.sendMessage("§6Ваш баланс: §aЗагрузка...");
                    economyHandler.getBalance(playerId).thenAccept(balance -> {
                        if (balance != null) {
                            player.sendMessage("§6Ваш баланс: §a" + balance + " вайши");
                        } else {
                            player.sendMessage("§cНе удалось загрузить баланс");
                        }
                    });
                    return true;
                    
                case "pay":
                    if (args.length < 3) {
                        player.sendMessage("§cИспользование: /money pay <player> <amount> [description]");
                        return true;
                    }
                    String payPlayerName = args[1];
                    Player payPlayer = Bukkit.getPlayer(payPlayerName);
                    if (payPlayer == null) {
                        player.sendMessage("§cИгрок не найден");
                        return true;
                    }
                    long payPlayerId = playerIdMapper.getPlayerIdSync(payPlayer);
                    if (payPlayerId == playerId) {
                        player.sendMessage("§cНельзя переводить самому себе");
                        return true;
                    }
                    int amount = Integer.parseInt(args[2]);
                    if (amount <= 0) {
                        player.sendMessage("§cСумма должна быть больше 0");
                        return true;
                    }
                    String description = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "Transfer";
                    
                    economyHandler.transfer(playerId, payPlayerId, amount, description).thenAccept(transfer -> {
                        if (transfer == null) {
                            player.sendMessage("§cОшибка перевода");
                            return;
                        }
                        player.sendMessage("§aПеревод выполнен! Сумма: " + transfer.getAmount() + " вайши");
                        player.sendMessage("§7Ваш баланс после перевода: §a" + transfer.getFromBalanceAfter());
                    }).exceptionally(error -> {
                        String msg = error.getCause() != null ? error.getCause().getMessage() : error.getMessage();
                        player.sendMessage("§cОшибка перевода: " + msg);
                        return null;
                    });
                    return true;
                    
                case "history":
                    int page = args.length > 1 ? Integer.parseInt(args[1]) : 1;
                    // TODO: Реализовать получение истории транзакций через API
                    player.sendMessage("§7История транзакций (стр. " + page + ")");
                    player.sendMessage("§cИстория транзакций пока не реализована");
                    return true;
                    
                case "top":
                    int limit = args.length > 1 ? Integer.parseInt(args[1]) : 10;
                    if (limit < 1 || limit > 100) {
                        player.sendMessage("§cLimit должен быть от 1 до 100");
                        return true;
                    }
                    player.sendMessage("§6=== Топ игроков по балансу (top " + limit + ") ===");
                    economyHandler.getTopByBalance(limit, playerId).thenAccept(top -> {
                        if (top == null) {
                            player.sendMessage("§cНе удалось загрузить топ");
                            return;
                        }
                        if (top.isEmpty()) {
                            player.sendMessage("§7Пусто");
                            return;
                        }
                        for (java.util.Map<String, Object> row : top) {
                            Object rank = row.get("rank");
                            Object nick = row.get("nickname");
                            Object bal = row.get("balance");
                            player.sendMessage(String.format("§e%s. §f%s §7— §a%s вайши",
                                fmtNum(rank), nick, fmtNum(bal)));
                        }
                    });
                    return true;
                    
                default:
                    sendUsage(player);
                    return true;
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§cНеверный формат числа");
            return true;
        } catch (Exception e) {
            logger.error("Error executing economy command", e);
            player.sendMessage("§cОшибка выполнения команды: " + e.getMessage());
            return true;
        }
    }
    
    // Gson декодирует числа в JSON-объектах как Double — приводим к long для отображения.
    private static String fmtNum(Object o) {
        if (o instanceof Number) return Long.toString(((Number) o).longValue());
        return String.valueOf(o);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("balance", "pay", "history", "top")
                .stream()
                .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("pay")) {
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            } else if (subCommand.equals("history") || subCommand.equals("top")) {
                return Arrays.asList("1", "2", "3", "10", "20");
            }
        }
        
        return new ArrayList<>();
    }
    
    private void sendUsage(Player player) {
        player.sendMessage("§6=== Команды экономики ===");
        player.sendMessage("§e/money balance §7- Баланс");
        player.sendMessage("§e/money pay <player> <amount> [description] §7- Перевести деньги");
        player.sendMessage("§e/money history [page] §7- История транзакций");
        player.sendMessage("§e/money top [limit] §7- Топ игроков по балансу");
    }
    
}
