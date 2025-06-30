package com.example.screenscrubber;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SensitiveDataDetector {

    public static class SensitiveMatch {
        public final String type;
        public final String value;
        public final int start;
        public final int end;

        public SensitiveMatch(String type, String value, int start, int end) {
            this.type = type;
            this.value = value;
            this.start = start;
            this.end = end;
        }
    }

    // Credit card pattern: 16 digits with optional spaces/dashes
    private static final Pattern CREDIT_CARD_PATTERN =
            Pattern.compile("\\b(?:\\d{4}[\\s-]?){3}\\d{4}\\b");

    // SSN pattern: XXX-XX-XXXX
    private static final Pattern SSN_PATTERN =
            Pattern.compile("\\b\\d{3}-?\\d{2}-?\\d{4}\\b");

    // Phone number pattern: various formats
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("\\b(?:\\+?1[\\s-]?)?\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4}\\b");

    // Email pattern
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

    public List<SensitiveMatch> detectSensitiveData(String text) {
        List<SensitiveMatch> matches = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return matches;
        }

        // Check for credit cards
        findMatches(text, CREDIT_CARD_PATTERN, "CREDIT_CARD", matches);

        // Check for SSNs
        findMatches(text, SSN_PATTERN, "SSN", matches);

        // Check for phone numbers
        findMatches(text, PHONE_PATTERN, "PHONE", matches);

        // Check for emails
        findMatches(text, EMAIL_PATTERN, "EMAIL", matches);

        return matches;
    }

    private void findMatches(String text, Pattern pattern, String type, List<SensitiveMatch> matches) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String match = matcher.group();

            // Additional validation for credit cards
            if ("CREDIT_CARD".equals(type) && !isValidCreditCard(match)) {
                continue;
            }

            matches.add(new SensitiveMatch(
                    type,
                    match,
                    matcher.start(),
                    matcher.end()
            ));
        }
    }

    private boolean isValidCreditCard(String cardNumber) {
        // Remove spaces and dashes
        String cleanNumber = cardNumber.replaceAll("[\\s-]", "");

        // Must be 16 digits
        if (cleanNumber.length() != 16) {
            return false;
        }

        // Basic Luhn algorithm check
        return isValidLuhn(cleanNumber);
    }

    private boolean isValidLuhn(String cardNumber) {
        int sum = 0;
        boolean alternate = false;

        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(cardNumber.charAt(i));

            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit = (digit % 10) + 1;
                }
            }

            sum += digit;
            alternate = !alternate;
        }

        return (sum % 10) == 0;
    }
}