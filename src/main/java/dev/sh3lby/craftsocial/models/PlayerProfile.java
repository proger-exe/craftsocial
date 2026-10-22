package dev.sh3lby.craftsocial.models;

import com.google.gson.annotations.SerializedName;

/**
 * Профиль игрока.
 * Формат ответа POST/GET /user/profile: объект в корне с полями id, player_id, nickname, age, gender, source.
 */
public class PlayerProfile {
    private int id;
    @SerializedName("player_id")
    private long playerId;
    private String nickname;
    private Integer age;
    private String gender;
    private String source;
    
    public PlayerProfile() {}
    
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
    
    public String getNickname() {
        return nickname;
    }
    
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
    
    public Integer getAge() {
        return age;
    }
    
    public void setAge(Integer age) {
        this.age = age;
    }
    
    public String getGender() {
        return gender;
    }
    
    public void setGender(String gender) {
        this.gender = gender;
    }
    
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
}
