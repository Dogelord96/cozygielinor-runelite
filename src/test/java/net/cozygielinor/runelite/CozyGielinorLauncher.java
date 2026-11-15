package net.cozygielinor.runelite;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CozyGielinorLauncher
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(CozyGielinorPlugin.class);
        RuneLite.main(args);
    }
}
