package de.kesuaheli.keepinventorycost.messages;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

final class AdventureBridge {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private AdventureBridge() {
    }

    static boolean isSupported(CommandSender sender) {
        return sender instanceof Audience;
    }

    static void send(CommandSender sender, String prefix, String body) {
        if (!(sender instanceof Audience audience)) {
            return;
        }
        Component bodyComp = MINI_MESSAGE.deserialize(body);
        if (prefix != null && !prefix.isBlank()) {
            Component prefixComp = MINI_MESSAGE.deserialize(prefix).appendSpace();
            audience.sendMessage(prefixComp.append(bodyComp));
        } else {
            audience.sendMessage(bodyComp);
        }
    }
}
