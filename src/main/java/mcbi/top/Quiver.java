package mcbi.top;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.Color;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Quiver implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey quiverIdKey;
    private final NamespacedKey ownerIdKey;
    private final File dataFolder;
    private final Map<Integer, QuiverData> quiverDataCache = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> openQuivers = new ConcurrentHashMap<>();
    private final Set<Material> allowedItems = new HashSet<>(Arrays.asList(
            Material.ARROW,
            Material.SPECTRAL_ARROW,
            Material.TIPPED_ARROW,
            Material.FIREWORK_ROCKET
    ));
    private final Set<UUID> usingQuiver = new HashSet<>();
    private final Set<UUID> cooldownPlayers = new HashSet<>();
    private int nextQuiverId = 1;

    public Quiver(JavaPlugin plugin) {
        this.plugin = plugin;
        this.quiverIdKey = new NamespacedKey(plugin, "quiver_id");
        this.ownerIdKey = new NamespacedKey(plugin, "owner_id");
        this.dataFolder = new File(plugin.getDataFolder(), "quivers");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        loadNextQuiverId();
        startAutoSaveTask();
        registerCommands();
    }

    private void loadNextQuiverId() {
        File idFile = new File(dataFolder, "next_id.txt");
        if (idFile.exists()) {
            try {
                Scanner scanner = new Scanner(idFile);
                if (scanner.hasNextInt()) {
                    nextQuiverId = scanner.nextInt();
                }
                scanner.close();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to load next quiver ID: " + e.getMessage());
            }
        }
    }

    private void saveNextQuiverId() {
        File idFile = new File(dataFolder, "next_id.txt");
        try {
            java.io.FileWriter writer = new java.io.FileWriter(idFile);
            writer.write(String.valueOf(nextQuiverId));
            writer.close();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save next quiver ID: " + e.getMessage());
        }
    }

    private void registerCommands() {
        PluginCommand command = plugin.getCommand("givequiver");
        if (command != null) {
            command.setExecutor(this::onGiveQuiverCommand);
            command.setTabCompleter(this::onGiveQuiverTabComplete);
        }
    }

    private boolean onGiveQuiverCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                giveQuiverToPlayer(player, player);
                return true;
            }
            return false;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(MessageService.get().get("quiver.command.notplayer"));
            return false;
        }
        if (args.length > 1) {
            try {
                int quiverId = Integer.parseInt(args[1]);
                giveQuiverWithId(target, quiverId, sender);
            } catch (NumberFormatException e) {
                String playerName = args[1];
                Player quiverOwner = Bukkit.getPlayer(playerName);
                if (quiverOwner != null) {
                    giveBoundQuiverToPlayer(sender, target, quiverOwner);
                } else {
                    sender.sendMessage(MessageService.get().get("quiver.command.notplayer"));
                    return false;
                }
            }
        } else {
            giveBoundQuiverToPlayer(sender, target, target);
        }
        return true;
    }

    private List<String> onGiveQuiverTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partialName = args[0].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partialName)) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 2) {
            String partialId = args[1].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partialId)) {
                    completions.add(player.getName());
                }
            }
            File[] quiverFiles = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (quiverFiles != null) {
                for (File file : quiverFiles) {
                    String fileName = file.getName();
                    if (fileName.endsWith(".yml")) {
                        String idStr = fileName.substring(0, fileName.length() - 4);
                        int id = Integer.parseInt(idStr);
                        if (String.valueOf(id).startsWith(partialId)) {
                            completions.add(String.valueOf(id));
                        }
                    }
                }
            }
            if (completions.isEmpty()) {
                completions.add("<quiver_id_or_player>");
            }
        }
        return completions;
    }

    private void giveQuiverToPlayer(CommandSender sender, Player target) {
        ItemStack quiver = createQuiverItem(target);
        HashMap<Integer, ItemStack> leftover = target.getInventory().addItem(quiver);
        if (!leftover.isEmpty()) {
            target.getWorld().dropItem(target.getLocation(), leftover.get(0));
        }
        if (sender != target) {
            sender.sendMessage(MessageService.get().get("quiver.message.give"));
        }
        target.sendMessage(MessageService.get().get("quiver.message.get"));
    }

    public void giveBoundQuiverToPlayer(CommandSender sender, Player target, Player owner) {
        Integer boundQuiverId = findBoundQuiverId(owner.getUniqueId());

        if (boundQuiverId != null) {
            giveQuiverWithId(target, boundQuiverId, sender);
        } else {
            ItemStack quiver = createQuiverItem(owner);
            HashMap<Integer, ItemStack> leftover = target.getInventory().addItem(quiver);
            if (!leftover.isEmpty()) {
                target.getWorld().dropItem(target.getLocation(), leftover.get(0));
            }
            if (sender != target) {
                sender.sendMessage(MessageService.get().get("quiver.message.give"));
            }
            target.sendMessage(MessageService.get().get("quiver.message.get"));
        }
    }

    public void giveQuiverWithId(Player target, int quiverId, CommandSender sender) {
        File dataFile = new File(dataFolder, quiverId + ".yml");
        if (!dataFile.exists()) {
            if (sender instanceof Player) {
                sender.sendMessage("§cQuiver with ID " + quiverId + " does not exist!");
            } else {
                plugin.getLogger().warning("Quiver with ID " + quiverId + " does not exist!");
            }
            return;
        }
        ItemStack quiver = new ItemStack(Material.BUCKET);
        ItemMeta meta = quiver.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageService.get().get("quiver.name"));
            meta.setCustomModelData(1001);
            List<String> lore = new ArrayList<>();
            lore.add(MessageService.get().get("quiver.lore.type"));
            lore.add("§7");
            lore.add(MessageService.get().get("quiver.lore.lore1"));
            lore.add(MessageService.get().get("quiver.lore.lore2"));
            lore.add("§7");
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(quiverIdKey, PersistentDataType.INTEGER, quiverId);
            YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
            if (config.contains("owner")) {
                String ownerUuidStr = config.getString("owner");
                try {
                    UUID ownerUuid = UUID.fromString(ownerUuidStr);
                    meta.getPersistentDataContainer().set(ownerIdKey, PersistentDataType.STRING, ownerUuid.toString());
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid owner UUID in quiver data: " + quiverId);
                }
            }
            quiver.setItemMeta(meta);
            HashMap<Integer, ItemStack> leftover = target.getInventory().addItem(quiver);
            if (!leftover.isEmpty()) {
                target.getWorld().dropItem(target.getLocation(), leftover.get(0));
            }
            if (sender != target) {
                sender.sendMessage(MessageService.get().get("quiver.message.give"));
                plugin.getLogger().info("Give " + target.getName() + " Quiver, ID: " + quiverId);
            }
            target.sendMessage(MessageService.get().get("quiver.message.get"));
            getQuiverData(quiverId);
        } else {
            plugin.getLogger().info("Create quiver fail!");
        }
    }

    private Integer findBoundQuiverId(UUID playerUuid) {
        File[] quiverFiles = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (quiverFiles != null) {
            for (File file : quiverFiles) {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                if (config.contains("owner") && playerUuid.toString().equals(config.getString("owner"))) {
                    String fileName = file.getName();
                    return Integer.parseInt(fileName.substring(0, fileName.length() - 4));
                }
            }
        }
        return null;
    }

    private void startAutoSaveTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                saveAllQuiverData();
            }
        }.runTaskTimer(plugin, 20 * 60 * 15, 20 * 60 * 15);
    }

    public void reloadData() {
        saveAllQuiverData();
        quiverDataCache.clear();
        loadNextQuiverId();
        plugin.getLogger().info("SunislandArrowAddon reloaded!");
    }


    public ItemStack createQuiverItem(Player owner) {
        ItemStack quiver = new ItemStack(Material.BUCKET);
        ItemMeta meta = quiver.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageService.get().get("quiver.name"));
            meta.setCustomModelData(1001);
            List<String> lore = new ArrayList<>();
            lore.add(MessageService.get().get("quiver.lore.type"));
            lore.add("§7");
            lore.add(MessageService.get().get("quiver.lore.lore1"));
            lore.add(MessageService.get().get("quiver.lore.lore2"));
            lore.add("§7");
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            int quiverId = nextQuiverId++;
            meta.getPersistentDataContainer().set(quiverIdKey, PersistentDataType.INTEGER, quiverId);
            meta.getPersistentDataContainer().set(ownerIdKey, PersistentDataType.STRING, owner.getUniqueId().toString());
            QuiverData quiverData = new QuiverData();
            quiverData.owner = owner.getUniqueId();
            saveQuiverData(quiverId, quiverData);
            saveNextQuiverId();

            quiver.setItemMeta(meta);
        }
        return quiver;
    }

    public UUID getQuiverOwner(ItemStack quiver) {
        if (quiver == null) return null;
        ItemMeta meta = quiver.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String ownerUuidStr = pdc.get(ownerIdKey, PersistentDataType.STRING);
        if (ownerUuidStr != null) {
            try {
                return UUID.fromString(ownerUuidStr);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    public boolean isQuiverItem(ItemStack item) {
        if (item == null || item.getType() != Material.BUCKET) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(quiverIdKey, PersistentDataType.INTEGER);
    }

    public Integer getQuiverId(ItemStack quiver) {
        if (quiver == null) return null;
        ItemMeta meta = quiver.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(quiverIdKey, PersistentDataType.INTEGER);
    }

    public static class QuiverData {
        public ItemStack[] arrows = new ItemStack[9];
        public int prioritySlot = 0;
        public long lastModified = System.currentTimeMillis();
        public UUID owner;

        public ItemStack getPriorityArrow() {
            if (prioritySlot < arrows.length && isValidArrow(arrows[prioritySlot])) {
                return arrows[prioritySlot].clone();
            }
            for (ItemStack arrow : arrows) {
                if (isValidArrow(arrow)) {
                    return arrow.clone();
                }
            }
            return null;
        }

        public boolean consumeArrow() {
            if (prioritySlot < arrows.length && consumeArrowAtSlot(prioritySlot)) {
                return true;
            }
            for (int i = 0; i < arrows.length; i++) {
                if (i != prioritySlot && consumeArrowAtSlot(i)) {
                    return true;
                }
            }
            return false;
        }

        private boolean consumeArrowAtSlot(int slot) {
            if (slot < 0 || slot >= arrows.length) return false;
            ItemStack arrow = arrows[slot];
            if (isValidArrow(arrow)) {
                arrow.setAmount(arrow.getAmount() - 1);
                if (arrow.getAmount() <= 0) {
                    arrows[slot] = null;
                }
                lastModified = System.currentTimeMillis();
                return true;
            }
            return false;
        }

        private static boolean isValidArrow(ItemStack item) {
            return item != null && item.getAmount() > 0 && (
                    item.getType() == Material.ARROW ||
                            item.getType() == Material.SPECTRAL_ARROW ||
                            item.getType() == Material.TIPPED_ARROW ||
                            item.getType() == Material.FIREWORK_ROCKET
            );
        }
    }

    public QuiverData getQuiverData(int quiverId) {
        return quiverDataCache.computeIfAbsent(quiverId, this::loadQuiverData);
    }

    public void saveQuiverData(int quiverId, QuiverData data) {
        quiverDataCache.put(quiverId, data);
        saveQuiverDataToFile(quiverId, data);
    }

    private QuiverData loadQuiverData(int quiverId) {
        File file = new File(dataFolder, quiverId + ".yml");
        if (!file.exists()) return new QuiverData();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        QuiverData data = new QuiverData();
        for (int i = 0; i < 9; i++) {
            if (config.contains("arrows." + i)) {
                data.arrows[i] = config.getItemStack("arrows." + i);
            }
        }
        data.prioritySlot = config.getInt("prioritySlot", 0);
        data.lastModified = config.getLong("lastModified", System.currentTimeMillis());
        if (config.contains("owner")) {
            String ownerUuidStr = config.getString("owner");
            try {
                data.owner = UUID.fromString(ownerUuidStr);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid owner UUID in quiver data: " + quiverId);
            }
        }
        return data;
    }

    private void saveQuiverDataToFile(int quiverId, QuiverData data) {
        File file = new File(dataFolder, quiverId + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        for (int i = 0; i < data.arrows.length; i++) {
            if (data.arrows[i] != null) {
                config.set("arrows." + i, data.arrows[i]);
            }
        }
        config.set("prioritySlot", data.prioritySlot);
        config.set("lastModified", data.lastModified);

        // 保存所有者信息
        if (data.owner != null) {
            config.set("owner", data.owner.toString());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Save Quiver data fail: " + quiverId);
            e.printStackTrace();
        }
    }

    public void saveAllQuiverData() {
        quiverDataCache.forEach(this::saveQuiverDataToFile);
        saveNextQuiverId();
        plugin.getLogger().info("Save all Quiver data!");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        ItemStack item = event.getItem();
        if (event.getHand() != EquipmentSlot.HAND ||
                (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        if (isQuiverItem(item)) {
            handleQuiverOpen(event, player, item);
            event.setCancelled(true);
            return;
        }
        if (item != null && (item.getType() == Material.BOW || item.getType() == Material.CROSSBOW)) {
            handleBowDraw(event, player, item);
        }
    }

    private void handleQuiverOpen(PlayerInteractEvent event, Player player, ItemStack quiverItem) {
        Integer quiverId = getQuiverId(quiverItem);
        if (quiverId == null) return;

        Inventory inv = Bukkit.createInventory(player, InventoryType.DISPENSER, (MessageService.get().get("quiver.guititle")));
        QuiverData data = getQuiverData(quiverId);
        player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_INSERT, 1.1f, 0.7f);

        for (int i = 0; i < Math.min(9, data.arrows.length); i++) {
            if (data.arrows[i] != null) {
                inv.setItem(i, data.arrows[i].clone());
            }
        }
        player.openInventory(inv);
        openQuivers.put(player.getUniqueId(), quiverItem);
    }

    private void handleBowDraw(PlayerInteractEvent event, Player player, ItemStack weapon) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (hasEnoughArrowsInInventory(player)) {
            return;
        }
        ItemStack quiverItem = findActiveQuiver(player);
        if (quiverItem == null) return;
        Integer quiverId = getQuiverId(quiverItem);
        if (quiverId == null) return;
        QuiverData data = getQuiverData(quiverId);
        ItemStack arrow = data.getPriorityArrow();
        if (weapon.getType() == Material.CROSSBOW && arrow != null && arrow.getType() == Material.FIREWORK_ROCKET) {
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (offhand != null && offhand.getType() != Material.AIR &&
                    offhand.getType() != Material.FIREWORK_ROCKET) {
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(offhand);
                if (!leftover.isEmpty()) {
                    player.sendMessage(MessageService.get().get("quiver.message.fullinv"));
                    event.setCancelled(true);
                    return;
                }
            }
            ItemStack singleFirework = arrow.clone();
            singleFirework.setAmount(1);
            player.getInventory().setItemInOffHand(singleFirework);
            data.consumeArrow();
            saveQuiverData(quiverId, data);
            player.updateInventory();
            return;
        }
        if (weapon.getType() == Material.BOW && arrow != null && arrow.getType() == Material.FIREWORK_ROCKET) {
            arrow = findNonFireworkArrow(data);
            if (arrow == null) {
                player.sendMessage(MessageService.get().get("quiver.message.outarrow"));
                event.setCancelled(true);
                return;
            }
        }
        if (arrow == null) {
            player.sendMessage(MessageService.get().get("quiver.message.noarrow"));
            event.setCancelled(true);
            return;
        }
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(MessageService.get().get("quiver.message.fullinv"));
            event.setCancelled(true);
            return;
        }
        if (data.consumeArrow()) {
            ItemStack singleArrow = arrow.clone();
            singleArrow.setAmount(1);
            player.getInventory().addItem(singleArrow);
            player.updateInventory();
            saveQuiverData(quiverId, data);
            usingQuiver.add(player.getUniqueId());
            player.sendMessage(MessageService.get().get("quiver.message.takeout"));
        } else {
            player.sendMessage(MessageService.get().get("quiver.message.takefail"));
            event.setCancelled(true);
        }
    }

    private boolean hasEnoughArrowsInInventory(Player player) {
        int arrowCount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !isQuiverItem(item)) {
                switch (item.getType()) {
                    case ARROW:
                    case SPECTRAL_ARROW:
                    case TIPPED_ARROW:
                        arrowCount += item.getAmount();
                        if (arrowCount > 1) {
                            return true;
                        }
                        break;
                }
            }
        }
        return false;
    }

    private ItemStack findNonFireworkArrow(QuiverData data) {
        for (int i = data.prioritySlot + 1; i < data.arrows.length; i++) {
            if (isValidNonFireworkArrow(data.arrows[i])) {
                return data.arrows[i].clone();
            }
        }
        for (int i = 0; i < data.prioritySlot; i++) {
            if (isValidNonFireworkArrow(data.arrows[i])) {
                return data.arrows[i].clone();
            }
        }
        return null;
    }

    private boolean isValidNonFireworkArrow(ItemStack item) {
        return item != null && item.getAmount() > 0 && item.getType() != Material.FIREWORK_ROCKET && (
                item.getType() == Material.ARROW ||
                        item.getType() == Material.SPECTRAL_ARROW ||
                        item.getType() == Material.TIPPED_ARROW
        );
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player) ||
                event.getInventory().getType() != InventoryType.DISPENSER ||
                !event.getView().getTitle().equals(MessageService.get().get("quiver.guititle"))) {
            return;
        }
        Player player = (Player) event.getPlayer();
        ItemStack quiverItem = openQuivers.remove(player.getUniqueId());
        if (quiverItem == null) return;
        Integer quiverId = getQuiverId(quiverItem);
        if (quiverId == null) return;
        Inventory inv = event.getInventory();
        QuiverData data = getQuiverData(quiverId);
        player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_DROP_CONTENTS, 1f, 0.7f);
        List<ItemStack> disallowedItems = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null) {
                if (isAllowedItem(item)) {
                    data.arrows[i] = item.clone();
                } else {
                    disallowedItems.add(item.clone());
                    item.setAmount(0);
                }
            } else {
                data.arrows[i] = null;
            }
        }
        saveQuiverData(quiverId, data);
        if (!disallowedItems.isEmpty()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (ItemStack disallowedItem : disallowedItems) {
                        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(disallowedItem);
                        if (!leftover.isEmpty()) {
                            for (ItemStack remaining : leftover.values()) {
                                player.getWorld().dropItem(player.getLocation(), remaining);
                            }
                        }
                    }
                }
            }.runTaskLater(plugin, 1L);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryView view = event.getView();
        if (view.getType() != InventoryType.DISPENSER ||
                !view.getTitle().equals(MessageService.get().get("quiver.guititle"))) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();
        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();
        if (clickedInventory != null && clickedInventory.getType() == InventoryType.DISPENSER) {
            if ((currentItem != null && isQuiverItem(currentItem)) ||
                    (cursorItem != null && isQuiverItem(cursorItem))) {
                event.setCancelled(true);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                return;
            }
            if ((event.getAction() == InventoryAction.PLACE_ALL ||
                    event.getAction() == InventoryAction.PLACE_ONE ||
                    event.getAction() == InventoryAction.PLACE_SOME ||
                    event.getAction() == InventoryAction.SWAP_WITH_CURSOR ||
                    event.getAction() == InventoryAction.HOTBAR_SWAP ||
                    event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD ||
                    event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) &&
                    cursorItem != null && !isAllowedItem(cursorItem)) {
                event.setCancelled(true);
                player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_REMOVE_ONE, 0.8f, 0.8f);
                player.sendMessage(MessageService.get().get("quiver.message.onlyarrow"));
                return;
            }
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY &&
                    currentItem != null && !isAllowedItem(currentItem)) {
                event.setCancelled(true);
                player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_REMOVE_ONE, 0.8f, 0.8f);
                player.sendMessage(MessageService.get().get("quiver.message.onlyarrow"));
                return;
            }
            if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                if (cursorItem != null && !isAllowedItem(cursorItem)) {
                    event.setCancelled(true);
                    player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_REMOVE_ONE, 0.8f, 0.8f);
                    player.sendMessage(MessageService.get().get("quiver.message.onlyarrow"));
                    return;
                }
            }
        }
        if (clickedInventory != null && clickedInventory.getType() != InventoryType.DISPENSER) {
            if ((event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY ||
                    event.getAction() == InventoryAction.COLLECT_TO_CURSOR) &&
                    currentItem != null && !isAllowedItem(currentItem)) {
                event.setCancelled(true);
                player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_REMOVE_ONE, 0.8f, 0.8f);
                player.sendMessage(MessageService.get().get("quiver.message.onlyarrow"));
                return;
            }
            if (currentItem != null && isQuiverItem(currentItem)) {
                event.setCancelled(true);
                player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_DROP_CONTENTS, 0.5f, 1.0f);
                return;
            }
        }
        if (event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD ||
                event.getAction() == InventoryAction.HOTBAR_SWAP) {
            if (currentItem != null && !isAllowedItem(currentItem)) {
                event.setCancelled(true);
                player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_REMOVE_ONE, 0.8f, 0.8f);
                player.sendMessage(MessageService.get().get("quiver.message.onlyarrow"));
                return;
            }
        }
        if (cursorItem != null && !isAllowedItem(cursorItem) &&
                (event.getAction() == InventoryAction.PLACE_ALL ||
                        event.getAction() == InventoryAction.PLACE_ONE ||
                        event.getAction() == InventoryAction.PLACE_SOME ||
                        event.getAction() == InventoryAction.SWAP_WITH_CURSOR)) {
            Inventory targetInventory = event.getInventory();
            if (targetInventory.getType() == InventoryType.DISPENSER &&
                    view.getTitle().equals(MessageService.get().get("quiver.guititle"))) {
                event.setCancelled(true);
                player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_REMOVE_ONE, 0.8f, 0.8f);
                player.sendMessage(MessageService.get().get("quiver.message.onlyarrow"));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        ItemStack arrowItem = event.getConsumable();
        if (arrowItem != null && isQuiverItem(arrowItem)) {
            event.setConsumeItem(false);
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
            event.setConsumeItem(false);
            return;
        }
        boolean isUsingQuiver = usingQuiver.remove(player.getUniqueId());
        if (!isUsingQuiver) {
            return;
        }
        handleQuiverShoot(event, player);
    }

    private void handleQuiverShoot(EntityShootBowEvent event, Player player) {
        ItemStack quiverItem = findActiveQuiver(player);
        if (quiverItem == null) {
            event.setCancelled(true);
            return;
        }
        Integer quiverId = getQuiverId(quiverItem);
        if (quiverId == null) {
            event.setCancelled(true);
            return;
        }
        QuiverData data = getQuiverData(quiverId);
        if (data.consumeArrow()) {
            saveQuiverData(quiverId, data);
            ItemStack arrowStack = event.getConsumable();
            if (arrowStack != null && arrowStack.getType() == Material.TIPPED_ARROW &&
                    arrowStack.getItemMeta() instanceof PotionMeta) {
                addPotionArrowEffect(event.getProjectile(), player);
            }
        }
    }

    private void addPotionArrowEffect(Entity arrow, Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (arrow.isDead() || arrow.isOnGround()) {
                    this.cancel();
                    return;
                }
                player.getWorld().spawnParticle(
                        Particle.SPELL_MOB,
                        arrow.getLocation(),
                        3,
                        0.1, 0.1, 0.1,
                        0,
                        null,
                        true
                );
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCrossbowShoot(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player)) return;
        Player player = (Player) event.getEntity().getShooter();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        boolean isUsingQuiver = usingQuiver.remove(player.getUniqueId());
        if (!isUsingQuiver) {
            return;
        }

        handleCrossbowShoot(event, player);
    }

    private void handleCrossbowShoot(ProjectileLaunchEvent event, Player player) {
        ItemStack quiverItem = findActiveQuiver(player);
        if (quiverItem == null) {
            event.setCancelled(true);
            return;
        }
        Integer quiverId = getQuiverId(quiverItem);
        if (quiverId == null) {
            event.setCancelled(true);
            return;
        }
        QuiverData data = getQuiverData(quiverId);
        ItemStack arrow = data.getPriorityArrow();
        if (arrow == null) {
            return;
        }
        if (data.consumeArrow()) {
            if (event.getEntity() instanceof Arrow &&
                    arrow.getType() == Material.TIPPED_ARROW &&
                    arrow.getItemMeta() instanceof PotionMeta) {
                addPotionArrowEffect(event.getEntity(), player);
            }
            saveQuiverData(quiverId, data);
        }
    }

    @EventHandler
    public void onSlotChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        if (cooldownPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        ItemStack quiverItem = findActiveQuiver(player);
        if (quiverItem == null || isQuiverItem(player.getInventory().getItemInOffHand())) {
            return;
        }
        Integer quiverId = getQuiverId(quiverItem);
        if (quiverId == null) return;
        int newSlot = event.getNewSlot();
        QuiverData data = getQuiverData(quiverId);
        if (data.prioritySlot == newSlot) {
            return;
        }
        data.prioritySlot = newSlot;
        saveQuiverData(quiverId, data);
        String formattedMessage = MessageService.get().getFormatted("quiver.roll", (newSlot + 1));
        if (formattedMessage != null) {
            player.sendMessage(formattedMessage);
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 0.8f);
        cooldownPlayers.add(player.getUniqueId());
        new BukkitRunnable() {
            @Override
            public void run() {
                cooldownPlayers.remove(player.getUniqueId());
            }
        }.runTaskLater(plugin, 3L);
        event.setCancelled(true);
    }
    private boolean isAllowedItem(ItemStack item) {
        return item != null && allowedItems.contains(item.getType());
    }
    private boolean hasArrows(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !isQuiverItem(item)) {
                switch (item.getType()) {
                    case ARROW:
                    case SPECTRAL_ARROW:
                    case TIPPED_ARROW:
                        return true;
                }
            }
        }
        return false;
    }
    private boolean hasArrowsInInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && allowedItems.contains(item.getType())) {
                if (!isQuiverItem(item)) {
                    return true;
                }
            }
        }
        return false;
    }
    private ItemStack findActiveQuiver(Player player) {
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isQuiverItem(offHand)) return offHand;

        for (ItemStack item : player.getInventory().getContents()) {
            if (isQuiverItem(item)) {
                return item;
            }
        }
        return null;
    }
    public void onDisable() {
        saveAllQuiverData();
    }
}