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
package org.spout.vanilla.material.block.plant;

import java.util.Random;

import org.spout.api.Platform;
import org.spout.api.entity.Entity;
import org.spout.api.event.Cause;
import org.spout.api.event.cause.EntityCause;
import org.spout.api.geo.cuboid.Block;
import org.spout.api.material.BlockMaterial;
import org.spout.api.material.DynamicMaterial;
import org.spout.api.material.block.BlockFace;
import org.spout.api.material.block.BlockFaces;
import org.spout.api.material.range.CuboidEffectRange;
import org.spout.api.material.range.EffectRange;
import org.spout.api.math.GenericMath;
import org.spout.api.math.IntVector3;
import org.spout.api.math.Vector3;
import org.spout.api.util.BlockIterator;

import org.spout.vanilla.VanillaPlugin;
import org.spout.vanilla.component.entity.misc.EntityHead;
import org.spout.vanilla.data.drops.flag.ToolTypeFlags;
import org.spout.vanilla.data.resources.VanillaMaterialModels;
import org.spout.vanilla.material.Burnable;
import org.spout.vanilla.material.VanillaBlockMaterial;
import org.spout.vanilla.material.VanillaMaterials;
import org.spout.vanilla.material.block.Spreading;
import org.spout.vanilla.protocol.VanillaServerNetworkSynchronizer;
import org.spout.vanilla.render.VanillaEffects;
import org.spout.vanilla.world.lighting.VanillaLighting;

public class Vines extends VanillaBlockMaterial implements Spreading, Plant, Burnable, DynamicMaterial {
	private static final EffectRange VINE_RANGE = new CuboidEffectRange(-4, -1, -4, 4, 1, 4);
	private static final int MAX_PER_GROUP = 5;

	public Vines(String name, int id) {
		super(name, id, VanillaMaterialModels.VINES);
		this.setLiquidObstacle(false);
		this.setHardness(0.2F).setResistance(0.3F).setTransparent();
		this.getDrops().DEFAULT.clear().add(this).addFlags(ToolTypeFlags.SHEARS);
		if (VanillaPlugin.getInstance().getEngine().getPlatform() == Platform.CLIENT) {
			if (!getModel().getRenderMaterial().getBufferEffects().contains(VanillaEffects.BIOME_FOLIAGE_COLOR)) {
				getModel().getRenderMaterial().addBufferEffect(VanillaEffects.BIOME_FOLIAGE_COLOR);
			}
		}
	}

	@Override
	public int getBurnPower() {
		return 15;
	}

	@Override
	public int getCombustChance() {
		return 100;
	}

	private int getMask(BlockFace face) {
		switch (face) {
			case WEST:
				return 0x1;
			case NORTH:
				return 0x2;
			case EAST:
				return 0x4;
			case SOUTH:
				return 0x8;
			default:
				return 0;
		}
	}

	public boolean isAttachedTo(Block block, BlockFace face) {
		return block.isDataBitSet(getMask(face));
	}

	/**
	 * Sets whether a certain face is attached or not
	 * @param block of this material
	 * @param face to attach to
	 * @param attached whether or not to attach
	 */
	public void setFaceAttached(Block block, BlockFace face, boolean attached) {
		block.setDataBits(getMask(face), attached);
	}

	@Override
	public boolean hasPhysics() {
		return true;
	}

	@Override
	public int getMinimumLightToSpread() {
		return 0;
	}

	@Override
	public void onUpdate(BlockMaterial oldMaterial, Block block) {
		//check all directions if it still supports it
		Block above = block.translate(BlockFace.TOP);
		if (block.getBlockData() != 0) {
			BlockMaterial abovemat = above.getMaterial();
			for (BlockFace face : BlockFaces.NESW) {
				if (!this.isAttachedTo(block, face)) {
					continue;
				}

				if (!this.canAttachTo(block.translate(face), face.getOpposite())) {
					//is there a vine block above to which it can support itself?
					if (!abovemat.equals(VanillaMaterials.VINES) || !this.isAttachedTo(above, face)) {
						this.setFaceAttached(block, face, false);
					}
				}
			}
		}
		if (block.getBlockData() == 0) {
			//check if there is a block above it can attach to, else destroy
			if (!this.canAttachTo(above, BlockFace.BOTTOM)) {
				this.onDestroy(block, above.getMaterial().toCause(above));
			}
		}
	}

	public boolean canAttachTo(BlockMaterial material, BlockFace face) {
		if (material instanceof VanillaBlockMaterial) {
			return (((VanillaBlockMaterial) material).canSupport(this, face));
		}
		return false;
	}

	public boolean canAttachTo(Block block, BlockFace face) {
		return this.canAttachTo(block.getMaterial(), face);
	}

	private BlockFace getTracedFace(Block block, Cause<?> cause) {
		if (block.getMaterial().equals(VanillaMaterials.VINES) && cause instanceof EntityCause) {
			//get block by block tracing from the player view
			Entity entity = ((EntityCause) cause).getSource();
			EntityHead head = entity.get(EntityHead.class);
			if (head != null) {
				BlockIterator iter = head.getBlockView();
				Block next;
				while (iter.hasNext()) {
					next = iter.next();
					if (next.equals(block)) {
						Block target = iter.hasNext() ? iter.next() : null;
						if (target != null) {
							//get what face this target is relative to the main block
							for (BlockFace face : BlockFaces.NESWBT) {
								if (block.translate(face).equals(target)) {
									return face;
								}
							}
						}
						return null;
					}
				}
			}
		}
		return null;
	}

	@Override
	public boolean canSupport(BlockMaterial material, BlockFace face) {
		return material.equals(VanillaMaterials.FIRE);
	}

	@Override
	public boolean canPlace(Block block, short data, BlockFace face, Vector3 clickedPos, boolean isClicked, Cause<?> cause) {
		if (block.getMaterial().equals(VanillaMaterials.VINES)) {
			if (isClicked) {
				face = getTracedFace(block, cause);
				if (face == null) {
					return false;
				}
			} else {
				return false;
			}
		}
		if (face == BlockFace.BOTTOM) {
			return false; //TODO: possibly place on top of vines?
		} else if (face == BlockFace.TOP) {
			if (isClicked) {
				return false;
			}

			// check below block
			return this.canAttachTo(block.translate(BlockFace.TOP), BlockFace.BOTTOM);
		} else {
			return this.canAttachTo(block.translate(face), face.getOpposite()) && !this.isAttachedTo(block, face);
		}
	}

	@Override
	public void onPlacement(Block block, short data, BlockFace against, Vector3 clickedPos, boolean isClickedBlock, Cause<?> cause) {
		super.onPlacement(block, data, against, clickedPos, isClickedBlock, cause);
		BlockFace attached = against;
		if (block.getMaterial().equals(VanillaMaterials.VINES)) {
			if (isClickedBlock) {
				attached = getTracedFace(block, cause);
				if (attached == null) {
					return;
				}
			}
		} else if (against == BlockFace.BOTTOM || against == BlockFace.TOP) {
			return;
		}
		this.setFaceAttached(block, attached, true);
	}

	@Override
	public boolean isPlacementObstacle() {
		return false;
	}

	@Override
	public EffectRange getDynamicRange() {
		return EffectRange.THIS_AND_NEIGHBORS;
	}

	@Override
	public void onFirstUpdate(Block b, long currentTime) {
		//TODO : Delay before first grow
		b.dynamicUpdate(10000 + currentTime, true);
	}

	@Override
	public void onDynamicUpdate(Block block, long updateTime, int data) {
		final Random rand = GenericMath.getRandom();
		if (rand.nextInt(4) != 0) {
			return;
		}
		int minLight = getMinimumLightToSpread();
		if (minLight > 0 && VanillaLighting.getLight(block) < minLight) {
			return;
		}

		// get a random direction to spread to
		BlockFace spreadDirection = BlockFaces.NESWBT.random(rand);

		// can we spread?
		boolean denySpreadHorUp = false;
		int max = MAX_PER_GROUP;
		for (IntVector3 coord : VINE_RANGE) {
			if (block.translate(coord).isMaterial(this) && --max <= 0) {
				denySpreadHorUp = true;
				break;
			}
		}

		// Horizontal spreading
		if (BlockFaces.NESW.contains(spreadDirection) && !this.isAttachedTo(block, spreadDirection)) {
			if (denySpreadHorUp) {
				return;
			}

			if (this.canAttachTo(block, spreadDirection)) {
				// add attached face
				this.setFaceAttached(block, spreadDirection, true);
			} else {
				Block neigh = block.translate(spreadDirection);
				BlockFace left = BlockFaces.NESW.previous(spreadDirection);
				BlockFace right = BlockFaces.NESW.next(spreadDirection);

				// attach relative left
				if (this.isAttachedTo(block, left)) {
					Block newVine = neigh.translate(left);
					if (this.canAttachTo(neigh, left)) {
						newVine.setMaterial(this, getMask(left));
						return;
					}
					if (newVine.isMaterial(VanillaMaterials.AIR) && this.canAttachTo(block, left)) {
						newVine.setMaterial(this, getMask(spreadDirection.getOpposite()));
						return;
					}
				}

				// attach relative right
				if (this.isAttachedTo(block, right)) {
					Block newVine = neigh.translate(right);
					if (this.canAttachTo(neigh, right)) {
						newVine.setMaterial(this, getMask(right));
						return;
					}
					if (newVine.isMaterial(VanillaMaterials.AIR) && this.canAttachTo(block, right)) {
						newVine.setMaterial(this, getMask(spreadDirection.getOpposite()));
						return;
					}
				}

				// attach to top
				if (this.canAttachTo(neigh, BlockFace.TOP)) {
					neigh.setMaterial(this);
					return;
				}
			}
			return;
		}

		int randomData = rand.nextInt(16) & block.getBlockData();

		// Upwards spreading
		if (spreadDirection == BlockFace.TOP && block.getY() < VanillaServerNetworkSynchronizer.WORLD_HEIGHT) {
			Block above = block.translate(BlockFace.TOP);
			if (above.isMaterial(VanillaMaterials.AIR)) {
				// spread up
				if (denySpreadHorUp) {
					return;
				}

				// get all the faces it can attach to randomly			
				if (randomData > 0) {
					for (BlockFace nextFace : BlockFaces.NESW) {
						// can not attach to that material? Remove face.
						if (!this.canAttachTo(above, nextFace)) {
							randomData &= ~getMask(nextFace);
						}
					}
					if (randomData > 0) {
						above.setMaterial(this, randomData);
					}
				}
				return;
			}
		}

		// Downwards spreading
		if (block.getY() > 1) {
			Block below = block.translate(BlockFace.BOTTOM);
			BlockMaterial belowMat = below.getMaterial();
			if (belowMat.equals(VanillaMaterials.AIR)) {
				if (randomData > 0) {
					// create new vine
					below.setMaterial(this, randomData);
				}
			} else if (belowMat.equals(this)) {
				// add face to existing vine
				below.setDataBits(randomData);
			}
		}

		block.dynamicUpdate(updateTime + 10000, true);
	}
}
