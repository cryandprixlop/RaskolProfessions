package ru.raskol.professions;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.raskol.professions.command.ProfCommand;
import ru.raskol.professions.listener.ProfActionListener;
import ru.raskol.professions.store.ProfStore;

public final class RaskolProfessions extends JavaPlugin {

    private ProfStore store;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new ProfStore(this);
        store.load();

        PluginCommand cmd = getCommand("prof");
        if (cmd != null) {
            ProfCommand executor = new ProfCommand(this, store);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(
                new ProfActionListener(this, store), this);

        // Автосейв раз в 5 минут
        getServer().getScheduler().runTaskTimer(this, () -> store.saveIfDirty(), 200L, 20L * 300);

        getLogger().info("RaskolProfessions v" + getDescription().getVersion()
                + " включён. Игроков в базе: " + store.playerCount()
                + ". Макс. уровень: " + store.maxLevel()
                + ". Прогрессия: " + store.xpToNext(1) + "/" + store.xpToNext(2) + "/" + store.xpToNext(3) + "...");
    }

    @Override
    public void onDisable() {
        if (store != null) store.save();
        getLogger().info("RaskolProfessions выключен.");
    }

    public ProfStore getStore() { return store; }
}
