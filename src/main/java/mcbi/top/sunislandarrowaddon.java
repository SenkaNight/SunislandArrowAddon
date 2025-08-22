package mcbi.top;

import mcbi.top.CustomArrow.CustomArrowListener;
import mcbi.top.CustomArrow.CustomArrowManager;
import mcbi.top.CustomArrow.CustomArrow;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class sunislandarrowaddon extends JavaPlugin {

    private Quiver quiverSystem;
    private CustomArrowManager arrowManager;
    private CustomArrow CustomArrow;
    private MessageService MessageService;

    @Override
    public void onEnable() {
        mcbi.top.MessageService.init(this);
        arrowManager = new CustomArrowManager(this);
        arrowManager.reload();
        arrowManager.loadArrows();
        getServer().getPluginManager().registerEvents(
                new CustomArrowListener(this, arrowManager), this);
        this.quiverSystem = new Quiver(this);
        getServer().getPluginManager().registerEvents(quiverSystem, this);
        this.getCommand("arrow").setExecutor(new ArrowCommand(arrowManager));
        this.getCommand("arrow").setTabCompleter(new ArrowCommand(arrowManager));
        setupCommands();
        getLogger().info("SunislandArrowAddon loaded! by natako");
    }

    private void setupCommands() {
        getCommand("givequiver").setExecutor((sender, cmd, label, args) -> {
            Player player = (Player) sender;
            if (args.length == 0) {
                player.getInventory().addItem(quiverSystem.createQuiverItem(player));
                sender.sendMessage(mcbi.top.MessageService.get().get("quiver.getquiver"));
                return true;
            }
            if (args.length >= 1) {
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    sender.sendMessage(mcbi.top.MessageService.get().get("quiver.command.notplayer"));
                    return true;
                }
                if (args.length >= 2) {
                    try {
                        int quiverId = Integer.parseInt(args[1]);
                        quiverSystem.giveQuiverWithId(target, quiverId, sender);
                    } catch (NumberFormatException e) {
                        Player quiverOwner = Bukkit.getPlayer(args[1]);
                        if (quiverOwner != null) {
                            quiverSystem.giveBoundQuiverToPlayer(sender, target, quiverOwner);
                        } else {
                            sender.sendMessage(mcbi.top.MessageService.get().get("quiver.command.notplayer"));
                        }
                    }
                } else {
                    quiverSystem.giveBoundQuiverToPlayer(sender, target, target);
                }
                return true;
            }
            return true;
        });
        getCommand("savequivers").setExecutor((sender, cmd, label, args) -> {
            quiverSystem.saveAllQuiverData();
            sender.sendMessage(mcbi.top.MessageService.get().get("quiver.save"));
            return true;
        });
        getCommand("arrowaddonreload").setExecutor((sender, cmd, label, args) -> {
            if (!sender.hasPermission("quiver.reload")) {
                sender.sendMessage(mcbi.top.MessageService.get().get("error.no_permission"));
                return true;
            }
            quiverSystem.reloadData();
            arrowManager.reload();
            mcbi.top.MessageService.reloadMessages1();
            sender.sendMessage(mcbi.top.MessageService.get().get("command.reload.success"));
            return true;
        });
    }

    @Override
    public void onDisable() {
        if (quiverSystem != null) {
            quiverSystem.onDisable();
        }
        getLogger().info("Disabled SunislandArrowAddon!");
    }

}