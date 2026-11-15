package net.cozygielinor.runelite;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Duration;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.gameval.VarbitID;
import okhttp3.Request;

final class ProgressSync
{
    // RuneLite gameval VarPlayerID.MUSICMULTI_1 through MUSICMULTI_28.
    private static final int[] MUSIC = {20, 21, 22, 23, 24, 25, 298, 311, 346, 414,
        464, 598, 662, 721, 906, 1009, 1338, 1681, 2065, 2237, 2950, 3418, 3575,
        4066, 4411, 4944, 5238, 6014};

    private final BridgeHttp http;
    private final PortalPairing pairing;
    private volatile String saved;
    private volatile boolean sending;
    private long nextCheck;
    private volatile long session;

    ProgressSync(BridgeHttp http, PortalPairing pairing)
    {
        this.http = http;
        this.pairing = pairing;
    }

    void reset()
    {
        session++;
        saved = null;
        nextCheck = 0;
    }

    void tick(Client client)
    {
        if (client.getGameState() != GameState.LOGGED_IN || sending
            || System.nanoTime() < nextCheck)
        {
            return;
        }
        Player player = client.getLocalPlayer();
        PortalPairing.Credential credential = player == null ? null : pairing.credentialFor(player.getName());
        if (credential == null)
        {
            return;
        }
        int[] varps = client.getVarps();
        if (varps.length <= MUSIC[MUSIC.length - 1])
        {
            return;
        }
        nextCheck = System.nanoTime() + Duration.ofSeconds(30).toNanos();

        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("account", player.getName());
        snapshot.addProperty("questPoints", varps[101]);
        JsonObject quests = new JsonObject();
        for (Quest quest : Quest.values())
        {
            quests.addProperty(Integer.toString(quest.getId()), quest.getState(client).name());
        }
        snapshot.add("questStates", quests);
        JsonObject diaries = new JsonObject();
        for (int id = 4458; id <= 4498; id++)
        {
            if (id != 4470) diaries.addProperty(Integer.toString(id), client.getVarbitValue(id) != 0);
        }
        for (int id : new int[]{4566, 7925, 7926, 7927, 7928})
        {
            diaries.addProperty(Integer.toString(id), client.getVarbitValue(id) != 0);
        }
        snapshot.add("diaryTiers", diaries);
        JsonObject karamja = new JsonObject();
        karamja.addProperty("easy", client.getVarbitValue(VarbitID.KARAMJA_EASY_COUNT));
        karamja.addProperty("medium", client.getVarbitValue(VarbitID.KARAMJA_MED_COUNT));
        karamja.addProperty("hard", client.getVarbitValue(VarbitID.KARAMJA_HARD_COUNT));
        snapshot.add("karamjaTaskCounts", karamja);
        JsonArray music = new JsonArray();
        for (int id : MUSIC)
        {
            music.add(Integer.toUnsignedLong(varps[id]));
        }
        snapshot.add("musicFlags", music);

        long activeSession = session;
        String body = snapshot.toString();
        if (body.equals(saved)) return;
        Request request = new Request.Builder().url(PortalPairing.ORIGIN + "/api/runelite/client/progress")
            .header("Authorization", "Bearer " + credential.token).post(BridgeHttp.json(body)).build();
        sending = true;
        http.send(request, 15, false, (code, unused) ->
        {
            if (session == activeSession && code == 200) saved = body;
            sending = false;
        });
    }
}
