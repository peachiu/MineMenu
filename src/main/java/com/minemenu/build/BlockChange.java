package com.minemenu.build;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/** One block to set (and, for undo, what was there): position, state and the block entity's data (chests...). */
public record BlockChange(BlockPos pos, BlockState state, @Nullable NbtCompound blockEntity) {
}
