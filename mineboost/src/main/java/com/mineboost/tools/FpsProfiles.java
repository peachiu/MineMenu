package com.mineboost.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mineboost.PresetState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** FPS profiles: your graphics settings saved under a name (config/minemeboost/fps-profiles.json), shareable as "MBB1:..." codes. */
public final class FpsProfiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("minemeboost").resolve("fps-profiles.json");
    private static final String PREFIX = "MBB1:";
    private static Map<String, PresetState> profiles;

    private FpsProfiles() {
    }

    public static Map<String, PresetState> all() {
        if (profiles == null) {
            profiles = new LinkedHashMap<>();
            try {
                if (Files.exists(FILE)) {
                    Map<String, PresetState> read = GSON.fromJson(Files.readString(FILE), new TypeToken<LinkedHashMap<String, PresetState>>() { }.getType());
                    if (read != null) {
                        profiles.putAll(read);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return profiles;
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(all()));
        } catch (Exception ignored) {
        }
    }

    public static void saveCurrent(MinecraftClient client, String name) {
        all().put(name, PresetState.capture(client));
        save();
    }

    public static void load(MinecraftClient client, String name) {
        PresetState state = all().get(name);
        if (state != null) {
            state.restore(client);
        }
    }

    public static void delete(String name) {
        all().remove(name);
        save();
    }

    public static String export(String name) {
        Map<String, PresetState> one = new LinkedHashMap<>();
        one.put(name, all().get(name));
        return PREFIX + Base64.getEncoder().encodeToString(new Gson().toJson(one).getBytes(StandardCharsets.UTF_8));
    }

    public static String importCode(String code) {
        try {
            if (code == null || !code.trim().startsWith(PREFIX)) {
                return null;
            }
            String json = new String(Base64.getDecoder().decode(code.trim().substring(PREFIX.length())), StandardCharsets.UTF_8);
            Map<String, PresetState> read = new Gson().fromJson(json, new TypeToken<LinkedHashMap<String, PresetState>>() { }.getType());
            if (read == null || read.isEmpty()) {
                return null;
            }
            Map.Entry<String, PresetState> entry = read.entrySet().iterator().next();
            PresetState.validate(entry.getValue());
            all().put(entry.getKey(), entry.getValue());
            save();
            return entry.getKey();
        } catch (Exception e) {
            return null;
        }
    }
}
