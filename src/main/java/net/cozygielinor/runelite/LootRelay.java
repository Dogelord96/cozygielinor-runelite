package net.cozygielinor.runelite;

import com.google.gson.Gson;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import okhttp3.Request;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class LootRelay
{
    private static final Logger LOG = LoggerFactory.getLogger(LootRelay.class);
    private final Client client;
    private final ItemManager items;
    private final DrawManager drawManager;
    private final Gson gson;
    private final BridgeHttp http;
    private final String key = System.getenv("COZY_RELAY_KEY");
    private volatile boolean enabled;
    private volatile long generation;

    @Inject
    LootRelay(Client client, ItemManager items, DrawManager drawManager, Gson gson, BridgeHttp http)
    {
        this.client = client;
        this.items = items;
        this.drawManager = drawManager;
        this.gson = gson;
        this.http = http;
    }

    void start()
    {
        enabled = key != null && !key.isBlank() && System.getenv("COZY_LOOT_MIN_GP") != null;
    }

    void stop()
    {
        generation++;
        enabled = false;
    }

    void onLoot(LootReceived event)
    {
        Player player = client.getLocalPlayer();
        if (!enabled || player == null || player.getName() == null)
        {
            return;
        }

        long minimum = Long.parseLong(System.getenv("COZY_LOOT_MIN_GP"));
        long total = 0;
        List<String> names = new ArrayList<>();
        for (ItemStack stack : event.getItems())
        {
            int id = items.canonicalize(stack.getId());
            long value = Math.max(0, items.getItemPrice(id)) * stack.getQuantity();
            total += value;
            if (names.size() < 12)
            {
                names.add(stack.getQuantity() + " × " + items.getItemComposition(id).getName());
            }
        }
        if (total < minimum)
        {
            return;
        }

        String playerName = Text.removeTags(player.getName());
        String source = event.getName();
        long value = total;
        long requestGeneration = generation;
        drawManager.requestNextFrameListener(image ->
        {
            if (requestGeneration != generation || image == null)
            {
                return;
            }
            int width = image.getWidth(null);
            int height = image.getHeight(null);
            if (width <= 0 || height <= 0)
            {
                return;
            }
            double scale = Math.min(1, 1600d / Math.max(width, height));
            BufferedImage frame = new BufferedImage(Math.max(1, (int) (width * scale)),
                Math.max(1, (int) (height * scale)), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = frame.createGraphics();
            try
            {
                graphics.drawImage(image, 0, 0, frame.getWidth(), frame.getHeight(), null);
            }
            finally
            {
                graphics.dispose();
            }
            CompletableFuture.runAsync(() -> upload(frame, playerName, source, value, names, requestGeneration));
        });
    }

    private void upload(BufferedImage frame, String player, String source, long value,
        List<String> names, long requestGeneration)
    {
        try
        {
            ByteArrayOutputStream image = new ByteArrayOutputStream();
            if (!ImageIO.write(frame, "jpg", image) || requestGeneration != generation)
            {
                return;
            }
            com.google.gson.JsonObject payload = new com.google.gson.JsonObject();
            payload.addProperty("player", player);
            payload.addProperty("source", source);
            payload.addProperty("value", value);
            payload.addProperty("items", String.join(", ", names));
            payload.addProperty("screenshot", Base64.getEncoder().encodeToString(image.toByteArray()));
            Request request = new Request.Builder().url("http://127.0.0.1:48731/loot")
                .header("Authorization", "Bearer " + key).post(BridgeHttp.json(gson.toJson(payload))).build();
            http.send(request, 20, false, (code, unused) ->
            {
                if (code != 200)
                {
                    LOG.warn("CozyGielinor loot relay failed");
                }
            });
        }
        catch (IOException error)
        {
            LOG.warn("CozyGielinor loot screenshot encoding failed", error);
        }
    }
}
