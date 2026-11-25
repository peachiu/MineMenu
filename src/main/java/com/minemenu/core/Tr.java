package com.minemenu.core;

import net.minecraft.util.Language;

/**
 * Translations for text that is kept as a String (names of hacks, actions, pages, settings...): looks the key up
 * in the current language (assets/minemenu/lang/*.json) when it's shown, with the English text as the fallback.
 */
public final class Tr {
    private Tr() {
    }

    public static String get(String key, String fallback) {
        return Language.getInstance().get(key, fallback);
    }

    /** Translates a key, or returns the text itself if it isn't a key (or has no translation). */
    public static String get(String keyOrText) {
        return Language.getInstance().get(keyOrText);
    }

    public static String format(String key, String fallback, Object... args) {
        return String.format(get(key, fallback), args);
    }
}
