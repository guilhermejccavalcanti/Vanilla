/*
 * This file is part of Vanilla.
 *
 * Copyright (c) 2011-2012, Spout LLC <http://www.spout.org/>
 * Vanilla is licensed under the Spout License Version 1.
 *
 * Vanilla is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * In addition, 180 days after any changes are published, you can use the
 * software, incorporating those changes, under the terms of the MIT license,
 * as described in the Spout License Version 1.
 *
 * Vanilla is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License,
 * the MIT license and the Spout License Version 1 along with this program.
 * If not, see <http://www.gnu.org/licenses/> for the GNU Lesser General Public
 * License and see <http://spout.in/licensev1> for the full license, including
 * the MIT license.
 */
package org.spout.vanilla.protocol.handler.player.pos;

import org.spout.api.entity.Player;
import org.spout.api.math.QuaternionMath;
import org.spout.api.protocol.MessageHandler;
import org.spout.api.protocol.ClientSession;
import org.spout.api.protocol.ServerSession;

import org.spout.vanilla.component.entity.living.Human;
import org.spout.vanilla.protocol.VanillaServerNetworkSynchronizer;
import org.spout.vanilla.protocol.msg.player.pos.PlayerLookMessage;

public final class PlayerLookHandler extends MessageHandler<PlayerLookMessage> {
	@Override
	public void handleServer(ServerSession session, PlayerLookMessage message) {
		if (!session.hasPlayer()) {
			return;
		}

		//First look packet is to login/receive terrain, is not a valid rotation
		if (session.getDataMap().get("first_login", 0) == 0) {
			session.getDataMap().put("first_login", 1);
			((VanillaServerNetworkSynchronizer) session.getPlayer().getNetworkSynchronizer()).sendPosition();
			return;
		}

		Player holder = session.getPlayer();

		holder.getScene().setRotation(QuaternionMath.rotation(message.getPitch(), message.getYaw(), 0));
		Human human = holder.get(Human.class);
		if (human != null) {
			human.setOnGround(message.isOnGround());
			human.getHead().setOrientation(message.getRotation());
		}
	}

	@Override
	public void handleClient(ClientSession session, PlayerLookMessage message) {
		if (!session.hasPlayer()) {
			return;
		}

		Player holder = session.getPlayer();

		holder.getScene().setRotation(QuaternionMath.rotation(message.getPitch(), message.getYaw(), 0));
		Human human = holder.get(Human.class);
		if (human != null) {
			human.setOnGround(message.isOnGround());
			human.getHead().setOrientation(message.getRotation());
		}
	}
}
