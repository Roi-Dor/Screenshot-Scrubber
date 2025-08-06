package com.example.screenscrubber;

import android.util.Log;

/**
 * Shared utility methods to avoid duplication
 * Single responsibility: Common helper functions
 */
public class ScreenScrubberUtils {
    private static final String TAG = "ScreenScrubberUtils";

    /**
     * Normalize text for better matching (handle OCR issues)
     */
    public static String normalizeText(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", "").toLowerCase();
    }

    /**
     * Mask sensitive values for logging (unified approach)
     */
    public static String maskSensitiveValue(String value, String type) {
        if (value == null || value.length() < 4) return "***";

        switch (type) {
            case "CREDIT_CARD":
                if (value.length() >= 8) {
                    String digits = value.replaceAll("[^0-9]", "");
                    if (digits.length() >= 8) {
                        return digits.substring(0, 4) + " **** **** " + digits.substring(digits.length() - 4);
                    }
                }
                return "****";
            case "US_SSN": return "***-**-****";
            case "US_PHONE": return "***-***-****";
            case "ISRAELI_ID": return "***-***-***";
            case "ISRAELI_PHONE": return "***-***-****";
            case "ISRAELI_BANK_ACCOUNT": return "**-***-******";
            case "EMAIL":
                int atIndex = value.indexOf('@');
                if (atIndex > 2) {
                    return value.substring(0, 2) + "***" + value.substring(atIndex);
                }
                return "***@***";
            default: return "***";
        }
    }

    /**
     * Check if a string contains mostly repeated digits (likely invalid)
     */
    public static boolean isRepeatedDigits(String number) {
        if (number == null || number.length() < 4) return false;

        char firstDigit = number.charAt(0);
        int count = 0;
        for (char c : number.toCharArray()) {
            if (c == firstDigit) count++;
        }
        return (count * 1.0 / number.length()) > 0.7;
    }

    /**
     * Extract digits only from a string
     */
    public static String extractDigits(String text) {
        if (text == null) return "";
        return text.replaceAll("[^0-9]", "");
    }

    /**
     * Check if text contains common blacklisted patterns
     */
    public static boolean isBlacklistedPattern(String text, String type) {
        if (text == null) return false;

        String clean = extractDigits(text);

        // Common invalid patterns
        String[] blacklist = {
                "000000000", "111111111", "123456789", "987654321",
                "000000000000000", "111111111111111", "123456789012345"
        };

        for (String pattern : blacklist) {
            if (clean.equals(pattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Safe logging method that automatically masks sensitive data
     */
    public static void logSafely(String tag, String level, String message, String sensitiveValue, String type) {
        String maskedMessage = message;
        if (sensitiveValue != null && type != null) {
            String masked = maskSensitiveValue(sensitiveValue, type);
            maskedMessage = message.replace(sensitiveValue, masked);
        }

        switch (level.toUpperCase()) {
            case "DEBUG":
            case "D":
                Log.d(tag, maskedMessage);
                break;
            case "INFO":
            case "I":
                Log.i(tag, maskedMessage);
                break;
            case "WARN":
            case "W":
                Log.w(tag, maskedMessage);
                break;
            case "ERROR":
            case "E":
                Log.e(tag, maskedMessage);
                break;
            default:
                Log.d(tag, maskedMessage);
        }
    }

    /**
     * Validate that essential components are not null
     */
    public static boolean validateComponents(Object... components) {
        for (Object component : components) {
            if (component == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Calculate similarity between two strings (for fuzzy matching)
     */
    public static double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;

        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) return 1.0;

        return (maxLength - levenshteinDistance(s1, s2)) / (double) maxLength;
    }

    /**
     * Calculate Levenshtein distance for string similarity
     */
    private static int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }

        return dp[s1.length()][s2.length()];
    }

    /**
     * Format file size for logging
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * Check if string matches phone number pattern (any country)
     */
    public static boolean looksLikePhoneNumber(String text) {
        if (text == null) return false;

        String digits = extractDigits(text);
        return digits.length() >= 7 && digits.length() <= 15 &&
                !isRepeatedDigits(digits) && !isBlacklistedPattern(text, "PHONE");
    }

    /**
     * Check if string matches credit card pattern
     */
    public static boolean looksLikeCreditCard(String text) {
        if (text == null) return false;

        String digits = extractDigits(text);
        return digits.length() >= 13 && digits.length() <= 19 &&
                !isRepeatedDigits(digits) && !isBlacklistedPattern(text, "CREDIT_CARD");
    }

    /**
     * Check if string matches email pattern
     */
    public static boolean looksLikeEmail(String text) {
        if (text == null) return false;

        return text.contains("@") && text.contains(".") &&
                text.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$");
    }
}