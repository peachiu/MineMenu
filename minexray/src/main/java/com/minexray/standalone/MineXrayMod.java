package com.minexray.standalone;

import com.minexray.MineXrayClient;
import com.minexray.XrayHost;
import net.fabricmc.api.ClientModInitializer;

/** MineXray on its own (the MineXray jar). Inside MineMeBoostXray, MineMeBoostClient starts it instead. */
public class MineXrayMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (XrayHost.embedded()) {
            return; // MineMeBoostXray already contains MineXray (and refuses to load next to this jar)
        }
        MineXrayClient.init();
    }
}
