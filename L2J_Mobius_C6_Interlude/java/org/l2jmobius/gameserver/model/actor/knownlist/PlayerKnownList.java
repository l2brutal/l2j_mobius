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
package org.l2jmobius.gameserver.model.actor.knownlist;

import org.l2jmobius.gameserver.ai.CreatureAI;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Summon;
import org.l2jmobius.gameserver.model.actor.instance.BoatInstance;
import org.l2jmobius.gameserver.model.actor.instance.DoorInstance;
import org.l2jmobius.gameserver.model.actor.instance.FenceInstance;
import org.l2jmobius.gameserver.model.actor.instance.NpcInstance;
import org.l2jmobius.gameserver.model.actor.instance.PetInstance;
import org.l2jmobius.gameserver.model.actor.instance.PlayerInstance;
import org.l2jmobius.gameserver.model.actor.instance.StaticObjectInstance;
import org.l2jmobius.gameserver.model.items.instance.ItemInstance;
import org.l2jmobius.gameserver.network.serverpackets.CharInfo;
import org.l2jmobius.gameserver.network.serverpackets.DeleteObject;
import org.l2jmobius.gameserver.network.serverpackets.DoorInfo;
import org.l2jmobius.gameserver.network.serverpackets.DoorStatusUpdate;
import org.l2jmobius.gameserver.network.serverpackets.DropItem;
import org.l2jmobius.gameserver.network.serverpackets.GetOnVehicle;
import org.l2jmobius.gameserver.network.serverpackets.NpcInfo;
import org.l2jmobius.gameserver.network.serverpackets.PetInfo;
import org.l2jmobius.gameserver.network.serverpackets.PetItemList;
import org.l2jmobius.gameserver.network.serverpackets.PrivateStoreMsgBuy;
import org.l2jmobius.gameserver.network.serverpackets.PrivateStoreMsgSell;
import org.l2jmobius.gameserver.network.serverpackets.RecipeShopMsg;
import org.l2jmobius.gameserver.network.serverpackets.RelationChanged;
import org.l2jmobius.gameserver.network.serverpackets.SpawnItem;
import org.l2jmobius.gameserver.network.serverpackets.SpawnItemPoly;
import org.l2jmobius.gameserver.network.serverpackets.StaticObject;
import org.l2jmobius.gameserver.network.serverpackets.VehicleInfo;

public class PlayerKnownList extends PlayableKnownList
{
	public PlayerKnownList(PlayerInstance player)
	{
		super(player);
	}
	
	/**
	 * Add a visible WorldObject to PlayerInstance _knownObjects and _knownPlayer (if necessary) and send Server-Client Packets needed to inform the PlayerInstance of its state and actions in progress.<BR>
	 * <BR>
	 * <B><U> object is a ItemInstance </U> :</B><BR>
	 * <BR>
	 * <li>Send Server-Client Packet DropItem/SpawnItem to the PlayerInstance</li><BR>
	 * <BR>
	 * <B><U> object is a DoorInstance </U> :</B><BR>
	 * <BR>
	 * <li>Send Server-Client Packets DoorInfo and DoorStatusUpdate to the PlayerInstance</li>
	 * <li>Send Server->Client packet MoveToPawn/CharMoveToLocation and AutoAttackStart to the PlayerInstance</li><BR>
	 * <BR>
	 * <B><U> object is a NpcInstance </U> :</B><BR>
	 * <BR>
	 * <li>Send Server-Client Packet NpcInfo to the PlayerInstance</li>
	 * <li>Send Server->Client packet MoveToPawn/CharMoveToLocation and AutoAttackStart to the PlayerInstance</li><BR>
	 * <BR>
	 * <B><U> object is a Summon </U> :</B><BR>
	 * <BR>
	 * <li>Send Server-Client Packet NpcInfo/PetItemList (if the PlayerInstance is the owner) to the PlayerInstance</li>
	 * <li>Send Server->Client packet MoveToPawn/CharMoveToLocation and AutoAttackStart to the PlayerInstance</li><BR>
	 * <BR>
	 * <B><U> object is a PlayerInstance </U> :</B><BR>
	 * <BR>
	 * <li>Send Server-Client Packet CharInfo to the PlayerInstance</li>
	 * <li>If the object has a private store, Send Server-Client Packet PrivateStoreMsgSell to the PlayerInstance</li>
	 * <li>Send Server->Client packet MoveToPawn/CharMoveToLocation and AutoAttackStart to the PlayerInstance</li><BR>
	 * <BR>
	 * @param object The WorldObject to add to _knownObjects and _knownPlayer
	 */
	@Override
	public boolean addKnownObject(WorldObject object)
	{
		return addKnownObject(object, null);
	}
	
	@Override
	public boolean addKnownObject(WorldObject object, Creature dropper)
	{
		if (!super.addKnownObject(object, dropper))
		{
			return false;
		}
		
		final PlayerInstance active_char = getActiveChar();
		if (active_char == null)
		{
			return false;
		}
		
		if (object.getPoly().isMorphed() && object.getPoly().getPolyType().equals("item"))
		{
			active_char.sendPacket(new SpawnItemPoly(object));
		}
		else
		{
			if (object instanceof ItemInstance)
			{
				if (dropper != null)
				{
					active_char.sendPacket(new DropItem((ItemInstance) object, dropper.getObjectId()));
				}
				else
				{
					active_char.sendPacket(new SpawnItem((ItemInstance) object));
				}
			}
			else if (object instanceof DoorInstance)
			{
				if (((DoorInstance) object).getCastle() != null)
				{
					active_char.sendPacket(new DoorInfo((DoorInstance) object, true));
				}
				else
				{
					active_char.sendPacket(new DoorInfo((DoorInstance) object, false));
				}
				active_char.sendPacket(new DoorStatusUpdate((DoorInstance) object));
			}
			else if (object instanceof FenceInstance)
			{
				((FenceInstance) object).sendInfo(active_char);
			}
			else if (object instanceof BoatInstance)
			{
				if (!active_char.isInBoat())
				{
					if (object != active_char.getBoat())
					{
						active_char.sendPacket(new VehicleInfo((BoatInstance) object));
						((BoatInstance) object).sendVehicleDeparture(active_char);
					}
				}
			}
			else if (object instanceof StaticObjectInstance)
			{
				active_char.sendPacket(new StaticObject((StaticObjectInstance) object));
			}
			else if (object instanceof NpcInstance)
			{
				active_char.sendPacket(new NpcInfo((NpcInstance) object, active_char));
			}
			else if (object instanceof Summon)
			{
				Summon summon = (Summon) object;
				
				// Check if the PlayerInstance is the owner of the Pet
				if (active_char.equals(summon.getOwner()))
				{
					active_char.sendPacket(new PetInfo(summon));
					// The PetInfo packet wipes the PartySpelled (list of active spells' icons). Re-add them
					summon.updateEffectIcons(true);
					
					if (summon instanceof PetInstance)
					{
						active_char.sendPacket(new PetItemList((PetInstance) summon));
					}
				}
				else
				{
					active_char.sendPacket(new NpcInfo(summon, active_char));
				}
			}
			else if (object instanceof PlayerInstance)
			{
				PlayerInstance otherPlayer = (PlayerInstance) object;
				if (otherPlayer.isInBoat())
				{
					otherPlayer.getPosition().setWorldPosition(otherPlayer.getBoat().getPosition().getWorldPosition());
					active_char.sendPacket(new CharInfo(otherPlayer));
					
					final int relation = otherPlayer.getRelation(active_char);
					
					if ((otherPlayer.getKnownList().getKnownRelations().get(active_char.getObjectId()) != null) && (otherPlayer.getKnownList().getKnownRelations().get(active_char.getObjectId()) != relation))
					{
						active_char.sendPacket(new RelationChanged(otherPlayer, relation, active_char.isAutoAttackable(otherPlayer)));
					}
					
					active_char.sendPacket(new GetOnVehicle(otherPlayer, otherPlayer.getBoat(), otherPlayer.getInBoatPosition().getX(), otherPlayer.getInBoatPosition().getY(), otherPlayer.getInBoatPosition().getZ()));
					
				}
				else
				{
					active_char.sendPacket(new CharInfo(otherPlayer));
					
					final int relation = otherPlayer.getRelation(active_char);
					
					if ((otherPlayer.getKnownList().getKnownRelations().get(active_char.getObjectId()) != null) && (otherPlayer.getKnownList().getKnownRelations().get(active_char.getObjectId()) != relation))
					{
						active_char.sendPacket(new RelationChanged(otherPlayer, relation, active_char.isAutoAttackable(otherPlayer)));
					}
				}
				
				if (otherPlayer.getPrivateStoreType() == PlayerInstance.STORE_PRIVATE_SELL)
				{
					active_char.sendPacket(new PrivateStoreMsgSell(otherPlayer));
				}
				else if (otherPlayer.getPrivateStoreType() == PlayerInstance.STORE_PRIVATE_BUY)
				{
					active_char.sendPacket(new PrivateStoreMsgBuy(otherPlayer));
				}
				else if (otherPlayer.getPrivateStoreType() == PlayerInstance.STORE_PRIVATE_MANUFACTURE)
				{
					active_char.sendPacket(new RecipeShopMsg(otherPlayer));
				}
			}
			
			if (object instanceof Creature)
			{
				// Update the state of the Creature object client side by sending Server->Client packet MoveToPawn/CharMoveToLocation and AutoAttackStart to the PlayerInstance
				Creature obj = (Creature) object;
				
				final CreatureAI obj_ai = obj.getAI();
				if (obj_ai != null)
				{
					obj_ai.describeStateToPlayer(active_char);
				}
			}
		}
		
		return true;
	}
	
	/**
	 * Remove a WorldObject from PlayerInstance _knownObjects and _knownPlayer (if necessary) and send Server-Client Packet DeleteObject to the PlayerInstance.<BR>
	 * <BR>
	 * @param object The WorldObject to remove from _knownObjects and _knownPlayer
	 */
	@Override
	public boolean removeKnownObject(WorldObject object)
	{
		if (!super.removeKnownObject(object))
		{
			return false;
		}
		
		final PlayerInstance active_char = getActiveChar();
		
		PlayerInstance object_char = null;
		if (object instanceof PlayerInstance)
		{
			object_char = (PlayerInstance) object;
		}
		
		// TEMP FIX: If player is not visible don't send packets broadcast to all his KnowList. This will avoid GM detection with l2net and olympiad's crash. We can now find old problems with invisible mode.
		if ((object_char != null) && !active_char.isGM())
		{ // GM has to receive remove however because he can see any invisible or inobservermode player
			
			if (!object_char.getAppearance().isInvisible() && !object_char.inObserverMode())
			{
				// Send Server-Client Packet DeleteObject to the PlayerInstance
				active_char.sendPacket(new DeleteObject(object));
			}
			else if (object_char.isGM() && object_char.getAppearance().isInvisible() && !object_char.isTeleporting())
			{
				// Send Server-Client Packet DeleteObject to the PlayerInstance
				active_char.sendPacket(new DeleteObject(object));
			}
		}
		else // All other objects has to be removed
		{
			
			// Send Server-Client Packet DeleteObject to the PlayerInstance
			active_char.sendPacket(new DeleteObject(object));
		}
		
		return true;
	}
	
	@Override
	public PlayerInstance getActiveChar()
	{
		return (PlayerInstance) super.getActiveChar();
	}
	
	@Override
	public int getDistanceToForgetObject(WorldObject object)
	{
		// When knownlist grows, the distance to forget should be at least the same as the previous watch range, or it becomes possible that extra charinfo packets are being sent (watch-forget-watch-forget).
		final int knownlistSize = getKnownObjects().size();
		
		if (knownlistSize <= 25)
		{
			return 4200;
		}
		
		if (knownlistSize <= 35)
		{
			return 3600;
		}
		
		if (knownlistSize <= 70)
		{
			return 2910;
		}
		return 2310;
	}
	
	@Override
	public int getDistanceToWatchObject(WorldObject object)
	{
		final int knownlistSize = getKnownObjects().size();
		
		if (knownlistSize <= 25)
		{
			return 3500; // empty field
		}
		
		if (knownlistSize <= 35)
		{
			return 2900;
		}
		
		if (knownlistSize <= 70)
		{
			return 2300;
		}
		return 1700; // Siege, TOI, city
	}
}
