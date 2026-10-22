package dev.sh3lby.craftsocial.models;

/**
 * Участник клана.
 */
public class ClanMember {
    private int id;
    private long playerId;
    private int clanId;
    private String role; // leader, moderator, member
    private String joinedAt;
    
    public ClanMember() {}
    
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
    
    public int getClanId() {
        return clanId;
    }
    
    public void setClanId(int clanId) {
        this.clanId = clanId;
    }
    
    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }
    
    public String getJoinedAt() {
        return joinedAt;
    }
    
    public void setJoinedAt(String joinedAt) {
        this.joinedAt = joinedAt;
    }
    
    public boolean isLeader() {
        return "leader".equals(role);
    }
    
    public boolean isModerator() {
        return "moderator".equals(role) || isLeader();
    }
}
