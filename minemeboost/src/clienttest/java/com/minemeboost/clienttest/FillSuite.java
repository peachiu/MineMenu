package com.minemeboost.clienttest;

import com.minemenu.ServerCheats;
import com.minexray.XrayConfig;
import com.minexray.XrayMode;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Diagnosis: frame times while a 64x64x64 fill runs, with MineXray's ESP off and on. */
class FillSuite extends Harness {
    FillSuite() {
        super("fill");
    }

    @Override
    protected void build() {
        step(200, this::setupArena);
        fill("ESP off", false);
        fill("Ore ESP + Block ESP on", true);
        step(5, c -> check("done", true));
    }

    private void fill(String name, boolean esp) {
        step(5, c -> { XrayConfig.get().set(XrayMode.ORE_ESP, esp); XrayConfig.get().set(XrayMode.BLOCK_ESP, esp); });
        step(100, c -> clearArea());
        step(5, c -> { run(() -> { com.minemenu.build.Selection.set(1, rel(-20, 0, -30), host()); com.minemenu.build.Selection.set(2, rel(43, 63, 33), host()); host().getInventory().setStack(0, new ItemStack(Items.STONE)); }); });
        step(5, c -> { action(ServerCheats.Action.FILL_SELECTION); startRec(); });
        step(200, c -> {});
        step(5, c -> { double[] r = stopRec("fill, " + name); log.add("frames " + count + " " + name); });
        step(300, c -> action(ServerCheats.Action.UNDO));
    }
}
