package mcbi.top;

import mcbi.top.CustomArrow.CustomArrow;
import mcbi.top.CustomArrow.CustomArrowManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ArrowCommand implements CommandExecutor, TabCompleter {
    private final CustomArrowManager arrowManager;

    public ArrowCommand(CustomArrowManager arrowManager) {
        this.arrowManager = arrowManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("list")) {
            String availableIds = String.join(", ", arrowManager.getArrowIds());
            String formattedMessage = MessageService.get().getFormatted("arrow.allarrow", availableIds);
            if (formattedMessage != null) {
                sender.sendMessage(formattedMessage);
            }
            return true;
        }
        Player targetPlayer;
        String arrowId;
        int amount = 1;
        int argIndex = 0;
        if (args.length > 1 && Bukkit.getPlayer(args[0]) != null) {
            targetPlayer = Bukkit.getPlayer(args[0]);
            argIndex = 1;
        } else {
            if (!(sender instanceof Player)) {
                return true;
            }
            targetPlayer = (Player) sender;
        }
        if (args.length <= argIndex) {
            sendHelp(sender);
            return true;
        }
        arrowId = args[argIndex].toLowerCase();
        if (args.length > argIndex + 1) {
            try {
                amount = Math.min(64, Math.max(1, Integer.parseInt(args[argIndex + 1])));
            } catch (NumberFormatException e) {
                return true;
            }
        }
        CustomArrow arrow = arrowManager.getArrow(arrowId);
        if (arrow == null) {
            String availableIds = String.join(", ", arrowManager.getArrowIds());
            String formattedMessage = MessageService.get().getFormatted("arrow.unknown", availableIds);
            if (formattedMessage != null) {
                sender.sendMessage(formattedMessage);
            }
            return true;
        }
        ItemStack arrowItem = arrow.createItemStack(amount);
        targetPlayer.getInventory().addItem(arrowItem);

        String displayName = arrowItem.getItemMeta().hasDisplayName() ?
                arrowItem.getItemMeta().getDisplayName() :
                arrowItem.getType().toString();

        // 发送成功消息
        if (sender.equals(targetPlayer)) {
            String formattedMessage = MessageService.get().getFormatted("arrow.give", amount, displayName);
            if (formattedMessage != null) {
                sender.sendMessage(formattedMessage);
            }
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        if (sender instanceof Player) {
            String formattedMessage = MessageService.get().getFormatted("command.getarrow");
            if (formattedMessage != null) {
                sender.sendMessage(formattedMessage);
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("help");
            options.add("list");
            options.addAll(arrowManager.getArrowIds());
            if (sender.hasPermission("arrow.allarrow") || !(sender instanceof Player)) {
                options.addAll(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList()));
            }
            return StringUtil.copyPartialMatches(args[0], options, new ArrayList<>());
        }
        else if (args.length == 2) {
            Player potentialPlayer = Bukkit.getPlayer(args[0]);
            if (potentialPlayer != null) {
                return StringUtil.copyPartialMatches(args[1], arrowManager.getArrowIds(), new ArrayList<>());
            } else {
                if (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("list")) {
                    return completions;
                }
            }
        }
        else if (args.length == 3) {
            Player potentialPlayer = Bukkit.getPlayer(args[0]);
            if (potentialPlayer != null) {
                return Arrays.asList("1", "8", "16", "32", "64");
            }
        }

        return completions;
    }
}