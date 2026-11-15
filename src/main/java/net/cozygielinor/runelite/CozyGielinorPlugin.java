package net.cozygielinor.runelite;

import com.google.gson.Gson;
import java.util.Objects;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(name = "CozyGielinor", description = "CozyGielinor clan panel")
public class CozyGielinorPlugin extends Plugin
{
    @Inject
    private ClientToolbar toolbar;

    @Inject
    private Client client;

    private ClanRelay clanRelay;

    @Inject
    private net.runelite.client.callback.ClientThread clientThread;
    @Inject
    private Gson gson;

    @Inject
    private BridgeHttp http;

    private ClanRelay leadershipRelay;
    private ProgressSync progressSync;


    private NavigationButton navigation;
    private volatile CozyGielinorPanel panel;
    private String displayedName;
    private boolean displayedMembership;
    private String displayedRank;
    private String displayedRelayStatus;

    @Override
    protected void startUp()
    {
        PortalPairing pairing = new PortalPairing(http, gson);
        progressSync = new ProgressSync(http, pairing);
        clanRelay = new ClanRelay(client, clientThread, gson, http, pairing, false);
        leadershipRelay = new ClanRelay(client, clientThread, gson, http, pairing, true);
        displayedRelayStatus = null;
        panel = new CozyGielinorPanel(http, gson, pairing);
        navigation = NavigationButton.builder()
            .tooltip("CozyGielinor")
            .icon(ImageUtil.resizeImage(ImageUtil.loadImageResource(CozyGielinorPlugin.class, "cozy-gielinor-logo.png"), 16, 16))
            .panel(panel)
            .build();
        toolbar.addNavigation(navigation);
    }

    @Override
    protected void shutDown()
    {
        clanRelay.reset();
        leadershipRelay.reset();
        CozyGielinorPanel closing = panel;
        panel = null;
        SwingUtilities.invokeLater(closing::stop);
        toolbar.removeNavigation(navigation);
        navigation = null;
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        progressSync.tick(client);
        clanRelay.tick();
        leadershipRelay.tick();
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            Player player = client.getLocalPlayer();
            String name = player == null ? null : player.getName();
            ClanSettings clan = client.getClanSettings();
            ClanMember member = name != null && clan != null
                && "CozyGielinor".equalsIgnoreCase(clan.getName().replace(" ", ""))
                ? clan.findMember(name) : null;
            boolean inClan = member != null && !ClanRank.GUEST.equals(member.getRank());
            ClanTitle rank = inClan ? clan.titleForRank(member.getRank()) : null;
            updateAccount(name, inClan, rank == null ? null : rank.getName());
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        clanRelay.onChat(event);
        leadershipRelay.onChat(event);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        switch (event.getGameState())
        {
            case LOGIN_SCREEN:
            case LOGIN_SCREEN_AUTHENTICATOR:
            case LOGGING_IN:
                progressSync.reset();
                clanRelay.reset();
                leadershipRelay.reset();
                updateAccount(null, false, null);
                break;
            default:
                break;
        }
    }

    private void updateAccount(String name, boolean inClan, String rank)
    {
        CozyGielinorPanel currentPanel = panel;
        String relayStatus = clanRelay.status();
        if (currentPanel == null || (Objects.equals(displayedName, name)
            && displayedMembership == inClan && Objects.equals(displayedRank, rank)
            && Objects.equals(displayedRelayStatus, relayStatus)))
        {
            return;
        }

        displayedName = name;
        displayedMembership = inClan;
        displayedRank = rank;
        displayedRelayStatus = relayStatus;
        SwingUtilities.invokeLater(() ->
        {
            if (panel == currentPanel)
            {
                currentPanel.setActiveAccount(name, inClan, rank);
                currentPanel.setRelayStatus(relayStatus);
            }
        });
    }
}
