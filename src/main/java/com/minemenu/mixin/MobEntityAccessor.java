package com.minemenu.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Tame Anything: a pet's own target-finding AI is removed so it never turns on you. */
@Mixin(MobEntity.class)
public interface MobEntityAccessor {
    @Accessor("targetSelector")
    GoalSelector minemenu$getTargetSelector();
}
