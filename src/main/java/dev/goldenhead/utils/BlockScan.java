package dev.goldenhead.utils;

import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

import java.util.*;

/** Finds blocks in loaded chunks, skipping chunk sections whose palette can't contain them. */
public final class BlockScan {
    private BlockScan() {
    }

    public static List<BlockPos> find(Set<Block> blocks, double range) {
        MinecraftClient mc = MinecraftClient.getInstance();
        List<BlockPos> found = new ArrayList<>();
        if (mc.player == null) return found;

        int chunkRange = (int) Math.ceil(range / 16) + 1;
        ChunkPos playerChunk = mc.player.getChunkPos();
        double rangeSq = range * range;
        for (Chunk chunk : Utils.chunks()) {
            ChunkPos cp = chunk.getPos();
            if (Math.abs(cp.x - playerChunk.x) > chunkRange || Math.abs(cp.z - playerChunk.z) > chunkRange) continue;

            ChunkSection[] sections = chunk.getSectionArray();
            for (int i = 0; i < sections.length; i++) {
                ChunkSection section = sections[i];
                if (section.isEmpty() || !section.hasAny(state -> blocks.contains(state.getBlock()))) continue;

                int baseY = chunk.sectionIndexToCoord(i) << 4;
                for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                    if (!blocks.contains(section.getBlockState(x, y, z).getBlock())) continue;
                    BlockPos pos = new BlockPos(cp.getStartX() + x, baseY + y, cp.getStartZ() + z);
                    if (mc.player.getBlockPos().getSquaredDistance(pos) <= rangeSq) found.add(pos);
                }
            }
        }
        return found;
    }

    /** The biggest group of touching positions (26-neighbourhood); empty if none. */
    public static List<BlockPos> largestCluster(Collection<BlockPos> positions) {
        Set<BlockPos> left = new HashSet<>(positions);
        List<BlockPos> best = List.of();
        while (!left.isEmpty()) {
            BlockPos start = left.iterator().next();
            left.remove(start);
            List<BlockPos> cluster = new ArrayList<>();
            Deque<BlockPos> queue = new ArrayDeque<>(List.of(start));
            while (!queue.isEmpty()) {
                BlockPos p = queue.poll();
                cluster.add(p);
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    BlockPos n = p.add(dx, dy, dz);
                    if (left.remove(n)) queue.add(n);
                }
            }
            if (cluster.size() > best.size()) best = cluster;
        }
        return best;
    }

    public static Box bounds(Collection<BlockPos> positions) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : positions) {
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        return new Box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }
}
