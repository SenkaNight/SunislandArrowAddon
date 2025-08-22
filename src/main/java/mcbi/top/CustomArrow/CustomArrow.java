package mcbi.top.CustomArrow;

import mcbi.top.MessageService;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CustomArrow {
    private final String id;
    private final Material material;
    private final String displayName;
    private int customModelData;
    private double extraDamage;
    private Particle particle;
    private int particleCount;
    private final List<String> lore;
    private final Map<Enchantment, Integer> enchantments;
    private final boolean unbreakable;
    private final PotionEffect potionEffect;
    private final Set<ItemFlag> itemFlags;
    private final Map<String, Object> nbtData;
    private final boolean hideAttributes;
    private final boolean hideEnchants;
    private final boolean hidePotionEffects;
    private JavaPlugin plugin;
    private final List<String> mythicSkills;
    public CustomArrow(String id, ConfigurationSection config) {
        this.id = id;
        this.plugin = plugin;
        Material mat = Material.ARROW;
        try {
            mat = Material.valueOf(config.getString("material", "ARROW").toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Error material type: " + config.getString("material"));
        }
        this.material = mat;
        this.displayName = ChatColor.translateAlternateColorCodes('&',
                config.getString("display-name", "CustomArrow"));
        this.customModelData = config.getInt("custom-model-data", 0);
        this.extraDamage = config.getDouble("extra-damage", 0.0);
        Particle part = Particle.CRIT;
        try {
            part = Particle.valueOf(config.getString("particle", "CRIT").toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Error particle type: " + config.getString("particle"));
        }
        this.particle = part;
        this.particleCount = Math.max(1, config.getInt("particle-count", 3));
        this.lore = new ArrayList<>();
        for (String line : config.getStringList("lore")) {
            if (line != null && !line.isEmpty()) {
                this.lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
        }
        this.enchantments = new HashMap<>();
        ConfigurationSection enchants = config.getConfigurationSection("enchantments");
        if (enchants != null) {
            for (String key : enchants.getKeys(false)) {
                try {
                    Enchantment enchant = Enchantment.getByName(key.toUpperCase());
                    if (enchant != null) {
                        this.enchantments.put(enchant, enchants.getInt(key));
                    }
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Error enchant type: " + key);
                }
            }
        }
        this.unbreakable = config.getBoolean("unbreakable", false);
        this.potionEffect = parsePotionEffect(config.getString("potion-effect"));
        this.itemFlags = new HashSet<>();
        for (String flag : config.getStringList("item-flags")) {
            try {
                this.itemFlags.add(ItemFlag.valueOf(flag.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Error ItemFlag type: " + flag);
            }
        }
        this.nbtData = new HashMap<>();
        ConfigurationSection nbtSection = config.getConfigurationSection("nbt-data");
        if (nbtSection != null) {
            for (String key : nbtSection.getKeys(false)) {
                this.nbtData.put(key, nbtSection.get(key));
            }
        }
        this.hideAttributes = config.getBoolean("hide-attributes", false);
        this.hideEnchants = config.getBoolean("hide-enchants", false);
        this.hidePotionEffects = config.getBoolean("hide-potion-effects", true);
        this.mythicSkills = config.getStringList("mythic-skills");
    }

    private PotionEffect parsePotionEffect(String effectStr) {
        if (effectStr == null || effectStr.isEmpty()) return null;
        try {
            String[] parts = effectStr.split(":");
            if (parts.length == 0) return null;
            PotionEffectType type = PotionEffectType.getByName(parts[0].toUpperCase());
            if (type == null) {
                plugin.getLogger().warning("Error potion type: " + parts[0]);
                return null;
            }
            int duration = parts.length > 1 ? Integer.parseInt(parts[1]) : 200;
            int amplifier = parts.length > 2 ? Math.max(0, Integer.parseInt(parts[2]) - 1) : 0;
            return new PotionEffect(type, duration, amplifier);
        } catch (Exception e) {
            return null;
        }
    }

    public ItemStack createItemStack(int amount) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(displayName);
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        List<String> finalLore = new ArrayList<>();
        finalLore.add(MessageService.get().get("arrow.lore.type"));
        finalLore.add("§7 ");
        if (extraDamage > 0) {
            finalLore.add(String.format(MessageService.get().get("arrow.lore.damage"), extraDamage));
            finalLore.add("§7 ");
        }
        finalLore.addAll(lore);
        meta.setLore(finalLore);
        enchantments.forEach((ench, level) -> {
            meta.addEnchant(ench, level, true);
        });
        if (unbreakable) {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }
        if (!itemFlags.isEmpty()) {
            meta.addItemFlags(itemFlags.toArray(new ItemFlag[0]));
        }
        if (hideAttributes) meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (hideEnchants) meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        if (hidePotionEffects) meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
        if (meta instanceof PotionMeta potionMeta) {
            if (potionEffect != null) {
                potionMeta.addCustomEffect(potionEffect, true);
            }
            if (hidePotionEffects) {
                potionMeta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    public boolean isValid() {
        return this.id != null && !this.id.isEmpty() &&
                this.material != null &&
                this.displayName != null && !this.displayName.isEmpty() &&
                this.extraDamage >= 0 &&
                this.particle != null &&
                this.particleCount > 0 &&
                this.lore != null;
    }

    public String getId() { return id; }
    public Material getMaterial() { return material; }
    public String getDisplayName() { return displayName; }
    public int getCustomModelData() { return customModelData; }
    public Particle getParticle() { return particle; }
    public int getParticleCount() { return particleCount; }
    public List<String> getLore() { return lore; }
    public Map<Enchantment, Integer> getEnchantments() { return enchantments; }
    public boolean isUnbreakable() { return unbreakable; }
    public PotionEffect getPotionEffect() { return potionEffect; }
    public Set<ItemFlag> getItemFlags() { return itemFlags; }
    public List<String> getMythicSkills() { return mythicSkills; }
    public void setCustomModelData(int customModelData) { this.customModelData = customModelData; }
    public void setParticle(Particle particle) { this.particle = particle; }
    public void setParticleCount(int particleCount) { this.particleCount = particleCount; }
    public double getExtraDamage() {
        return extraDamage;
    }
    public void setDamageMultiplier(double damageMultiplier) {
        this.extraDamage = damageMultiplier;
    }
}