package dev.sh3lby.craftsocial.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory хранилище приглашений в клан.
 * Приглашённый игрок получает запись; принятие/отклонение через /clan accept и /clan decline.
 */
public class ClanInviteManager {
    /** По playerId приглашённого — список приглашений (clanId + inviter). */
    private final Map<Long, List<ClanInvite>> invitesByPlayer = new ConcurrentHashMap<>();

    public static final class ClanInvite {
        private final int clanId;
        private final long inviterPlayerId;
        private final String inviterName;
        private final String clanName;

        public ClanInvite(int clanId, long inviterPlayerId, String inviterName, String clanName) {
            this.clanId = clanId;
            this.inviterPlayerId = inviterPlayerId;
            this.inviterName = inviterName != null ? inviterName : "";
            this.clanName = clanName != null ? clanName : ("ID " + clanId);
        }

        public int getClanId() { return clanId; }
        public long getInviterPlayerId() { return inviterPlayerId; }
        public String getInviterName() { return inviterName; }
        public String getClanName() { return clanName; }
    }

    /**
     * Добавляет приглашение для игрока invitedPlayerId в клан clanId от inviter.
     * clanName — для сообщения приглашённому (например из Clan.getName()).
     */
    public void addInvite(long invitedPlayerId, int clanId, long inviterPlayerId, String inviterName, String clanName) {
        invitesByPlayer.computeIfAbsent(invitedPlayerId, k -> new ArrayList<>())
            .add(new ClanInvite(clanId, inviterPlayerId, inviterName, clanName));
    }

    /**
     * Удаляет приглашение в клан clanId для игрока invitedPlayerId.
     */
    public void removeInvite(long invitedPlayerId, int clanId) {
        List<ClanInvite> list = invitesByPlayer.get(invitedPlayerId);
        if (list == null) return;
        list.removeIf(inv -> inv.getClanId() == clanId);
        if (list.isEmpty()) invitesByPlayer.remove(invitedPlayerId);
    }

    /**
     * Возвращает список приглашений для игрока (не null).
     */
    public List<ClanInvite> getInvites(long invitedPlayerId) {
        List<ClanInvite> list = invitesByPlayer.get(invitedPlayerId);
        return list != null ? new ArrayList<>(list) : new ArrayList<>();
    }

    /**
     * Берёт одно приглашение для игрока (первое в списке) и удаляет его. Возвращает null, если приглашений нет.
     */
    public ClanInvite takeFirstInvite(long invitedPlayerId) {
        List<ClanInvite> list = invitesByPlayer.get(invitedPlayerId);
        if (list == null || list.isEmpty()) return null;
        ClanInvite first = list.remove(0);
        if (list.isEmpty()) invitesByPlayer.remove(invitedPlayerId);
        return first;
    }

    /**
     * Удаляет все приглашения для игрока в клан clanId.
     */
    public void removeAllInvitesToClan(long invitedPlayerId, int clanId) {
        removeInvite(invitedPlayerId, clanId);
    }
}
