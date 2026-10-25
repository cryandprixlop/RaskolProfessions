package ru.raskol.professions.store;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.raskol.professions.RaskolProfessions;
import ru.raskol.professions.model.ProfEntry;
import ru.raskol.professions.model.Profession;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Прогресс профессий: professions.yml.
 * Сейв по dirty-флагу (раз в 5 минут + onDisable).
 * Прогрессия опыта геометрическая: xp(N->N+1) = base * multiplier^(N-1).
 */
public final class ProfStore {

    private final RaskolProfessions plugin;
    private final Map<String, Map<Profession, ProfEntry>> players = new HashMap<>();
    private final Map<String, String> names = new HashMap<>();
    private volatile boolean dirty = false;

    public ProfStore(RaskolProfessions plugin) { this.plugin = plugin; }

    public void load() {
        players.clear();
        names.clear();
        File file = new File(plugin.getDataFolder(), "professions.yml");
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("players");
        if (root == null) return;
        for (String uuid : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(uuid);
            if (s == null) continue;
            names.put(uuid, s.getString("name", ""));
            Map<Profession, ProfEntry> map = new EnumMap<>(Profession.class);
            for (Profession prof : Profession.values()) {
                ConfigurationSection ps = s.getConfigurationSection(prof.name());
                if (ps == null) continue;
                ProfEntry e = new ProfEntry();
                e.level = ps.getInt("level", 1);
                e.xp = ps.getInt("xp", 0);
                map.put(prof, e);
            }
            players.put(uuid, map);
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, Map<Profession, ProfEntry>> pe : players.entrySet()) {
            ConfigurationSection s = cfg.createSection("players." + pe.getKey());
            s.set("name", names.getOrDefault(pe.getKey(), ""));
            for (Map.Entry<Profession, ProfEntry> ee : pe.getValue().entrySet()) {
                ConfigurationSection ps = s.createSection(ee.getKey().name());
                ps.set("level", ee.getValue().level);
                ps.set("xp", ee.getValue().xp);
            }
        }
        try {
            cfg.save(new File(plugin.getDataFolder(), "professions.yml"));
            dirty = false;
        } catch (Exception e) {
            plugin.getLogger().severe("[Prof] не удалось сохранить professions.yml: " + e.getMessage());
        }
    }

    public void saveIfDirty() {
        if (dirty) save();
    }

    public int playerCount() { return players.size(); }

    public ProfEntry entry(String uuid, String name, Profession prof) {
        names.putIfAbsent(uuid, name);
        Map<Profession, ProfEntry> map = players.computeIfAbsent(uuid, k -> new EnumMap<>(Profession.class));
        return map.computeIfAbsent(prof, k -> new ProfEntry());
    }

    public int level(String uuid, Profession prof) {
        Map<Profession, ProfEntry> map = players.get(uuid);
        if (map == null) return 1;
        ProfEntry e = map.get(prof);
        return e == null ? 1 : e.level;
    }

    public Map<Profession, ProfEntry> all(String uuid) {
        return players.getOrDefault(uuid, new EnumMap<>(Profession.class));
    }

    public String nameOf(String uuid) { return names.get(uuid); }

    public int maxLevel() {
        return plugin.getConfig().getInt("max-level", 10);
    }

    /** Геометрическая прогрессия: 8, 16, 32, 64, ... */
    public int xpToNext(int level) {
        int base = plugin.getConfig().getInt("xp-base", 8);
        int mult = plugin.getConfig().getInt("xp-multiplier", 2);
        return (int) Math.round(base * Math.pow(mult, level - 1));
    }

    /** Начислить XP; возвращает список достигнутых уровней (для сообщения). */
    public List<Integer> addXp(String uuid, String name, Profession prof, int amount) {
        List<Integer> ups = new ArrayList<>();
        ProfEntry e = entry(uuid, name, prof);
        if (e.level >= maxLevel()) return ups;
        e.xp += amount;
        while (e.level < maxLevel() && e.xp >= xpToNext(e.level)) {
            e.xp -= xpToNext(e.level);
            e.level++;
            ups.add(e.level);
        }
        if (e.level >= maxLevel()) e.xp = 0;
        dirty = true;
        return ups;
    }

    public void setLevel(String uuid, String name, Profession prof, int level) {
        ProfEntry e = entry(uuid, name, prof);
        e.level = Math.max(1, Math.min(maxLevel(), level));
        e.xp = 0;
        dirty = true;
    }
}
