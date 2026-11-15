package net.cozygielinor.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import okhttp3.Request;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Text;

final class ClanRelay
{
    private final URI endpoint;
    private final boolean leadership;
    private final Client client;
    private final ClientThread clientThread;
    private final String key;
    private final Set<String> displayedIds = new LinkedHashSet<>();
    private final Gson gson;
    private final BridgeHttp http;
    private ChatMessageType activeChat;
    private String activePlayer;
    private long nextPoll;
    private long nextRoster;
    private long generation;
    private String cursor;
    private boolean polling;
    private boolean enabled;
    private boolean rendering;
    private volatile String status = "Relay not configured";

    String status()
    {
        return status;
    }

    @Inject
    ClanRelay(Client client, ClientThread clientThread, Gson gson, BridgeHttp http)
    {
        this(client, clientThread, gson, http, false);
    }

    ClanRelay(Client client, ClientThread clientThread, Gson gson, BridgeHttp http, boolean leadership)
    {
        this.leadership = leadership;
        endpoint = URI.create("http://127.0.0.1:48731/" + (leadership ? "leadership" : "clan"));
        key = System.getenv(leadership ? "COZY_LEADERSHIP_RELAY_KEY" : "COZY_RELAY_KEY");
        this.client = client;
        this.clientThread = clientThread;
        this.gson = gson;
        this.http = http;
    }

    void start()
    {
        enabled = key != null && !key.isBlank();
        status = enabled ? "Join CozyGielinor Clan Chat" : "Relay not configured";
    }

    void stop()
    {
        enabled = false;
        reset();
    }

    void reset()
    {
        activeChat = null;
        status = enabled ? "Join CozyGielinor Clan Chat" : "Relay not configured";
        cursor = null;
        nextPoll = 0;
        polling = false;
        generation++;
    }

    void tick()
    {
        if (!enabled)
        {
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
        if ((chat == ChatMessageType.CLAN_CHAT || chat == ChatMessageType.FRIENDSCHAT) && now >= nextRoster)
        {
            publishRoster();
            nextRoster = now + 60000;
        }
        if (chat == null || polling || now < nextPoll)
        {
            return;
        }
        polling = true;
        nextPoll = now + 5000;
        long requestGeneration = generation;
        URI uri = cursor == null ? endpoint : URI.create(endpoint + "?after=" + cursor);
        http.send(request(uri).get().build(), 20, true, (code, body) -> clientThread.invokeLater(() ->
        {
            if (requestGeneration != generation)
            {
                return;
            }
            polling = false;
            if (chat != currentChat() || !java.util.Objects.equals(activePlayer, playerName()))
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
        if (!enabled || rendering || client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }
        ChatMessageType type = event.getType();
        if (leadership && (type != ChatMessageType.FRIENDSCHAT || !inLeadershipChat()))
        {
            return;
        }
        boolean guest = type == ChatMessageType.CLAN_GUEST_CHAT || type == ChatMessageType.CLAN_GUEST_MESSAGE;
        boolean broadcast = type == ChatMessageType.CLAN_MESSAGE || type == ChatMessageType.CLAN_GUEST_MESSAGE;
        if (!leadership && (!(guest || broadcast || type == ChatMessageType.CLAN_CHAT)
            || !isCozy(guest ? client.getGuestClanChannel() : client.getClanChannel())))
        {
            return;
        }
        String name = clean(event.getName());
        String text = clean(event.getMessage());
        if (text.isBlank() || name.startsWith("[Discord]") || name.contains("(Discord)") || (!broadcast && name.isBlank()))
        {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("name", name);
        payload.addProperty("text", text);
        payload.addProperty("broadcast", broadcast);
        if (!broadcast && !leadership)
        {
            ClanSettings settings = guest ? client.getGuestClanSettings() : client.getClanSettings();
            ClanMember member = settings == null ? null : settings.findMember(name);
            ClanTitle rank = member == null ? null : settings.titleForRank(member.getRank());
            if (rank != null)
            {
                payload.addProperty("rank", rank.getName());
            }
            String rawName = event.getName();
            if (rawName != null)
            {
                java.util.Map<Integer, String> accountTypes = java.util.Map.of(
                    2, "ironman", 3, "ultimate_ironman", 10, "hardcore_ironman",
                    41, "group_ironman", 42, "hardcore_group_ironman", 43, "unranked_group_ironman");
                java.util.regex.Matcher icons = java.util.regex.Pattern.compile("<img=(\\d{1,3})>").matcher(rawName);
                while (icons.find())
                {
                    String accountType = accountTypes.get(Integer.parseInt(icons.group(1)));
                    if (accountType != null)
                    {
                        payload.addProperty("accountType", accountType);
                        break;
                    }
                }
            }
        }
        http.send(request(endpoint).post(BridgeHttp.json(gson.toJson(payload))).build(),
            20, false, (code, body) -> {});
    }

    private void publishRoster()
    {
        if (leadership)
        {
            com.google.gson.JsonArray roster = new com.google.gson.JsonArray();
            for (net.runelite.api.FriendsChatMember member : client.getFriendsChatManager().getMembers())
            {
                String name = clean(member.getName());
                if (name.matches("[a-zA-Z0-9 _-]{1,12}"))
                {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("name", name);
                    entry.addProperty("guest", false);
                    roster.add(entry);
                }
            }
            sendRoster(roster, "leadership-roster");
            return;
        }
        ClanChannel channel = client.getClanChannel();
        if (!isCozy(channel) || channel.getMembers().isEmpty())
        {
            return;
        }
        com.google.gson.JsonArray roster = new com.google.gson.JsonArray();
        for (net.runelite.api.clan.ClanChannelMember member : channel.getMembers())
        {
            String name = clean(member.getName());
            if (!name.matches("[a-zA-Z0-9 _-]{1,12}"))
            {
                continue;
            }
            JsonObject row = new JsonObject();
            row.addProperty("name", name);
            row.addProperty("guest", net.runelite.api.clan.ClanRank.GUEST.equals(member.getRank()));
            roster.add(row);
        }
        sendRoster(roster, "roster");
    }

    private void sendRoster(com.google.gson.JsonArray roster, String route)
    {
        if (roster.size() > 0)
        {
            http.send(request(URI.create("http://127.0.0.1:48731/" + route))
                .post(BridgeHttp.json(gson.toJson(roster))).build(), 25, false, (code, body) -> {});
        }
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

    private Request.Builder request(URI uri)
    {
        Request.Builder builder = new Request.Builder().url(uri.toString())
            .header("Authorization", "Bearer " + key);
        if (leadership)
        {
            builder.header("X-Cozy-Player", playerName());
        }
        return builder;
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
