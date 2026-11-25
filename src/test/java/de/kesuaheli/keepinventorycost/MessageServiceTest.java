package de.kesuaheli.keepinventorycost;

import de.kesuaheli.keepinventorycost.messages.MessageService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageServiceTest {

    @Test
    void translatesMiniMessageAndHexToLegacy() {
        assertEquals("§5Cookie§r §aEnabled§r",
                MessageService.toLegacy("<dark_purple>Cookie<reset> <green>Enabled</green>"));
        assertEquals("§x§a§b§1§2§c§dHi",
                MessageService.toLegacy("<#AB12CD>Hi"));
        assertEquals("§x§1§1§2§2§3§3Yo §cRed",
                MessageService.toLegacy("&#112233Yo &cRed"));
    }

    @Test
    void formatsNamedAndLegacyPlaceholdersSafelyWithLiteralPercent() {
        String template = "Fee 5% -> Paid {cost} in {world} for %s!";
        String formatted = MessageService.formatPlaceholders(
                template,
                Map.of("cost", "50.00 Coins", "world", "survival"),
                "Steve"
        );
        assertEquals("Fee 5% -> Paid 50.00 Coins in survival for Steve!", formatted);
    }
}
