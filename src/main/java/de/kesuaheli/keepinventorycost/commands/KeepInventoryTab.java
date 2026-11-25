package de.kesuaheli.keepinventorycost.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public class KeepInventoryTab implements TabCompleter {

    private static final List<String> ROOT_COMMANDS = List.of("toggle", "on", "off", "get", "status", "set", "cost");
    private static final List<String> BOOLEANS = List.of("true", "false");

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                                @NotNull String label, @NotNull String[] args) {
        List<String> candidates;
        if (args.length == 1) {
            candidates = ROOT_COMMANDS;
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            candidates = BOOLEANS;
        } else {
            return List.of();
        }

        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(option -> option.startsWith(prefix))
                .sorted()
                .toList();
    }
}
