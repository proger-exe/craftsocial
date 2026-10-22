package dev.sh3lby.craftsocial.commands;

import dev.sh3lby.craftsocial.gui.ClanGui;
import dev.sh3lby.craftsocial.gui.ClanManagementGui;
import dev.sh3lby.craftsocial.handlers.ClanHandler;
import dev.sh3lby.craftsocial.handlers.PlayerHandler;
import dev.sh3lby.craftsocial.storage.ClanInviteManager;
import dev.sh3lby.craftsocial.utils.PlayerIdMapper;
import org.bukkit.Bukkit;
import org.bukkit.World;
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
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Обработчик команд для кланов.
 */
public class ClanCommandHandler implements CommandExecutor, TabCompleter {
    private static final Logger logger = LoggerFactory.getLogger(ClanCommandHandler.class);
    
    private final ClanHandler clanHandler;
    private final ClanGui clanGui;
    private final ClanManagementGui clanManagementGui;
    private final ClanInviteManager clanInviteManager;
    private final PlayerHandler playerHandler;
    private final PlayerIdMapper playerIdMapper;
    private final Plugin plugin;

    public ClanCommandHandler(ClanHandler clanHandler, ClanGui clanGui, ClanManagementGui clanManagementGui,
                              ClanInviteManager clanInviteManager,
                              PlayerHandler playerHandler, PlayerIdMapper playerIdMapper, Plugin plugin) {
        this.clanHandler = clanHandler;
        this.clanGui = clanGui;
        this.clanManagementGui = clanManagementGui;
        this.clanInviteManager = clanInviteManager;
        this.playerHandler = playerHandler;
        this.playerIdMapper = playerIdMapper;
        this.plugin = plugin;
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
                case "create":
                    if (args.length < 3) {
                        player.sendMessage("§cИспользование: /clan create <name> <tag> [description]");
                        return true;
                    }
                    playerHandler.getAnalytics(playerId).thenAccept(analytics -> {
                        if (analytics != null && analytics.getClanId() != null) {
                            player.sendMessage("§cВы уже состоите в клане (или создали клан). Покиньте клан перед созданием нового.");
                            return;
                        }
                        String name = args[1];
                        String tag = args[2];
                        String description = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : null;
                        String prefix = "[" + tag + "]";
                        clanHandler.createClan(name, tag, description, prefix, playerId).thenAccept(clan -> {
                            if (clan != null) {
                                player.sendMessage("§aКлан создан! ID: " + clan.getId());
                            } else {
                                player.sendMessage("§cОшибка создания клана");
                            }
                        });
                    });
                    return true;
                    
                case "list":
                    int page = args.length > 1 ? Integer.parseInt(args[1]) : 1;
                    clanHandler.getClans(null, null, null, page, 20, playerId).thenAccept(clans -> {
                        if (clans != null && !clans.isEmpty()) {
                            player.sendMessage("§6=== Кланы (стр. " + page + ") ===");
                            for (dev.sh3lby.craftsocial.models.Clan clan : clans) {
                                player.sendMessage("§7ID: §f" + clan.getId() + " §7- §e" + clan.getName() + " [" + clan.getTag() + "]");
                            }
                        } else {
                            player.sendMessage("§7Кланы не найдены");
                        }
                    });
                    return true;
                    
                case "info":
                    Integer infoClanId = null;
                    if (args.length > 1) {
                        infoClanId = Integer.parseInt(args[1]);
                    } else {
                        // Получаем клан игрока из аналитики
                        playerHandler.getAnalytics(playerId).thenAccept(analytics -> {
                            if (analytics != null && analytics.getClanId() != null) {
                                clanGui.openClan(player, analytics.getClanId());
                            } else {
                                player.sendMessage("§cВы не состоите в клане. Укажите ID клана.");
                            }
                        });
                        return true;
                    }
                    clanGui.openClan(player, infoClanId);
                    return true;
                    
                case "manage":
                    Integer manageClanId = null;
                    if (args.length > 1) {
                        manageClanId = Integer.parseInt(args[1]);
                    } else {
                        // Получаем клан игрока из аналитики
                        playerHandler.getAnalytics(playerId).thenAccept(analytics -> {
                            if (analytics != null && analytics.getClanId() != null) {
                                clanManagementGui.openManagement(player, analytics.getClanId());
                            } else {
                                clanManagementGui.openManagement(player, null);
                            }
                        });
                        return true;
                    }
                    clanManagementGui.openManagement(player, manageClanId);
                    return true;
                    
                case "edit":
                    if (args.length < 3) {
                        player.sendMessage("§cИспользование: /clan edit <field> <value>");
                        player.sendMessage("§7Поля: name, tag, description, prefix");
                        return true;
                    }
                    String field = args[1].toLowerCase();
                    String value = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                    
                    // Получаем клан игрока
                    playerHandler.getAnalytics(playerId).thenAccept(analytics -> {
                        if (analytics == null || analytics.getClanId() == null) {
                            player.sendMessage("§cВы не состоите в клане");
                            return;
                        }
                        
                        int clanId = analytics.getClanId();
                        String nameVal = field.equals("name") ? value : null;
                        String tagVal = field.equals("tag") ? value : null;
                        String descVal = field.equals("description") ? value : null;
                        String prefixVal = field.equals("prefix") ? value : null;
                        
                        clanHandler.updateClan(clanId, nameVal, tagVal, descVal, prefixVal, playerId).thenAccept(clan -> {
                            if (clan != null) {
                                player.sendMessage("§aКлан обновлен");
                            } else {
                                player.sendMessage("§cОшибка обновления клана");
                            }
                        });
                    });
                    return true;
                    
                case "delete":
                    playerHandler.getAnalytics(playerId).thenAccept(analytics -> {
                        if (analytics == null || analytics.getClanId() == null) {
                            player.sendMessage("§cВы не состоите в клане");
                            return;
                        }
                        
                        int clanId = analytics.getClanId();
                        clanHandler.deleteClan(clanId, playerId).thenAccept(success -> {
                            if (success) {
                                player.sendMessage("§aКлан удален");
                            } else {
                                player.sendMessage("§cОшибка удаления клана");
                            }
                        });
                    });
                    return true;
                    
                case "invite":
                    if (args.length < 2) {
                        player.sendMessage("§cИспользование: /clan invite <player>");
                        return true;
                    }
                    String invitePlayerName = args[1];
                    Player invitePlayer = Bukkit.getPlayer(invitePlayerName);
                    if (invitePlayer == null) {
                        player.sendMessage("§cИгрок не найден");
                        return true;
                    }
                    long invitePlayerId = playerIdMapper.getPlayerIdSync(invitePlayer);
                    
                    playerHandler.getAnalytics(playerId).thenAccept(analytics -> {
                        if (analytics == null || analytics.getClanId() == null) {
                            player.sendMessage("§cВы не состоите в клане");
                            return;
                        }
                        int clanId = analytics.getClanId();
                        clanHandler.getClan(clanId, playerId).thenAccept(clan -> {
                            String clanName = clan != null ? clan.getName() : ("ID " + clanId);
                            clanInviteManager.addInvite(invitePlayerId, clanId, playerId, player.getName(), clanName);
                            player.sendMessage("§aПриглашение отправлено.");
                            invitePlayer.sendMessage("§eВас пригласили в клан §6" + clanName + "§e. Принять: §a/clan accept§e, отклонить: §c/clan decline");
                        });
                    });
                    return true;
                    
                case "accept":
                    ClanInviteManager.ClanInvite acceptInv = clanInviteManager.takeFirstInvite(playerId);
                    if (acceptInv == null) {
                        player.sendMessage("§cУ вас нет приглашений в клан.");
                        return true;
                    }
                    clanHandler.addMember(acceptInv.getClanId(), playerId, "member", acceptInv.getInviterPlayerId()).thenAccept(success -> {
                        if (success) {
                            playerHandler.invalidateAnalytics(playerId);
                            clanHandler.invalidateClanCache(acceptInv.getClanId());
                            player.sendMessage("§aВы вступили в клан §6" + acceptInv.getClanName() + "§a.");
                            plugin.getServer().getScheduler().runTask(plugin, () ->
                                clanManagementGui.openManagement(player, acceptInv.getClanId()));
                        } else {
                            player.sendMessage("§cНе удалось вступить в клан.");
                            clanInviteManager.addInvite(playerId, acceptInv.getClanId(), acceptInv.getInviterPlayerId(), acceptInv.getInviterName(), acceptInv.getClanName());
                        }
                    });
                    return true;
                    
                case "decline":
                    ClanInviteManager.ClanInvite declineInv = clanInviteManager.takeFirstInvite(playerId);
                    if (declineInv == null) {
                        player.sendMessage("§cУ вас нет приглашений в клан.");
                        return true;
                    }
                    player.sendMessage("§7Вы отклонили приглашение в клан §f" + declineInv.getClanName() + "§7.");
                    return true;
                    
                case "kick":
                    if (args.length < 2) {
                        player.sendMessage("§cИспользование: /clan kick <player>");
                        return true;
                    }
                    String kickPlayerName = args[1];
                    
                    playerHandler.getAnalytics(playerId).thenAccept(analytics -> {
                        if (analytics == null || analytics.getClanId() == null) {
                            player.sendMessage("§cВы не состоите в клане");
                            return;
                        }
                        
                        int clanId = analytics.getClanId();
                        clanHandler.getClan(clanId, playerId).thenAccept(clan -> {
                            if (clan == null || clan.getMembers() == null) {
                                player.sendMessage("§cИгрок не найден в клане");
                                return;
                            }

                            resolveClanMemberByNickname(clan, kickPlayerName).thenAccept(match -> {
                                if (match == null) {
                                    player.sendMessage("§cИгрок не найден в клане");
                                    return;
                                }
                                if (match == ClanMemberMatch.AMBIGUOUS) {
                                    player.sendMessage("§cНик неоднозначен среди участников клана");
                                    return;
                                }
                                clanHandler.removeMember(clanId, match.playerId, playerId).thenAccept(success -> {
                                    if (success) {
                                        playerHandler.invalidateAnalytics(match.playerId);
                                        player.sendMessage("§aИгрок исключен из клана");
                                    } else {
                                        player.sendMessage("§cОшибка исключения игрока");
                                    }
                                });
                            });
                        });
                    });
                    return true;
                    
                case "promote":
                    if (args.length < 2) {
                        player.sendMessage("§cИспользование: /clan promote <player>");
                        return true;
                    }
                    String promotePlayerName = args[1];
                    Player promotePlayer = Bukkit.getPlayer(promotePlayerName);
                    if (promotePlayer == null) {
                        player.sendMessage("§cИгрок не найден");
                        return true;
                    }
                    long promotePlayerId = playerIdMapper.getPlayerIdSync(promotePlayer);
                    
                    playerHandler.getAnalytics(playerId).thenAccept(analytics -> {
                        if (analytics == null || analytics.getClanId() == null) {
                            player.sendMessage("§cВы не состоите в клане");
                            return;
                        }
                        
                        int clanId = analytics.getClanId();
                        // Получаем memberId и текущую роль, затем повышаем
                        clanHandler.getClan(clanId, playerId).thenAccept(clan -> {
                            if (clan != null && clan.getMembers() != null) {
                                dev.sh3lby.craftsocial.models.ClanMember member = clan.getMembers().stream()
                                    .filter(m -> m.getPlayerId() == promotePlayerId)
                                    .findFirst()
                                    .orElse(null);
                                
                                if (member != null) {
                                    String newRole = member.getRole().equals("member") ? "moderator" : "leader";
                                    // member_id в API - это player_id участника
                                    clanHandler.changeMemberRole(clanId, promotePlayerId, newRole, playerId).thenAccept(success -> {
                                        if (success) {
                                            player.sendMessage("§aРоль игрока повышена");
                                        } else {
                                            player.sendMessage("§cОшибка повышения роли");
                                        }
                                    });
                                } else {
                                    player.sendMessage("§cИгрок не найден в клане");
                                }
                            }
                        });
                    });
                    return true;
                    
                case "demote":
                    if (args.length < 2) {
                        player.sendMessage("§cИспользование: /clan demote <player>");
                        return true;
                    }
                    String demotePlayerName = args[1];
                    Player demotePlayer = Bukkit.getPlayer(demotePlayerName);
                    if (demotePlayer == null) {
                        player.sendMessage("§cИгрок не найден");
                        return true;
                    }
                    long demotePlayerId = playerIdMapper.getPlayerIdSync(demotePlayer);
                    
                    playerHandler.getAnalytics(playerId).thenAccept(analytics -> {
                        if (analytics == null || analytics.getClanId() == null) {
                            player.sendMessage("§cВы не состоите в клане");
                            return;
                        }
                        
                        int clanId = analytics.getClanId();
                        // Получаем memberId и текущую роль, затем понижаем
                        clanHandler.getClan(clanId, playerId).thenAccept(clan -> {
                            if (clan != null && clan.getMembers() != null) {
                                dev.sh3lby.craftsocial.models.ClanMember member = clan.getMembers().stream()
                                    .filter(m -> m.getPlayerId() == demotePlayerId)
                                    .findFirst()
                                    .orElse(null);
                                
                                if (member != null) {
                                    String newRole = member.getRole().equals("leader") ? "moderator" : "member";
                                    // member_id в API - это player_id участника
                                    clanHandler.changeMemberRole(clanId, demotePlayerId, newRole, playerId).thenAccept(success -> {
                                        if (success) {
                                            player.sendMessage("§aРоль игрока понижена");
                                        } else {
                                            player.sendMessage("§cОшибка понижения роли");
                                        }
                                    });
                                } else {
                                    player.sendMessage("§cИгрок не найден в клане");
                                }
                            }
                        });
                    });
                    return true;
                    
                case "leave":
                    playerHandler.getAnalytics(playerId).thenAccept(analytics -> {
                        if (analytics == null || analytics.getClanId() == null) {
                            player.sendMessage("§cВы не состоите в клане");
                            return;
                        }
                        
                        int clanId = analytics.getClanId();
                        // Получаем memberId игрока и удаляем
                        clanHandler.getClan(clanId, playerId).thenAccept(clan -> {
                            if (clan != null && clan.getMembers() != null) {
                                dev.sh3lby.craftsocial.models.ClanMember member = clan.getMembers().stream()
                                    .filter(m -> m.getPlayerId() == playerId)
                                    .findFirst()
                                    .orElse(null);
                                
                                if (member != null) {
                                    // member_id в API - это player_id участника
                                    clanHandler.removeMember(clanId, playerId, playerId).thenAccept(success -> {
                                        if (success) {
                                            playerHandler.invalidateAnalytics(playerId);
                                            player.sendMessage("§aВы покинули клан");
                                        } else {
                                            player.sendMessage("§cОшибка выхода из клана");
                                        }
                                    });
                                } else {
                                    player.sendMessage("§cОшибка: вы не найдены в списке участников");
                                }
                            }
                        });
                    });
                    return true;
                
                case "home":
                    playerHandler.getAnalytics(playerId).thenAccept(analytics -> {
                        if (analytics == null || analytics.getClanId() == null) {
                            player.sendMessage("§cВы не состоите в клане");
                            return;
                        }
                        int clanId = analytics.getClanId();
                        String homeSub = args.length > 1 ? args[1].toLowerCase() : "";
                        if ("set".equals(homeSub) && args.length >= 3) {
                            String homeName = args[2];
                            String dim = player.getWorld().getName();
                            clanHandler.addHome(clanId, homeName, dim, player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ(), playerId)
                                .thenAccept(clan -> {
                                    if (clan != null) player.sendMessage("§aДом §e" + homeName + " §aустановлен.");
                                    else player.sendMessage("§cНе удалось установить дом (макс. " + ClanHandler.MAX_CLAN_HOMES + " или имя занято).");
                                });
                            return;
                        }
                        if ("remove".equals(homeSub) && args.length >= 3) {
                            String homeName = args[2];
                            clanHandler.removeHome(clanId, homeName, playerId).thenAccept(clan -> {
                                if (clan != null) player.sendMessage("§aДом §e" + homeName + " §aудалён.");
                                else player.sendMessage("§cДом не найден.");
                            });
                            return;
                        }
                        if ("list".equals(homeSub)) {
                            clanHandler.getClan(clanId, playerId).thenAccept(clan -> {
                                if (clan == null || clan.getHomes() == null || clan.getHomes().isEmpty()) {
                                    player.sendMessage("§7Домов клана нет.");
                                    return;
                                }
                                player.sendMessage("§6Дома клана (§b" + clan.getHomes().size() + "§6):");
                                for (java.util.Map<String, Object> h : clan.getHomes()) {
                                    String n = String.valueOf(h.get("name"));
                                    String d = String.valueOf(h.get("dim"));
                                    Object x = h.get("x"), y = h.get("y"), z = h.get("z");
                                    player.sendMessage("§7- §e" + n + " §f" + d + " §7(" + x + ", " + y + ", " + z + ")");
                                }
                            });
                            return;
                        }
                        if ("tp".equals(homeSub) && args.length >= 3) {
                            String homeName = args[2];
                            clanHandler.getClan(clanId, playerId).thenAccept(clan -> {
                                if (clan == null || clan.getHomes() == null) {
                                    player.sendMessage("§cДом не найден.");
                                    return;
                                }
                                java.util.Map<String, Object> home = null;
                                for (java.util.Map<String, Object> h : clan.getHomes()) {
                                    if (homeName.equals(h.get("name"))) { home = h; break; }
                                }
                                if (home == null) {
                                    player.sendMessage("§cДом не найден.");
                                    return;
                                }
                                String dim = String.valueOf(home.get("dim"));
                                double x = ((Number) home.getOrDefault("x", 0)).doubleValue();
                                double y = ((Number) home.getOrDefault("y", 64)).doubleValue();
                                double z = ((Number) home.getOrDefault("z", 0)).doubleValue();
                                World w = Bukkit.getWorld(dim);
                                if (w == null) {
                                    player.sendMessage("§cМир §f" + dim + " §cне найден.");
                                    return;
                                }
                                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("CraftSocial"), () -> {
                                    player.teleport(new org.bukkit.Location(w, x, y, z));
                                    player.sendMessage("§aТелепорт на дом §e" + homeName + "§a.");
                                });
                            });
                            return;
                        }
                        if (args.length >= 2 && !"set".equals(homeSub) && !"remove".equals(homeSub) && !"list".equals(homeSub)) {
                            String homeName = args[1];
                            clanHandler.getClan(clanId, playerId).thenAccept(clan -> {
                                if (clan == null || clan.getHomes() == null) {
                                    player.sendMessage("§cДом не найден.");
                                    return;
                                }
                                java.util.Map<String, Object> home = null;
                                for (java.util.Map<String, Object> h : clan.getHomes()) {
                                    if (homeName.equals(h.get("name"))) { home = h; break; }
                                }
                                if (home == null) {
                                    player.sendMessage("§cДом не найден.");
                                    return;
                                }
                                String dim = String.valueOf(home.get("dim"));
                                double x = ((Number) home.getOrDefault("x", 0)).doubleValue();
                                double y = ((Number) home.getOrDefault("y", 64)).doubleValue();
                                double z = ((Number) home.getOrDefault("z", 0)).doubleValue();
                                World w = Bukkit.getWorld(dim);
                                if (w == null) {
                                    player.sendMessage("§cМир §f" + dim + " §cне найден.");
                                    return;
                                }
                                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("CraftSocial"), () -> {
                                    player.teleport(new org.bukkit.Location(w, x, y, z));
                                    player.sendMessage("§aТелепорт на дом §e" + homeName + "§a.");
                                });
                            });
                            return;
                        }
                        player.sendMessage("§e/clan home set <name> §7- Установить дом");
                        player.sendMessage("§e/clan home remove <name> §7- Удалить дом");
                        player.sendMessage("§e/clan home list §7- Список домов");
                        player.sendMessage("§e/clan home [tp] <name> §7- Телепорт на дом");
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
            logger.error("Error executing clan command", e);
            player.sendMessage("§cОшибка выполнения команды: " + e.getMessage());
            return true;
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "list", "info", "manage", "edit", "delete", "invite", "accept", "decline", "kick", "promote", "demote", "leave", "home")
                .stream()
                .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("list")) {
                return Arrays.asList("1", "2", "3");
            } else if (subCommand.equals("edit")) {
                return Arrays.asList("name", "tag", "description", "prefix")
                    .stream()
                    .filter(field -> field.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            } else if (subCommand.equals("home")) {
                return Arrays.asList("set", "remove", "list", "tp").stream()
                    .filter(c -> c.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            } else if (subCommand.equals("invite") || subCommand.equals("kick") || subCommand.equals("promote") || subCommand.equals("demote")) {
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        return new ArrayList<>();
    }
    
    private void sendUsage(Player player) {
        player.sendMessage("§6=== Команды кланов ===");
        player.sendMessage("§e/clan create <name> <tag> [description] §7- Создать клан");
        player.sendMessage("§e/clan list [page] §7- Список кланов");
        player.sendMessage("§e/clan info [clanId] §7- Информация о клане");
        player.sendMessage("§e/clan manage [clanId] §7- Управление кланом");
        player.sendMessage("§e/clan edit <field> <value> §7- Редактировать клан");
        player.sendMessage("§e/clan delete §7- Удалить клан");
        player.sendMessage("§e/clan invite <player> §7- Пригласить игрока");
        player.sendMessage("§e/clan accept §7- Принять приглашение в клан");
        player.sendMessage("§e/clan decline §7- Отклонить приглашение");
        player.sendMessage("§e/clan kick <player> §7- Исключить игрока");
        player.sendMessage("§e/clan promote <player> §7- Повысить роль");
        player.sendMessage("§e/clan demote <player> §7- Понизить роль");
        player.sendMessage("§e/clan leave §7- Покинуть клан");
        player.sendMessage("§e/clan home set|remove|list|tp [name] §7- Дома клана (макс. 3)");
    }

    private CompletableFuture<ClanMemberMatch> resolveClanMemberByNickname(dev.sh3lby.craftsocial.models.Clan clan, String nickname) {
        String needle = nickname.toLowerCase(Locale.ROOT);
        List<dev.sh3lby.craftsocial.models.ClanMember> members = clan.getMembers();
        if (members == null || members.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<ClanMemberMatch>> futures = new ArrayList<>();
        for (dev.sh3lby.craftsocial.models.ClanMember member : members) {
            Player online = Bukkit.getOnlinePlayers().stream()
                .filter(p -> playerIdMapper.getPlayerIdSync(p) == member.getPlayerId())
                .findFirst()
                .orElse(null);
            if (online != null) {
                String onlineName = online.getName();
                if (onlineName != null && onlineName.equalsIgnoreCase(nickname)) {
                    futures.add(CompletableFuture.completedFuture(new ClanMemberMatch(member.getPlayerId(), onlineName)));
                    continue;
                }
            }

            futures.add(playerHandler.getProfile(member.getPlayerId()).thenApply(profile -> {
                if (profile == null || profile.getNickname() == null) {
                    return null;
                }
                return profile.getNickname().toLowerCase(Locale.ROOT).equals(needle)
                    ? new ClanMemberMatch(member.getPlayerId(), profile.getNickname())
                    : null;
            }));
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            List<ClanMemberMatch> matches = futures.stream()
                .map(CompletableFuture::join)
                .filter(match -> match != null)
                .collect(Collectors.toList());
            if (matches.isEmpty()) {
                return null;
            }
            if (matches.size() > 1) {
                return ClanMemberMatch.AMBIGUOUS;
            }
            return matches.get(0);
        });
    }

    private static final class ClanMemberMatch {
        private static final ClanMemberMatch AMBIGUOUS = new ClanMemberMatch(-1L, "");

        private final long playerId;
        @SuppressWarnings("unused")
        private final String nickname;

        private ClanMemberMatch(long playerId, String nickname) {
            this.playerId = playerId;
            this.nickname = nickname;
        }
    }
    
}
