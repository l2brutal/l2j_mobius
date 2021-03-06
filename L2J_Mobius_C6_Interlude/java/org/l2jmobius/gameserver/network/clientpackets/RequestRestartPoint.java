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
package org.l2jmobius.gameserver.network.clientpackets;

import java.util.logging.Logger;

import org.l2jmobius.Config;
import org.l2jmobius.commons.concurrent.ThreadPool;
import org.l2jmobius.gameserver.datatables.csv.MapRegionTable;
import org.l2jmobius.gameserver.instancemanager.CastleManager;
import org.l2jmobius.gameserver.instancemanager.ClanHallManager;
import org.l2jmobius.gameserver.instancemanager.FortManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.SiegeClan;
import org.l2jmobius.gameserver.model.actor.instance.PlayerInstance;
import org.l2jmobius.gameserver.model.entity.ClanHall;
import org.l2jmobius.gameserver.model.entity.event.CTF;
import org.l2jmobius.gameserver.model.entity.event.DM;
import org.l2jmobius.gameserver.model.entity.event.TvT;
import org.l2jmobius.gameserver.model.entity.siege.Castle;
import org.l2jmobius.gameserver.model.entity.siege.Fort;
import org.l2jmobius.gameserver.network.serverpackets.Revive;
import org.l2jmobius.gameserver.util.IllegalPlayerAction;
import org.l2jmobius.gameserver.util.Util;

/**
 * @author programmos
 */
public class RequestRestartPoint extends GameClientPacket
{
	private static Logger LOGGER = Logger.getLogger(RequestRestartPoint.class.getName());
	
	protected int _requestedPointType;
	protected boolean _continuation;
	
	@Override
	protected void readImpl()
	{
		_requestedPointType = readD();
	}
	
	class DeathTask implements Runnable
	{
		PlayerInstance player;
		
		DeathTask(PlayerInstance _player)
		{
			player = _player;
		}
		
		@Override
		public void run()
		{
			if ((player._inEventTvT && TvT.is_started()) || (player._inEventDM && DM.is_started()) || (player._inEventCTF && CTF.is_started()))
			{
				player.sendMessage("You can't restart in Event!");
				return;
			}
			try
			{
				Location loc = null;
				Castle castle = null;
				Fort fort = null;
				
				if (player.isInJail())
				{
					_requestedPointType = 27;
				}
				else if (player.isFestivalParticipant())
				{
					_requestedPointType = 4;
				}
				
				if (player.isPhoenixBlessed())
				{
					player.stopPhoenixBlessing(null);
				}
				
				switch (_requestedPointType)
				{
					case 1: // to clanhall
					{
						if (player.getClan() != null)
						{
							if (player.getClan().getHasHideout() == 0)
							{
								// cheater
								player.sendMessage("You may not use this respawn point!");
								Util.handleIllegalPlayerAction(player, "Player " + player.getName() + " used respawn cheat.", IllegalPlayerAction.PUNISH_KICK);
								return;
							}
							loc = MapRegionTable.getInstance().getTeleToLocation(player, MapRegionTable.TeleportWhereType.ClanHall);
							if ((ClanHallManager.getInstance().getClanHallByOwner(player.getClan()) != null) && (ClanHallManager.getInstance().getClanHallByOwner(player.getClan()).getFunction(ClanHall.FUNC_RESTORE_EXP) != null))
							{
								player.restoreExp(ClanHallManager.getInstance().getClanHallByOwner(player.getClan()).getFunction(ClanHall.FUNC_RESTORE_EXP).getLvl());
							}
							break;
						}
						loc = MapRegionTable.getInstance().getTeleToLocation(player, MapRegionTable.TeleportWhereType.Town);
						break;
					}
					case 2: // to castle
					{
						Boolean isInDefense = false;
						castle = CastleManager.getInstance().getCastle(player);
						fort = FortManager.getInstance().getFort(player);
						MapRegionTable.TeleportWhereType teleportWhere = MapRegionTable.TeleportWhereType.Town;
						if ((castle != null) && castle.getSiege().getIsInProgress())
						{
							// siege in progress
							if (castle.getSiege().checkIsDefender(player.getClan()))
							{
								isInDefense = true;
							}
						}
						if ((fort != null) && fort.getSiege().getIsInProgress())
						{
							// siege in progress
							if (fort.getSiege().checkIsDefender(player.getClan()))
							{
								isInDefense = true;
							}
						}
						if ((player.getClan().getHasCastle() == 0) && (player.getClan().getHasFort() == 0) && !isInDefense)
						{
							// cheater
							player.sendMessage("You may not use this respawn point!");
							Util.handleIllegalPlayerAction(player, "Player " + player.getName() + " used respawn cheat.", IllegalPlayerAction.PUNISH_KICK);
							return;
						}
						if (CastleManager.getInstance().getCastleByOwner(player.getClan()) != null)
						{
							teleportWhere = MapRegionTable.TeleportWhereType.Castle;
						}
						else if (FortManager.getInstance().getFortByOwner(player.getClan()) != null)
						{
							teleportWhere = MapRegionTable.TeleportWhereType.Fortress;
						}
						loc = MapRegionTable.getInstance().getTeleToLocation(player, teleportWhere);
						break;
					}
					case 3: // to siege HQ
					{
						SiegeClan siegeClan = null;
						castle = CastleManager.getInstance().getCastle(player);
						fort = FortManager.getInstance().getFort(player);
						if ((castle != null) && castle.getSiege().getIsInProgress())
						{
							siegeClan = castle.getSiege().getAttackerClan(player.getClan());
						}
						else if ((fort != null) && fort.getSiege().getIsInProgress())
						{
							siegeClan = fort.getSiege().getAttackerClan(player.getClan());
						}
						if ((siegeClan == null) || (siegeClan.getFlag().size() == 0))
						{
							// cheater
							player.sendMessage("You may not use this respawn point!");
							Util.handleIllegalPlayerAction(player, "Player " + player.getName() + " used respawn cheat.", IllegalPlayerAction.PUNISH_KICK);
							return;
						}
						loc = MapRegionTable.getInstance().getTeleToLocation(player, MapRegionTable.TeleportWhereType.SiegeFlag);
						break;
					}
					case 4: // Fixed or Player is a festival participant
					{
						if (!player.isGM() && !player.isFestivalParticipant())
						{
							// cheater
							player.sendMessage("You may not use this respawn point!");
							Util.handleIllegalPlayerAction(player, "Player " + player.getName() + " used respawn cheat.", IllegalPlayerAction.PUNISH_KICK);
							return;
						}
						loc = new Location(player.getX(), player.getY(), player.getZ()); // spawn them where they died
						break;
					}
					case 27: // to jail
					{
						if (!player.isInJail())
						{
							return;
						}
						loc = new Location(-114356, -249645, -2984);
						break;
					}
					default:
					{
						if ((player.getKarma() > 0) && Config.ALT_KARMA_TELEPORT_TO_FLORAN)
						{
							loc = new Location(17836, 170178, -3507);// Floran Village
							break;
						}
						loc = MapRegionTable.getInstance().getTeleToLocation(player, MapRegionTable.TeleportWhereType.Town);
						break;
					}
				}
				
				// Stand up and teleport, proof dvp video.
				player.setIsIn7sDungeon(false);
				player.setIsPendingRevive(true);
				player.teleToLocation(loc, true);
			}
			catch (Throwable e)
			{
				e.printStackTrace();
				// LOGGER.warning( "", e);
			}
		}
	}
	
	@Override
	protected void runImpl()
	{
		final PlayerInstance player = getClient().getPlayer();
		
		if (player == null)
		{
			return;
		}
		
		if (player.isFakeDeath())
		{
			player.stopFakeDeath(null);
			player.broadcastPacket(new Revive(player));
			return;
		}
		else if (!player.isAlikeDead())
		{
			LOGGER.warning("Living player [" + player.getName() + "] called RestartPointPacket! Ban this player!");
			return;
		}
		
		final Castle castle = CastleManager.getInstance().getCastle(player.getX(), player.getY(), player.getZ());
		if ((castle != null) && castle.getSiege().getIsInProgress())
		{
			if ((player.getClan() != null) && castle.getSiege().checkIsAttacker(player.getClan()))
			{
				// Schedule respawn delay for attacker
				ThreadPool.schedule(new DeathTask(player), castle.getSiege().getAttackerRespawnDelay());
				player.sendMessage("You will be re-spawned in " + (castle.getSiege().getAttackerRespawnDelay() / 1000) + " seconds");
				return;
			}
		}
		// run immediately (no need to schedule)
		new DeathTask(player).run();
	}
}