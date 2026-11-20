package net.cozygielinor.runelite;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import okhttp3.Request;
import java.util.Objects;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

final class CozyGielinorPanel extends PluginPanel
{
    private static final Color BACKGROUND = ColorScheme.DARK_GRAY_COLOR;
    private static final Color SURFACE = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color BORDER = ColorScheme.DARK_GRAY_COLOR;
    private static final Color TEXT = new Color(0xE8E8E8);
    private static final Color MUTED = new Color(0xB5B5B5);
    private static final Color ACCENT = new Color(0xE4C17A);

    private static final String PORTAL = "https://home.cozygielinor.net";
    private static final String PORTAL_NOTICE = "Requests expose your IP address to the CozyGielinor portal, "
        + "which is operated separately from RuneLite.";

    private final JLabel activeAccount = label("OSRS account: signed out", 11, Font.PLAIN, MUTED);
    private final JLabel clanRank = label("Log in to view your clan rank", 11, Font.BOLD, ACCENT);
    private final JLabel relayStatus = label("Relay not configured", 10, Font.PLAIN, MUTED);
    private final JPanel clanProgress = stack(SURFACE);
    private final JButton joinDiscord = new JButton("<html>Join our Discord<br>and become part of our clan ↗</html>");
    private final JPanel content = new SectionList();
    private final javax.swing.JViewport viewport = new javax.swing.JViewport();

    private final BridgeHttp http;
    private final Gson gson;
    private final PortalPairing pairing;
    private final Timer refresh = new Timer(15000, event -> { loadPortal(); loadLeadership(); });
    private final JTabbedPane tabs = new JTabbedPane();
    private final JPanel leadership = stack(BACKGROUND);
    private final JPanel pairingControls = stack(BACKGROUND);
    private final JLabel pairingStatus = label("", 10, Font.PLAIN, MUTED);
    private long leadershipVersion;
    private String character;
    private long requestVersion;

    CozyGielinorPanel(BridgeHttp http, Gson gson, PortalPairing pairing)
    {
        super(false);
        this.http = http;
        this.gson = gson;
        this.pairing = pairing;
        setLayout(new BorderLayout());
        setBackground(BACKGROUND);
        setBorder(new EmptyBorder(5, 5, 5, 5));

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel logo = new JLabel(new ImageIcon(ImageUtil.resizeImage(
            ImageUtil.loadImageResource(CozyGielinorPanel.class, "cozy-gielinor-logo.png"), 30, 30)));
        header.add(logo, BorderLayout.WEST);
        JPanel brand = stack(BACKGROUND);
        brand.add(label("CozyGielinor", 16, Font.BOLD, ACCENT));
        brand.add(label("Clan portal", 10, Font.PLAIN, MUTED));
        header.add(brand, BorderLayout.CENTER);
        JPanel pinned = stack(BACKGROUND);
        pinned.add(header);
        pinned.add(Box.createVerticalStrut(5));
        pinned.add(activeAccount);
        pinned.add(relayStatus);
        pinned.add(Box.createVerticalStrut(4));
        pinned.add(clanRank);
        pinned.add(Box.createVerticalStrut(5));
        pinned.add(clanProgress);
        clanProgress.setVisible(false);
        joinDiscord.setAlignmentX(Component.LEFT_ALIGNMENT);
        joinDiscord.setForeground(ACCENT);
        joinDiscord.setBackground(SURFACE);
        joinDiscord.setBorder(new EmptyBorder(5, 7, 5, 7));
        joinDiscord.addActionListener(event -> LinkBrowser.browse("https://cozygielinor.net/discord"));
        joinDiscord.setVisible(false);
        pinned.add(joinDiscord);
        JTextField code = new JTextField();
        code.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        code.setToolTipText("Paste the one-time code from the clan portal");
        JButton generate = new JButton("Get connection code ↗");
        generate.addActionListener(event -> LinkBrowser.browse(PortalPairing.ORIGIN + "/runelite-pairing"));
        JButton pair = new JButton("Connect account");
        pair.addActionListener(event ->
        {
            if (character == null || !code.getText().strip().matches("[A-Za-z0-9_-]{32}"))
            {
                pairingStatus.setText("Log in and paste a valid connection code.");
                return;
            }
            String pairingCharacter = character;
            pair.setEnabled(false);
            pairing.exchange(code.getText().strip(), pairingCharacter, accepted -> SwingUtilities.invokeLater(() ->
            {
                pair.setEnabled(true);
                code.setText("");
                pairingStatus.setText(accepted ? "Connected." : "Code expired or belongs to another character.");
                updatePairingControls();
                loadPortal();
                loadLeadership();
            }));
        });
        pairingControls.add(label("Connect for private portal access", 11, Font.BOLD, TEXT));
        pairingControls.add(generate);
        pairingControls.add(code);
        pairingControls.add(pair);
        pinned.add(pairingControls);
        pinned.add(pairingStatus);
        updatePairingControls();
        pinned.setBorder(new EmptyBorder(0, 0, 5, 0));
        add(pinned, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane();
        scroll.setViewport(viewport);
        viewport.setView(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        tabs.addTab("Dashboard", scroll);
        add(tabs, BorderLayout.CENTER);
        renderPortal(null, "Log in to load your portal data.");
    }

    private void updatePairingControls()
    {
        pairingControls.setVisible(character != null && pairing.credentialFor(character) == null);
    }

    private void lockLeadership()
    {
        int index = tabs.indexOfComponent(leadership);
        if (index >= 0) tabs.removeTabAt(index);
        leadership.removeAll();
        pairing.approveLeadership(false);
    }

    private void loadLeadership()
    {
        long version = ++leadershipVersion;
        PortalPairing.Credential credential = pairing.credentialFor(character);
        updatePairingControls();
        if (credential == null) { lockLeadership(); return; }
        Request request = new Request.Builder().url(PORTAL + "/api/runelite/client/leadership")
            .header("Authorization", "Bearer " + credential.token).build();
        http.send(request, 15, true, (status, body) -> SwingUtilities.invokeLater(() ->
        {
            if (version != leadershipVersion || pairing.credentialFor(character) != credential) return;
            if (status != 200)
            {
                lockLeadership();
                if (status == 401 || status == 403)
                {
                    pairing.disconnect();
                    updatePairingControls();
                    pairingStatus.setText("Connection ended. Verify your access in the portal.");
                }
                return;
            }
            try
            {
                JsonObject data = gson.fromJson(body, JsonObject.class);
                if (data == null || !data.has("leadership") || !data.get("leadership").getAsBoolean())
                { lockLeadership(); return; }
                pairing.approveLeadership(true);
                leadership.removeAll();
                JsonObject meeting = object(data.get("nextMeeting"));
                if (meeting.size() == 0) card(leadership, "No upcoming leadership meeting");
                else
                {
                    String date = java.time.Instant.parse(value(meeting, "startsAt", ""))
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, HH:mm z"));
                    card(leadership, "Next leadership meeting", value(meeting, "title", "Meeting"), date,
                        "Focus: " + value(meeting, "focus", "team"));
                }
                portalLink(leadership, "Open meeting agenda", "/calendar.html");
                if (tabs.indexOfComponent(leadership) < 0) tabs.addTab("Leadership", leadership);
                leadership.revalidate();
                leadership.repaint();
            }
            catch (RuntimeException invalid) { lockLeadership(); }
        }));
    }

    void setRelayStatus(String status)
    {
        if (!status.equals(relayStatus.getText()))
        {
            relayStatus.setText(status);
            relayStatus.setForeground("Clan relay connected".equals(status) ? new Color(0xA0C78A) : MUTED);
        }
    }

    void stop()
    {
        refresh.stop();
        requestVersion++;
        leadershipVersion++;
        lockLeadership();
    }

    void setActiveAccount(String name, boolean inClan, String rank)
    {
        String text = name == null ? "OSRS account: signed out" : "OSRS: " + name;
        if (!text.equals(activeAccount.getText()))
        {
            activeAccount.setText(text);
        }
        clanRank.setVisible(name == null || inClan);
        clanRank.setText(name == null ? "Log in to view your clan rank"
            : rank == null ? "CozyGielinor member" : "In-game title: " + rank);
        clanProgress.setVisible(name != null);
        joinDiscord.setVisible(name != null && !inClan);
        if (!Objects.equals(character, name))
        {
            character = name;
            lockLeadership();
            updatePairingControls();
            loadLeadership();
            if (name != null)
            {
                renderPortal(null, "Loading portal data…");
            }
            refresh.stop();
            loadPortal();
            if (name != null)
            {
                refresh.start();
            }
        }
    }

    private void loadPortal()
    {
        long version = ++requestVersion;
        if (character == null)
        {
            renderPortal(null, "Log in to load your portal data.");
            return;
        }
        PortalPairing.Credential credential = pairing.credentialFor(character);
        if (credential == null)
        {
            renderPortal(null, "Portal not connected");
            return;
        }
        Request request = new Request.Builder().url(PortalPairing.ORIGIN + "/api/runelite/client/snapshot")
            .header("Authorization", "Bearer " + credential.token).build();
        http.send(request, 30, true, (code, body) ->
            SwingUtilities.invokeLater(() ->
            {
                if (version != requestVersion)
                {
                    return;
                }
                if (code != 200)
                {
                    String message = code == 401 ? "Pair your account again."
                        : code == 403 ? "Check your clan membership and linked character."
                        : "Portal data unavailable.";
                    if ((code == 401 || code == 403) && pairing.credentialFor(character) == credential)
                    { pairing.disconnect(); updatePairingControls(); lockLeadership(); }
                    renderPortal(null, message);
                    return;
                }
                JsonObject snapshot;
                try
                {
                    snapshot = gson.fromJson(body, JsonObject.class);
                }
                catch (RuntimeException invalid)
                {
                    renderPortal(null, "Portal response unavailable.");
                    return;
                }
                renderPortal(snapshot, null);
            }));
    }

    private void renderPortal(JsonObject snapshot, String message)
    {
        content.removeAll();
        clanProgress.removeAll();
        if (snapshot == null)
        {
            card(content, message);
            portalLink(content, "Open clan portal", "/home.html");

        }
        else
        {
            renderRank(object(snapshot.get("rank")));
            String member = value(snapshot, "member", "");
            if (!member.isBlank())
            {
                clanProgress.add(label("Linked member: " + member, 11, Font.PLAIN, MUTED), 0);
            }
        }
        revalidate();
        repaint();
    }

    private void renderRank(JsonObject rank)
    {
        if (rank.size() == 0)
        {
            card(clanProgress, "Clan XP unavailable");
            return;
        }
        long xp = Long.parseLong(value(rank, "totalXp", "0"));
        JsonObject current = object(rank.get("currentRoleReward"));
        JsonObject next = object(rank.get("nextRoleReward"));
        String title = value(current, "name", "Clan member");
        card(clanProgress, title, "Level " + value(rank, "level", "1") + " · " + String.format("%,d Clan XP", xp));
        if (next.size() > 0)
        {
            long start = Long.parseLong(value(current, "xpRequired", "0"));
            long end = Long.parseLong(value(next, "xpRequired", "0"));
            JProgressBar progress = new JProgressBar(0, 100);
            progress.setValue((int) Math.max(0, Math.min(100, 100.0 * (xp - start) / Math.max(1, end - start))));
            progress.setForeground(ACCENT);
            progress.setAlignmentX(Component.LEFT_ALIGNMENT);
            progress.setPreferredSize(new Dimension(100, 6));
            progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
            clanProgress.add(progress);
            clanProgress.add(label(String.format("%,d XP to %s", Math.max(0, end - xp), value(next, "name", "next rank")),
                11, Font.PLAIN, MUTED));
        }
    }

    private static JsonObject object(JsonElement item)
    {
        return item != null && item.isJsonObject() ? item.getAsJsonObject() : new JsonObject();
    }

    private static String value(JsonObject data, String key, String fallback)
    {
        JsonElement value = data.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private void portalLink(JPanel target, String title, String path)
    {
        JButton link = new JButton(title + " ↗");
        link.setAlignmentX(Component.LEFT_ALIGNMENT);
        link.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        link.setFont(link.getFont().deriveFont(Font.BOLD, 11f));
        link.setForeground(TEXT);
        link.setBackground(SURFACE);
        link.setOpaque(true);
        link.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER), new EmptyBorder(6, 5, 6, 5)));
        link.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, link.getPreferredSize().height));
        link.setToolTipText(PORTAL + path);
        link.addActionListener(event -> LinkBrowser.browse(PORTAL + path));
        target.add(link);

    }

    private JPanel card(JPanel target, String title, String... lines)
    {
        JPanel card = stack(SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER), new EmptyBorder(5, 7, 5, 7)));
        card.add(label(title, 11, Font.BOLD, TEXT));
        for (String line : lines)
        {
            card.add(Box.createVerticalStrut(3));
            card.add(label(line, 12, Font.PLAIN, TEXT));
        }
        target.add(card);
        target.add(Box.createVerticalStrut(2));
        return card;
    }

    private static JPanel stack(Color background)
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(background);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private static JLabel label(String text, int size, int style, Color color)
    {
        JLabel label = new JLabel();
        label.putClientProperty("html.disable", true);
        label.setText(text);
        label.setFont(label.getFont().deriveFont(style, (float) size));
        label.setForeground(color);
        label.setToolTipText(text);
        return label;
    }
    private static final class SectionList extends JPanel implements Scrollable
    {
        SectionList()
        {
            setBackground(BACKGROUND);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(16, visible.height - 16); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
