package net.cozygielinor.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import okhttp3.Request;

final class PortalPairing
{
    static final String ORIGIN = "https://home.cozygielinor.net";
    private final BridgeHttp http;
    private final Gson gson;
    private volatile Credential credential;
    private volatile boolean relayEnabled;
    private volatile boolean leadershipRelayEnabled;

    PortalPairing(BridgeHttp http, Gson gson)
    {
        this.http = http;
        this.gson = gson;
    }

    void exchange(String code, java.util.function.Consumer<Boolean> result)
    {
        JsonObject body = new JsonObject();
        body.addProperty("code", code);
        Request request = new Request.Builder().url(ORIGIN + "/api/runelite/pairing/exchange")
            .post(BridgeHttp.json(body.toString())).build();
        http.send(request, 15, true, (status, response) ->
        {
            Credential paired = null;
            if (status == 200)
            {
                try
                {
                    paired = gson.fromJson(response, Credential.class);
                }
                catch (JsonParseException ignored)
                {
                    // Treat an invalid response like a rejected code.
                }
            }
            boolean accepted = paired != null && paired.token != null && paired.account != null
                && paired.expiresAt > System.currentTimeMillis();
            if (accepted)
            {
                credential = paired;
            }
            result.accept(accepted);
        });
    }

    void setRelayEnabled(boolean leadership, boolean enabled)
    {
        if (leadership)
        {
            leadershipRelayEnabled = enabled;
        }
        else
        {
            relayEnabled = enabled;
        }
    }

    boolean isRelayEnabled(boolean leadership)
    {
        return leadership ? leadershipRelayEnabled : relayEnabled;
    }

    Credential credentialFor(String character)
    {
        Credential current = credential;
        return character != null && current != null && current.expiresAt > System.currentTimeMillis()
            && character.replace('_', ' ').equalsIgnoreCase(current.account.replace('_', ' ')) ? current : null;
    }

    static final class Credential
    {
        String token;
        String account;
        long expiresAt;
    }
}
