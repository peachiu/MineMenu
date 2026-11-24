package com.minemenu.mixin;

import net.minecraft.structure.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Copy / Paste: the blocks inside a structure template, to place them in batches. */
@Mixin(StructureTemplate.class)
public interface StructureTemplateAccessor {
    @Accessor("blockInfoLists")
    List<StructureTemplate.PalettedBlockInfoList> minemenu$getBlockInfoLists();
}
