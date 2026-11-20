package net.cozygielinor.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.Request;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import net.runelite.client.RuneLite;

final class PortalPairing
{
    static final String ORIGIN = "https://home.cozygielinor.net";
    private final BridgeHttp http;
    private final Gson gson;
    private volatile Credential credential;
    private volatile boolean leadershipApproved;
    private final Path savedCredential = RuneLite.RUNELITE_DIR.toPath().resolve("cozygielinor-pairing.json");

    PortalPairing(BridgeHttp http, Gson gson)
    {
        this.http = http;
        this.gson = gson;
        try
        {
            if (Files.exists(savedCredential)) credential = gson.fromJson(Files.readString(savedCredential), Credential.class);
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
        try { Files.deleteIfExists(savedCredential); } catch (Exception ignored) { }
    }

    private synchronized void save()
    {
        Path temporary = savedCredential.resolveSibling("cozygielinor-pairing.tmp");
        try
        {
            Files.createDirectories(savedCredential.getParent());
            Files.deleteIfExists(temporary);
            Files.createFile(temporary);
            try { Files.setPosixFilePermissions(temporary, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")); }
            catch (UnsupportedOperationException ignored) { }
            Files.writeString(temporary, gson.toJson(credential), StandardCharsets.UTF_8);
            Files.move(temporary, savedCredential, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (Exception ignored)
        {
            try { Files.deleteIfExists(temporary); } catch (Exception cleanupIgnored) { }
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
