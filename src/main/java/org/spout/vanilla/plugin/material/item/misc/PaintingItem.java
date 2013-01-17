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
package org.spout.vanilla.plugin.material.item.misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.spout.api.entity.Entity;
import org.spout.api.entity.Player;
import org.spout.api.event.player.PlayerInteractEvent.Action;
import org.spout.api.geo.World;
import org.spout.api.geo.cuboid.Block;
import org.spout.api.material.block.BlockFace;
import org.spout.api.math.Vector3;

import org.spout.vanilla.api.data.PaintingType;
import org.spout.vanilla.plugin.component.substance.Painting;
import org.spout.vanilla.plugin.material.VanillaMaterials;
import org.spout.vanilla.plugin.material.item.VanillaItemMaterial;

public class PaintingItem extends VanillaItemMaterial {
	private final Random random = new Random();

	public PaintingItem(String name, int id) {
		super(name, id, null);
	}

	@Override
	public void onInteract(Entity entity, Block block, Action type, BlockFace face) {
		if (!(entity instanceof Player) || type != Action.RIGHT_CLICK) {
			return;
		}
		if (!BlockFace.TOP.equals(face) && !BlockFace.BOTTOM.equals(face)) {
			List<PaintingType> list = new ArrayList<PaintingType>();
			World world = block.getWorld();
			BlockFace direction = face.getOpposite();
			PaintingType[] types = PaintingType.values();
			boolean good = true;
			for (PaintingType paintingType : types) {
				good = true;
				//A value higher than 0 means that it takes more block than just 1
				int blockHeight = paintingType.getHeight() / 16 - 1;
				int blockWidth = paintingType.getWidth() / 16 - 1;
				if (blockHeight >= 1 || blockWidth >= 1) {
					for (int height = 0; height <= blockHeight && good; height++) {
						for (int width = 0; width <= blockWidth && good; width++) {
							Vector3 vector = Vector3.ZERO;
							switch (direction) {
								case NORTH:
								case SOUTH:
									vector = vector.add(0, 0, width);
									break;
								case EAST:
								case WEST:
									vector = vector.add(width,0,0);
									break;
							}
							if (!block.translate(face.getOffset()).translate(vector.add(0,height,0)).getMaterial().equals(VanillaMaterials.AIR) || block.translate(vector.add(0,height,0)).getMaterial().equals(VanillaMaterials.AIR)) {
								good = false;
							}
						}
					}
					if (good) {
						list.add(paintingType);
					}
				} else {
					list.add(paintingType);
				}
			}
			PaintingType paintingType = list.get(random.nextInt(list.size() - 1));
			
			
			Entity e = world.createEntity(paintingType.getCenter(direction, block.getPosition()), Painting.class);
			Painting painting = e.add(Painting.class);
			painting.setType(paintingType);
			painting.setFace(direction);
			world.spawnEntity(e);
		}
	}
}
