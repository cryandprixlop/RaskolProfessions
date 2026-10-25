package ru.raskol.professions.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.raskol.professions.RaskolProfessions;
import ru.raskol.professions.model.ProfEntry;
import ru.raskol.professions.model.Profession;
import ru.raskol.professions.store.ProfStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ProfCommand implements CommandExecutor, TabCompleter {

    private final RaskolProfessions plugin;
    private final ProfStore store;

    public ProfCommand(RaskolProfessions plugin, ProfStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }
        switch (args[0].toLowerCase()) {
            case "info" -> handleInfo(sender, args);
            case "list" -> handleList(sender);
            case "xp" -> handleXp(sender, args);
            case "set" -> handleSet(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== RaskolProfessions ===");
        sender.sendMessage("§e/prof info [игрок] §7— прогресс профессий");
        sender.sendMessage("§e/prof list §7— список профессий");
        sender.sendMessage("§e/prof xp <игрок> <prof> <кол-во> §7— начислить опыт");
        sender.sendMessage("§e/prof set <игрок> <prof> <уровень> §7— выставить уровень");
        sender.sendMessage("§e/prof reload §7— перезагрузить конфиг");
        sender.sendMessage("§7Профессии: FISHING, FARMING, LUMBER, MINING");
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("prof.admin")) return true;
        sender.sendMessage("§cНет прав.");
        return false;
    }

    private void handleInfo(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = plugin.getServer().getPlayer(args[1]);
            if (target == null) { sender.sendMessage("§cИгрок не найден."); return; }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage("§c/prof info <игрок>");
            return;
        }
        String uuid = target.getUniqueId().toString();
        sender.sendMessage("§6=== Профессии " + target.getName() + " ===");
        Map<Profession, ProfEntry> all = store.all(uuid);
        for (Profession prof : Profession.values()) {
            ProfEntry e = all.get(prof);
            int level = e == null ? 1 : e.level;
            int xp = e == null ? 0 : e.xp;
            String progress = level >= store.maxLevel()
                    ? "§7MAX"
                    : "§7" + xp + "/" + store.xpToNext(level) + " XP";
            sender.sendMessage(prof.color + prof.displayName + " §7ур. §e" + level
                    + " " + progress + " §7удвоение: §e" + level + "%");
        }
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage("§6=== Профессии ===");
        for (Profession prof : Profession.values()) {
            sender.sendMessage(prof.color + prof.displayName + " §7(" + prof.name() + ")");
        }
        sender.sendMessage("§7В разработке: Зачарователь, Кузнец");
    }

    private void handleXp(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 4) { sender.sendMessage("§c/prof xp <игрок> <prof> <кол-во>"); return; }
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { sender.sendMessage("§cИгрок не найден."); return; }
        Profession prof = Profession.fromName(args[2]);
        if (prof == null) { sender.sendMessage("§cНеизвестная профессия."); return; }
        int amount;
        try { amount = Integer.parseInt(args[3]); }
        catch (NumberFormatException e) { sender.sendMessage("§cКоличество — число."); return; }
        var ups = store.addXp(target.getUniqueId().toString(), target.getName(), prof, amount);
        sender.sendMessage("§aНачислено " + amount + " XP (" + prof.displayName + ") игроку " + target.getName());
        for (int lv : ups) {
            target.sendMessage("§6⬆ Профессия " + prof.color + prof.displayName
                    + " §6: уровень §e" + lv + "§6! Шанс удвоения: §e" + lv + "%");
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 4) { sender.sendMessage("§c/prof set <игрок> <prof> <уровень>"); return; }
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { sender.sendMessage("§cИгрок не найден."); return; }
        Profession prof = Profession.fromName(args[2]);
        if (prof == null) { sender.sendMessage("§cНеизвестная профессия."); return; }
        int level;
        try { level = Integer.parseInt(args[3]); }
        catch (NumberFormatException e) { sender.sendMessage("§cУровень — число."); return; }
        store.setLevel(target.getUniqueId().toString(), target.getName(), prof, level);
        sender.sendMessage("§aУровень " + prof.displayName + " для " + target.getName()
                + " установлен: " + Math.max(1, Math.min(store.maxLevel(), level)));
    }

    private void handleReload(CommandSender sender) {
        if (!admin(sender)) return;
        plugin.reloadConfig();
        sender.sendMessage("§aКонфиг RaskolProfessions перезагружен.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("info", "list", "xp", "set", "reload"), args[0]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("xp") || args[0].equalsIgnoreCase("set"))) {
            return filter(Arrays.asList("FISHING", "FARMING", "LUMBER", "MINING"), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : list) if (s.toLowerCase().startsWith(lower)) out.add(s);
        return out;
    }
}
