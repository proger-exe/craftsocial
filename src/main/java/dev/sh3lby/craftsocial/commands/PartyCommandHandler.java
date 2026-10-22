package dev.sh3lby.craftsocial.commands;

import dev.sh3lby.craftsocial.gui.PartyManagementGui;
import dev.sh3lby.craftsocial.handlers.PartyHandler;
import dev.sh3lby.craftsocial.handlers.PlayerHandler;
import dev.sh3lby.craftsocial.storage.PartyInventoryManager;
import dev.sh3lby.craftsocial.storage.PartyInviteManager;
import dev.sh3lby.craftsocial.utils.MessagePalette;
import dev.sh3lby.craftsocial.utils.PlayerIdMapper;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Обработчик команд для групп (Party).
 */
public class PartyCommandHandler implements CommandExecutor, TabCompleter {
    private static final Logger logger = LoggerFactory.getLogger(PartyCommandHandler.class);
    
    private final PartyHandler partyHandler;
    private final PartyManagementGui partyManagementGui;
    private final PartyInventoryManager inventoryManager;
    private final PartyInviteManager partyInviteManager;
    private final PlayerHandler playerHandler;
    private final PlayerIdMapper playerIdMapper;
    private final Plugin plugin;

    public PartyCommandHandler(PartyHandler partyHandler, PartyManagementGui partyManagementGui,
                               PartyInventoryManager inventoryManager, PartyInviteManager partyInviteManager,
                               PlayerHandler playerHandler, PlayerIdMapper playerIdMapper, Plugin plugin) {
        this.partyHandler = partyHandler;
        this.partyManagementGui = partyManagementGui;
        this.inventoryManager = inventoryManager;
        this.partyInviteManager = partyInviteManager;
        this.playerHandler = playerHandler;
        this.playerIdMapper = playerIdMapper;
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            MessagePalette.sendRaw(sender, "§cЭта команда доступна только игрокам");
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
                case "create":
                    if (args.length < 2) {
                        MessagePalette.sendRaw(player, "§cИспользование: §6/jsparty create <name>");
                        return true;
                    }
                    String partyName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    partyHandler.createParty(partyName, playerId).thenAccept(party -> {
                        if (party != null) {
                            MessagePalette.sendRaw(player, "§6Группа создана! ID: §f" + party.getId());
                            // Создаем пустой инвентарь для новой группы
                            inventoryManager.loadInventory(party.getId());
                        } else {
                            MessagePalette.sendRaw(player, "§cОшибка создания группы");
                        }
                    });
                    return true;
                    
                case "list":
                    int page = args.length > 1 ? Integer.parseInt(args[1]) : 1;
                    partyHandler.getParties(page, 20, playerId).thenAccept(parties -> {
                        if (parties != null && !parties.isEmpty()) {
                            MessagePalette.sendRaw(player, "§c=== Группы (стр. §6" + page + "§c) ===");
                            for (dev.sh3lby.craftsocial.models.Party party : parties) {
                                MessagePalette.sendRaw(player, "§fID: §6" + party.getId() + " §f- §c" + party.getName());
                            }
                        } else {
                            MessagePalette.sendRaw(player, "§fГруппы не найдены");
                        }
                    });
                    return true;
                    
                case "info":
                    Integer infoPartyId = null;
                    if (args.length > 1) {
                        infoPartyId = Integer.parseInt(args[1]);
                    } else {
                        // Получаем группу игрока из аналитики
                        playerHandler.getAnalytics(playerId).thenAccept(analytics -> {
                            if (analytics != null && analytics.getPartyId() != null) {
                                showPartyInfo(player, analytics.getPartyId());
                            } else {
                                MessagePalette.sendRaw(player, "§cВы не состоите в группе. Укажите ID группы.");
                            }
                        });
                        return true;
                    }
                    showPartyInfo(player, infoPartyId);
                    return true;
                    
                case "manage":
                    Integer managePartyId = null;
                    if (args.length > 1) {
                        managePartyId = Integer.parseInt(args[1]);
                    } else {
                        // Получаем группу игрока из аналитики
                        playerHandler.getAnalytics(playerId).thenAccept(analytics -> {
                            if (analytics != null && analytics.getPartyId() != null) {
                                partyManagementGui.openManagement(player, analytics.getPartyId());
                            } else {
                                partyManagementGui.openManagement(player, null);
                            }
                        });
                        return true;
                    }
                    partyManagementGui.openManagement(player, managePartyId);
                    return true;
                    
                case "invite":
                    if (args.length < 2) {
                        MessagePalette.sendRaw(player, "§cИспользование: §6/jsparty invite <player>");
                        return true;
                    }
                    String invitePlayerName = args[1];
                    Player invitePlayer = Bukkit.getPlayer(invitePlayerName);
                    if (invitePlayer == null) {
                        MessagePalette.sendRaw(player, "§cИгрок не найден");
                        return true;
                    }
                    long invitePlayerId = playerIdMapper.getPlayerIdSync(invitePlayer);
                    
                    playerHandler.getAnalytics(playerId).thenAccept(analytics -> {
                        if (analytics == null || analytics.getPartyId() == null) {
                            MessagePalette.sendRaw(player, "§cВы не состоите в группе");
                            return;
                        }
                        int partyId = analytics.getPartyId();
                        partyHandler.getParty(partyId, playerId).thenAccept(party -> {
                            String name = party != null ? party.getName() : ("ID " + partyId);
                            partyInviteManager.addInvite(invitePlayerId, partyId, playerId, player.getName(), name);
                            MessagePalette.sendRaw(player, "§6Приглашение отправлено.");
                            MessagePalette.sendRaw(invitePlayer, "§cВас пригласили в группу §6" + name + "§f. Принять: §6/jsparty accept§f, отклонить: §c/jsparty decline");
                        });
                    });
                    return true;
                    
                case "accept":
                    PartyInviteManager.PartyInvite acceptInv = partyInviteManager.takeFirstInvite(playerId);
                    if (acceptInv == null) {
                        MessagePalette.sendRaw(player, "§cУ вас нет приглашений в группу.");
                        return true;
                    }
                    partyHandler.addMember(acceptInv.getPartyId(), playerId, acceptInv.getInviterPlayerId()).thenAccept(success -> {
                        if (success) {
                            playerHandler.invalidateAnalytics(playerId);
                            MessagePalette.sendRaw(player, "§6Вы вступили в группу §c" + acceptInv.getPartyName() + "§6.");
                            plugin.getServer().getScheduler().runTask(plugin, () ->
                                partyManagementGui.openManagement(player, acceptInv.getPartyId()));
                        } else {
                            MessagePalette.sendRaw(player, "§cНе удалось вступить в группу.");
                            partyInviteManager.addInvite(playerId, acceptInv.getPartyId(), acceptInv.getInviterPlayerId(), acceptInv.getInviterName(), acceptInv.getPartyName());
                        }
                    });
                    return true;
                    
                case "decline":
                    PartyInviteManager.PartyInvite declineInv = partyInviteManager.takeFirstInvite(playerId);
                    if (declineInv == null) {
                        MessagePalette.sendRaw(player, "§cУ вас нет приглашений в группу.");
                        return true;
                    }
                    MessagePalette.sendRaw(player, "§fВы отклонили приглашение в группу §c" + declineInv.getPartyName() + "§f.");
                    return true;
                    
                case "kick":
                    if (args.length < 2) {
                        MessagePalette.sendRaw(player, "§cИспользование: §6/jsparty kick <player>");
                        return true;
                    }
                    String kickPlayerName = args[1];
                    Player kickPlayer = Bukkit.getPlayer(kickPlayerName);
                    if (kickPlayer == null) {
                        MessagePalette.sendRaw(player, "§cИгрок не найден");
                        return true;
                    }
                    long kickPlayerId = playerIdMapper.getPlayerIdSync(kickPlayer);
                    
                    playerHandler.getAnalytics(playerId).thenAccept(analytics -> {
                        if (analytics == null || analytics.getPartyId() == null) {
                            MessagePalette.sendRaw(player, "§cВы не состоите в группе");
                            return;
                        }
                        
                        int partyId = analytics.getPartyId();
                        // Проверяем, что игрок в группе (members теперь List<Integer>)
                        partyHandler.getParty(partyId, playerId).thenAccept(party -> {
                            if (party != null && party.getMembers() != null) {
                                boolean isMember = party.getMembers().contains(kickPlayerId);
                                
                                if (isMember) {
                                    // member_id в API - это player_id участника
                                    partyHandler.removeMember(partyId, kickPlayerId, playerId).thenAccept(success -> {
                                        if (success) {
                                            playerHandler.invalidateAnalytics(kickPlayerId);
                                            MessagePalette.sendRaw(player, "§6Игрок исключен из группы");
                                        } else {
                                            MessagePalette.sendRaw(player, "§cОшибка исключения игрока");
                                        }
                                    });
                                } else {
                                    MessagePalette.sendRaw(player, "§cИгрок не найден в группе");
                                }
                            }
                        });
                    });
                    return true;
                    
                case "leave":
                    playerHandler.getAnalytics(playerId).thenAccept(analytics -> {
                        if (analytics == null || analytics.getPartyId() == null) {
                            MessagePalette.sendRaw(player, "§cВы не состоите в группе");
                            return;
                        }
                        
                        int partyId = analytics.getPartyId();
                        // Проверяем, что игрок в группе (members теперь List<Integer>)
                        partyHandler.getParty(partyId, playerId).thenAccept(party -> {
                            if (party != null && party.getMembers() != null) {
                                boolean isMember = party.getMembers().contains(playerId);
                                
                                if (isMember) {
                                    // member_id в API - это player_id участника
                                    partyHandler.removeMember(partyId, playerId, playerId).thenAccept(success -> {
                                        if (success) {
                                            playerHandler.invalidateAnalytics(playerId);
                                            MessagePalette.sendRaw(player, "§6Вы покинули группу");
                                        } else {
                                            MessagePalette.sendRaw(player, "§cОшибка выхода из группы");
                                        }
                                    });
                                } else {
                                    MessagePalette.sendRaw(player, "§cОшибка: вы не найдены в списке участников");
                                }
                            }
                        });
                    });
                    return true;
                    
                case "transfer":
                    if (args.length < 2) {
                        MessagePalette.sendRaw(player, "§cИспользование: §6/jsparty transfer <player>");
                        return true;
                    }
                    String transferPlayerName = args[1];
                    Player transferPlayer = Bukkit.getPlayer(transferPlayerName);
                    if (transferPlayer == null) {
                        MessagePalette.sendRaw(player, "§cИгрок не найден");
                        return true;
                    }
                    long transferPlayerId = playerIdMapper.getPlayerIdSync(transferPlayer);
                    
                    playerHandler.getAnalytics(playerId).thenAccept(analytics -> {
                        if (analytics == null || analytics.getPartyId() == null) {
                            MessagePalette.sendRaw(player, "§cВы не состоите в группе");
                            return;
                        }
                        
                        int partyId = analytics.getPartyId();
                        partyHandler.transferLeadership(partyId, transferPlayerId, playerId).thenAccept(success -> {
                            if (success) {
                                MessagePalette.sendRaw(player, "§6Лидерство передано");
                            } else {
                                MessagePalette.sendRaw(player, "§cОшибка передачи лидерства");
                            }
                        });
                    });
                    return true;
                    
                case "delete":
                    playerHandler.getAnalytics(playerId).thenAccept(analytics -> {
                        if (analytics == null || analytics.getPartyId() == null) {
                            MessagePalette.sendRaw(player, "§cВы не состоите в группе");
                            return;
                        }
                        
                        int partyId = analytics.getPartyId();
                        partyHandler.deleteParty(partyId, playerId).thenAccept(success -> {
                            if (success) {
                                MessagePalette.sendRaw(player, "§6Группа удалена");
                                // Удаляем инвентарь группы
                                inventoryManager.removeInventory(partyId);
                            } else {
                                MessagePalette.sendRaw(player, "§cОшибка удаления группы");
                            }
                        });
                    });
                    return true;
                    
                case "edit":
                    if (args.length < 2) {
                        MessagePalette.sendRaw(player, "§cИспользование: §6/jsparty edit <name>");
                        return true;
                    }
                    String newName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    
                    playerHandler.getAnalytics(playerId).thenAccept(analytics -> {
                        if (analytics == null || analytics.getPartyId() == null) {
                            MessagePalette.sendRaw(player, "§cВы не состоите в группе");
                            return;
                        }
                        
                        int partyId = analytics.getPartyId();
                        partyHandler.updateParty(partyId, newName, playerId).thenAccept(success -> {
                            if (success) {
                                MessagePalette.sendRaw(player, "§6Группа обновлена");
                            } else {
                                MessagePalette.sendRaw(player, "§cОшибка обновления группы");
                            }
                        });
                    });
                    return true;
                    
                case "inventory":
                    playerHandler.getAnalytics(playerId).thenAccept(analytics -> {
                        if (analytics == null || analytics.getPartyId() == null) {
                            MessagePalette.sendRaw(player, "§cВы не состоите в группе");
                            return;
                        }
                        
                        int partyId = analytics.getPartyId();
                        partyManagementGui.openInventory(player, partyId);
                    });
                    return true;
                    
                default:
                    sendUsage(player);
                    return true;
            }
        } catch (NumberFormatException e) {
            MessagePalette.sendRaw(player, "§cНеверный формат числа");
            return true;
        } catch (Exception e) {
            logger.error("Error executing party command", e);
            MessagePalette.sendRaw(player, "§cОшибка выполнения команды: §f" + e.getMessage());
            return true;
        }
    }
    
    private void showPartyInfo(Player player, int partyId) {
        long playerId = playerIdMapper.getPlayerIdSync(player);
        partyHandler.getParty(partyId, playerId).thenAccept(party -> {
            if (party != null) {
                MessagePalette.sendRaw(player, "§c=== Группа: §6" + party.getName() + " §c===");
                MessagePalette.sendRaw(player, "§fID: §6" + party.getId());
                MessagePalette.sendRaw(player, "§fЛидер: §6" + party.getLeaderId());
                MessagePalette.sendRaw(player, "§fУчастников: §6" + (party.getMembers() != null ? party.getMembers().size() : 0));
            } else {
                MessagePalette.sendRaw(player, "§cГруппа не найдена");
            }
        });
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "list", "info", "manage", "invite", "accept", "decline", "kick", "leave", "transfer", "delete", "edit", "inventory")
                .stream()
                .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("list")) {
                return Arrays.asList("1", "2", "3");
            } else if (subCommand.equals("invite") || subCommand.equals("kick") || subCommand.equals("transfer")) {
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        return new ArrayList<>();
    }
    
    private void sendUsage(Player player) {
        MessagePalette.sendRaw(player, "§c=== Команды групп ===");
        MessagePalette.sendRaw(player, "§6/jsparty create <name> §f- Создать группу");
        MessagePalette.sendRaw(player, "§6/jsparty list [page] §f- Список групп");
        MessagePalette.sendRaw(player, "§6/jsparty info [partyId] §f- Информация о группе");
        MessagePalette.sendRaw(player, "§6/jsparty manage [partyId] §f- Управление группой");
        MessagePalette.sendRaw(player, "§6/jsparty invite <player> §f- Пригласить игрока");
        MessagePalette.sendRaw(player, "§6/jsparty accept §f- Принять приглашение в группу");
        MessagePalette.sendRaw(player, "§6/jsparty decline §f- Отклонить приглашение");
        MessagePalette.sendRaw(player, "§6/jsparty kick <player> §f- Исключить игрока");
        MessagePalette.sendRaw(player, "§6/jsparty leave §f- Покинуть группу");
        MessagePalette.sendRaw(player, "§6/jsparty transfer <player> §f- Передать лидерство");
        MessagePalette.sendRaw(player, "§6/jsparty delete §f- Удалить группу");
        MessagePalette.sendRaw(player, "§6/jsparty edit <name> §f- Редактировать группу");
        MessagePalette.sendRaw(player, "§6/jsparty inventory §f- Открыть инвентарь группы");
    }
    
}
