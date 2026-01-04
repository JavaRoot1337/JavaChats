package ru.javaroot.javachats.utils;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {

    /***
     *
     * @param s Parseable Text
     * @return String containing colorized text to be used in minecraft
     */
    public static String colorize(String s) {
        if (s == null)
            return "";
        s = ChatColor.translateAlternateColorCodes(ChatColor.COLOR_CHAR, s);
        s = findAndReplaceRegex("!#[0-9,a-f,A-F]{6}", s);
        s = findAndReplaceRegex("&#[0-9,a-f,A-F]{6}", s);
        s = ChatColor.translateAlternateColorCodes('&', s);
        return s;
    }

    private static String findAndReplaceRegex(String regex, String input) {

        ArrayList<String> matches = new ArrayList<>();
        ArrayList<ChatColor> colorSet = new ArrayList<>();
        Matcher patternMatcher = Pattern.compile(regex).matcher(input);
        while (patternMatcher.find()) {
            matches.add(patternMatcher.group());
        }
        for (String match : matches) {
            colorSet.add(ChatColor.of(match.substring(1)));
        }
        Iterator<String> matchIterator = matches.iterator();
        Iterator<ChatColor> colorIterator = colorSet.iterator();
        while (matchIterator.hasNext() && colorIterator.hasNext()) {
            input = input.replaceFirst(Pattern.quote(matchIterator.next()), colorIterator.next().toString());
        }
        return input;
    }

    /***
     * Rainbowify's messages
     * 
     * @param message The message to be rainbowified
     * @return rainbowified message
     */
    public static String rainbowText(String message) {
        StringBuilder finalizedMessage = new StringBuilder();
        int hue = 0;
        for (int messageChar = 0; messageChar < message.toCharArray().length; messageChar++) {
            Color color = Color.getHSBColor(((float) hue / 360), 1f, 1f);
            String red = Integer.toHexString(color.getRed());
            String green = Integer.toHexString(color.getGreen());
            String blue = Integer.toHexString(color.getBlue());

            String hexColor = "!#" + (red.length() <= 2 ? repeat("0", 2 - red.length()) + red : red) +
                    (green.length() <= 2 ? repeat("0", 2 - green.length()) + green : green) +
                    (blue.length() <= 2 ? repeat("0", 2 - blue.length()) + blue : blue);
            finalizedMessage.append(hexColor).append(message.toCharArray()[messageChar]);
            hue += (360 / message.toCharArray().length);
        }
        return colorize(finalizedMessage.toString());
    }

    /***
     * Makes the message a gradient from the startColor to endColor
     * 
     * @param message    message to add gradient to
     * @param startColor color that text should begin with
     * @param endColor   color that text should end with
     * @return gradient formatted message
     */
    public static String gradientText(String message, Color startColor, Color endColor) {
        StringBuilder finalizedMessage = new StringBuilder();
        for (int messageChar = 0; messageChar < message.toCharArray().length; messageChar++) {
            float ratio = (float) messageChar / (float) message.toCharArray().length;
            int red = (int) (endColor.getRed() * ratio + startColor.getRed() * (1 - ratio));
            int green = (int) (endColor.getGreen() * ratio + startColor.getGreen() * (1 - ratio));
            int blue = (int) (endColor.getBlue() * ratio + startColor.getBlue() * (1 - ratio));
            Color stepColor = new Color(red, green, blue);
            String redHex = Integer.toHexString(stepColor.getRed());
            String greenHex = Integer.toHexString(stepColor.getGreen());
            String blueHex = Integer.toHexString(stepColor.getBlue());

            String hexColor = "!#" + (redHex.length() <= 2 ? repeat("0", 2 - redHex.length()) + redHex : redHex) +
                    (greenHex.length() <= 2 ? repeat("0", 2 - greenHex.length()) + greenHex : greenHex) +
                    (blueHex.length() <= 2 ? repeat("0", 2 - blueHex.length()) + blueHex : blueHex);
            finalizedMessage.append(hexColor).append(message.toCharArray()[messageChar]);
        }
        return colorize(finalizedMessage.toString());
    }

    /***
     * Parses a hex code into a {@link java.awt.Color}
     * 
     * @param hexColor hex color string
     * @return color
     */
    public static Color parseHexColor(String hexColor) {
        hexColor = hexColor.replaceAll("&", "");
        if (!hexColor.startsWith("#")) {
            hexColor = "#" + hexColor;
        }
        return ChatColor.of(hexColor).getColor();
    }

    private static String repeat(String s, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    public static String stripColor(String message) {
        return ChatColor.stripColor(colorize(message));
    }

    public static String getLastColors(String input) {
        int length = input.length();

        // Regex for Bungee Hex: §x followed by 6 pairs of § and a character
        Pattern bungeeHex = Pattern.compile("§x(§[0-9a-fA-F]){6}");

        StringBuilder recordedStyle = new StringBuilder();
        String lastColor = "";

        for (int i = 0; i < length; i++) {
            char c = input.charAt(i);
            if (c == '§' && i + 1 < length) {
                char code = input.charAt(i + 1);

                // Check if this is the start of a known Hex sequence
                if (Character.toLowerCase(code) == 'x') {
                    // Check if it matches the hex pattern from here
                    if (i + 13 < length) {
                        String potentialHex = input.substring(i, i + 14);
                        if (bungeeHex.matcher(potentialHex).matches()) {
                            lastColor = potentialHex;
                            recordedStyle.setLength(0);
                            i += 13;
                            continue;
                        }
                    }
                }

                if ("0123456789abcdefABCDEF".indexOf(code) != -1) {
                    lastColor = "§" + code;
                    recordedStyle.setLength(0);
                    i++;
                } else if ("klmnorKLMNOR".indexOf(code) != -1) {
                    if (Character.toLowerCase(code) == 'r') {
                        lastColor = "";
                        recordedStyle.setLength(0);
                    } else {
                        recordedStyle.append("§").append(code);
                    }
                    i++;
                }
            }
        }

        return lastColor + recordedStyle.toString();
    }

}
