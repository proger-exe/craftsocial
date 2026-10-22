package dev.sh3lby.craftsocial.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory хранилище приглашений в группу (party).
 * Приглашённый игрок получает запись; принятие/отклонение через /party accept и /party decline.
 */
public class PartyInviteManager {
    /** По playerId приглашённого — список приглашений (partyId + inviter). */
    private final Map<Long, List<PartyInvite>> invitesByPlayer = new ConcurrentHashMap<>();

    public static final class PartyInvite {
        private final int partyId;
        private final long inviterPlayerId;
        private final String inviterName;
        private final String partyName;

        public PartyInvite(int partyId, long inviterPlayerId, String inviterName, String partyName) {
            this.partyId = partyId;
            this.inviterPlayerId = inviterPlayerId;
            this.inviterName = inviterName != null ? inviterName : "";
            this.partyName = partyName != null ? partyName : ("ID " + partyId);
        }

        public int getPartyId() { return partyId; }
        public long getInviterPlayerId() { return inviterPlayerId; }
        public String getInviterName() { return inviterName; }
        public String getPartyName() { return partyName; }
    }

    /**
     * Добавляет приглашение для игрока invitedPlayerId в группу partyId от inviter.
     * partyName — для сообщения приглашённому (например из Party.getName()).
     */
    public void addInvite(long invitedPlayerId, int partyId, long inviterPlayerId, String inviterName, String partyName) {
        invitesByPlayer.computeIfAbsent(invitedPlayerId, k -> new ArrayList<>())
            .add(new PartyInvite(partyId, inviterPlayerId, inviterName, partyName));
    }

    /**
     * Удаляет приглашение в группу partyId для игрока invitedPlayerId.
     */
    public void removeInvite(long invitedPlayerId, int partyId) {
        List<PartyInvite> list = invitesByPlayer.get(invitedPlayerId);
        if (list == null) return;
        list.removeIf(inv -> inv.getPartyId() == partyId);
        if (list.isEmpty()) invitesByPlayer.remove(invitedPlayerId);
    }

    /**
     * Возвращает список приглашений для игрока (не null).
     */
    public List<PartyInvite> getInvites(long invitedPlayerId) {
        List<PartyInvite> list = invitesByPlayer.get(invitedPlayerId);
        return list != null ? new ArrayList<>(list) : new ArrayList<>();
    }

    /**
     * Берёт одно приглашение для игрока (первое в списке) и удаляет его. Возвращает null, если приглашений нет.
     */
    public PartyInvite takeFirstInvite(long invitedPlayerId) {
        List<PartyInvite> list = invitesByPlayer.get(invitedPlayerId);
        if (list == null || list.isEmpty()) return null;
        PartyInvite first = list.remove(0);
        if (list.isEmpty()) invitesByPlayer.remove(invitedPlayerId);
        return first;
    }
}
