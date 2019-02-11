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
package org.spout.vanilla.world.generator.nether;

import java.util.Random;

import net.royawesome.jlibnoise.NoiseQuality;
import net.royawesome.jlibnoise.module.modifier.ScalePoint;
import net.royawesome.jlibnoise.module.source.Perlin;

import org.spout.api.generator.WorldGeneratorUtils;
import org.spout.api.generator.biome.BiomeManager;
import org.spout.api.generator.biome.BiomePopulator;
import org.spout.api.geo.World;
import org.spout.api.geo.cuboid.Chunk;
import org.spout.api.geo.discrete.Point;
import org.spout.api.math.Vector3;
import org.spout.api.util.cuboid.CuboidBlockMaterialBuffer;

import org.spout.vanilla.material.VanillaMaterials;
import org.spout.vanilla.material.block.Liquid;
import org.spout.vanilla.protocol.VanillaServerNetworkSynchronizer;
import org.spout.vanilla.world.generator.biome.VanillaBiomes;
import org.spout.vanilla.world.generator.biome.VanillaSingleBiomeGenerator;
import org.spout.vanilla.world.generator.nether.populator.BlockPatchPopulator;
import org.spout.vanilla.world.generator.nether.populator.FortressPopulator;
import org.spout.vanilla.world.generator.nether.populator.NetherCavePopulator;
import org.spout.vanilla.world.generator.normal.populator.BedrockBoundPopulator;
import org.spout.vanilla.world.generator.normal.populator.BedrockBoundPopulator.BedrockBound;

public class NetherGenerator extends VanillaSingleBiomeGenerator {
	// numeric constants
	public static final int HEIGHT = 128;
	public static final int SEA_LEVEL = 31;
	private static final byte BEDROCK_DEPTH = 4;
	// noise for generation
	private static final Perlin PERLIN = new Perlin();
	private static final ScalePoint NOISE = new ScalePoint();
	// smooth stuff
	private static final int SMOOTH_HEIGHT = 16;
	private static final int LOW_SMOOTH_START = BEDROCK_DEPTH + SMOOTH_HEIGHT;
	private static final int HIGH_SMOOTH_START = HEIGHT - 1 - BEDROCK_DEPTH - SMOOTH_HEIGHT;

	static {
		PERLIN.setFrequency(0.01);
		PERLIN.setLacunarity(2);
		PERLIN.setNoiseQuality(NoiseQuality.BEST);
		PERLIN.setPersistence(0.5);
		PERLIN.setOctaveCount(16);

		NOISE.SetSourceModule(0, PERLIN);
		NOISE.setxScale(0.5);
		NOISE.setyScale(1);
		NOISE.setzScale(0.5);
	}

	public NetherGenerator() {
		super(VanillaBiomes.NETHERRACK);
	}

	@Override
	public void registerBiomes() {
		super.registerBiomes();
		register(VanillaBiomes.NETHERRACK);
		final BedrockBoundPopulator bedrock = new BedrockBoundPopulator();
		bedrock.addBound(0, 1, BEDROCK_DEPTH);
		final BedrockBound top = new BedrockBound(HEIGHT - BEDROCK_DEPTH, 1, BEDROCK_DEPTH);
		top.setFlipped(true);
		bedrock.addBound(top);
		addGeneratorPopulators(
				bedrock,
				new NetherCavePopulator(),
				new BlockPatchPopulator(VanillaMaterials.SOUL_SAND), new BlockPatchPopulator(VanillaMaterials.GRAVEL));
		addPopulators(
				new FortressPopulator(),
				new BiomePopulator());
	}

	@Override
	public String getName() {
		return "VanillaNether";
	}

	@Override
	protected void generateTerrain(CuboidBlockMaterialBuffer blockData, int x, int y, int z, BiomeManager biomeManager, long seed) {
		PERLIN.setSeed((int) seed * 17);
		final Vector3 size = blockData.getSize();
		final int sizeX = size.getFloorX();
		final int sizeY = Math.min(size.getFloorY(), HEIGHT);
		final int sizeZ = size.getFloorZ();
		final double[][][] noise = WorldGeneratorUtils.fastNoise(NOISE, sizeX, sizeY, sizeZ, 4, x, y, z);
		for (int xx = 0; xx < sizeX; xx++) {
			for (int zz = 0; zz < sizeZ; zz++) {
				for (int yy = 0; yy < sizeY; yy++) {
					double value = noise[xx][yy][zz];
					if (SMOOTH_HEIGHT > 0) {
						if (yy + y < LOW_SMOOTH_START) {
							value -= cubic(1d / SMOOTH_HEIGHT * (y + yy - LOW_SMOOTH_START));
						} else if (yy + y > HIGH_SMOOTH_START) {
							value += cubic(1d / SMOOTH_HEIGHT * (y + yy - HIGH_SMOOTH_START));
						}
					}
					if (value >= 0) {
						blockData.set(x + xx, y + yy, z + zz, VanillaMaterials.NETHERRACK);
					} else {
						if (y + yy <= SEA_LEVEL) {
							blockData.set(x + xx, y + yy, z + zz, VanillaMaterials.STATIONARY_LAVA);
						} else {
							blockData.set(x + xx, y + yy, z + zz, VanillaMaterials.AIR);
						}
					}
				}
			}
		}
	}

	private static double cubic(double x) {
		return x * x * x;
	}

	@Override
	public Point getSafeSpawn(World world) {
		final Random random = new Random();
		for (byte attempts = 0; attempts < 32; attempts++) {
			final int x = random.nextInt(256) - 127;
			final int z = random.nextInt(256) - 127;
			final int y = getHighestSolidBlock(world, x, z);
			if (y != -1) {
				return new Point(world, x, y + 0.5f, z);
			}
		}
		return new Point(world, 0, 80, 0);
	}

	private int getHighestSolidBlock(World world, int x, int z) {
		int y = HEIGHT / 2;
		if (!world.getBlockMaterial(x, y, z).isInvisible()) {
			return -1;
		}
		while (world.getBlockMaterial(x, y, z).isInvisible()) {
			if (--y == 0 || world.getBlockMaterial(x, y, z) instanceof Liquid) {
				return -1;
			}
		}
		return ++y;
	}

	@Override
	public int[][] getSurfaceHeight(World world, int chunkX, int chunkZ) {
		int height = VanillaServerNetworkSynchronizer.WORLD_HEIGHT - 1;
		int[][] heights = new int[Chunk.BLOCKS.SIZE][Chunk.BLOCKS.SIZE];
		for (int x = 0; x < Chunk.BLOCKS.SIZE; x++) {
			for (int z = 0; z < Chunk.BLOCKS.SIZE; z++) {
				heights[x][z] = height;
			}
		}
		return heights;
	}
}
