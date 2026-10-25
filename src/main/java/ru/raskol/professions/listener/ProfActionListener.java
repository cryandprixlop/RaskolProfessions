package ru.raskol.professions.listener;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import ru.raskol.professions.RaskolProfessions;
import ru.raskol.professions.model.Profession;
import ru.raskol.professions.store.ProfStore;

import java.util.Collection;
import java.util.List;

/**
 * Начисление опыта профессии за действие + шанс удвоения ресурса (level * N%).
 * Отменённые события (лицензионные гейты) XP не дают.
 */
public final class ProfActionListener implements Listener {

    private static final List<String> DEF_LOGS = List.of(
            "OAK_LOG", "SPRUCE_LOG", "BIRCH_LOG", "JUNGLE_LOG",
            "ACACIA_LOG", "DARK_OAK_LOG", "MANGROVE_LOG", "CHERRY_LOG");
    private static final List<String> DEF_ORES = List.of(
            "COAL_ORE", "DEEPSLATE_COAL_ORE", "IRON_ORE", "DEEPSLATE_IRON_ORE",
            "COPPER_ORE", "DEEPSLATE_COPPER_ORE", "GOLD_ORE", "DEEPSLATE_GOLD_ORE",
            "REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE", "LAPIS_ORE", "DEEPSLATE_LAPIS_ORE",
            "DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE", "EMERALD_ORE", "DEEPSLATE_EMERALD_ORE",
            "NETHER_GOLD_ORE", "NETHER_QUARTZ_ORE");
    private static final List<String> DEF_CROPS = List.of(
            "WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART", "COCOA",
            "MELON", "PUMPKIN", "SUGAR_CANE", "CACTUS", "BAMBOO", "SWEET_BERRY_BUSH");
    private static final Material[] FISHES = {
            Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH};

    private final RaskolProfessions plugin;
    private final ProfStore store;

    public ProfActionListener(RaskolProfessions plugin, ProfStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    /* ========== Блоки: лес / руда / урожай ========== */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        String mat = event.getBlock().getType().name();
        Profession prof;
        if (list("logs", DEF_LOGS).contains(mat)) prof = Profession.LUMBER;
        else if (list("ores", DEF_ORES).contains(mat)) prof = Profession.MINING;
        else if (list("crops", DEF_CROPS).contains(mat)) prof = Profession.FARMING;
        else return;

        Player player = event.getPlayer();
        grant(player, prof);

        int level = store.level(player.getUniqueId().toString(), prof);
        double chance = level * plugin.getConfig().getDouble("double-chance-per-level", 1.0);
        if (Math.random() * 100.0 < chance) {
            ItemStack tool = player.getInventory().getItemInMainHand();
            Collection<ItemStack> drops = event.getBlock().getDrops(tool);
            if (drops.isEmpty()) {
                event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(),
                        new ItemStack(event.getBlock().getType()));
            } else {
                for (ItemStack d : drops) {
                    event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), d.clone());
                }
            }
            player.sendMessage(prof.color + "✦ Удвоение (" + prof.displayName
                    + " ур. " + level + "): +дополнительный ресурс!");
        }
    }

    /* ========== Рыбалка ========== */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player player = event.getPlayer();

        // Если RaskolFishing установлен — уважаем его лицензию
        Boolean licensed = fishingLicenseValid(player);
        if (licensed != null && !licensed) return;

        grant(player, Profession.FISHING);

        int level = store.level(player.getUniqueId().toString(), Profession.FISHING);
        double chance = level * plugin.getConfig().getDouble("double-chance-per-level", 1.0);
        if (Math.random() * 100.0 < chance) {
            ItemStack extra = null;
            if (event.getCaught() instanceof Item item && !item.isDead()) {
                extra = item.getItemStack().clone();
                extra.setAmount(1);
            }
            if (extra == null) extra = new ItemStack(FISHES[(int) (Math.random() * FISHES.length)], 1);
            player.getInventory().addItem(extra);
            player.sendMessage(Profession.FISHING.color + "✦ Удвоение (Рыбак ур. "
                    + level + "): +дополнительная рыба!");
        }
    }

    /* ========== Вспомогательные ========== */

    private void grant(Player player, Profession prof) {
        int amount = plugin.getConfig().getInt("xp." + prof.name().toLowerCase(), defaultXp(prof));
        var ups = store.addXp(player.getUniqueId().toString(), player.getName(), prof, amount);
        for (int lv : ups) {
            player.sendMessage("§6⬆ Профессия " + prof.color + prof.displayName
                    + " §6: уровень §e" + lv + "§6! Шанс удвоения: §e" + lv + "%");
            plugin.getLogger().info("[Prof] " + player.getName() + " -> "
                    + prof.name() + " level " + lv);
        }
    }

    private int defaultXp(Profession prof) {
        return switch (prof) {
            case LUMBER -> 1;
            default -> 2;
        };
    }

    private List<String> list(String key, List<String> def) {
        List<String> cfg = plugin.getConfig().getStringList(key);
        return cfg.isEmpty() ? def : cfg;
    }

    /** null = RaskolFishing не установлен (лицензия не нужна). */
    private Boolean fishingLicenseValid(Player player) {
        try {
            Object fp = Bukkit.getPluginManager().getPlugin("RaskolFishing");
            if (fp == null || !plugin.getServer().getPluginManager().isPluginEnabled("RaskolFishing")) return null;
            Object svc = fp.getClass().getMethod("getLicenseService").invoke(fp);
            return (Boolean) svc.getClass()
                    .getMethod("hasValidLicense", Player.class).invoke(svc, player);
        } catch (Throwable t) {
            return null;
        }
    }
}
