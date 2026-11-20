package net.cozygielinor.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.Request;
import java.nio.file.StandardCopyOption;
import net.runelite.client.util.Filepath;

final class PortalPairing
{
    static final String ORIGIN = "https://home.cozygielinor.net";
    private final BridgeHttp http;
    private final Gson gson;
    private volatile Credential credential;
    private volatile boolean leadershipApproved;
    private final Filepath directory;
    private final Filepath savedCredential;

    PortalPairing(BridgeHttp http, Gson gson, Filepath directory)
    {
        this.http = http;
        this.gson = gson;
        this.directory = directory;
        this.savedCredential = directory.joinSegment("pairing.json");
        try
        {
            if (savedCredential.exists())
            {
                try (var reader = savedCredential.openBufferedReader())
                {
                    credential = gson.fromJson(reader, Credential.class);
                }
            }
            if (!valid(credential)) disconnect();
        }
        catch (Exception ignored) { disconnect(); }
    }

    void exchange(String code, String character, java.util.function.Consumer<Boolean> result)
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
                try { paired = gson.fromJson(response, Credential.class); }
                catch (RuntimeException ignored) { }
            }
            boolean accepted = valid(paired) && character != null
                && character.replace('_', ' ').equalsIgnoreCase(paired.account.replace('_', ' '));
            if (accepted)
            {
                credential = paired;
                save();
            }
            result.accept(accepted);
        });
    }

    private static boolean valid(Credential value)
    {
        return value != null && value.token != null && value.token.matches("[A-Za-z0-9_-]{32}")
            && value.account != null && value.account.matches("[A-Za-z0-9 _-]{1,12}")
            && value.expiresAt > System.currentTimeMillis();
    }

    synchronized void disconnect()
    {
        credential = null;
        leadershipApproved = false;
        try { savedCredential.deleteIfExists(); } catch (Exception ignored) { }
    }

    private synchronized void save()
    {
        Filepath temporary = null;
        try
        {
            directory.createDirectories();
            temporary = directory.createTempFile("pairing-", ".tmp");
            temporary.write(gson.toJson(credential));
            temporary.moveTo(savedCredential, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (Exception ignored) { }
        finally
        {
            if (temporary != null)
            {
                try { temporary.deleteIfExists(); } catch (Exception ignored) { }
            }
        }
    }

    void approveLeadership(boolean approved) { leadershipApproved = approved; }

    boolean leadershipApproved() { return leadershipApproved; }

    Credential credentialFor(String character)
    {
        Credential current = credential;
        return character != null && valid(current)
            && character.replace('_', ' ').equalsIgnoreCase(current.account.replace('_', ' ')) ? current : null;
    }

    static final class Credential
    {
        String token;
        String account;
        long expiresAt;
    }
}
