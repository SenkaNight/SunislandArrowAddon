package mcbi.top;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MessageService {
    private static MessageService instance;
    private final JavaPlugin plugin;
    private YamlConfiguration messages;
    private final Map<String, String> messageCache = new ConcurrentHashMap<>();

    private MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        reloadMessages();
    }

    public static void init(JavaPlugin plugin) {
        if (instance == null) {
            instance = new MessageService(plugin);
        }
    }

    public static MessageService get() {
        if (instance == null) {
            throw new IllegalStateException("MessageService not initialized!");
        }
        return instance;
    }

    public static void reloadMessages1() {
        if (instance == null) {
            throw new IllegalStateException("MessageService not initialized!");
        }
        instance.reloadMessages();
    }

    public void reloadMessages() {
        File messageFile = new File(plugin.getDataFolder(), "messages.yml");

        if (!messageFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messageFile);
        messageCache.clear();
        try (Reader defaultConfig = new InputStreamReader(
                plugin.getResource("messages.yml"), StandardCharsets.UTF_8)) {
            YamlConfiguration defaultMessages = YamlConfiguration.loadConfiguration(defaultConfig);
            messages.setDefaults(defaultMessages);
            messages.options().copyDefaults(true);
            messages.save(messageFile);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load messages: " + e.getMessage());
        }
    }

    public String get(String key) {
        return get(key, Collections.emptyMap());
    }

    public String get(String key, Map<String, String> placeholders) {
        String message = messageCache.computeIfAbsent(key, k -> {
            String msg = messages.getString(k);
            if (msg == null) {
                plugin.getLogger().warning("Missing message key: " + k);
                return null;
            }
            // 检查是否为空字符串，如果是则返回null
            if (msg.trim().isEmpty()) {
                return null;
            }
            String trimmedMsg = msg.trim();
            return ChatColor.translateAlternateColorCodes('&', trimmedMsg);
        });
        if (message == null) {
            return null;
        }
        return replacePlaceholders(message, placeholders);
    }

    public String getFormatted(String key, Object... args) {
        String message = get(key);
        if (message == null) {
            return null;
        }
        return String.format(message, args);
    }

    public String getFormatted(String key, Map<String, String> placeholders, Object... args) {
        String message = get(key, placeholders);
        if (message == null) {
            return null;
        }
        return String.format(message, args);
    }

    private String replacePlaceholders(String text, Map<String, String> placeholders) {
        if (text == null || placeholders == null || placeholders.isEmpty()) {
            return text;
        }
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    public Map<String, String> getBulk(String prefix) {
        Map<String, String> result = new HashMap<>();
        for (String key : messages.getKeys(true)) {
            if (key.startsWith(prefix)) {
                String message = get(key);
                if (message != null) {
                    result.put(key.substring(prefix.length()), message);
                }
            }
        }
        return result;
    }
}