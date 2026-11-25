package com.mineboost;

import net.minecraft.util.Language;

/**
 * Translations for text that is kept as a String (names of hacks, actions, pages, settings...): looks the key up
 * in the current language (assets/mineboost/lang/*.json) when it's shown, with the English text as the fallback.
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

    /** For fixed English texts: the key is prefix + the text in lower case with _ between words. */
    public static String auto(String prefix, String english) {
        return get(prefix + slug(english), english);
    }

    public static String slug(String english) {
        String s = english.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return s.length() > 50 ? s.substring(0, 50) : s;
    }

    public static String format(String key, String fallback, Object... args) {
        return String.format(get(key, fallback), args);
    }
}
