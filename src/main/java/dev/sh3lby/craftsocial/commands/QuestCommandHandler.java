package dev.sh3lby.craftsocial.commands;

import dev.sh3lby.craftsocial.api.ApiClient;
import dev.sh3lby.craftsocial.api.ApiResponse;
import dev.sh3lby.craftsocial.models.PlayerQuest;
import dev.sh3lby.craftsocial.models.PlayerQuestApplication;
import dev.sh3lby.craftsocial.utils.GsonUtils;
import dev.sh3lby.craftsocial.utils.MessagePalette;
import dev.sh3lby.craftsocial.utils.PlayerIdMapper;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Команды для маркетплейса заданий (player-created quests).
 * Соответствует контракту из API_UPDATE.md в CraftCoreAPI.
 */
public class QuestCommandHandler implements CommandExecutor, TabCompleter {
    private static final Logger logger = LoggerFactory.getLogger(QuestCommandHandler.class);
    private static final int PAGE_SIZE = 20;
    private static final List<String> SUBCOMMANDS = Arrays.asList(
        "list", "mine", "info", "create", "apply", "apps", "myapps",
        "accept", "reject", "withdraw", "submit", "approve", "changes",
        "abandon", "cancel", "delete"
    );
    private static final List<String> QUEST_STATUSES = Arrays.asList(
        "open", "in_progress", "pending_review", "changes_requested",
        "completed", "cancelled", "deleted"
    );
    private static final List<String> APPLICATION_STATUSES = Arrays.asList(
        "applied", "accepted", "rejected", "withdrawn", "submitted",
        "changes_requested", "completed", "cancelled"
    );

    private final ApiClient apiClient;
    private final PlayerIdMapper playerIdMapper;

    public QuestCommandHandler(ApiClient apiClient, PlayerIdMapper playerIdMapper) {
        this.apiClient = apiClient;
        this.playerIdMapper = playerIdMapper;
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

        String sub = args[0].toLowerCase();
        try {
            switch (sub) {
                case "list":
                    handleList(player, playerId, args, null);
                    return true;
                case "mine":
                    handleList(player, playerId, args, playerId);
                    return true;
                case "info":
                    handleInfo(player, playerId, args);
                    return true;
                case "create":
                    handleCreate(player, playerId, args);
                    return true;
                case "apply":
                    handleApply(player, playerId, args);
                    return true;
                case "apps":
                    handleQuestApplications(player, playerId, args);
                    return true;
                case "myapps":
                    handleMyApplications(player, playerId, args);
                    return true;
                case "accept":
                    handleAppAction(player, playerId, args, "accept");
                    return true;
                case "reject":
                    handleAppReview(player, playerId, args);
                    return true;
                case "withdraw":
                    handleAppAction(player, playerId, args, "withdraw");
                    return true;
                case "submit":
                    handleSubmit(player, playerId, args);
                    return true;
                case "approve":
                    handleQuestAction(player, playerId, args, "approve", "§6Задание принято, награда выплачена");
                    return true;
                case "changes":
                    handleRequestChanges(player, playerId, args);
                    return true;
                case "abandon":
                    handleQuestAction(player, playerId, args, "abandon", "§6Вы отказались от задания");
                    return true;
                case "cancel":
                    handleQuestAction(player, playerId, args, "cancel", "§6Задание отменено, награда возвращена");
                    return true;
                case "delete":
                    handleDelete(player, playerId, args);
                    return true;
                default:
                    sendUsage(player);
                    return true;
            }
        } catch (NumberFormatException e) {
            MessagePalette.sendRaw(player, "§cНеверный формат числа");
            return true;
        } catch (Exception e) {
            logger.error("Error executing quest command", e);
            MessagePalette.sendRaw(player, "§cОшибка выполнения команды: §f" + e.getMessage());
            return true;
        }
    }

    private void handleList(Player player, long playerId, String[] args, Long creatorId) {
        String status = args.length > 1 ? args[1] : null;
        int page = args.length > 2 ? Math.max(1, Integer.parseInt(args[2])) : 1;
        int offset = (page - 1) * PAGE_SIZE;

        StringBuilder endpoint = new StringBuilder("/user/quests/player-created?limit=")
            .append(PAGE_SIZE).append("&offset=").append(offset);
        if (status != null) endpoint.append("&status=").append(status);
        if (creatorId != null) endpoint.append("&creator_id=").append(creatorId);

        apiClient.get(endpoint.toString(), List.class, playerId).thenAccept(response -> {
            if (!response.isSuccess()) {
                MessagePalette.sendRaw(player, "§cОшибка загрузки заданий: §f" + response.getError());
                return;
            }
            List<PlayerQuest> quests = deserializeList(response, PlayerQuest.class);
            String header = creatorId != null ? "Мои задания" : "Задания";
            MessagePalette.sendRaw(player, "§c=== " + header + " (стр. §6" + page + "§c) ===");
            if (quests.isEmpty()) {
                MessagePalette.sendRaw(player, "§fПусто");
                return;
            }
            for (PlayerQuest q : quests) {
                MessagePalette.sendRaw(player,
                    "§6#" + q.getId() + " §f" + q.getTitle()
                        + " §c[" + q.getStatus() + "] §fнаграда: §6" + q.getReward()
                        + " §fоткликов: §6" + q.getApplicationsCount());
            }
            MessagePalette.sendRaw(player, "§fПодробнее: §6/quest info <id>");
        });
    }

    private void handleInfo(Player player, long playerId, String[] args) {
        if (args.length < 2) {
            MessagePalette.sendRaw(player, "§cИспользование: §6/quest info <id>");
            return;
        }
        int questId = Integer.parseInt(args[1]);
        apiClient.get("/user/quests/" + questId, PlayerQuest.class, playerId).thenAccept(response -> {
            if (!response.isSuccess() || response.getData() == null) {
                MessagePalette.sendRaw(player, "§cЗадание не найдено: §f" + response.getError());
                return;
            }
            renderQuest(player, response.getData());
        });
    }

    private void renderQuest(Player player, PlayerQuest q) {
        MessagePalette.sendRaw(player, "§c=== Задание §6#" + q.getId() + " §c===");
        MessagePalette.sendRaw(player, "§fНазвание: §6" + q.getTitle());
        MessagePalette.sendRaw(player, "§fОписание: §f" + q.getDescription());
        MessagePalette.sendRaw(player, "§fНаграда: §6" + q.getReward());
        MessagePalette.sendRaw(player, "§fСтатус: §6" + q.getStatus());
        MessagePalette.sendRaw(player, "§fАвтор: §6" + q.getCreatorId());
        if (q.getExecutorId() != null) {
            MessagePalette.sendRaw(player, "§fИсполнитель: §6" + q.getExecutorId());
        }
        MessagePalette.sendRaw(player, "§fОткликов: §6" + q.getApplicationsCount());
        if (q.getMyApplicationStatus() != null) {
            MessagePalette.sendRaw(player, "§fМой отклик: §6" + q.getMyApplicationStatus());
        }
        if (q.getSubmissionMessage() != null) {
            MessagePalette.sendRaw(player, "§fОтчёт: §f" + q.getSubmissionMessage());
        }
        if (q.getReviewComment() != null) {
            MessagePalette.sendRaw(player, "§fКомментарий: §f" + q.getReviewComment());
        }
        List<String> actions = new ArrayList<>();
        if (q.isCanApply()) actions.add("apply");
        if (q.isCanViewApplications()) actions.add("apps");
        if (q.isCanSubmit()) actions.add("submit");
        if (q.isCanApprove()) actions.add("approve");
        if (q.isCanRequestChanges()) actions.add("changes");
        if (q.isCanAbandon()) actions.add("abandon");
        if (q.isCanCancel()) actions.add("cancel");
        if (q.isCanDelete()) actions.add("delete");
        if (!actions.isEmpty()) {
            MessagePalette.sendRaw(player, "§fДействия: §6" + String.join(", ", actions));
        }
    }

    private void handleCreate(Player player, long playerId, String[] args) {
        if (args.length < 3) {
            MessagePalette.sendRaw(player, "§cИспользование: §6/quest create <награда> <название [| описание]>");
            return;
        }
        int reward = Integer.parseInt(args[1]);
        String rest = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
        String title;
        String description;
        int sep = rest.indexOf('|');
        if (sep >= 0) {
            title = rest.substring(0, sep).trim();
            description = rest.substring(sep + 1).trim();
        } else {
            title = rest;
            description = rest;
        }
        if (title.isEmpty()) {
            MessagePalette.sendRaw(player, "§cНазвание не может быть пустым");
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", description);
        body.put("reward", reward);

        apiClient.post("/user/quests/create", body, PlayerQuest.class, playerId).thenAccept(response -> {
            if (!response.isSuccess() || response.getData() == null) {
                MessagePalette.sendRaw(player, "§cНе удалось создать задание: §f" + errorMessage(response));
                return;
            }
            PlayerQuest q = response.getData();
            MessagePalette.sendRaw(player, "§6Задание #" + q.getId() + " создано, награда §f" + q.getReward() + " §6заморожена");
        });
    }

    private void handleApply(Player player, long playerId, String[] args) {
        if (args.length < 2) {
            MessagePalette.sendRaw(player, "§cИспользование: §6/quest apply <id> [сопроводительное сообщение]");
            return;
        }
        int questId = Integer.parseInt(args[1]);
        String coverLetter = args.length > 2
            ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim()
            : null;

        Map<String, Object> body = new HashMap<>();
        body.put("cover_letter", coverLetter);

        apiClient.post("/user/quests/" + questId + "/applications", body, PlayerQuestApplication.class, playerId)
            .thenAccept(response -> {
                if (!response.isSuccess() || response.getData() == null) {
                    MessagePalette.sendRaw(player, "§cНе удалось откликнуться: §f" + errorMessage(response));
                    return;
                }
                MessagePalette.sendRaw(player, "§6Отклик #" + response.getData().getId() + " отправлен");
            });
    }

    private void handleQuestApplications(Player player, long playerId, String[] args) {
        if (args.length < 2) {
            MessagePalette.sendRaw(player, "§cИспользование: §6/quest apps <questId>");
            return;
        }
        int questId = Integer.parseInt(args[1]);
        apiClient.get("/user/quests/" + questId + "/applications", List.class, playerId).thenAccept(response -> {
            if (!response.isSuccess()) {
                MessagePalette.sendRaw(player, "§cОшибка загрузки откликов: §f" + errorMessage(response));
                return;
            }
            List<PlayerQuestApplication> apps = deserializeList(response, PlayerQuestApplication.class);
            MessagePalette.sendRaw(player, "§c=== Отклики на задание §6#" + questId + " §c===");
            if (apps.isEmpty()) {
                MessagePalette.sendRaw(player, "§fПусто");
                return;
            }
            for (PlayerQuestApplication a : apps) {
                MessagePalette.sendRaw(player,
                    "§6#" + a.getId() + " §fигрок §6" + a.getApplicantId()
                        + " §c[" + a.getStatus() + "]"
                        + (a.getCoverLetter() != null ? " §f— " + a.getCoverLetter() : ""));
            }
        });
    }

    private void handleMyApplications(Player player, long playerId, String[] args) {
        String status = args.length > 1 ? args[1] : null;
        int page = args.length > 2 ? Math.max(1, Integer.parseInt(args[2])) : 1;
        int offset = (page - 1) * PAGE_SIZE;

        StringBuilder endpoint = new StringBuilder("/user/quests/applications/mine?limit=")
            .append(PAGE_SIZE).append("&offset=").append(offset);
        if (status != null) endpoint.append("&status=").append(status);

        apiClient.get(endpoint.toString(), List.class, playerId).thenAccept(response -> {
            if (!response.isSuccess()) {
                MessagePalette.sendRaw(player, "§cОшибка загрузки: §f" + errorMessage(response));
                return;
            }
            List<PlayerQuestApplication> apps = deserializeList(response, PlayerQuestApplication.class);
            MessagePalette.sendRaw(player, "§c=== Мои отклики (стр. §6" + page + "§c) ===");
            if (apps.isEmpty()) {
                MessagePalette.sendRaw(player, "§fПусто");
                return;
            }
            for (PlayerQuestApplication a : apps) {
                MessagePalette.sendRaw(player,
                    "§6#" + a.getId() + " §fзадание §6#" + a.getQuestId()
                        + (a.getQuestTitle() != null ? " §f" + a.getQuestTitle() : "")
                        + " §c[" + a.getStatus() + "]");
            }
        });
    }

    private void handleAppAction(Player player, long playerId, String[] args, String action) {
        if (args.length < 3) {
            MessagePalette.sendRaw(player, "§cИспользование: §6/quest " + action + " <questId> <appId>");
            return;
        }
        int questId = Integer.parseInt(args[1]);
        int appId = Integer.parseInt(args[2]);
        String endpoint = "/user/quests/" + questId + "/applications/" + appId + "/" + action;
        apiClient.post(endpoint, null, Void.class, playerId).thenAccept(response -> {
            if (!response.isSuccess()) {
                MessagePalette.sendRaw(player, "§cОшибка: §f" + errorMessage(response));
                return;
            }
            MessagePalette.sendRaw(player, "§6Готово");
        });
    }

    private void handleAppReview(Player player, long playerId, String[] args) {
        if (args.length < 3) {
            MessagePalette.sendRaw(player, "§cИспользование: §6/quest reject <questId> <appId> [комментарий]");
            return;
        }
        int questId = Integer.parseInt(args[1]);
        int appId = Integer.parseInt(args[2]);
        String comment = args.length > 3
            ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)).trim()
            : null;

        Map<String, Object> body = new HashMap<>();
        body.put("comment", comment);

        String endpoint = "/user/quests/" + questId + "/applications/" + appId + "/reject";
        apiClient.post(endpoint, body, PlayerQuestApplication.class, playerId).thenAccept(response -> {
            if (!response.isSuccess()) {
                MessagePalette.sendRaw(player, "§cОшибка: §f" + errorMessage(response));
                return;
            }
            MessagePalette.sendRaw(player, "§6Отклик отклонён");
        });
    }

    private void handleSubmit(Player player, long playerId, String[] args) {
        if (args.length < 2) {
            MessagePalette.sendRaw(player, "§cИспользование: §6/quest submit <questId> [сообщение]");
            return;
        }
        int questId = Integer.parseInt(args[1]);
        String message = args.length > 2
            ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim()
            : null;

        Map<String, Object> body = new HashMap<>();
        body.put("submission_message", message);

        apiClient.post("/user/quests/" + questId + "/submit", body, PlayerQuest.class, playerId).thenAccept(response -> {
            if (!response.isSuccess()) {
                MessagePalette.sendRaw(player, "§cОшибка отправки на проверку: §f" + errorMessage(response));
                return;
            }
            MessagePalette.sendRaw(player, "§6Работа отправлена на проверку");
        });
    }

    private void handleRequestChanges(Player player, long playerId, String[] args) {
        if (args.length < 2) {
            MessagePalette.sendRaw(player, "§cИспользование: §6/quest changes <questId> [комментарий]");
            return;
        }
        int questId = Integer.parseInt(args[1]);
        String comment = args.length > 2
            ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim()
            : null;

        Map<String, Object> body = new HashMap<>();
        body.put("comment", comment);

        apiClient.post("/user/quests/" + questId + "/request-changes", body, PlayerQuest.class, playerId).thenAccept(response -> {
            if (!response.isSuccess()) {
                MessagePalette.sendRaw(player, "§cОшибка: §f" + errorMessage(response));
                return;
            }
            MessagePalette.sendRaw(player, "§6Отправлено на доработку");
        });
    }

    private void handleQuestAction(Player player, long playerId, String[] args, String action, String successMessage) {
        if (args.length < 2) {
            MessagePalette.sendRaw(player, "§cИспользование: §6/quest " + action + " <questId>");
            return;
        }
        int questId = Integer.parseInt(args[1]);
        apiClient.post("/user/quests/" + questId + "/" + action, null, PlayerQuest.class, playerId).thenAccept(response -> {
            if (!response.isSuccess()) {
                MessagePalette.sendRaw(player, "§cОшибка: §f" + errorMessage(response));
                return;
            }
            MessagePalette.sendRaw(player, successMessage);
        });
    }

    private void handleDelete(Player player, long playerId, String[] args) {
        if (args.length < 2) {
            MessagePalette.sendRaw(player, "§cИспользование: §6/quest delete <questId>");
            return;
        }
        int questId = Integer.parseInt(args[1]);
        apiClient.delete("/user/quests/" + questId, Void.class, playerId).thenAccept(response -> {
            if (!response.isSuccess()) {
                MessagePalette.sendRaw(player, "§cОшибка удаления: §f" + errorMessage(response));
                return;
            }
            MessagePalette.sendRaw(player, "§6Задание удалено");
        });
    }

    private static <T> List<T> deserializeList(ApiResponse<?> response, Class<T> itemClass) {
        if (response.getRawJson() == null) return new ArrayList<>();
        return GsonUtils.deserializeList(response.getRawJson(), itemClass);
    }

    private static String errorMessage(ApiResponse<?> response) {
        if (response.getDetail() != null && !response.getDetail().isEmpty()) return response.getDetail();
        if (response.getError() != null && !response.getError().isEmpty()) return response.getError();
        return "HTTP " + response.getStatusCode();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("list") || sub.equals("mine")) {
                return QUEST_STATUSES.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
            if (sub.equals("myapps")) {
                return APPLICATION_STATUSES.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }

    private void sendUsage(Player player) {
        MessagePalette.sendRaw(player, "§c=== Задания ===");
        MessagePalette.sendRaw(player, "§6/quest list [status] [page] §f- Список заданий");
        MessagePalette.sendRaw(player, "§6/quest mine [status] [page] §f- Мои задания");
        MessagePalette.sendRaw(player, "§6/quest info <id> §f- Детали");
        MessagePalette.sendRaw(player, "§6/quest create <награда> <название [| описание]> §f- Создать");
        MessagePalette.sendRaw(player, "§6/quest apply <id> [сообщение] §f- Откликнуться");
        MessagePalette.sendRaw(player, "§6/quest apps <id> §f- Отклики на моё задание");
        MessagePalette.sendRaw(player, "§6/quest myapps [status] [page] §f- Мои отклики");
        MessagePalette.sendRaw(player, "§6/quest accept <questId> <appId> §f- Принять отклик");
        MessagePalette.sendRaw(player, "§6/quest reject <questId> <appId> [комментарий] §f- Отклонить отклик");
        MessagePalette.sendRaw(player, "§6/quest withdraw <questId> <appId> §f- Отозвать свой отклик");
        MessagePalette.sendRaw(player, "§6/quest submit <id> [сообщение] §f- Отправить работу");
        MessagePalette.sendRaw(player, "§6/quest approve <id> §f- Принять работу");
        MessagePalette.sendRaw(player, "§6/quest changes <id> [комментарий] §f- Вернуть на доработку");
        MessagePalette.sendRaw(player, "§6/quest abandon <id> §f- Отказаться от задания");
        MessagePalette.sendRaw(player, "§6/quest cancel <id> §f- Отменить задание");
        MessagePalette.sendRaw(player, "§6/quest delete <id> §f- Удалить задание");
    }
}
