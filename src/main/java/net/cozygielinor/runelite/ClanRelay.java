package net.cozygielinor.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import okhttp3.Request;
import java.util.LinkedHashSet;
import java.util.Set;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Text;

final class ClanRelay
{
    private final URI endpoint;
    private final boolean leadership;
    private final Client client;
    private final ClientThread clientThread;
    private final PortalPairing pairing;
    private final Set<String> displayedIds = new LinkedHashSet<>();
    private final Gson gson;
    private final BridgeHttp http;
    private ChatMessageType activeChat;
    private String activePlayer;
    private long nextPoll;
    private long generation;
    private String cursor;
    private boolean polling;
    private boolean rendering;
    private volatile String status = "Relay not configured";

    String status()
    {
        return status;
    }

    ClanRelay(Client client, ClientThread clientThread, Gson gson, BridgeHttp http, PortalPairing pairing, boolean leadership)
    {
        this.leadership = leadership;
        endpoint = URI.create(PortalPairing.ORIGIN + "/api/runelite/client/chat/" + (leadership ? "leadership" : "clan"));
        this.pairing = pairing;
        this.client = client;
        this.clientThread = clientThread;
        this.gson = gson;
        this.http = http;
    }

    void reset()
    {
        activeChat = null;
        status = "Discord relay disconnected";
        cursor = null;
        nextPoll = 0;
        polling = false;
        generation++;
    }

    void tick()
    {
        PortalPairing.Credential credential = relayCredential();
        if (credential == null)
        {
            if (activeChat != null) reset();
            return;
        }
        ChatMessageType chat = currentChat();
        if (chat != activeChat || !java.util.Objects.equals(activePlayer, playerName()))
        {
            reset();
            activeChat = chat;
            activePlayer = playerName();
            if (chat != null)
            {
                status = "Connecting clan relay…";
            }
        }
        long now = System.currentTimeMillis();
        if (chat == null || polling || now < nextPoll)
        {
            return;
        }
        polling = true;
        nextPoll = now + 5000;
        long requestGeneration = generation;
        URI uri = cursor == null ? endpoint : URI.create(endpoint + "?after=" + cursor);
        http.send(request(uri, credential).get().build(), 20, true, (code, body) -> clientThread.invokeLater(() ->
        {
            if (requestGeneration != generation)
            {
                return;
            }
            polling = false;
            if (relayCredential() != credential || chat != currentChat()
                || !java.util.Objects.equals(activePlayer, playerName()))
            {
                return;
            }
            if (code != 200)
            {
                status = "Clan relay unavailable";
                return;
            }
            Batch batch;
            try
            {
                batch = gson.fromJson(body, Batch.class);
            }
            catch (com.google.gson.JsonParseException invalid)
            {
                status = "Clan relay unavailable";
                return;
            }
            if (batch == null || batch.cursor == null || !batch.cursor.matches("[0-9]{17,20}"))
            {
                return;
            }
            cursor = batch.cursor;
            status = "Clan relay connected";
            if (batch.lines == null)
            {
                return;
            }
            rendering = true;
            try
            {
                for (Line line : batch.lines)
                {
                    if (line.id == null || !displayedIds.add(line.id))
                    {
                        continue;
                    }
                    if (displayedIds.size() > 256)
                    {
                        displayedIds.remove(displayedIds.iterator().next());
                    }
                    String sender = line.announcement ? "Announcement" : clean(line.name);
                    String channelName = leadership ? client.getFriendsChatManager().getName() : "CozyGielinor";
                    client.addChatMessage(chat, sender + " (Discord)", clean(line.text), channelName, false);
                }
                client.refreshChat();
            }
            finally
            {
                rendering = false;
            }
        }));
    }

    void onChat(ChatMessage event)
    {
        PortalPairing.Credential credential = relayCredential();
        if (credential == null || rendering || client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }
        ChatMessageType type = event.getType();
        if (leadership && (type != ChatMessageType.FRIENDSCHAT || !inLeadershipChat()))
        {
            return;
        }
        boolean guest = type == ChatMessageType.CLAN_GUEST_CHAT;
        if (!leadership && (!(guest || type == ChatMessageType.CLAN_CHAT)
            || !isCozy(guest ? client.getGuestClanChannel() : client.getClanChannel())))
        {
            return;
        }
        String name = clean(event.getName());
        String text = clean(event.getMessage());
        if (text.isBlank() || name.isBlank() || !name.equalsIgnoreCase(clean(playerName())))
        {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("text", text);
        http.send(request(endpoint, credential).post(BridgeHttp.json(gson.toJson(payload))).build(),
            20, false, (code, body) -> {});
    }

    private ChatMessageType currentChat()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return null;
        }
        if (leadership)
        {
            return inLeadershipChat() ? ChatMessageType.FRIENDSCHAT : null;
        }
        if (isCozy(client.getClanChannel()))
        {
            return ChatMessageType.CLAN_CHAT;
        }
        return isCozy(client.getGuestClanChannel()) ? ChatMessageType.CLAN_GUEST_CHAT : null;
    }

    private static boolean isCozy(ClanChannel channel)
    {
        return channel != null && "CozyGielinor".equalsIgnoreCase(clean(channel.getName()).replace(" ", ""));
    }

    private static String clean(String value)
    {
        return value == null ? "" : Text.removeTags(value).replace('\u00a0', ' ').strip();
    }

    private boolean inLeadershipChat()
    {
        net.runelite.api.FriendsChatManager chat = client.getFriendsChatManager();
        return chat != null && "CozyGielinor".equalsIgnoreCase(clean(chat.getOwner()).replace(" ", ""))
            && playerName() != null;
    }

    private String playerName()
    {
        net.runelite.api.Player player = client.getLocalPlayer();
        return player == null ? null : player.getName();
    }

    private PortalPairing.Credential relayCredential()
    {
        return pairing.isRelayEnabled(leadership) ? pairing.credentialFor(playerName()) : null;
    }

    private Request.Builder request(URI uri, PortalPairing.Credential credential)
    {
        return new Request.Builder().url(uri.toString())
            .header("Authorization", "Bearer " + credential.token);
    }

    private static final class Batch
    {
        String cursor;
        Line[] lines;
    }

    private static final class Line
    {
        String id;
        String name;
        String text;
        boolean announcement;
    }
}
