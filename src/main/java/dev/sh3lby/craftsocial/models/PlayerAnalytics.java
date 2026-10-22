package dev.sh3lby.craftsocial.models;

import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.Map;

/**
 * Аналитика игрока.
 * API /user/analytics возвращает snake_case: player_id, clan_id, party_id и т.д.
 */
public class PlayerAnalytics {
    private int id;
    @SerializedName("player_id")
    private long playerId;
    @SerializedName("hours_played")
    private int hoursPlayed;
    @SerializedName("invites_count")
    private int invitesCount;
    private Map<String, String> socials;
    @SerializedName("world_rating")
    private String worldRating;
    @SerializedName("clan_id")
    private Integer clanId;
    @SerializedName("party_id")
    private Integer partyId;
    @SerializedName("earned_money")
    private int earnedMoney;
    @SerializedName("spent_money")
    private int spentMoney;
    private String bio;
    @SerializedName("era_stamp")
    private String eraStamp;
    @SerializedName("join_streak_days")
    private int joinStreakDays;
    @SerializedName("join_streak_max")
    private int joinStreakMax;
    @SerializedName("last_login")
    private String lastLogin;
    @SerializedName("in_war")
    private boolean inWar;
    
    public PlayerAnalytics() {
        // Инициализируем socials пустой картой, так как поле required
        this.socials = new HashMap<>();
    }
    
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
    
    public int getHoursPlayed() {
        return hoursPlayed;
    }
    
    public void setHoursPlayed(int hoursPlayed) {
        this.hoursPlayed = hoursPlayed;
    }
    
    public int getInvitesCount() {
        return invitesCount;
    }
    
    public void setInvitesCount(int invitesCount) {
        this.invitesCount = invitesCount;
    }
    
    public Map<String, String> getSocials() {
        return socials;
    }
    
    public void setSocials(Map<String, String> socials) {
        // socials required, не может быть null
        this.socials = socials != null ? socials : new HashMap<>();
    }
    
    public String getWorldRating() {
        return worldRating;
    }
    
    public void setWorldRating(String worldRating) {
        this.worldRating = worldRating;
    }
    
    public Integer getClanId() {
        return clanId;
    }
    
    public void setClanId(Integer clanId) {
        this.clanId = clanId;
    }
    
    public Integer getPartyId() {
        return partyId;
    }
    
    public void setPartyId(Integer partyId) {
        this.partyId = partyId;
    }
    
    public int getEarnedMoney() {
        return earnedMoney;
    }
    
    public void setEarnedMoney(int earnedMoney) {
        this.earnedMoney = earnedMoney;
    }
    
    public int getSpentMoney() {
        return spentMoney;
    }
    
    public void setSpentMoney(int spentMoney) {
        this.spentMoney = spentMoney;
    }
    
    public String getBio() {
        return bio;
    }
    
    public void setBio(String bio) {
        this.bio = bio;
    }
    
    public String getEraStamp() {
        return eraStamp;
    }
    
    public void setEraStamp(String eraStamp) {
        this.eraStamp = eraStamp;
    }
    
    public int getJoinStreakDays() {
        return joinStreakDays;
    }
    
    public void setJoinStreakDays(int joinStreakDays) {
        this.joinStreakDays = joinStreakDays;
    }
    
    public int getJoinStreakMax() {
        return joinStreakMax;
    }
    
    public void setJoinStreakMax(int joinStreakMax) {
        this.joinStreakMax = joinStreakMax;
    }
    
    public String getLastLogin() {
        return lastLogin;
    }
    
    public void setLastLogin(String lastLogin) {
        this.lastLogin = lastLogin;
    }
    
    public boolean isInWar() {
        return inWar;
    }
    
    public void setInWar(boolean inWar) {
        this.inWar = inWar;
    }
}
