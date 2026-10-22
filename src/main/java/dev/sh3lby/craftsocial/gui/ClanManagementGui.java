package dev.sh3lby.craftsocial.gui;

import dev.sh3lby.craftsocial.handlers.ClanHandler;
import dev.sh3lby.craftsocial.handlers.PlayerHandler;
import dev.sh3lby.craftsocial.models.Clan;
import dev.sh3lby.craftsocial.models.ClanMember;
import dev.sh3lby.craftsocial.storage.ClanInventoryManager;
import dev.sh3lby.craftsocial.utils.MessagePalette;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * GUI для управления кланом (создание, редактирование, участники, дома, инвентарь).
 */
public class ClanManagementGui {
    private final ClanHandler clanHandler;
    private final ClanInventoryManager clanInventoryManager;
    private final Plugin plugin;

    public ClanManagementGui(ClanHandler clanHandler, ClanInventoryManager clanInventoryManager, Plugin plugin) {
        this.clanHandler = clanHandler;
        this.clanInventoryManager = clanInventoryManager;
        this.plugin = plugin;
    }
    
    /**
     * Открывает GUI управления кланом.
     */
    public void openManagement(Player player, Integer clanId) {
        long playerId = ((dev.sh3lby.craftsocial.CraftSocialPlugin) plugin).getPlayerIdMapper().getPlayerIdSync(player);
        
        if (clanId == null) {
            // Открываем GUI создания клана
            openCreateClanGui(player);
        } else {
            // Открываем GUI управления существующим кланом
            clanHandler.getClan(clanId, playerId).thenAccept(clan -> {
                if (clan == null) {
                    MessagePalette.send(player, "Клан не найден");
                    return;
                }
                List<ClanMember> members = clan.getMembers() != null ? clan.getMembers() : new ArrayList<>();
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Map<Long, String> names = new ConcurrentHashMap<>();
                    dev.sh3lby.craftsocial.CraftSocialPlugin js = (dev.sh3lby.craftsocial.CraftSocialPlugin) plugin;
                    for (ClanMember m : members) {
                        String n = getOnlineDisplayName(js, m.getPlayerId());
                        if (n != null) names.put(m.getPlayerId(), n);
                    }
                    List<ClanMember> needFetch = members.stream().filter(m -> !names.containsKey(m.getPlayerId())).collect(Collectors.toList());
                    if (needFetch.isEmpty()) {
                        openManagementWithNames(player, clanId, clan, names);
                        return;
                    }
                    PlayerHandler playerHandler = js.getPlayerHandler();
                    CompletableFuture<?>[] futures = needFetch.stream()
                        .map(m -> playerHandler.getProfile(m.getPlayerId())
                            .thenAccept(p -> names.put(m.getPlayerId(),
                                p != null && p.getNickname() != null ? p.getNickname() : ("#" + m.getPlayerId()))))
                        .toArray(CompletableFuture[]::new);
                    CompletableFuture.allOf(futures).thenRun(() ->
                        plugin.getServer().getScheduler().runTask(plugin, () -> openManagementWithNames(player, clanId, clan, names)));
                });
            }).exceptionally(error -> {
                MessagePalette.send(player, "§cОшибка загрузки клана: §f" + error.getMessage());
                return null;
            });
        }
    }

    private static String getOnlineDisplayName(dev.sh3lby.craftsocial.CraftSocialPlugin plugin, long memberPlayerId) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.getPlayerIdMapper().getPlayerIdSync(p) == memberPlayerId) {
                return p.getName();
            }
        }
        return null;
    }

    private void openManagementWithNames(Player player, int clanId, Clan clan, Map<Long, String> memberNames) {
        Inventory inventory = Bukkit.createInventory(null, 54, "Управление клана: " + clanId + " " + clan.getName());

        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(MessagePalette.paint("§c" + clan.getName() + " [" + clan.getTag() + "]"));
            List<String> lore = new ArrayList<>();
            lore.add("§fID: §6" + clan.getId());
            lore.add("§fТег: " + clan.getTag());
            lore.add("§fОписание: " + (clan.getDescription() != null ? clan.getDescription() : "Нет"));
            lore.add("§fПрефикс: " + (clan.getPrefix() != null ? clan.getPrefix() : "Нет"));
            lore.add("§fУчастников: §6" + (clan.getMembers() != null ? clan.getMembers().size() : 0));
            infoMeta.setLore(MessagePalette.paint(lore));
            infoItem.setItemMeta(infoMeta);
        }
        inventory.setItem(4, infoItem);

        ItemStack editItem = new ItemStack(Material.ANVIL);
        ItemMeta editMeta = editItem.getItemMeta();
        if (editMeta != null) {
            editMeta.setDisplayName(MessagePalette.paint("§6Редактировать клан"));
            editMeta.setLore(MessagePalette.paint(List.of("§fИспользуйте команду:", "§6/clan edit <field> <value>")));
            editItem.setItemMeta(editMeta);
        }
        inventory.setItem(9, editItem);

        ItemStack inviteItem = new ItemStack(Material.EMERALD);
        ItemMeta inviteMeta = inviteItem.getItemMeta();
        if (inviteMeta != null) {
            inviteMeta.setDisplayName(MessagePalette.paint("§cПригласить игрока"));
            inviteMeta.setLore(MessagePalette.paint(List.of("§fИспользуйте команду:", "§6/clan invite <player>")));
            inviteItem.setItemMeta(inviteMeta);
        }
        inventory.setItem(10, inviteItem);

        ItemStack homesItem = new ItemStack(Material.COMPASS);
        ItemMeta homesMeta = homesItem.getItemMeta();
        if (homesMeta != null) {
            homesMeta.setDisplayName(MessagePalette.paint("§cДома клана"));
            List<String> homesLore = new ArrayList<>();
            homesLore.add("§fМакс. §6" + ClanHandler.MAX_CLAN_HOMES + " §fдома. ЛКМ - открыть.");
            if (clan.getHomes() != null && !clan.getHomes().isEmpty()) {
                for (Map<String, Object> h : clan.getHomes()) {
                    homesLore.add("§f- §6" + h.get("name"));
                }
            }
            homesMeta.setLore(MessagePalette.paint(homesLore));
            homesItem.setItemMeta(homesMeta);
        }
        inventory.setItem(11, homesItem);

        ItemStack invItem = new ItemStack(Material.CHEST);
        ItemMeta invMeta = invItem.getItemMeta();
        if (invMeta != null) {
            invMeta.setDisplayName(MessagePalette.paint("§cИнвентарь клана"));
            invMeta.setLore(MessagePalette.paint(List.of("§fОбщий инвентарь клана. ЛКМ - открыть.")));
            invItem.setItemMeta(invMeta);
        }
        inventory.setItem(12, invItem);

        ItemStack deleteItem = new ItemStack(Material.BARRIER);
        ItemMeta deleteMeta = deleteItem.getItemMeta();
        if (deleteMeta != null) {
            deleteMeta.setDisplayName(MessagePalette.paint("§cУдалить клан"));
            deleteMeta.setLore(MessagePalette.paint(List.of("§fИспользуйте команду:", "§6/clan delete")));
            deleteItem.setItemMeta(deleteMeta);
        }
        inventory.setItem(17, deleteItem);

        ItemStack leaveItem = new ItemStack(Material.OAK_DOOR);
        ItemMeta leaveMeta = leaveItem.getItemMeta();
        if (leaveMeta != null) {
            leaveMeta.setDisplayName(MessagePalette.paint("§6Покинуть клан"));
            leaveMeta.setLore(MessagePalette.paint(List.of("§fИспользуйте команду:", "§6/clan leave")));
            leaveItem.setItemMeta(leaveMeta);
        }
        inventory.setItem(16, leaveItem);

        if (clan.getMembers() != null) {
            int slot = 18;
            for (ClanMember member : clan.getMembers()) {
                if (slot >= 45) break;
                String displayName = memberNames.getOrDefault(member.getPlayerId(), "#" + member.getPlayerId());

                ItemStack memberItem = new ItemStack(Material.PLAYER_HEAD);
                ItemMeta memberMeta = memberItem.getItemMeta();
                if (memberMeta != null) {
                    memberMeta.setDisplayName(MessagePalette.paint("§c" + displayName));
                    List<String> memberLore = new ArrayList<>();
                    memberLore.add("§fРоль: §6" + member.getRole());
                    memberLore.add("§fПрисоединился: " + (member.getJoinedAt() != null ? member.getJoinedAt() : "Неизвестно"));
                    memberLore.add("");
                    if (member.isLeader()) {
                        memberLore.add("§6ЛКМ - Понизить роль");
                    } else if (member.isModerator()) {
                        memberLore.add("§6ЛКМ - Понизить роль");
                        memberLore.add("§cПКМ - Исключить");
                    } else {
                        memberLore.add("§6ЛКМ - Повысить роль");
                        memberLore.add("§cПКМ - Исключить");
                    }
                    memberMeta.setLore(MessagePalette.paint(memberLore));
                    memberItem.setItemMeta(memberMeta);
                }
                inventory.setItem(slot++, memberItem);
            }
        }

        player.openInventory(inventory);
    }
    
    /**
     * Открывает GUI создания клана.
     */
    private void openCreateClanGui(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 9, "Создание клана");
            
            ItemStack infoItem = new ItemStack(Material.BOOK);
            ItemMeta infoMeta = infoItem.getItemMeta();
            if (infoMeta != null) {
                infoMeta.setDisplayName(MessagePalette.paint("§cСоздание клана"));
                List<String> lore = new ArrayList<>();
                lore.add("§fИспользуйте команду:");
                lore.add("§6/clan create <name> <tag> [description]");
                lore.add("");
                lore.add("§fПример:");
                lore.add("§6/clan create EliteWarriors EW Лучший клан");
                infoMeta.setLore(MessagePalette.paint(lore));
                infoItem.setItemMeta(infoMeta);
            }
            inventory.setItem(4, infoItem);
            
            player.openInventory(inventory);
        });
    }
    
    /**
     * Обрабатывает клик в GUI управления.
     */
    public void handleClick(Player player, int slot, ItemStack clickedItem, Integer clanId) {
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }
        
        if (slot == 9) {
            // Редактировать клан
            player.closeInventory();
            MessagePalette.send(player, "Используйте команду: §6/clan edit <field> <value>");
        } else if (slot == 10) {
            player.closeInventory();
            MessagePalette.send(player, "Используйте команду: §6/clan invite <player>");
        } else if (slot == 11) {
            openHomesGui(player, clanId);
        } else if (slot == 12) {
            openClanInventory(player, clanId);
        } else if (slot == 16) {
            // Покинуть клан
            player.closeInventory();
            MessagePalette.send(player, "Используйте команду: §6/clan leave");
        } else if (slot == 17) {
            // Удалить клан
            player.closeInventory();
            MessagePalette.send(player, "Используйте команду: §6/clan delete");
        } else if (slot >= 18 && slot < 45) {
            player.closeInventory();
            MessagePalette.send(player, "Используйте команды: §6/clan promote§f, §6/clan demote§f, §6/clan kick");
        }
    }
    
    /**
     * Открывает инвентарь клана (по команде или из manage).
     * Сетка загружается в инвентарь, курсор (слот 54) — на курсор игрока.
     */
    public void openClanInventory(Player player, int clanId) {
        long playerId = ((dev.sh3lby.craftsocial.CraftSocialPlugin) plugin).getPlayerIdMapper().getPlayerIdSync(player);
        clanHandler.getClan(clanId, playerId).thenAccept(clan -> {
            if (clan == null) {
                MessagePalette.send(player, "Клан не найден");
                return;
            }
            final int fid = clanId;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                ItemStack[] contents = ClanInventoryManager.fromApiInventory(clan.getInventory());
                clanInventoryManager.putCacheFromApi(clanId, contents);
                Inventory inv = Bukkit.createInventory(null, 54, "Инвентарь клана: " + clanId);
                for (int i = 0; i < 54 && i < contents.length; i++) {
                    if (contents[i] != null) inv.setItem(i, contents[i].clone());
                }
                player.openInventory(inv);
                if (contents.length > ClanInventoryManager.CURSOR_SLOT && contents[ClanInventoryManager.CURSOR_SLOT] != null
                        && !contents[ClanInventoryManager.CURSOR_SLOT].getType().isAir()) {
                    ItemStack cursorItem = contents[ClanInventoryManager.CURSOR_SLOT].clone();
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (!player.isOnline()) return;
                        String openTitle = player.getOpenInventory().getTitle();
                        if (openTitle != null && openTitle.equals("Инвентарь клана: " + fid)) {
                            player.setItemOnCursor(cursorItem);
                        }
                    }, 2L);
                }
            });
        });
    }
    
    /**
     * Сохраняет инвентарь клана при закрытии (вызывается из listener).
     * Сетка 0..53 и курсор сохраняются в кеш и в API (долгосрочное хранение).
     */
    public void saveClanInventory(Player player, int clanId, Inventory inventory) {
        long playerId = ((dev.sh3lby.craftsocial.CraftSocialPlugin) plugin).getPlayerIdMapper().getPlayerIdSync(player);
        ItemStack[] grid = new ItemStack[54];
        for (int i = 0; i < 54; i++) {
            ItemStack item = inventory.getItem(i);
            grid[i] = item != null ? item.clone() : null;
        }
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && (cursor.getType().isAir() || cursor.isEmpty())) cursor = null;
        else if (cursor != null) cursor = cursor.clone();
        clanInventoryManager.setInventory(clanId, grid, cursor, playerId);
    }
    
    /**
     * Открывает GUI домов клана (до 3 слотов). Перед запросом инвалидирует кеш.
     */
    public void openHomesGui(Player player, int clanId) {
        long playerId = ((dev.sh3lby.craftsocial.CraftSocialPlugin) plugin).getPlayerIdMapper().getPlayerIdSync(player);
        clanHandler.invalidateClanCache(clanId);
        clanHandler.getClan(clanId, playerId).thenAccept(clan -> {
            if (clan == null) {
                MessagePalette.send(player, "Клан не найден");
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> openHomesGuiFromClan(player, clan));
        });
    }
    
    /**
     * Открывает GUI домов по уже полученному объекту клана (без запроса к API).
     * Вызывать с главного потока или через runTask.
     */
    public void openHomesGui(Player player, Clan clan) {
        if (clan == null) {
            MessagePalette.send(player, "Клан не найден");
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> openHomesGuiFromClan(player, clan));
    }
    
    private void openHomesGuiFromClan(Player player, Clan clan) {
        int clanId = clan.getId();
        Inventory inv = Bukkit.createInventory(null, 9, "Дома клана: " + clanId);
        List<Map<String, Object>> homes = clan.getHomes() != null ? clan.getHomes() : new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            if (i < homes.size()) {
                Map<String, Object> h = homes.get(i);
                ItemStack item = new ItemStack(Material.MAP);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(MessagePalette.paint("§6" + h.get("name")));
                    meta.setLore(MessagePalette.paint(List.of(
                        "§fМир: " + h.get("dim"),
                        "§fX: §6" + h.get("x") + " §fY: §6" + h.get("y") + " §fZ: §6" + h.get("z"),
                        "§fЛКМ - телепорт, ПКМ - удалить"
                    )));
                    item.setItemMeta(meta);
                }
                inv.setItem(i, item);
            } else {
                ItemStack add = new ItemStack(Material.PAPER);
                ItemMeta addMeta = add.getItemMeta();
                if (addMeta != null) {
                    addMeta.setDisplayName(MessagePalette.paint("§cУстановить дом " + (i + 1)));
                    addMeta.setLore(MessagePalette.paint(List.of("§fВстаньте на место и нажмите.", "§fИмя: §6home" + (i + 1))));
                    add.setItemMeta(addMeta);
                }
                inv.setItem(i, add);
            }
        }
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(MessagePalette.paint("§cНазад"));
            back.setItemMeta(backMeta);
        }
        inv.setItem(8, back);
        player.openInventory(inv);
    }
    
    /**
     * Обрабатывает клик в GUI домов клана.
     */
    public void handleHomesClick(Player player, int slot, ItemStack clickedItem, Integer clanId, boolean rightClick) {
        if (clanId == null || clickedItem == null || clickedItem.getType() == Material.AIR) return;
        if (slot == 8) {
            player.closeInventory();
            openManagement(player, clanId);
            return;
        }
        if (slot < 0 || slot > 2) return;
        long playerId = ((dev.sh3lby.craftsocial.CraftSocialPlugin) plugin).getPlayerIdMapper().getPlayerIdSync(player);
        clanHandler.getClan(clanId, playerId).thenAccept(clan -> {
            if (clan == null) return;
            List<Map<String, Object>> homes = clan.getHomes() != null ? clan.getHomes() : new ArrayList<>();
            if (slot < homes.size()) {
                Map<String, Object> home = homes.get(slot);
                String name = String.valueOf(home.get("name"));
                if (rightClick) {
                    clanHandler.removeHome(clanId, name, playerId).thenAccept(updatedClan -> {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            MessagePalette.sendRaw(player, updatedClan != null ? "§6Дом удалён." : "§cОшибка.");
                            if (updatedClan != null) openHomesGui(player, updatedClan);
                            else openHomesGui(player, clanId);
                        });
                    });
                } else {
                    String dim = String.valueOf(home.get("dim"));
                    double x = ((Number) home.getOrDefault("x", 0)).doubleValue();
                    double y = ((Number) home.getOrDefault("y", 64)).doubleValue();
                    double z = ((Number) home.getOrDefault("z", 0)).doubleValue();
                    World w = Bukkit.getWorld(dim);
                    if (w != null) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            player.closeInventory();
                            player.teleport(new org.bukkit.Location(w, x, y, z));
                            MessagePalette.sendRaw(player, "§6Телепорт на дом §c" + name + "§6.");
                        });
                    } else {
                        MessagePalette.sendRaw(player, "§cМир не найден.");
                    }
                }
            } else {
                if (rightClick) return;
                String homeName = "home" + (slot + 1);
                String dim = player.getWorld().getName();
                clanHandler.addHome(clanId, homeName, dim, player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ(), playerId)
                    .thenAccept(updatedClan -> {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            MessagePalette.sendRaw(player, updatedClan != null
                                ? "§6Дом §c" + homeName + " §6установлен."
                                : "§cНе удалось (макс. §f" + ClanHandler.MAX_CLAN_HOMES + "§c).");
                            if (updatedClan != null) openHomesGui(player, updatedClan);
                            else openHomesGui(player, clanId);
                        });
                    });
            }
        });
    }
    
}
