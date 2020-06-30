package io.github.mmagicala.gnomeRestaurant;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GnomeRestaurantPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GnomeRestaurantPlugin.class);
		RuneLite.main(args);
	}
}