package de.kesuaheli.keepinventorycost.messages;

import org.bukkit.command.CommandSender;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageService {

    private static final Pattern HEX_TAG_PATTERN = Pattern.compile("<(?:color:)?#([0-9a-fA-F]{6})>");
    private static final Pattern AMP_HEX_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern AMP_CODE_PATTERN = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
    private static final Pattern CLOSING_TAG_PATTERN = Pattern.compile("</[a-zA-Z0-9_:#-]+>");
    private static final Pattern UNKNOWN_TAG_PATTERN = Pattern.compile("<[a-zA-Z0-9_:#-]+>");

    private static final Map<String, String> TAG_MAP = Map.ofEntries(
            Map.entry("<black>", "§0"),
            Map.entry("<dark_blue>", "§1"),
            Map.entry("<dark_green>", "§2"),
            Map.entry("<dark_aqua>", "§3"),
            Map.entry("<dark_red>", "§4"),
            Map.entry("<dark_purple>", "§5"),
            Map.entry("<gold>", "§6"),
            Map.entry("<gray>", "§7"),
            Map.entry("<grey>", "§7"),
            Map.entry("<dark_gray>", "§8"),
            Map.entry("<dark_grey>", "§8"),
            Map.entry("<blue>", "§9"),
            Map.entry("<green>", "§a"),
            Map.entry("<aqua>", "§b"),
            Map.entry("<red>", "§c"),
            Map.entry("<light_purple>", "§d"),
            Map.entry("<yellow>", "§e"),
            Map.entry("<white>", "§f"),
            Map.entry("<obfuscated>", "§k"),
            Map.entry("<obf>", "§k"),
            Map.entry("<bold>", "§l"),
            Map.entry("<b>", "§l"),
            Map.entry("<strikethrough>", "§m"),
            Map.entry("<st>", "§m"),
            Map.entry("<underlined>", "§n"),
            Map.entry("<u>", "§n"),
            Map.entry("<italic>", "§o"),
            Map.entry("<i>", "§o"),
            Map.entry("<reset>", "§r"),
            Map.entry("<r>", "§r")
    );

    private final boolean adventureAvailable;

    public MessageService() {
        boolean detected;
        try {
            Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
            Class<?> audienceClass = Class.forName("net.kyori.adventure.audience.Audience");
            detected = audienceClass.isAssignableFrom(CommandSender.class);
        } catch (Throwable ignored) {
            detected = false;
        }
        this.adventureAvailable = detected;
    }

    public boolean isAdventureAvailable() {
        return adventureAvailable;
    }

    public void send(CommandSender sender, String prefix, String body) {
        if (sender == null || body == null) {
            return;
        }
        if (adventureAvailable && AdventureBridge.isSupported(sender)) {
            AdventureBridge.send(sender, prefix, body);
            return;
        }
        String combined = (prefix != null && !prefix.isBlank()) ? prefix + " " + body : body;
        sender.sendMessage(toLegacy(combined));
    }

    public static String formatPlaceholders(String template, Map<String, String> named, String... legacyArgs) {
        if (template == null) {
            return "";
        }
        String result = template;
        if (named != null) {
            for (Map.Entry<String, String> entry : named.entrySet()) {
                String value = entry.getValue() != null ? entry.getValue() : "";
                result = result.replace("{" + entry.getKey() + "}", value);
            }
        }
        if (legacyArgs != null && legacyArgs.length > 0 && result.contains("%s")) {
            for (String arg : legacyArgs) {
                int idx = result.indexOf("%s");
                if (idx < 0) {
                    break;
                }
                String safeArg = arg != null ? arg : "";
                result = result.substring(0, idx) + safeArg + result.substring(idx + 2);
            }
        }
        return result;
    }

    public static String toLegacy(String miniMessageText) {
        if (miniMessageText == null || miniMessageText.isEmpty()) {
            return "";
        }
        String text = miniMessageText;
        for (Map.Entry<String, String> entry : TAG_MAP.entrySet()) {
            text = replaceIgnoreCase(text, entry.getKey(), entry.getValue());
        }

        Matcher hexTag = HEX_TAG_PATTERN.matcher(text);
        StringBuilder hexBuffer = new StringBuilder();
        while (hexTag.find()) {
            hexTag.appendReplacement(hexBuffer, Matcher.quoteReplacement(toSectionHex(hexTag.group(1))));
        }
        hexTag.appendTail(hexBuffer);
        text = hexBuffer.toString();

        text = CLOSING_TAG_PATTERN.matcher(text).replaceAll("§r");
        text = UNKNOWN_TAG_PATTERN.matcher(text).replaceAll("");

        Matcher ampHex = AMP_HEX_PATTERN.matcher(text);
        StringBuilder ampBuffer = new StringBuilder();
        while (ampHex.find()) {
            ampHex.appendReplacement(ampBuffer, Matcher.quoteReplacement(toSectionHex(ampHex.group(1))));
        }
        ampHex.appendTail(ampBuffer);
        text = ampBuffer.toString();

        text = AMP_CODE_PATTERN.matcher(text).replaceAll("§$1");
        return text;
    }

    private static String toSectionHex(String hex) {
        String lower = hex.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder("§x");
        for (int i = 0; i < lower.length(); i++) {
            sb.append('§').append(lower.charAt(i));
        }
        return sb.toString();
    }

    private static String replaceIgnoreCase(String source, String target, String replacement) {
        String lowerSource = source.toLowerCase(Locale.ROOT);
        String lowerTarget = target.toLowerCase(Locale.ROOT);
        int idx = lowerSource.indexOf(lowerTarget);
        if (idx < 0) {
            return source;
        }
        StringBuilder sb = new StringBuilder(source.length());
        int start = 0;
        while (idx >= 0) {
            sb.append(source, start, idx).append(replacement);
            start = idx + target.length();
            idx = lowerSource.indexOf(lowerTarget, start);
        }
        sb.append(source, start, source.length());
        return sb.toString();
    }
}
