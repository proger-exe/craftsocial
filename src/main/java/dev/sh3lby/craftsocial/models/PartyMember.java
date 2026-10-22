package dev.sh3lby.craftsocial.models;

/**
 * Участник группы.
 */
public class PartyMember {
    private int id;
    private long playerId;
    private int partyId;
    private boolean isLeader;
    private String joinedAt;
    
    public PartyMember() {}
    
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public long getPlayerId() {
        return playerId;
    }
    
    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }
    
    public int getPartyId() {
        return partyId;
    }
    
    public void setPartyId(int partyId) {
        this.partyId = partyId;
    }
    
    public boolean isLeader() {
        return isLeader;
    }
    
    public void setLeader(boolean leader) {
        isLeader = leader;
    }
    
    public String getJoinedAt() {
        return joinedAt;
    }
    
    public void setJoinedAt(String joinedAt) {
        this.joinedAt = joinedAt;
    }
}
