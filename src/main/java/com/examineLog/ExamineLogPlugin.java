package com.examineLog;

import com.google.inject.Provides;
import javax.inject.Inject;
import java.io.*;
import java.util.*;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import static net.runelite.client.RuneLite.RUNELITE_DIR;

@Slf4j
@PluginDescriptor(
	name = "Examine Text Collection Log"
)
public class ExamineLogPlugin extends Plugin
{
	public static final File EXAMINE_TEXT_DIR = new File(RUNELITE_DIR, "examineText");
	private final Deque<PendingExamine> pending = new ArrayDeque<>();
	private final Map<String, String> examineTextMap = new HashMap<>();

	private int examineTextCount = 0;

	@Inject
	private Client client;

	@Inject
	private ExamineLogConfig config;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Override
	protected void startUp() throws Exception
	{
		// Load file and count
		loadHashMapFromFile();
		countMap();
	}

	@Override
	protected void shutDown() throws Exception
	{
		saveHashMapToFile();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		pending.clear();
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked menuOptionClicked)
	{
		if (!menuOptionClicked.getMenuOption().equals("Examine"))
		{
			return;
		}
		// Create queue object for each examine text
		// Copied from ExaminePlugin
		final ChatMessageType type;
		int id;
		switch(menuOptionClicked.getMenuAction())
		{
			case EXAMINE_ITEM_GROUND:
			case EXAMINE_OBJECT:
			case EXAMINE_NPC:
			case CC_OP_LOW_PRIORITY:
			{
				id = menuOptionClicked.getId();
				break;
			}
			default:
			{
				return;
			}
		}

		PendingExamine pendingExamine = new PendingExamine();
		pendingExamine.setId(id);
		pending.push(pendingExamine);

	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (pending.isEmpty())
		{
			return;
		}

		PendingExamine pendingExamine = pending.poll();

		// Check the message text against locally stored json file

		String eventMessage = event.getMessage();
		// We don't want x amount of coins as a new message plugin
		// Also don't have repeat messages
		if(eventMessage.isEmpty() || eventMessage.contains("x Coins") || eventMessage.contains("<colHIGHLIGHT>") || examineTextMap.containsKey(eventMessage))
		{
			return;
		}

		examineTextMap.put(eventMessage, new Date().toString());
		examineTextCount++;

		final ChatMessageBuilder message = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append("New Examine Text Unlocked! (" + examineTextCount + " Total Unique Texts)");

		saveHashMapToFile();

		chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.ITEM_EXAMINE)
				.runeLiteFormattedMessage(message.build())
				.build());

	}

	private void countMap()
	{
		examineTextCount = examineTextMap.size();
	}

	private void loadHashMapFromFile()
	{
		BufferedReader br = null;
		try{
			File file = new File(EXAMINE_TEXT_DIR.getPath() + "/examine_text_collection_log.txt");

			br = new BufferedReader(new FileReader(file));

			String line = null;
			while((line = br.readLine()) != null)
			{
				String[] parts = line.split(":");

				// first part is name, second is number
				String name = parts[0].trim();
				String date = parts[1].trim();

				// put name, number in HashMap if they are
				// not empty
				if (!name.equals("") && !date.equals(""))
					examineTextMap.put(name, date);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private void saveHashMapToFile()
	{
		File file = new File(EXAMINE_TEXT_DIR.getPath() + "/examine_text_collection_log.txt");

		BufferedWriter bf = null;
		try {
			bf = new BufferedWriter(new FileWriter(file));

			//iterate through map and save
			for(Map.Entry<String, String> entry: examineTextMap.entrySet())
					{
						bf.write(entry.getKey() + ":" + entry.getValue());
						bf.newLine();
					}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				bf.close();
			}
			catch (Exception e) {
			}
		}
	}


	@Provides
	ExamineLogConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ExamineLogConfig.class);
	}
}

@Data
class PendingExamine
{
	private int id;
}

