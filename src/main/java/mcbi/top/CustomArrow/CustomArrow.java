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
    private final String normalizedId;
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
        this.normalizedId = id.toLowerCase();
        this.plugin = plugin;

        if (plugin != null) {
            plugin.getLogger().info("Loading custom arrow: " + id);
        }

        Material mat = Material.ARROW;
        try {
            String materialStr = config.getString("material", "ARROW");
            if (materialStr != null && !materialStr.isEmpty()) {
                mat = Material.valueOf(materialStr.toUpperCase());
            }
        } catch (IllegalArgumentException e) {
            if (plugin != null) {
                plugin.getLogger().warning("Error material type: " + config.getString("material") + " for arrow " + id);
            }
        }
        this.material = mat;

        this.displayName = ChatColor.translateAlternateColorCodes('&',
                config.getString("display-name", "CustomArrow"));
        this.customModelData = config.getInt("custom-model-data", 0);
        this.extraDamage = config.getDouble("extra-damage", 0.0);

        this.particle = null;
        this.particleCount = 0;

        if (config.contains("particle")) {
            String particleConfig = config.getString("particle");
            if (particleConfig != null && !particleConfig.isEmpty()) {
                try {
                    this.particle = Particle.valueOf(particleConfig.toUpperCase().trim());
                    this.particleCount = Math.max(1, config.getInt("particle-count", 5));

                    if (plugin != null) {
                        plugin.getLogger().info("Arrow " + id + " - Particle: " + this.particle + ", Count: " + this.particleCount);
                    }
                } catch (IllegalArgumentException e) {
                    if (plugin != null) {
                        plugin.getLogger().warning("Error particle type: " + particleConfig + " for arrow " + id);
                    }
                }
            }
        }

        this.lore = new ArrayList<>();
        List<String> configLore = config.getStringList("lore");
        if (configLore != null) {
            for (String line : configLore) {
                if (line != null && !line.isEmpty()) {
                    this.lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
            }
        }

        this.enchantments = new HashMap<>();
        ConfigurationSection enchants = config.getConfigurationSection("enchantments");
        if (enchants != null) {
            for (String key : enchants.getKeys(false)) {
                try {
                    Enchantment enchant = Enchantment.getByName(key.toUpperCase());
                    if (enchant != null) {
                        int level = enchants.getInt(key, 1);
                        this.enchantments.put(enchant, level);
                    } else {
                        if (plugin != null) {
                            plugin.getLogger().warning("Error enchant type: " + key + " for arrow " + id);
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    if (plugin != null) {
                        plugin.getLogger().warning("Error enchant type: " + key + " for arrow " + id);
                    }
                }
            }
        }

        this.unbreakable = config.getBoolean("unbreakable", false);
        this.potionEffect = parsePotionEffect(config.getString("potion-effect"));

        this.itemFlags = new HashSet<>();
        List<String> flagsList = config.getStringList("item-flags");
        if (flagsList != null) {
            for (String flag : flagsList) {
                if (flag != null && !flag.isEmpty()) {
                    try {
                        this.itemFlags.add(ItemFlag.valueOf(flag.toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        if (plugin != null) {
                            plugin.getLogger().warning("Error ItemFlag type: " + flag + " for arrow " + id);
                        }
                    }
                }
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
        this.hidePotionEffects = config.getBoolean("hide-potion-effects", false);
        this.mythicSkills = config.getStringList("mythic-skills");

        if (!isValid()) {
            if (plugin != null) {
                plugin.getLogger().warning("Custom arrow " + id + " configuration is invalid!");
            }
        }
    }

    public boolean matchesId(String otherId) {
        return this.normalizedId.equals(otherId.toLowerCase());
    }

    public String getNormalizedId() {
        return normalizedId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        CustomArrow that = (CustomArrow) obj;
        return normalizedId.equals(that.normalizedId);
    }

    @Override
    public int hashCode() {
        return normalizedId.hashCode();
    }

    private PotionEffect parsePotionEffect(String effectStr) {
        if (effectStr == null || effectStr.isEmpty()) return null;
        try {
            String[] parts = effectStr.split(":");
            if (parts.length == 0) return null;

            PotionEffectType type = PotionEffectType.getByName(parts[0].toUpperCase());
            if (type == null) {
                if (plugin != null) {
                    plugin.getLogger().warning("Error potion type: " + parts[0] + " for arrow " + id);
                }
                return null;
            }

            int duration;
            int amplifier;
            if (parts.length == 1) {
                duration = 1600;
                amplifier = 0;
            } else if (parts.length == 2) {
                duration = Math.max(0, Integer.parseInt(parts[1])) * 20;
                amplifier = 0;
            } else {
                duration = Math.max(0, Integer.parseInt(parts[1])) * 20;
                amplifier = Math.max(0, Integer.parseInt(parts[2]));
            }

            if (plugin != null) {
                plugin.getLogger().info("Arrow " + id + " - Potion effect: " + type.getName() +
                        ", Duration: " + duration + " ticks (" + (duration/20) + " seconds)" +
                        ", Amplifier: " + amplifier);
            }

            return new PotionEffect(type, duration, amplifier);
        } catch (Exception e) {
            if (plugin != null) {
                plugin.getLogger().warning("Error parsing potion effect: " + effectStr + " for arrow " + id + " - " + e.getMessage());
            }
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
        if (this.lore != null) {
            finalLore.addAll(this.lore);
        }
        meta.setLore(finalLore);

        if (this.enchantments != null) {
            this.enchantments.forEach((ench, level) -> {
                if (ench != null) {
                    meta.addEnchant(ench, level, true);
                }
            });
        }

        if (unbreakable) {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }

        if (this.itemFlags != null && !this.itemFlags.isEmpty()) {
            meta.addItemFlags(this.itemFlags.toArray(new ItemFlag[0]));
        }

        if (hideAttributes) meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (hideEnchants) meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        if (meta instanceof PotionMeta potionMeta) {
            if (potionEffect != null) {
                potionMeta.addCustomEffect(potionEffect, true);
            }
            if (hidePotionEffects) {
                potionMeta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
            } else {
                potionMeta.removeItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
            }
        } else if (potionEffect != null) {
            if (plugin != null) {
                plugin.getLogger().warning("CustomArrow " + id + " has potion effect but material is not a potion type: " + material);
            }
        }

        item.setItemMeta(meta);
        return item;
    }

    public boolean isValid() {
        boolean valid = this.id != null && !this.id.isEmpty() &&
                this.material != null &&
                this.displayName != null && !this.displayName.isEmpty() &&
                this.extraDamage >= 0 &&
                this.lore != null;

        if (!valid && plugin != null) {
            plugin.getLogger().warning("Validation failed for arrow: " + id);
        }

        return valid;
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
    public double getExtraDamage() { return extraDamage; }
    public void setDamageMultiplier(double damageMultiplier) { this.extraDamage = damageMultiplier; }
    public boolean hasParticle() { return particle != null && particleCount > 0; }
}