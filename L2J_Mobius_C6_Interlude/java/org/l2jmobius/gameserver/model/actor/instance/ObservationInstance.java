/*
 * This file is part of the L2J Mobius project.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.l2jmobius.gameserver.model.actor.instance;

import java.util.StringTokenizer;

import org.l2jmobius.gameserver.instancemanager.SiegeManager;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.ActionFailed;
import org.l2jmobius.gameserver.network.serverpackets.ItemList;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;

/**
 * The Class L2ObservationInstance.
 * @author NightMarez
 * @version $Revision: 1.3.2.2.2.5 $ $Date: 2005/03/27 15:29:32 $
 */
public class ObservationInstance extends FolkInstance
{
	/**
	 * Instantiates a new l2 observation instance.
	 * @param objectId the object id
	 * @param template the template
	 */
	public ObservationInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(PlayerInstance player, String command)
	{
		if (player.isInOlympiadMode())
		{
			player.sendMessage("You already participated in Olympiad!");
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (player._inEventTvT || player._inEventDM || player._inEventCTF)
		{
			player.sendMessage("You already participated in Event!");
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (player.isInCombat() || (player.getPvpFlag() > 0))
		{
			player.sendMessage("You are in combat now!");
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (command.startsWith("observeSiege"))
		{
			StringTokenizer st = new StringTokenizer(command);
			st.nextToken(); // Command
			
			int x = Integer.parseInt(st.nextToken()); // X location
			int y = Integer.parseInt(st.nextToken()); // Y location
			int z = Integer.parseInt(st.nextToken()); // Z location
			
			if (SiegeManager.getInstance().getSiege(x, y, z) != null)
			{
				doObserve(player, command);
			}
			else
			{
				player.sendPacket(new SystemMessage(SystemMessageId.ONLY_VIEW_SIEGE));
			}
		}
		else if (command.startsWith("observe"))
		{
			doObserve(player, command);
		}
		else
		{
			super.onBypassFeedback(player, command);
		}
	}
	
	@Override
	public String getHtmlPath(int npcId, int val)
	{
		String pom = "";
		if (val == 0)
		{
			pom = "" + npcId;
		}
		else
		{
			pom = npcId + "-" + val;
		}
		
		return "data/html/observation/" + pom + ".htm";
	}
	
	/**
	 * Do observe.
	 * @param player the player
	 * @param val the val
	 */
	private void doObserve(PlayerInstance player, String val)
	{
		StringTokenizer st = new StringTokenizer(val);
		st.nextToken(); // Command
		int x = Integer.parseInt(st.nextToken());
		int y = Integer.parseInt(st.nextToken());
		int z = Integer.parseInt(st.nextToken());
		int cost = Integer.parseInt(st.nextToken());
		
		if (player.reduceAdena("Broadcast", cost, this, true))
		{
			// enter mode
			player.enterObserverMode(x, y, z);
			final ItemList il = new ItemList(player, false);
			player.sendPacket(il);
		}
		
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
}
