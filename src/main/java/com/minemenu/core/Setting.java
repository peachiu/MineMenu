package com.minemenu.core;

/**
 * A number a hack can be tuned with (radius, strength, interval...). Shown as a slider when you right-click the
 * hack's button. {@code step} is the slider's step; {@code unit} is shown after the value ("blocks", "x", "s").
 */
public record Setting(String key, String label, double min, double max, double step, double defaultValue, String unit) {
    /** In the player's language (minemenu.setting.LABEL_IN_ENGLISH in the lang files). */
    @Override
    public String label() {
        return Tr.get("minemenu.setting." + label.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_"), label);
    }

    public String format(double value) {
        String number = step >= 1 ? String.valueOf((int) Math.round(value)) : String.format("%.2f", value)
                .replaceAll("0+$", "").replaceAll("\\.$", "");
        String shownUnit = unit.isEmpty() || unit.startsWith("x") || unit.equals("%") || unit.equals("s") || unit.equals("px") ? unit
                : Tr.get("minemenu.unit." + unit, unit);
        return number + (unit.isEmpty() ? "" : unit.startsWith("x") || unit.equals("%") ? unit : " " + shownUnit);
    }

    /** A yes/no setting, shown as an ON/OFF button. */
    public static Setting toggle(String key, String label, boolean defaultValue) {
        return new Setting(key, label, 0, 1, 1, defaultValue ? 1 : 0, "bool");
    }

    public boolean isToggle() {
        return unit.equals("bool");
    }
}
