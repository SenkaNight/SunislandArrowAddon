package mcbi.top.CustomArrow;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class CustomArrowManager {
    private final JavaPlugin plugin;
    private final Map<String, CustomArrow> arrows = new HashMap<>();
    private final Map<Integer, String> customModelDataToIdMap = new HashMap<>();

    public CustomArrowManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadArrows() {
        arrows.clear();
        customModelDataToIdMap.clear();
        File configFile = new File(plugin.getDataFolder(), "arrows.yml");
        if (!configFile.exists()) {
            plugin.getLogger().info("Create config...");
            plugin.saveResource("arrows.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
//        plugin.getLogger().info("正在加载配置文件: " + configFile.getAbsolutePath());
//        plugin.getLogger().info("配置文件内容: " + config.saveToString());
        Set<String> keys = config.getKeys(false);
        if (keys.isEmpty()) {
            plugin.getLogger().severe("Cannot find arrow config!");
            return;
        }

        for (String key : keys) {
            try {
                ConfigurationSection section = config.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }

                CustomArrow arrow = new CustomArrow(key, section);
                if (arrow.isValid()) {
                    arrows.put(key, arrow);
                    if (arrow.getCustomModelData() != 0) {
                        customModelDataToIdMap.put(arrow.getCustomModelData(), key);
                    }
                } else {
                    plugin.getLogger().warning("Arrow config " + key + " error, pass");
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Arrow config " + key + " errow:", e);
            }
        }
        if (arrows.isEmpty()) {
            plugin.getLogger().severe("No Arrow loaded!");
        } else {
            plugin.getLogger().info("Loaded Arrow: " + arrows.keySet());
        }
    }
    public Set<String> getArrowIds() {
        return arrows.keySet();
    }
    public String getArrowId(ItemStack arrow) {
        if (arrow == null || arrow.getType() == Material.AIR) return null;

        ItemMeta meta = arrow.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return null;

        int customModelData = meta.getCustomModelData();
        return customModelDataToIdMap.getOrDefault(customModelData, null);
    }
    public void reload() {
        loadArrows();
    }
    public CustomArrow getArrow(String id) {
        return arrows.get(id);
    }
    public boolean isCustomArrow(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        if (!(item.getType() == Material.ARROW || item.getType() == Material.TIPPED_ARROW || item.getType() == Material.SPECTRAL_ARROW)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasCustomModelData() &&
                customModelDataToIdMap.containsKey(meta.getCustomModelData());
    }
}