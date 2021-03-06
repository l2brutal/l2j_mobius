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
package org.l2jmobius.gameserver.datatables.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.l2jmobius.Config;
import org.l2jmobius.commons.concurrent.ThreadPool;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.idfactory.IdFactory;
import org.l2jmobius.gameserver.instancemanager.FortManager;
import org.l2jmobius.gameserver.instancemanager.FortSiegeManager;
import org.l2jmobius.gameserver.instancemanager.SiegeManager;
import org.l2jmobius.gameserver.model.actor.instance.PlayerInstance;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.clan.ClanMember;
import org.l2jmobius.gameserver.model.entity.siege.Fort;
import org.l2jmobius.gameserver.model.entity.siege.FortSiege;
import org.l2jmobius.gameserver.model.entity.siege.Siege;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.PledgeShowInfoUpdate;
import org.l2jmobius.gameserver.network.serverpackets.PledgeShowMemberListAll;
import org.l2jmobius.gameserver.network.serverpackets.PledgeShowMemberListUpdate;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;
import org.l2jmobius.gameserver.network.serverpackets.UserInfo;
import org.l2jmobius.gameserver.util.Util;

/**
 * @version $Revision: 1.11.2.5.2.5 $ $Date: 2005/03/27 15:29:18 $
 */
public class ClanTable
{
	private static Logger LOGGER = Logger.getLogger(ClanTable.class.getName());
	
	private final Map<Integer, Clan> _clans = new HashMap<>();
	
	private ClanTable()
	{
		load();
	}
	
	public void load()
	{
		_clans.clear();
		Clan clan;
		try (Connection con = DatabaseFactory.getConnection())
		{
			final PreparedStatement statement = con.prepareStatement("SELECT * FROM clan_data");
			final ResultSet result = statement.executeQuery();
			
			// Count the clans
			int clanCount = 0;
			
			while (result.next())
			{
				_clans.put(Integer.parseInt(result.getString("clan_id")), new Clan(Integer.parseInt(result.getString("clan_id"))));
				clan = getClan(Integer.parseInt(result.getString("clan_id")));
				if (clan.getDissolvingExpiryTime() != 0)
				{
					if (clan.getDissolvingExpiryTime() < System.currentTimeMillis())
					{
						destroyClan(clan.getClanId());
					}
					else
					{
						scheduleRemoveClan(clan.getClanId());
					}
				}
				
				clan.setNoticeEnabled(result.getBoolean("enabled"));
				clan.setNotice(result.getString("notice"));
				clan.setIntroduction(result.getString("introduction"), false);
				
				clanCount++;
			}
			result.close();
			statement.close();
			
			LOGGER.info("Restored " + clanCount + " clans from the database.");
		}
		catch (Exception e)
		{
			LOGGER.warning("Data error on ClanTable " + e);
		}
		
		restoreClanWars();
	}
	
	public Clan[] getClans()
	{
		return _clans.values().toArray(new Clan[_clans.size()]);
	}
	
	public int getTopRate(int clan_id)
	{
		Clan clan = getClan(clan_id);
		if (clan.getLevel() < 3)
		{
			return 0;
		}
		int i = 1;
		for (Clan clans : getClans())
		{
			if (clan != clans)
			{
				if (clan.getLevel() < clans.getLevel())
				{
					i++;
				}
				else if (clan.getLevel() == clans.getLevel())
				{
					if (clan.getReputationScore() <= clans.getReputationScore())
					{
						i++;
					}
				}
			}
		}
		return i;
	}
	
	/**
	 * @param clanId
	 * @return
	 */
	public Clan getClan(int clanId)
	{
		return _clans.get(clanId);
	}
	
	public Clan getClanByName(String clanName)
	{
		for (Clan clan : getClans())
		{
			if (clan.getName().equalsIgnoreCase(clanName))
			{
				return clan;
			}
		}
		
		return null;
	}
	
	/**
	 * Creates a new clan and store clan info to database
	 * @param player
	 * @param clanName
	 * @return NULL if clan with same name already exists
	 */
	public Clan createClan(PlayerInstance player, String clanName)
	{
		if (null == player)
		{
			return null;
		}
		
		LOGGER.info("{" + player.getObjectId() + "}({" + player.getName() + "}) requested a clan creation.");
		
		if (10 > player.getLevel())
		{
			player.sendPacket(SystemMessageId.YOU_DO_NOT_MEET_CRITERIA_IN_ORDER_TO_CREATE_A_CLAN);
			return null;
		}
		
		if (0 != player.getClanId())
		{
			player.sendPacket(SystemMessageId.FAILED_TO_CREATE_CLAN);
			return null;
		}
		
		if (System.currentTimeMillis() < player.getClanCreateExpiryTime())
		{
			player.sendPacket(SystemMessageId.YOU_MUST_WAIT_XX_DAYS_BEFORE_CREATING_A_NEW_CLAN);
			return null;
		}
		
		if (!isValidCalnName(player, clanName))
		{
			return null;
		}
		
		final Clan clan = new Clan(IdFactory.getInstance().getNextId(), clanName);
		final ClanMember leader = new ClanMember(clan, player.getName(), player.getLevel(), player.getClassId().getId(), player.getObjectId(), player.getPledgeType(), player.getPowerGrade(), player.getTitle());
		
		clan.setLeader(leader);
		leader.setPlayerInstance(player);
		clan.store();
		player.setClan(clan);
		player.setPledgeClass(leader.calculatePledgeClass(player));
		player.setClanPrivileges(Clan.CP_ALL);
		
		LOGGER.info("New clan created: {" + clan.getClanId() + "} {" + clan.getName() + "}");
		
		_clans.put(clan.getClanId(), clan);
		
		// should be update packet only
		player.sendPacket(new PledgeShowInfoUpdate(clan));
		player.sendPacket(new PledgeShowMemberListAll(clan, player));
		player.sendPacket(new UserInfo(player));
		player.sendPacket(new PledgeShowMemberListUpdate(player));
		player.sendPacket(SystemMessageId.CLAN_CREATED);
		
		return clan;
	}
	
	public boolean isValidCalnName(PlayerInstance player, String clanName)
	{
		if (!Util.isAlphaNumeric(clanName) || (clanName.length() < 2))
		{
			player.sendPacket(SystemMessageId.CLAN_NAME_INCORRECT);
			return false;
		}
		
		if (clanName.length() > 16)
		{
			player.sendPacket(SystemMessageId.CLAN_NAME_TOO_LONG);
			return false;
		}
		
		if (getClanByName(clanName) != null)
		{
			final SystemMessage sm = new SystemMessage(SystemMessageId.S1_ALREADY_EXISTS);
			sm.addString(clanName);
			player.sendPacket(sm);
			return false;
		}
		
		Pattern pattern;
		try
		{
			pattern = Pattern.compile(Config.CLAN_NAME_TEMPLATE);
		}
		catch (PatternSyntaxException e) // case of illegal pattern
		{
			LOGGER.warning("ERROR: Clan name pattern of config is wrong!");
			pattern = Pattern.compile(".*");
		}
		
		final Matcher match = pattern.matcher(clanName);
		
		if (!match.matches())
		{
			player.sendPacket(SystemMessageId.CLAN_NAME_INCORRECT);
			return false;
		}
		
		return true;
	}
	
	public synchronized void destroyClan(int clanId)
	{
		final Clan clan = getClan(clanId);
		
		if (clan == null)
		{
			return;
		}
		
		PlayerInstance leader = null;
		if ((clan.getLeader() != null) && ((leader = clan.getLeader().getPlayerInstance()) != null))
		{
			if (Config.CLAN_LEADER_COLOR_ENABLED && (clan.getLevel() >= Config.CLAN_LEADER_COLOR_CLAN_LEVEL))
			{
				if (Config.CLAN_LEADER_COLORED == 1)
				{
					leader.getAppearance().setNameColor(0x000000);
				}
				else
				{
					leader.getAppearance().setTitleColor(0xFFFF77);
				}
			}
			
			// remove clan leader skills
			leader.addClanLeaderSkills(false);
		}
		
		clan.broadcastToOnlineMembers(new SystemMessage(SystemMessageId.CLAN_HAS_DISPERSED));
		
		final int castleId = clan.getHasCastle();
		
		if (castleId == 0)
		{
			for (Siege siege : SiegeManager.getInstance().getSieges())
			{
				siege.removeSiegeClan(clanId);
			}
		}
		
		final int fortId = clan.getHasFort();
		
		if (fortId == 0)
		{
			for (FortSiege siege : FortSiegeManager.getInstance().getSieges())
			{
				siege.removeSiegeClan(clanId);
			}
		}
		
		final ClanMember leaderMember = clan.getLeader();
		
		if (leaderMember == null)
		{
			clan.getWarehouse().destroyAllItems("ClanRemove", null, null);
		}
		else
		{
			clan.getWarehouse().destroyAllItems("ClanRemove", clan.getLeader().getPlayerInstance(), null);
		}
		
		for (ClanMember member : clan.getMembers())
		{
			clan.removeClanMember(member.getName(), 0);
		}
		
		final int leaderId = clan.getLeaderId();
		final int clanLvl = clan.getLevel();
		
		_clans.remove(clanId);
		IdFactory.getInstance().releaseId(clanId);
		
		try (Connection con = DatabaseFactory.getConnection())
		{
			PreparedStatement statement = con.prepareStatement("DELETE FROM clan_data WHERE clan_id=?");
			statement.setInt(1, clanId);
			statement.execute();
			
			statement = con.prepareStatement("DELETE FROM clan_privs WHERE clan_id=?");
			statement.setInt(1, clanId);
			statement.execute();
			
			statement = con.prepareStatement("DELETE FROM clan_skills WHERE clan_id=?");
			statement.setInt(1, clanId);
			statement.execute();
			
			statement = con.prepareStatement("DELETE FROM clan_subpledges WHERE clan_id=?");
			statement.setInt(1, clanId);
			statement.execute();
			
			statement = con.prepareStatement("DELETE FROM clan_wars WHERE clan1=? OR clan2=?");
			statement.setInt(1, clanId);
			statement.setInt(2, clanId);
			statement.execute();
			
			if ((leader == null) && (leaderId != 0) && Config.CLAN_LEADER_COLOR_ENABLED && (clanLvl >= Config.CLAN_LEADER_COLOR_CLAN_LEVEL))
			{
				String query;
				if (Config.CLAN_LEADER_COLORED == 1)
				{
					query = "UPDATE characters SET name_color = '000000' WHERE obj_Id = ?";
				}
				else
				{
					query = "UPDATE characters SET title_color = 'FFFF77' WHERE obj_Id = ?";
				}
				statement = con.prepareStatement(query);
				statement.setInt(1, leaderId);
				statement.execute();
			}
			
			if (castleId != 0)
			{
				statement = con.prepareStatement("UPDATE castle SET taxPercent = 0 WHERE id = ?");
				statement.setInt(1, castleId);
				statement.execute();
			}
			
			if (fortId != 0)
			{
				final Fort fort = FortManager.getInstance().getFortById(fortId);
				if (fort != null)
				{
					final Clan owner = fort.getOwnerClan();
					if (clan == owner)
					{
						fort.removeOwner(clan);
					}
				}
			}
			
			LOGGER.info("Clan removed in db: " + clanId);
			
			statement.close();
		}
		catch (Exception e)
		{
			LOGGER.warning("Error while removing clan in db " + e);
		}
	}
	
	public void scheduleRemoveClan(int clanId)
	{
		ThreadPool.schedule(() ->
		{
			if (getClan(clanId) == null)
			{
				return;
			}
			
			if (getClan(clanId).getDissolvingExpiryTime() != 0)
			{
				destroyClan(clanId);
			}
		}, getClan(clanId).getDissolvingExpiryTime() - System.currentTimeMillis());
	}
	
	public boolean isAllyExists(String allyName)
	{
		for (Clan clan : getClans())
		{
			if ((clan.getAllyName() != null) && clan.getAllyName().equalsIgnoreCase(allyName))
			{
				return true;
			}
		}
		return false;
	}
	
	public void storeClanWars(int clanId1, int clanId2)
	{
		final Clan clan1 = getInstance().getClan(clanId1);
		final Clan clan2 = getInstance().getClan(clanId2);
		
		clan1.setEnemyClan(clan2);
		clan2.setAttackerClan(clan1);
		clan1.broadcastClanStatus();
		clan2.broadcastClanStatus();
		
		try (Connection con = DatabaseFactory.getConnection())
		{
			final PreparedStatement statement = con.prepareStatement("REPLACE INTO clan_wars (clan1, clan2, wantspeace1, wantspeace2) VALUES(?,?,?,?)");
			statement.setInt(1, clanId1);
			statement.setInt(2, clanId2);
			statement.setInt(3, 0);
			statement.setInt(4, 0);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			LOGGER.warning("Could not store clans wars data " + e);
		}
		
		SystemMessage msg = new SystemMessage(SystemMessageId.CLAN_WAR_DECLARED_AGAINST_S1_IF_KILLED_LOSE_LOW_EXP);
		msg.addString(clan2.getName());
		clan1.broadcastToOnlineMembers(msg);
		
		msg = new SystemMessage(SystemMessageId.CLAN_S1_DECLARED_WAR);
		msg.addString(clan1.getName());
		clan2.broadcastToOnlineMembers(msg);
	}
	
	public void deleteClanWars(int clanId1, int clanId2)
	{
		final Clan clan1 = getInstance().getClan(clanId1);
		final Clan clan2 = getInstance().getClan(clanId2);
		
		clan1.deleteEnemyClan(clan2);
		clan2.deleteAttackerClan(clan1);
		clan1.broadcastClanStatus();
		clan2.broadcastClanStatus();
		
		try (Connection con = DatabaseFactory.getConnection())
		{
			final PreparedStatement statement = con.prepareStatement("DELETE FROM clan_wars WHERE clan1=? AND clan2=?");
			statement.setInt(1, clanId1);
			statement.setInt(2, clanId2);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			LOGGER.warning("Could not restore clans wars data " + e);
		}
		
		SystemMessage msg = new SystemMessage(SystemMessageId.WAR_AGAINST_S1_HAS_STOPPED);
		msg.addString(clan2.getName());
		clan1.broadcastToOnlineMembers(msg);
		
		msg = new SystemMessage(SystemMessageId.CLAN_S1_HAS_DECIDED_TO_STOP);
		msg.addString(clan1.getName());
		clan2.broadcastToOnlineMembers(msg);
	}
	
	public void checkSurrender(Clan clan1, Clan clan2)
	{
		int count = 0;
		
		for (ClanMember player : clan1.getMembers())
		{
			if ((player != null) && (player.getPlayerInstance().getWantsPeace() == 1))
			{
				count++;
			}
		}
		
		if (count == (clan1.getMembers().length - 1))
		{
			clan1.deleteEnemyClan(clan2);
			clan2.deleteEnemyClan(clan1);
			deleteClanWars(clan1.getClanId(), clan2.getClanId());
		}
	}
	
	private void restoreClanWars()
	{
		try (Connection con = DatabaseFactory.getConnection())
		{
			final PreparedStatement statement = con.prepareStatement("SELECT clan1, clan2, wantspeace1, wantspeace2 FROM clan_wars");
			final ResultSet rset = statement.executeQuery();
			
			while (rset.next())
			{
				getClan(rset.getInt("clan1")).setEnemyClan(rset.getInt("clan2"));
				getClan(rset.getInt("clan2")).setAttackerClan(rset.getInt("clan1"));
			}
			rset.close();
			statement.close();
		}
		catch (Exception e)
		{
			LOGGER.warning("Could not restore clan wars data:");
			e.printStackTrace();
		}
	}
	
	public static ClanTable getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final ClanTable INSTANCE = new ClanTable();
	}
}
