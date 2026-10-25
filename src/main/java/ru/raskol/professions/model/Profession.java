package ru.raskol.professions.model;

/** Профессии. Позже добавим ENCHANTING и SMITHING. */
public enum Profession {

    FISHING("Рыбак", "§b"),
    FARMING("Фермер", "§a"),
    LUMBER("Дровосек", "§e"),
    MINING("Шахтёр", "§9");

    public final String displayName;
    public final String color;

    Profession(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public static Profession fromName(String name) {
        if (name == null) return null;
        try {
            return Profession.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
