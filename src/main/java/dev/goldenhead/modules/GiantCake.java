package dev.goldenhead.modules;

import dev.goldenhead.MeteorPitAddon;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

import java.util.Set;

/**
 * Giant Cake event: spam-click the cake. Each slice pays gold; the red clay "cherries" pay 150g and the black
 * clay "chocolate chips" 100 XP, so those are taken first when they're part of the cake (clay elsewhere on the
 * map is ignored). A clay block that is still there after 15 clicks is given up on.
 */
public class GiantCake extends ClickTarget {
    private static final Set<Block> BLOCKS = Set.of(Blocks.CAKE, Blocks.RED_TERRACOTTA, Blocks.BLACK_TERRACOTTA);

    public GiantCake() {
        super(MeteorPitAddon.PIT, "giant-cake", "Eats the Giant Cake as fast as possible, cherries and chocolate chips first.");
    }

    @Override
    protected Set<Block> candidateBlocks() {
        return BLOCKS;
    }

    @Override
    protected boolean isTargetBlock(BlockPos pos) {
        Block block = mc.world.getBlockState(pos).getBlock();
        return block == Blocks.CAKE || touchesCake(pos);
    }

    @Override
    protected double weight(BlockPos pos) {
        Block block = mc.world.getBlockState(pos).getBlock();
        if (block == Blocks.RED_TERRACOTTA) return 900;   // 150g: worth ~30 blocks of detour
        if (block == Blocks.BLACK_TERRACOTTA) return 400; // 100 XP
        return 0;
    }

    @Override
    protected int clickLimit(BlockPos pos) {
        return mc.world.getBlockState(pos).isOf(Blocks.CAKE) ? 0 : 15;
    }

    private boolean touchesCake(BlockPos pos) {
        for (BlockPos p : BlockPos.iterate(pos.add(-1, -1, -1), pos.add(1, 1, 1))) {
            if (mc.world.getBlockState(p).isOf(Blocks.CAKE)) return true;
        }
        return false;
    }
}
