package br.com.irmandade.imdd.relic;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MagmaBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/** Magma que "quebra" em 4 estágios (como o gelo do Frost Walker) e volta a ser lava. */
public class FrostedMagmaBlock extends MagmaBlock {
    public static final IntProperty AGE = Properties.AGE_3;

    public FrostedMagmaBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(AGE, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(AGE);
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        int age = state.get(AGE);
        if (age < 3) {
            world.setBlockState(pos, state.with(AGE, age + 1), Block.NOTIFY_LISTENERS);
            world.scheduleBlockTick(pos, this, 20 + random.nextInt(15));
        } else {
            world.setBlockState(pos, Blocks.LAVA.getDefaultState());
        }
    }
}
