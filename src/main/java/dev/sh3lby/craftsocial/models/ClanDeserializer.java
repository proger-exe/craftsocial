package dev.sh3lby.craftsocial.models;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Десериализует Clan из ответа API. Поле "members" приходит как массив пар [player_id, role];
 * парсим его явно и вызываем setMembers(), чтобы не зависеть от Gson для List&lt;List&lt;Object&gt;&gt;.
 */
public class ClanDeserializer implements JsonDeserializer<Clan> {

    @Override
    public Clan deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        JsonObject copy = obj.deepCopy();
        copy.remove("members");

        Clan clan = new Gson().fromJson(copy, Clan.class);
        if (clan == null) {
            clan = new Clan();
        }

        if (obj.has("members") && obj.get("members").isJsonArray()) {
            JsonArray membersArray = obj.getAsJsonArray("members");
            List<ClanMember> members = new ArrayList<>();
            int clanId = clan.getId();
            for (JsonElement e : membersArray) {
                if (!e.isJsonArray() || e.getAsJsonArray().size() < 2) {
                    continue;
                }
                JsonArray pair = e.getAsJsonArray();
                long playerId = pair.get(0).getAsLong();
                String role = pair.get(1).getAsString();
                ClanMember member = new ClanMember();
                member.setPlayerId(playerId);
                member.setRole(role);
                member.setClanId(clanId);
                members.add(member);
            }
            clan.setMembers(members);
        }

        return clan;
    }
}
