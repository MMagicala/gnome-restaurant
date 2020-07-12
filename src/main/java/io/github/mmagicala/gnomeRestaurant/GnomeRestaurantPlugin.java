/*
 * Copyright (c) 2020, MMagicala <https://github.com/MMagicala>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.github.mmagicala.gnomeRestaurant;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import io.github.mmagicala.gnomeRestaurant.itemOrder.BakedOrder;
import io.github.mmagicala.gnomeRestaurant.itemOrder.BakedToppedOrder;
import io.github.mmagicala.gnomeRestaurant.itemOrder.HeatedCocktailOrder;
import io.github.mmagicala.gnomeRestaurant.itemOrder.CocktailOrder;
import io.github.mmagicala.gnomeRestaurant.itemOrder.ItemOrder;
import java.security.InvalidParameterException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Named;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.ui.overlay.infobox.Timer;

@Slf4j
@PluginDescriptor(
	name = "Gnome Restaurant"
)
public class GnomeRestaurantPlugin extends Plugin
{
	private static final Pattern DELIVERY_START_PATTERN =
		Pattern.compile("([\\w .]+) wants (?:some|a) ([\\w ]+)");

	private static final String EASY_DELIVERY_DELAY_TEXT = "Fine, your loss. If you want another easy job one come back in five minutes and maybe I'll be able to find you one.";
	private static final String HARD_DELIVERY_DELAY_TEXT = "Fine, your loss. I may have an easier job for you, since you chickened out of that one, If you want another hard one come back in five minutes and maybe I'll be able to find you a something.";

	private static final ImmutableSet<String> easyOrderNPCs = ImmutableSet.of(
		"Burkor", "Brimstall", "Captain Errdo", "Coach", "Dalila", "Damwin", "Eebel", "Ermin", "Femi", "Froono",
		"Guard Vemmeldo", "Gulluck", "His Royal Highness King Narnode", "Meegle", "Perrdur", "Rometti", "Sarble",
		"Trainer Nacklepen", "Wurbel", "Heckel Funch"
	);

	private static final ImmutableSet<String> hardOrderNPCs = ImmutableSet.of(
		"Ambassador Ferrnook", "Ambassador Gimblewap", "Ambassador Spanfipple", "Brambickle", "Captain Bleemadge", "Captain Daerkin",
		"Captain Dalbur", "Captain Klemfoodle", "Captain Ninto", "G.L.O Caranock", "Garkor",
		"Gnormadium Avlafrim", "Hazelmere", "King Bolren", "Lieutenant Schepbur", "Penwie", "Professor Imblewyn", "Professor Manglethorp",
		"Professor Onglewip", "Wingstone"
	);

	@Inject
	private Client client;

	@Inject
	private GnomeRestaurantConfig config;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	private Timer orderTimer, delayTimer;
	private Overlay overlay;

	@Inject
	@Named("developerMode")
	boolean developerMode;
	private boolean isDeliveryForTesting = false;

	private static final Map<String, ItemOrder> itemOrders = Collections.unmodifiableMap(new Hashtable<String, ItemOrder>()
	{
		{
			// Gnomebowls

			put("worm hole",
				new BakedToppedOrder(
					ItemOrderType.GNOMEBOWL,
					ItemID.HALF_MADE_BOWL_9559,
					ItemID.UNFINISHED_BOWL_9560,
					ItemID.WORM_HOLE,
					new ArrayList<CookingItem>()
					{
						{
							add(new CookingItem(ItemID.KING_WORM, 4));
							add(new CookingItem(ItemID.ONION, 2));
							add(new CookingItem(ItemID.GNOME_SPICE, 1));
							add(new CookingItem(ItemID.EQUA_LEAVES, 1, true));
						}
					}
				)
			);
			put("vegetable ball", new BakedToppedOrder(
					ItemOrderType.GNOMEBOWL,
					ItemID.HALF_MADE_BOWL_9561,
					ItemID.UNFINISHED_BOWL_9562,
					ItemID.VEG_BALL,
					new ArrayList<CookingItem>()
					{
						{
							add(new CookingItem(ItemID.POTATO, 2));
							add(new CookingItem(ItemID.ONION, 2));
							add(new CookingItem(ItemID.GNOME_SPICE, 1));
							add(new CookingItem(ItemID.EQUA_LEAVES, 1, true));
						}
					}
				)
			);
			put("tangled toads legs", new BakedOrder(
					ItemOrderType.GNOMEBOWL,
					ItemID.HALF_MADE_BOWL,
					ItemID.TANGLED_TOADS_LEGS,
					new ArrayList<CookingItem>()
					{
						{
							add(new CookingItem(ItemID.TOADS_LEGS, 4));
							add(new CookingItem(ItemID.GNOME_SPICE, 2));
							add(new CookingItem(ItemID.CHEESE, 1));
							add(new CookingItem(ItemID.DWELLBERRIES, 1));
							add(new CookingItem(ItemID.EQUA_LEAVES, 1));
						}
					}
				)
			);
			put("chocolate bomb", new BakedToppedOrder(
					ItemOrderType.GNOMEBOWL,
					ItemID.HALF_MADE_BOWL_9563,
					ItemID.UNFINISHED_BOWL_9564,
					ItemID.CHOCOLATE_BOMB,
					new ArrayList<CookingItem>()
					{
						{
							add(new CookingItem(ItemID.CHOCOLATE_BAR, 4));
							add(new CookingItem(ItemID.EQUA_LEAVES, 2));
							add(new CookingItem(ItemID.CHOCOLATE_DUST, 1, true));
							add(new CookingItem(ItemID.POT_OF_CREAM, 2, true));
						}
					}
				)
			);

			// Battas

			put("fruit batta", new BakedToppedOrder(
					ItemOrderType.BATTA,
					ItemID.HALF_MADE_BATTA,
					ItemID.UNFINISHED_BATTA_9479,
					ItemID.FRUIT_BATTA,
					new ArrayList<CookingItem>()
					{
						{
							add(new CookingItem(ItemID.EQUA_LEAVES, 4));
							add(new CookingItem(ItemID.LIME_CHUNKS, 1));
							add(new CookingItem(ItemID.ORANGE_CHUNKS, 1));
							add(new CookingItem(ItemID.PINEAPPLE_CHUNKS, 1));
							add(new CookingItem(ItemID.GNOME_SPICE, 1, true));
						}
					}
				)
			);
			put("toad batta", new BakedOrder(
					ItemOrderType.BATTA,
					ItemID.HALF_MADE_BATTA_9482,
					ItemID.TOAD_BATTA,
					new ArrayList<CookingItem>()
					{
						{
							add(new CookingItem(ItemID.EQUA_LEAVES, 4));
							add(new CookingItem(ItemID.GNOME_SPICE, 1));
							add(new CookingItem(ItemID.CHEESE, 1));
							add(new CookingItem(ItemID.TOADS_LEGS, 1));
						}
					}
				)
			);
			put("worm batta", new BakedToppedOrder(
					ItemOrderType.BATTA,
					ItemID.HALF_MADE_BATTA_9480,
					ItemID.UNFINISHED_BATTA_9481,
					ItemID.WORM_BATTA,
					new ArrayList<CookingItem>()
					{
						{
							add(new CookingItem(ItemID.KING_WORM, 1));
							add(new CookingItem(ItemID.CHEESE, 1));
							add(new CookingItem(ItemID.GNOME_SPICE, 1));
							add(new CookingItem(ItemID.EQUA_LEAVES, 1, true));
						}
					}
				)
			);
			put("vegetable batta", new BakedToppedOrder(
					ItemOrderType.BATTA,
					ItemID.HALF_MADE_BATTA_9485,
					ItemID.UNFINISHED_BATTA_9486,
					ItemID.VEGETABLE_BATTA,
					new ArrayList<CookingItem>()
					{
						{
							add(new CookingItem(ItemID.TOMATO, 2));
							add(new CookingItem(ItemID.DWELLBERRIES, 1));
							add(new CookingItem(ItemID.ONION, 1));
							add(new CookingItem(ItemID.CHEESE, 1));
							add(new CookingItem(ItemID.CABBAGE, 1));
							add(new CookingItem(ItemID.EQUA_LEAVES, 1, true));
						}
					}
				)
			);
			put("cheese and tomato batta", new BakedToppedOrder(
					ItemOrderType.BATTA,
					ItemID.HALF_MADE_BATTA_9483,
					ItemID.UNFINISHED_BATTA_9484,
					ItemID.CHEESETOM_BATTA,
					new ArrayList<CookingItem>()
					{
						{
							add(new CookingItem(ItemID.CHEESE, 1));
							add(new CookingItem(ItemID.TOMATO, 1));
							add(new CookingItem(ItemID.EQUA_LEAVES, 1, true));
						}
					}
				)
			);

			// Crunchies

			put("choc chip crunchies", new BakedToppedOrder(
					ItemOrderType.CRUNCHIES,
					ItemID.HALF_MADE_CRUNCHY,
					ItemID.UNFINISHED_CRUNCHY_9578,
					ItemID.CHOCCHIP_CRUNCHIES,
					new ArrayList<CookingItem>()
					{
						{
							add(new CookingItem(ItemID.CHOCOLATE_BAR, 2));
							add(new CookingItem(ItemID.GNOME_SPICE, 1));
							add(new CookingItem(ItemID.CHOCOLATE_DUST, 1, true));
						}
					}
				)
			);
			put("spicy crunchies", new BakedToppedOrder(
					ItemOrderType.CRUNCHIES,
					ItemID.HALF_MADE_CRUNCHY_9579,
					ItemID.UNFINISHED_CRUNCHY_9580,
					ItemID.SPICY_CRUNCHIES,
					new ArrayList<CookingItem>()
					{
						{
							add(new CookingItem(ItemID.EQUA_LEAVES, 2));
							add(new CookingItem(ItemID.GNOME_SPICE, 1));
							add(new CookingItem(ItemID.GNOME_SPICE, 1, true));
						}
					}
				)
			);
			put("toad crunchies", new BakedToppedOrder(
					ItemOrderType.CRUNCHIES,
					ItemID.HALF_MADE_CRUNCHY_9581,
					ItemID.UNFINISHED_CRUNCHY_9582,
					ItemID.TOAD_CRUNCHIES,
					new ArrayList<CookingItem>()
					{
						{
							add(new CookingItem(ItemID.TOADS_LEGS, 2));
							add(new CookingItem(ItemID.GNOME_SPICE, 1));
							add(new CookingItem(ItemID.EQUA_LEAVES, 1, true));
						}
					}
				)
			);
			put("worm crunchies", new BakedToppedOrder(
					ItemOrderType.CRUNCHIES,
					ItemID.HALF_MADE_CRUNCHY_9583,
					ItemID.UNFINISHED_CRUNCHY_9584,
					ItemID.WORM_CRUNCHIES,
					new ArrayList<CookingItem>()
					{
						{
							add(new CookingItem(ItemID.KING_WORM, 2));
							add(new CookingItem(ItemID.GNOME_SPICE, 1));
							add(new CookingItem(ItemID.EQUA_LEAVES, 1));
							add(new CookingItem(ItemID.GNOME_SPICE, 1, true));
						}
					}
				)
			);

			// Gnome cocktails

			put("fruit blast", new CocktailOrder(
					ItemID.MIXED_BLAST,
					ItemID.FRUIT_BLAST,
					new ArrayList<CookingItem>()
					{
						{
							add(new CookingItem(ItemID.PINEAPPLE, 1));
							add(new CookingItem(ItemID.LEMON, 1));
							add(new CookingItem(ItemID.ORANGE, 1));
							add(new CookingItem(ItemID.LEMON_SLICES, 1, true));
						}
					}
				)
			);
			put("pineapple punch", new CocktailOrder(
					ItemID.MIXED_PUNCH,
					ItemID.PINEAPPLE_PUNCH,
					new ArrayList<CookingItem>()
					{
						{
							add(new CookingItem(ItemID.PINEAPPLE, 2));
							add(new CookingItem(ItemID.LEMON, 1));
							add(new CookingItem(ItemID.ORANGE, 1));
							add(new CookingItem(ItemID.LIME_CHUNKS, 1, true));
							add(new CookingItem(ItemID.PINEAPPLE_CHUNKS, 1, true));
							add(new CookingItem(ItemID.ORANGE_SLICES, 1, true));
						}
					}
				)
			);
			put("wizard blizzard", new CocktailOrder(
				ItemID.MIXED_BLIZZARD,
				ItemID.WIZARD_BLIZZARD,
				new ArrayList<CookingItem>()
				{
					{
						add(new CookingItem(ItemID.VODKA, 2));
						add(new CookingItem(ItemID.GIN, 1));
						add(new CookingItem(ItemID.LIME, 1));
						add(new CookingItem(ItemID.LEMON, 1));
						add(new CookingItem(ItemID.ORANGE, 1));
						add(new CookingItem(ItemID.PINEAPPLE_CHUNKS, 1, true));
						add(new CookingItem(ItemID.LIME_SLICES, 1, true));
					}
				}));
			put("short green guy", new CocktailOrder(
					ItemID.MIXED_SGG,
					ItemID.SHORT_GREEN_GUY,
					new ArrayList<CookingItem>()
					{
						{
							add(new CookingItem(ItemID.VODKA, 1));
							add(new CookingItem(ItemID.LIME, 3));
							add(new CookingItem(ItemID.LIME_SLICES, 1, true));
							add(new CookingItem(ItemID.EQUA_LEAVES, 1, true));
						}
					}
				)
			);
			put("drunk dragon", new HeatedCocktailOrder(
					HeatTiming.AFTER_ADDING_INGREDS,
					ItemID.MIXED_DRAGON,
					ItemID.MIXED_DRAGON_9575,
					ItemID.MIXED_DRAGON_9576,
					ItemID.DRUNK_DRAGON,
					new ArrayList<CookingItem>()
					{
						{
							add(new CookingItem(ItemID.VODKA, 1));
							add(new CookingItem(ItemID.GIN, 1));
							add(new CookingItem(ItemID.DWELLBERRIES, 1));
							add(new CookingItem(ItemID.PINEAPPLE_CHUNKS, 1, true));
							add(new CookingItem(ItemID.POT_OF_CREAM, 1, true));
						}
					}
				)
			);
			put("choc saturday", new HeatedCocktailOrder(
					HeatTiming.BEFORE_ADDING_INGREDS,
					ItemID.MIXED_SATURDAY,
					ItemID.MIXED_SATURDAY_9572,
					ItemID.MIXED_SATURDAY_9573,
					ItemID.CHOC_SATURDAY,
					new ArrayList<CookingItem>()
					{
						{
							add(new CookingItem(ItemID.WHISKY, 1));
							add(new CookingItem(ItemID.CHOCOLATE_BAR, 1));
							add(new CookingItem(ItemID.EQUA_LEAVES, 1));
							add(new CookingItem(ItemID.BUCKET_OF_MILK, 1));
							add(new CookingItem(ItemID.CHOCOLATE_DUST, 1, true));
							add(new CookingItem(ItemID.POT_OF_CREAM, 1, true));
						}
					}
				)
			);
			put("blurberry special", new CocktailOrder(
					ItemID.MIXED_SPECIAL,
					ItemID.BLURBERRY_SPECIAL,
					new ArrayList<CookingItem>()
					{
						{
							add(new CookingItem(ItemID.VODKA, 1));
							add(new CookingItem(ItemID.BRANDY, 1));
							add(new CookingItem(ItemID.GIN, 1));
							add(new CookingItem(ItemID.LEMON, 2));
							add(new CookingItem(ItemID.ORANGE, 1));
							add(new CookingItem(ItemID.LEMON_CHUNKS, 1, true));
							add(new CookingItem(ItemID.ORANGE_CHUNKS, 1, true));
							add(new CookingItem(ItemID.EQUA_LEAVES, 1, true));
							add(new CookingItem(ItemID.LIME_SLICES, 1, true));
						}
					}
				)
			);
		}
	});

	private ItemOrder itemOrder;
	private String orderRecipient;

	private int currentStageNodeIndex;
	private boolean isTrackingDelivery = false;

	public String getCurrentStageDirections()
	{
		return stageNodes.get(currentStageNodeIndex).getStage().directions;
	}

	private final ArrayList<StageNode> stageNodes = new ArrayList<>();

	private final Hashtable<Integer, OverlayEntry> currentItemsOverlayTable = new Hashtable<>();
	private final Hashtable<Integer, OverlayEntry> futureItemsOverlayTable = new Hashtable<>();

	@Override
	protected void shutDown() throws Exception
	{
		reset();
	}

	private void reset()
	{
		removeOrderTimer();
		removeDelayTimer();
		removeOverlay();
		client.clearHintArrow();

		isDeliveryForTesting = false;
		isTrackingDelivery = false;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getWidget(WidgetInfo.DIALOG_NPC_NAME) != null
			&& client.getWidget(WidgetInfo.DIALOG_NPC_NAME).getText().equals("Gianne jnr.")
		)
		{
			String dialog = client.getWidget(WidgetInfo.DIALOG_NPC_TEXT).getText();

			// Replace line breaks with spaces

			dialog = dialog.replace("<br>", " ");

			Matcher deliveryStartMatcher = DELIVERY_START_PATTERN.matcher(dialog);

			if (deliveryStartMatcher.find())
			{
				if (isTrackingDelivery && isDeliveryForTesting)
				{
					reset();
					chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.GAMEMESSAGE).value("Gnome Restaurant test cancelled. Real order started.").build());
				}

				if (!isTrackingDelivery)
				{
					startTrackingDelivery(deliveryStartMatcher.group(1), deliveryStartMatcher.group(2));
				}
			}

			if (config.showDelayTimer() && delayTimer == null && (dialog.contains(EASY_DELIVERY_DELAY_TEXT) || dialog.contains(HARD_DELIVERY_DELAY_TEXT)))
			{
				delayTimer = new Timer(5, ChronoUnit.MINUTES, itemManager.getImage(ItemID.ALUFT_ALOFT_BOX), this);
				delayTimer.setTooltip("Cannot place an order at this time");
				infoBoxManager.addInfoBox(delayTimer);
			}
		}
	}

	private void startTrackingDelivery(String orderRecipient, String orderName)
	{
		// We cannot test the overlay if it is disabled

		if (!config.showOverlay() && isDeliveryForTesting)
		{
			resetAndFailTest("Overlay must be enabled.");
			return;
		}

		itemOrder = itemOrders.get(orderName);
		this.orderRecipient = orderRecipient;

		if (itemOrder == null)
		{
			throw new InvalidParameterException("No order found with the name " + orderName);
		}

		if (!easyOrderNPCs.contains(orderRecipient) && !hardOrderNPCs.contains(orderRecipient))
		{
			throw new InvalidParameterException("No recipient found with the name " + orderName);
		}

		isTrackingDelivery = true;

		// Delete the delay timer if it is active (we can choose hard orders during a delay)

		removeDelayTimer();

		if (config.showOverlay())
		{
			// Build stage list

			rebuildStageNodeList();
			currentStageNodeIndex = 0;

			// Determine stage

			ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
			assert inventory != null;

			updateStage(inventory);

			if (shouldPluginTestingEnd())
			{
				return;
			}

			// Build overlay tables

			rebuildOverlayTables(inventory);

			overlay = new GnomeRestaurantOverlay(this, currentItemsOverlayTable, futureItemsOverlayTable);
			overlayManager.add(overlay);
		}

		if (config.showOrderTimer())
		{
			int numSecondsLeft = -1;

			if (easyOrderNPCs.contains(orderRecipient))
			{
				numSecondsLeft = 360;
			}
			else if (hardOrderNPCs.contains(orderRecipient))
			{
				numSecondsLeft = 660;
			}

			assert (numSecondsLeft != -1);

			orderTimer = new Timer(numSecondsLeft, ChronoUnit.SECONDS, itemManager.getImage(itemOrder.getItemId()), this);

			String tooltipText = "Deliver " + orderName + " to " + orderRecipient;
			orderTimer.setTooltip(tooltipText);
			infoBoxManager.addInfoBox(orderTimer);
		}

		// Draw hint arrow if we can already identify the NPC

		if (config.showHintArrow())
		{
			markNPCFromCache();
		}
	}

	private void markNPCFromCache()
	{
		NPC[] npcs = client.getCachedNPCs();

		for (NPC npc : npcs)
		{
			if (toggleMarkRecipient(npc, true))
			{
				return;
			}
		}
	}

	private boolean toggleMarkRecipient(NPC npc, boolean mark)
	{
		if (npc == null || npc.getName() == null)
		{
			return false;
		}

		if (npc.getName().equals(orderRecipient))
		{
			if (mark)
			{
				client.setHintArrow(npc);
			}
			else
			{
				client.clearHintArrow();
			}
			return true;
		}
		return false;
	}

	@Subscribe
	public void onNpcSpawned(final NpcSpawned event)
	{
		if (isTrackingDelivery && config.showHintArrow())
		{
			toggleMarkRecipient(event.getNpc(), true);
		}
	}

	@Subscribe
	public void onNpcDespawned(final NpcDespawned event)
	{
		if (isTrackingDelivery && config.showHintArrow())
		{
			toggleMarkRecipient(event.getNpc(), false);
		}
	}

	// Build a linear graph that links stages together, and define the items required to move to the next stage

	private void rebuildStageNodeList()
	{
		stageNodes.clear();

		// Ingredients

		ArrayList<CookingItem> initialIngredients = itemOrder.getIngredients(false);
		ArrayList<CookingItem> laterIngredients = itemOrder.getIngredients(true);

		if (itemOrder.getItemOrderType() == ItemOrderType.COCKTAIL)
		{
			// Starting items

			ArrayList<CookingItem> startingItems = new ArrayList<>(initialIngredients);
			startingItems.add(new CookingItem(ItemID.COCKTAIL_SHAKER, 1));
			stageNodes.add(new StageNode(MinigameStage.COMBINE_INGREDIENTS, startingItems));

			ArrayList<CookingItem> requiredItemsToPour = new ArrayList<CookingItem>();


			requiredItemsToPour.add(new CookingItem(ItemID.COCKTAIL_GLASS, 1));


			if (itemOrder instanceof HeatedCocktailOrder)
			{
				stageNodes.add(new StageNode(MinigameStage.POUR, requiredItemsToPour, ((HeatedCocktailOrder) itemOrder).getShakerMixId()));

				if (((HeatedCocktailOrder) itemOrder).getHeatTiming() == HeatTiming.BEFORE_ADDING_INGREDS)
				{
					stageNodes.add(new StageNode(MinigameStage.HEAT_AGAIN, ((HeatedCocktailOrder) itemOrder).getPouredMixId()));
					stageNodes.add(new StageNode(MinigameStage.TOP_WITH_INGREDIENTS, laterIngredients, ((HeatedCocktailOrder) itemOrder).getSecondPouredMixId()));
				}
				else
				{
					stageNodes.add(new StageNode(MinigameStage.TOP_WITH_INGREDIENTS, laterIngredients, ((HeatedCocktailOrder) itemOrder).getPouredMixId()));
					stageNodes.add(new StageNode(MinigameStage.HEAT_AGAIN, ((HeatedCocktailOrder) itemOrder).getSecondPouredMixId()));
				}
			}
			else
			{
				requiredItemsToPour.addAll(laterIngredients);
				stageNodes.add(new StageNode(MinigameStage.POUR, requiredItemsToPour, ((CocktailOrder) itemOrder).getShakerMixId()));
			}
		}
		else
		{
			ArrayList<CookingItem> startingItems = new ArrayList<>();
			startingItems.add(new CookingItem(ItemID.GIANNE_DOUGH, 1));
			startingItems.add(new CookingItem(itemOrder.getItemOrderType().getToolId(), 1));

			stageNodes.add(new StageNode(MinigameStage.CREATE_MOULD, startingItems));
			stageNodes.add(new StageNode(MinigameStage.BAKE_MOULD, itemOrder.getItemOrderType().getMouldId()));
			stageNodes.add(new StageNode(MinigameStage.COMBINE_INGREDIENTS, initialIngredients, itemOrder.getItemOrderType().getHalfBakedId()));

			stageNodes.add(new StageNode(MinigameStage.HEAT_AGAIN, ((BakedOrder) itemOrder).getHalfMadeId()));

			if (itemOrder instanceof BakedToppedOrder)
			{
				stageNodes.add(new StageNode(MinigameStage.TOP_WITH_INGREDIENTS, laterIngredients, ((BakedToppedOrder) itemOrder).getUnfinishedId()));
			}
		}
		stageNodes.add(new StageNode(MinigameStage.DELIVER, new ArrayList<CookingItem>()
		{
			{
				add(new CookingItem(ItemID.ALUFT_ALOFT_BOX, 1));
			}
		}, itemOrder.getItemId()));
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		// Ignore varbit changes while we are testing

		if (isTrackingDelivery && !isDeliveryForTesting && client.getVarbitValue(2478) == 0)
		{
			reset();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (overlay != null)
		{
			if (event.getContainerId() != InventoryID.INVENTORY.getId())
			{
				return;
			}

			boolean stageChanged = updateStage(event.getItemContainer());

			if (shouldPluginTestingEnd())
			{
				return;
			}

			// Update or rebuild overlay tables

			if (stageChanged)
			{
				rebuildOverlayTables(event.getItemContainer());
			}
			else
			{
				updateOverlayTables(event.getItemContainer());
			}
		}
	}

	private boolean updateStage(ItemContainer inventory)
	{
		int traversedStageNodeIndex = stageNodes.size() - 1;

		while (traversedStageNodeIndex > currentStageNodeIndex)
		{
			if (inventory.contains(stageNodes.get(traversedStageNodeIndex).getProducedItemId()))
			{
				currentStageNodeIndex = traversedStageNodeIndex;
				return true;
			}
			traversedStageNodeIndex--;
		}
		return false;
	}

	// Overlay table methods

	private void updateOverlayTables(ItemContainer inventory)
	{
		ArrayList<Hashtable<Integer, OverlayEntry>> overlayTables = new ArrayList<Hashtable<Integer, OverlayEntry>>()
		{
			{
				add(currentItemsOverlayTable);
				add(futureItemsOverlayTable);
			}
		};

		for (Hashtable<Integer, OverlayEntry> overlayTable : overlayTables)
		{
			for (Map.Entry<Integer, OverlayEntry> entry : overlayTable.entrySet())
			{
				int realInventoryCount = inventory.count(entry.getKey());
				if (entry.getValue().getInventoryCount() != realInventoryCount)
				{
					entry.getValue().setInventoryCount(realInventoryCount);
				}
			}
		}
	}

	private void addItemsToOverlayTable(Hashtable<Integer, OverlayEntry> overlayTable, ItemContainer inventory, ArrayList<CookingItem> itemStacks)
	{
		for (CookingItem itemStack : itemStacks)
		{
			String itemName = itemManager.getItemComposition(itemStack.getItemId()).getName();
			overlayTable.put(itemStack.getItemId(), new OverlayEntry(itemName, inventory.count(itemStack.getItemId()), itemStack.getCount()));
		}
	}

	private void rebuildOverlayTables(ItemContainer inventory)
	{
		futureItemsOverlayTable.clear();
		currentItemsOverlayTable.clear();

		for (int i = stageNodes.size() - 1; i >= currentStageNodeIndex; i--)
		{
			Hashtable<Integer, OverlayEntry> overlayTable;
			ArrayList<CookingItem> requiredItems = new ArrayList<>(stageNodes.get(i).getOtherRequiredItems());
			if (i == currentStageNodeIndex)
			{
				overlayTable = currentItemsOverlayTable;
				if (i > 0)
				{
					requiredItems.add(new CookingItem(stageNodes.get(i).getProducedItemId(), 1));
				}
			}
			else
			{
				overlayTable = futureItemsOverlayTable;
			}
			addItemsToOverlayTable(overlayTable, inventory, requiredItems);
		}
	}

	@Provides
	GnomeRestaurantConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GnomeRestaurantConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!config.showDelayTimer())
		{
			removeDelayTimer();
		}

		if (!config.showOrderTimer())
		{
			removeOrderTimer();
		}

		if (!config.showOverlay())
		{
			removeOverlay();

			if (isDeliveryForTesting)
			{
				resetAndFailTest("Overlay must be enabled.");
			}
		}

		if (!config.showHintArrow())
		{
			client.clearHintArrow();
		}
		else if (isTrackingDelivery)
		{
			// Re-enable hint arrow

			markNPCFromCache();
		}
	}

	private void removeOrderTimer()
	{
		infoBoxManager.removeInfoBox(orderTimer);
		orderTimer = null;
	}

	private void removeDelayTimer()
	{
		infoBoxManager.removeInfoBox(delayTimer);
		delayTimer = null;
	}

	private void removeOverlay()
	{
		overlayManager.remove(overlay);
		overlay = null;
	}

	// Plugin testing

	@Subscribe
	public void onCommandExecuted(CommandExecuted commandExecuted)
	{
		if (!developerMode || !commandExecuted.getCommand().equals("gnome") || commandExecuted.getArguments().length < 1)
		{
			return;
		}

		String orderName = commandExecuted.getArguments()[0].replace("_", " ");
		String startMessage = "Gnome Restaurant test started... Arguments: " + orderName;
		String recipientName = "Gnormadium Avlafrim";

		chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.GAMEMESSAGE).value(startMessage).build());

		reset();

		try
		{
			isDeliveryForTesting = true;
			startTrackingDelivery(recipientName, orderName);
		}
		catch (InvalidParameterException e)
		{
			resetAndFailTest(e.getMessage());
		}
	}

	private void resetAndFailTest(String message)
	{
		reset();
		String errorMessage = "Gnome Restaurant test failed. " + message;
		chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.GAMEMESSAGE).value(errorMessage).build());
	}

	private boolean shouldPluginTestingEnd()
	{
		if (isDeliveryForTesting && stageNodes.get(currentStageNodeIndex).getStage() == MinigameStage.DELIVER)
		{
			reset();
			chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.GAMEMESSAGE).value("Reached DELIVER stage. Gnome Restaurant testing ended.").build());
			return true;
		}
		return false;
	}
}
