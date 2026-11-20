package net.cozygielinor.runelite;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Offline behavioral checks: saved pairing, dashboard controls and revoked leadership. */
public final class CompanionVerification
{
    public static void main(String[] args) throws Exception
    {
        AtomicBoolean allowed = new AtomicBoolean(true);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
        {
            String path = chain.request().url().encodedPath();
            String body = path.endsWith("exchange")
                ? "{\"token\":\"abcdefghijklmnopqrstuvwxyz123456\",\"account\":\"Topkerel\",\"expiresAt\":" + (System.currentTimeMillis() + 60000) + "}"
                : path.endsWith("leadership") ? "{\"leadership\":true,\"nextMeeting\":null}" : "{}";
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(path.endsWith("leadership") && !allowed.get() ? 403 : 200).message("Test")
                .body(ResponseBody.create(okhttp3.MediaType.get("application/json"), body)).build();
        }).build();
        try
        {
            BridgeHttp http = new BridgeHttp(client);
            var directory = new VerificationPlugin().directory();
            PortalPairing pairing = new PortalPairing(http, new Gson(), directory);
            pairing.disconnect();
            CountDownLatch exchanged = new CountDownLatch(1);
            pairing.exchange("abcdefghijklmnopqrstuvwxyz123456", "Topkerel", accepted ->
            {
                require(accepted, "Pairing accepted");
                exchanged.countDown();
            });
            require(exchanged.await(5, TimeUnit.SECONDS), "Pairing completed");
            PortalPairing restored = new PortalPairing(http, new Gson(), directory);
            require(restored.credentialFor("Topkerel") != null, "Pairing survives restart");
            require(restored.credentialFor("Another") == null, "Pairing belongs to one character");
            CozyGielinorPanel[] panel = new CozyGielinorPanel[1];
            SwingUtilities.invokeAndWait(() ->
            {
                panel[0] = new CozyGielinorPanel(http, new Gson(), restored);
                panel[0].setActiveAccount("Topkerel", true, "Leadership");
            });
            waitFor(() -> ((JTabbedPane) field(panel[0], "tabs")).getTabCount() == 2);
            SwingUtilities.invokeAndWait(() -> require(!((JPanel) field(panel[0], "pairingControls")).isVisible(), "Hide connected controls"));
            allowed.set(false);
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    var method = CozyGielinorPanel.class.getDeclaredMethod("loadLeadership");
                    method.setAccessible(true);
                    method.invoke(panel[0]);
                }
                catch (Exception error) { throw new RuntimeException(error); }
            });
            waitFor(() -> ((JTabbedPane) field(panel[0], "tabs")).getTabCount() == 1);
            SwingUtilities.invokeAndWait(() ->
            {
                require(((JPanel) field(panel[0], "pairingControls")).isVisible(), "Show disconnected controls");
                require(restored.credentialFor("Topkerel") == null, "Clear revoked local credential");
                panel[0].stop();
            });
            require(new PortalPairing(http, new Gson(), directory).credentialFor("Topkerel") == null, "Revoked credential removed from disk");
            System.out.println("Pairing persistence, character scope, leadership unlock and revocation passed.");
        }
        finally
        {
            client.dispatcher().executorService().shutdownNow();
            client.connectionPool().evictAll();
        }
    }

    @net.runelite.client.plugins.PluginDescriptor(name = "CozyGielinor verification", internalName = "cozygielinor-verification")
    private static final class VerificationPlugin extends net.runelite.client.plugins.Plugin
    {
        net.runelite.client.util.Filepath directory() throws Exception { return getPluginDirectory(); }
    }

    private static Object field(Object target, String name)
    {
        try { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (Exception error) { throw new RuntimeException(error); }
    }

    private static void waitFor(java.util.function.BooleanSupplier condition) throws Exception
    {
        for (int i = 0; i < 100; i++)
        {
            AtomicBoolean ready = new AtomicBoolean();
            SwingUtilities.invokeAndWait(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return;
            Thread.sleep(50);
        }
        throw new AssertionError("UI state did not update");
    }

    private static void require(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
