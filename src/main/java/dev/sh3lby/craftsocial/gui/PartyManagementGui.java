package dev.sh3lby.craftsocial.gui;

import dev.sh3lby.craftsocial.handlers.PartyHandler;
import dev.sh3lby.craftsocial.handlers.PlayerHandler;
import dev.sh3lby.craftsocial.models.Party;
import dev.sh3lby.craftsocial.storage.PartyInventoryManager;
import dev.sh3lby.craftsocial.utils.MessagePalette;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
 * GUI для управления группой (создание, редактирование, управление участниками, инвентарь).
 */
public class PartyManagementGui {
    private final PartyHandler partyHandler;
    private final PartyInventoryManager inventoryManager;
    private final Plugin plugin;
    
    public PartyManagementGui(PartyHandler partyHandler, PartyInventoryManager inventoryManager, Plugin plugin) {
        this.partyHandler = partyHandler;
        this.inventoryManager = inventoryManager;
        this.plugin = plugin;
    }
    
    /**
     * Открывает GUI управления группой.
     */
    public void openManagement(Player player, Integer partyId) {
        long playerId = ((dev.sh3lby.craftsocial.CraftSocialPlugin) plugin).getPlayerIdMapper().getPlayerIdSync(player);
        
        if (partyId == null) {
            // Открываем GUI создания группы
            openCreatePartyGui(player);
        } else {
            // Открываем GUI управления существующей группой
            partyHandler.getParty(partyId, playerId).thenAccept(party -> {
                if (party == null) {
                    MessagePalette.send(player, "Группа не найдена");
                    return;
                }
                List<Long> memberIds = party.getMembers() != null ? party.getMembers() : new ArrayList<>();
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Map<Long, String> names = new ConcurrentHashMap<>();
                    dev.sh3lby.craftsocial.CraftSocialPlugin js = (dev.sh3lby.craftsocial.CraftSocialPlugin) plugin;
                    for (Long mid : memberIds) {
                        String n = getOnlineDisplayName(js, mid);
                        if (n != null) names.put(mid, n);
                    }
                    List<Long> needFetch = memberIds.stream().filter(id -> !names.containsKey(id)).collect(Collectors.toList());
                    if (needFetch.isEmpty()) {
                        openManagementWithNames(player, party, names);
                        return;
                    }
                    PlayerHandler playerHandler = js.getPlayerHandler();
                    CompletableFuture<?>[] futures = needFetch.stream()
                        .map(mid -> playerHandler.getProfile(mid)
                            .thenAccept(p -> names.put(mid, p != null && p.getNickname() != null ? p.getNickname() : ("#" + mid))))
                        .toArray(CompletableFuture[]::new);
                    CompletableFuture.allOf(futures).thenRun(() ->
                        plugin.getServer().getScheduler().runTask(plugin, () -> openManagementWithNames(player, party, names)));
                });
            }).exceptionally(error -> {
                MessagePalette.send(player, "§cОшибка загрузки группы: §f" + error.getMessage());
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

    private void openManagementWithNames(Player player, Party party, Map<Long, String> memberNames) {
                    Inventory inventory = Bukkit.createInventory(null, 54, "Управление группы: " + party.getId() + " " + party.getName());

        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(MessagePalette.paint("§c" + party.getName()));
            List<String> lore = new ArrayList<>();
            lore.add("§fID: §6" + party.getId());
            String leaderName = memberNames.getOrDefault(party.getLeaderId(), "#" + party.getLeaderId());
            lore.add("§fЛидер: " + leaderName);
            lore.add("§fУчастников: §6" + (party.getMembers() != null ? party.getMembers().size() : 0));
            infoMeta.setLore(MessagePalette.paint(lore));
            infoItem.setItemMeta(infoMeta);
        }
        inventory.setItem(4, infoItem);

        ItemStack editItem = new ItemStack(Material.ANVIL);
        ItemMeta editMeta = editItem.getItemMeta();
        if (editMeta != null) {
            editMeta.setDisplayName(MessagePalette.paint("§6Редактировать группу"));
            editMeta.setLore(MessagePalette.paint(List.of("§fИспользуйте команду:", "§6/party edit <name>")));
            editItem.setItemMeta(editMeta);
        }
        inventory.setItem(9, editItem);

        ItemStack inviteItem = new ItemStack(Material.EMERALD);
        ItemMeta inviteMeta = inviteItem.getItemMeta();
        if (inviteMeta != null) {
            inviteMeta.setDisplayName(MessagePalette.paint("§cПригласить игрока"));
            inviteMeta.setLore(MessagePalette.paint(List.of("§fИспользуйте команду:", "§6/party invite <player>")));
            inviteItem.setItemMeta(inviteMeta);
        }
        inventory.setItem(10, inviteItem);

        ItemStack inventoryItem = new ItemStack(Material.CHEST);
        ItemMeta inventoryMeta = inventoryItem.getItemMeta();
        if (inventoryMeta != null) {
            inventoryMeta.setDisplayName(MessagePalette.paint("§cИнвентарь группы"));
            inventoryMeta.setLore(MessagePalette.paint(List.of("§fНажмите для открытия", "§fинвентаря группы")));
            inventoryItem.setItemMeta(inventoryMeta);
        }
        inventory.setItem(12, inventoryItem);

        ItemStack leaveItem = new ItemStack(Material.OAK_DOOR);
        ItemMeta leaveMeta = leaveItem.getItemMeta();
        if (leaveMeta != null) {
            leaveMeta.setDisplayName(MessagePalette.paint("§6Покинуть группу"));
            leaveMeta.setLore(MessagePalette.paint(List.of("§fИспользуйте команду:", "§6/party leave")));
            leaveItem.setItemMeta(leaveMeta);
        }
        inventory.setItem(16, leaveItem);

        ItemStack deleteItem = new ItemStack(Material.BARRIER);
        ItemMeta deleteMeta = deleteItem.getItemMeta();
        if (deleteMeta != null) {
            deleteMeta.setDisplayName(MessagePalette.paint("§cУдалить группу"));
            deleteMeta.setLore(MessagePalette.paint(List.of("§fИспользуйте команду:", "§6/party delete")));
            deleteItem.setItemMeta(deleteMeta);
        }
        inventory.setItem(17, deleteItem);

        if (party.getMembers() != null) {
            int slot = 18;
            for (Long memberId : party.getMembers()) {
                if (slot >= 45) break;
                String displayName = memberNames.getOrDefault(memberId, "#" + memberId);
                boolean isLeader = (memberId == party.getLeaderId());

                ItemStack memberItem = new ItemStack(Material.PLAYER_HEAD);
                ItemMeta memberMeta = memberItem.getItemMeta();
                if (memberMeta != null) {
                    memberMeta.setDisplayName(MessagePalette.paint("§c" + displayName));
                    List<String> memberLore = new ArrayList<>();
                    memberLore.add("§fРоль: §6" + (isLeader ? "Лидер" : "Участник"));
                    memberLore.add("");
                    if (isLeader) {
                        memberLore.add("§6ЛКМ - Передать лидерство");
                    } else {
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
     * Открывает инвентарь группы.
     */
    public void openInventory(Player player, int partyId) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack[] partyInventory = inventoryManager.getInventory(partyId);
            Inventory inventory = Bukkit.createInventory(null, 54, "Инвентарь группы: " + partyId);
            
            // Копируем предметы из локального хранилища
            for (int i = 0; i < partyInventory.length && i < 54; i++) {
                if (partyInventory[i] != null) {
                    inventory.setItem(i, partyInventory[i].clone());
                }
            }
            
            player.openInventory(inventory);
        });
    }
    
    /**
     * Сохраняет инвентарь группы при закрытии GUI.
     */
    public void saveInventory(Player player, int partyId, Inventory inventory) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack[] items = new ItemStack[54];
            for (int i = 0; i < 54; i++) {
                ItemStack item = inventory.getItem(i);
                items[i] = item != null ? item.clone() : null;
            }
            inventoryManager.setInventory(partyId, items);
        });
    }
    
    /**
     * Открывает GUI создания группы.
     */
    private void openCreatePartyGui(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 9, "Создание группы");
            
            ItemStack infoItem = new ItemStack(Material.BOOK);
            ItemMeta infoMeta = infoItem.getItemMeta();
            if (infoMeta != null) {
                infoMeta.setDisplayName(MessagePalette.paint("§cСоздание группы"));
                List<String> lore = new ArrayList<>();
                lore.add("§fИспользуйте команду:");
                lore.add("§6/party create <name>");
                lore.add("");
                lore.add("§fПример:");
                lore.add("§6/party create AdventureSquad");
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
    public void handleClick(Player player, int slot, ItemStack clickedItem, Integer partyId) {
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }
        
        if (slot == 9) {
            // Редактировать группу
            player.closeInventory();
            MessagePalette.send(player, "Используйте команду: §6/party edit <name>");
        } else if (slot == 10) {
            // Пригласить игрока
            player.closeInventory();
            MessagePalette.send(player, "Используйте команду: §6/party invite <player>");
        } else if (slot == 12 && partyId != null) {
            // Открыть инвентарь группы
            player.closeInventory();
            openInventory(player, partyId);
        } else if (slot == 16) {
            // Покинуть группу
            player.closeInventory();
            MessagePalette.send(player, "Используйте команду: §6/party leave");
        } else if (slot == 17) {
            // Удалить группу
            player.closeInventory();
            MessagePalette.send(player, "Используйте команду: §6/party delete");
        } else if (slot >= 18 && slot < 45) {
            // Клик по участнику
            player.closeInventory();
            MessagePalette.send(player, "Используйте команды: §6/party transfer§f, §6/party kick");
        }
    }
    
}
