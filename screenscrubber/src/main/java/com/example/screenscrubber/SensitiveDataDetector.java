package com.example.screenscrubber;

import android.util.Log;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SensitiveDataDetector {
    private static final String TAG = "SensitiveDataDetector";
    private static final boolean ALLOW_LUHN_FAIL = false; // Flag to control strict validation

    public static class SensitiveMatch {
        public final String type;
        public final String value;
        public final int start;
        public final int end;
        public final double confidence;

        public SensitiveMatch(String type, String value, int start, int end, double confidence) {
            this.type = type;
            this.value = value;
            this.start = start;
            this.end = end;
            this.confidence = confidence;
        }

        public SensitiveMatch(String type, String value, int start, int end) {
            this(type, value, start, end, 1.0);
        }
    }

    // IMPROVED OCR-TOLERANT PATTERNS
    private static final Pattern CREDIT_CARD_PATTERN =
            Pattern.compile("(?:\\d[\\s-]*){13,19}");

    private static final Pattern US_SSN_PATTERN =
            Pattern.compile("\\b\\d{3}[-\\s]\\d{2}[-\\s]\\d{4}\\b");

    // Enhanced US Phone - handle both formatted and unformatted
    private static final Pattern US_PHONE_PATTERN = Pattern.compile(
            "\\b(?:\\+?1[\\s-]?)?(?:\\(?[2-9]\\d{2}\\)?[\\s.-]?)[2-9]\\d{2}[\\s.-]?\\d{4}\\b" +
                    "|\\b[2-9]\\d{9}\\b" +
                    "|\\b[2-9]\\d{2}\\.[2-9]\\d{2}\\.\\d{4}\\b"  // ADD DOT FORMAT
    );


    // Enhanced EMAIL - handle OCR spacing issues
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("\\b[a-zA-Z0-9][a-zA-Z0-9._%+-]*@[a-zA-Z0-9][a-zA-Z0-9.-]*\\s?\\.\\s?[a-zA-Z]{2,6}\\b");

    // Israeli patterns
    private static final Pattern ISRAELI_ID_PATTERN =
            Pattern.compile("\\b\\d{9}\\b|\\b\\d{3}[\\s-]\\d{3}[\\s-]\\d{3}\\b");

    private static final Pattern ISRAELI_PHONE_PATTERN = Pattern.compile(
            "\\b(?:\\+972[\\s-]?|0)?(?:2|3|4|5[0-9]|7[2-9]|8|9)[\\s-]?\\d{3}[\\s-]?\\d{4}\\b" +
                    "|\\b\\+972[\\s-]?[2-9][\\s-]?\\d{3}[\\s-]?\\d{4}\\b"  // ADD INTERNATIONAL FORMAT
    );
    private static final Pattern ISRAELI_BANK_ACCOUNT_PATTERN =
            Pattern.compile("\\b(?:0[1-9]|[1-9][0-9])[\\s-]?\\d{3}[\\s-]?\\d{6}\\b");

    // Validation sets
    private static final Set<String> VALID_ISRAELI_AREA_CODES = new HashSet<>();
    static {
        VALID_ISRAELI_AREA_CODES.add("02"); VALID_ISRAELI_AREA_CODES.add("03"); VALID_ISRAELI_AREA_CODES.add("04");
        VALID_ISRAELI_AREA_CODES.add("08"); VALID_ISRAELI_AREA_CODES.add("09"); VALID_ISRAELI_AREA_CODES.add("50");
        VALID_ISRAELI_AREA_CODES.add("51"); VALID_ISRAELI_AREA_CODES.add("52"); VALID_ISRAELI_AREA_CODES.add("53");
        VALID_ISRAELI_AREA_CODES.add("54"); VALID_ISRAELI_AREA_CODES.add("55"); VALID_ISRAELI_AREA_CODES.add("56");
        VALID_ISRAELI_AREA_CODES.add("57"); VALID_ISRAELI_AREA_CODES.add("58"); VALID_ISRAELI_AREA_CODES.add("59");
        VALID_ISRAELI_AREA_CODES.add("72"); VALID_ISRAELI_AREA_CODES.add("73"); VALID_ISRAELI_AREA_CODES.add("74");
        VALID_ISRAELI_AREA_CODES.add("76"); VALID_ISRAELI_AREA_CODES.add("77"); VALID_ISRAELI_AREA_CODES.add("78");
        VALID_ISRAELI_AREA_CODES.add("79");
    }

    private static final Set<String> VALID_ISRAELI_BANK_CODES = new HashSet<>();
    static {
        VALID_ISRAELI_BANK_CODES.add("10"); VALID_ISRAELI_BANK_CODES.add("11"); VALID_ISRAELI_BANK_CODES.add("12");
        VALID_ISRAELI_BANK_CODES.add("20"); VALID_ISRAELI_BANK_CODES.add("26"); VALID_ISRAELI_BANK_CODES.add("27");
        VALID_ISRAELI_BANK_CODES.add("31"); VALID_ISRAELI_BANK_CODES.add("17"); VALID_ISRAELI_BANK_CODES.add("14");
        VALID_ISRAELI_BANK_CODES.add("09"); VALID_ISRAELI_BANK_CODES.add("04"); VALID_ISRAELI_BANK_CODES.add("52");
        VALID_ISRAELI_BANK_CODES.add("54"); VALID_ISRAELI_BANK_CODES.add("46"); VALID_ISRAELI_BANK_CODES.add("22");
        VALID_ISRAELI_BANK_CODES.add("23");
    }

    public List<SensitiveMatch> detectSensitiveData(String text) {
        List<SensitiveMatch> matches = new ArrayList<>();

        if (text == null || text.trim().isEmpty()) {
            Log.w(TAG, "Empty or null text provided");
            return matches;
        }

        String cleanText = text.trim();
        Log.d(TAG, "🔍 PROCESSING TEXT (" + cleanText.length() + " chars)");

        try {
            // Order matters! Check Israeli patterns FIRST
            Log.d(TAG, "🇮🇱 === ISRAELI DETECTION PHASE ===");
            findIsraeliIDs(cleanText, matches);
            findIsraeliPhones(cleanText, matches);
            findIsraeliBankAccounts(cleanText, matches);

            Log.d(TAG, "🇺🇸 === US DETECTION PHASE ===");
            findCreditCards(cleanText, matches);
            findUSSSNs(cleanText, matches);
            findUSPhones(cleanText, matches);

            Log.d(TAG, "🌐 === UNIVERSAL DETECTION PHASE ===");
            findEmails(cleanText, matches);

            Log.d(TAG, "🔧 === OVERLAP RESOLUTION ===");
            matches = removeOverlappingMatches(matches);

            Log.d(TAG, "📊 DETECTION SUMMARY: " + matches.size() + " final matches");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in detection", e);
        }

        return matches;
    }

    private void findCreditCards(String text, List<SensitiveMatch> matches) {
        Matcher matcher = CREDIT_CARD_PATTERN.matcher(text);
        while (matcher.find()) {
            String match = matcher.group();
            String cleanNumber = match.replaceAll("[\\s-]", "");

            if (cleanNumber.length() >= 13 && cleanNumber.length() <= 19) {
                // Use more lenient validation - confidence boost for Luhn pass
                if (ALLOW_LUHN_FAIL || isValidCreditCardNumber(cleanNumber)) {
                    double confidence = isValidCreditCardNumber(cleanNumber) ? 0.95 : 0.7;
                    matches.add(new SensitiveMatch("CREDIT_CARD", match, matcher.start(), matcher.end(), confidence));
                    Log.d(TAG, "✅ Credit Card confirmed: " + maskValue(match, "CREDIT_CARD"));
                }
            }
        }
    }

    private void findUSPhones(String text, List<SensitiveMatch> matches) {
        // Add pattern for unformatted 10-digit numbers
        Pattern barePhonePattern = Pattern.compile("\\b[2-9]\\d{9}\\b");

        // Check formatted phones first
        Matcher matcher = US_PHONE_PATTERN.matcher(text);
        while (matcher.find()) {
            String match = matcher.group();

            if (!isIsraeliPhoneFormat(match) && !overlapsWithExisting(matches, matcher.start(), matcher.end(), "ISRAELI_PHONE")) {
                String cleanPhone = match.replaceAll("[\\s\\-\\(\\)\\.]", "");
                if (cleanPhone.startsWith("+1")) cleanPhone = cleanPhone.substring(2);
                if (cleanPhone.startsWith("1") && cleanPhone.length() == 11) cleanPhone = cleanPhone.substring(1);

                if (cleanPhone.length() == 10 && cleanPhone.charAt(0) >= '2' && !isRepeatedDigits(cleanPhone)) {
                    matches.add(new SensitiveMatch("US_PHONE", match, matcher.start(), matcher.end(), 0.8));
                    Log.d(TAG, "✅ US Phone confirmed: " + maskValue(match, "US_PHONE"));
                }
            }
        }

        // Check bare 10-digit numbers
        Matcher bareMatcher = barePhonePattern.matcher(text);
        while (bareMatcher.find()) {
            String match = bareMatcher.group();

            if (!overlapsWithExisting(matches, bareMatcher.start(), bareMatcher.end(), "ISRAELI_PHONE") &&
                    !overlapsWithExisting(matches, bareMatcher.start(), bareMatcher.end(), "US_PHONE")) {
                matches.add(new SensitiveMatch("US_PHONE", match, bareMatcher.start(), bareMatcher.end(), 0.7));
                Log.d(TAG, "✅ US Phone (bare) confirmed: " + maskValue(match, "US_PHONE"));
            }
        }
    }

    private void findEmails(String text, List<SensitiveMatch> matches) {
        // Normalize OCR spacing issues: "example .com" -> "example.com"
        String normalizedText = text.replaceAll("(\\w)\\s+\\.\\s+(\\w)", "$1.$2");

        Matcher matcher = EMAIL_PATTERN.matcher(normalizedText);
        while (matcher.find()) {
            String match = matcher.group().replaceAll("\\s+", ""); // Remove any remaining spaces

            if (match.contains("@") && match.contains(".") && match.length() > 5 && isValidEmailFormat(match)) {
                // Find position in original text
                int originalStart = findEmailInOriginalText(text, match);
                if (originalStart >= 0) {
                    matches.add(new SensitiveMatch("EMAIL", match, originalStart, originalStart + match.length(), 0.95));
                    Log.d(TAG, "✅ Email confirmed: " + maskValue(match, "EMAIL"));
                }
            }
        }
    }

    // Helper method to find email position in original text
    private int findEmailInOriginalText(String originalText, String normalizedEmail) {
        // Try exact match first
        int exactPos = originalText.indexOf(normalizedEmail);
        if (exactPos >= 0) return exactPos;

        // Look for spaced version
        String spacedEmail = normalizedEmail.replace(".", " . ");
        int spacedPos = originalText.indexOf(spacedEmail);
        if (spacedPos >= 0) return spacedPos;

        // Find by @ symbol and work around it
        String[] parts = normalizedEmail.split("@");
        if (parts.length == 2) {
            int atPos = originalText.indexOf("@");
            while (atPos >= 0) {
                // Check if this @ belongs to our email
                int start = Math.max(0, atPos - parts[0].length() - 2);
                int end = Math.min(originalText.length(), atPos + parts[1].length() + 3);
                String candidate = originalText.substring(start, end);
                if (candidate.replaceAll("\\s+", "").contains(normalizedEmail)) {
                    return start + candidate.indexOf(parts[0].charAt(0));
                }
                atPos = originalText.indexOf("@", atPos + 1);
            }
        }

        return 0; // Fallback
    }

    private void findIsraeliIDs(String text, List<SensitiveMatch> matches) {
        Matcher matcher = ISRAELI_ID_PATTERN.matcher(text);
        while (matcher.find()) {
            String match = matcher.group();
            if (isValidIsraeliID(match)) {
                matches.add(new SensitiveMatch("ISRAELI_ID", match, matcher.start(), matcher.end(), 0.95));
                Log.d(TAG, "✅ Israeli ID confirmed");
            }
        }
    }

    private void findUSSSNs(String text, List<SensitiveMatch> matches) {
        Matcher matcher = US_SSN_PATTERN.matcher(text);
        while (matcher.find()) {
            String match = matcher.group();

            if (!overlapsWithExisting(matches, matcher.start(), matcher.end(), "ISRAELI_ID")) {
                String cleanSSN = match.replaceAll("[-\\s]", "");
                if (cleanSSN.length() == 9 && !cleanSSN.equals("000000000") && !cleanSSN.equals("111111111")) {
                    matches.add(new SensitiveMatch("US_SSN", match, matcher.start(), matcher.end(), 0.9));
                    Log.d(TAG, "✅ US SSN confirmed");
                }
            }
        }
    }

    private void findIsraeliPhones(String text, List<SensitiveMatch> matches) {
        Matcher matcher = ISRAELI_PHONE_PATTERN.matcher(text);
        while (matcher.find()) {
            String match = matcher.group();
            if (isValidIsraeliPhone(match)) {
                matches.add(new SensitiveMatch("ISRAELI_PHONE", match, matcher.start(), matcher.end(), 0.9));
                Log.d(TAG, "✅ Israeli Phone confirmed");
            }
        }
    }

    private void findIsraeliBankAccounts(String text, List<SensitiveMatch> matches) {
        Matcher matcher = ISRAELI_BANK_ACCOUNT_PATTERN.matcher(text);
        while (matcher.find()) {
            String match = matcher.group();
            if (isValidIsraeliBankAccount(match)) {
                matches.add(new SensitiveMatch("ISRAELI_BANK_ACCOUNT", match, matcher.start(), matcher.end(), 0.85));
                Log.d(TAG, "✅ Israeli Bank confirmed");
            }
        }
    }

    // Helper method to check overlaps
    private boolean overlapsWithExisting(List<SensitiveMatch> matches, int start, int end, String type) {
        for (SensitiveMatch existing : matches) {
            if (existing.type.equals(type) && start < existing.end && end > existing.start) {
                return true;
            }
        }
        return false;
    }

    // Validation methods
    private boolean isValidCreditCardNumber(String number) {
        if (isRepeatedDigits(number)) return false;

        // Luhn algorithm
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(number.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) n = (n % 10) + 1;
            }
            sum += n;
            alternate = !alternate;
        }

        boolean luhnValid = (sum % 10 == 0);
        char firstDigit = number.charAt(0);
        boolean validPrefix = (firstDigit == '4' || firstDigit == '5' || firstDigit == '3' || firstDigit == '6');

        return luhnValid && validPrefix;
    }

    private boolean isRepeatedDigits(String number) {
        if (number.length() < 4) return false;
        char firstDigit = number.charAt(0);
        int count = 0;
        for (char c : number.toCharArray()) {
            if (c == firstDigit) count++;
        }
        return (count * 1.0 / number.length()) > 0.7;
    }

    private boolean isValidEmailFormat(String email) {
        return email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$");
    }

    private boolean isIsraeliPhoneFormat(String phone) {
        String cleanPhone = phone.replaceAll("[\\s\\-\\(\\)\\.]", "");
        return cleanPhone.startsWith("+972") || cleanPhone.startsWith("05") ||
                cleanPhone.startsWith("02") || cleanPhone.startsWith("03") ||
                cleanPhone.startsWith("04") || cleanPhone.startsWith("08") ||
                cleanPhone.startsWith("09") || cleanPhone.startsWith("972");
    }

    private boolean isValidIsraeliID(String id) {
        if (id == null) return false;
        String cleanID = id.replaceAll("[\\s-]", "");

        if (cleanID.length() != 9) {
            Log.d(TAG, "❌ Israeli ID wrong length: " + cleanID.length() + " for: " + cleanID);
            return false;
        }

        if (cleanID.equals("000000000") || cleanID.equals("111111111") || cleanID.equals("123456789")) {
            Log.d(TAG, "❌ Israeli ID blacklisted: " + cleanID);
            return false;
        }

        boolean checksumValid = isValidIsraeliIDChecksum(cleanID);
        Log.d(TAG, "🔍 Israeli ID checksum for " + cleanID + ": " + checksumValid);

        return checksumValid;
    }

    private boolean isValidIsraeliIDChecksum(String id) {
        try {
            int sum = 0;
            Log.d(TAG, "🧮 Calculating checksum for: " + id);

            for (int i = 0; i < 9; i++) {
                int digit = Character.getNumericValue(id.charAt(i));
                int originalDigit = digit;

                if (i % 2 == 1) {
                    digit *= 2;
                    if (digit > 9) {
                        digit = digit / 10 + digit % 10;
                    }
                }

                sum += digit;
                Log.d(TAG, "   Position " + i + ": " + originalDigit + " -> " + digit + " (sum: " + sum + ")");
            }

            boolean valid = (sum % 10 == 0);
            Log.d(TAG, "🔍 Final sum: " + sum + ", valid: " + valid);
            return valid;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in checksum calculation", e);
            return false;
        }
    }
    private boolean isValidIsraeliPhone(String phone) {
        if (phone == null) return false;
        String cleanPhone = phone.replaceAll("[\\s\\-\\(\\)\\.]", "");

        Log.d(TAG, "🔍 Validating Israeli phone: '" + phone + "' -> '" + cleanPhone + "'");

        if (cleanPhone.startsWith("+972")) {
            cleanPhone = cleanPhone.substring(4);
            Log.d(TAG, "   Removed +972: '" + cleanPhone + "'");
        }

        if (cleanPhone.startsWith("0")) {
            cleanPhone = cleanPhone.substring(1);
            Log.d(TAG, "   Removed leading 0: '" + cleanPhone + "'");
        }

        if (cleanPhone.length() < 8 || cleanPhone.length() > 9) {
            Log.d(TAG, "   ❌ Invalid length: " + cleanPhone.length());
            return false;
        }

        String areaCode = cleanPhone.substring(0, 2);
        boolean valid = VALID_ISRAELI_AREA_CODES.contains(areaCode);

        Log.d(TAG, "   Area code: '" + areaCode + "', valid: " + valid);
        return valid;
    }

    private boolean isValidIsraeliBankAccount(String account) {
        if (account == null) return false;
        String cleanAccount = account.replaceAll("[\\s-]", "");

        if (cleanAccount.length() != 11) return false;

        String bankCode = cleanAccount.substring(0, 2);
        return VALID_ISRAELI_BANK_CODES.contains(bankCode);
    }

    private List<SensitiveMatch> removeOverlappingMatches(List<SensitiveMatch> matches) {
        if (matches.size() <= 1) return matches;

        List<SensitiveMatch> result = new ArrayList<>();

        matches.sort((a, b) -> {
            int posCompare = Integer.compare(a.start, b.start);
            return posCompare != 0 ? posCompare : Double.compare(b.confidence, a.confidence);
        });

        for (SensitiveMatch current : matches) {
            boolean shouldAdd = true;

            for (int i = 0; i < result.size(); i++) {
                SensitiveMatch existing = result.get(i);

                if (current.start < existing.end && current.end > existing.start) {
                    if (current.confidence > existing.confidence) {
                        result.set(i, current);
                        shouldAdd = false;
                        break;
                    } else if (current.confidence == existing.confidence && isMoreSpecificType(current.type, existing.type)) {
                        result.set(i, current);
                        shouldAdd = false;
                        break;
                    }
                    shouldAdd = false;
                    break;
                }
            }

            if (shouldAdd) {
                result.add(current);
            }
        }

        return result;
    }

    private boolean isMoreSpecificType(String type1, String type2) {
        if (type1.startsWith("ISRAELI_") && type2.startsWith("US_")) return true;
        if (type1.startsWith("US_") && type2.startsWith("ISRAELI_")) return false;
        if (type1.equals("CREDIT_CARD") && (type2.contains("PHONE") || type2.contains("SSN"))) return true;
        return false;
    }

    private String maskValue(String value, String type) {
        if (value == null || value.length() < 4) return "***";

        switch (type) {
            case "CREDIT_CARD":
                if (value.length() >= 8) {
                    return value.substring(0, 4) + " **** **** " + value.substring(value.length() - 4);
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

    // Stats class for testing
    public DetectionStats getDetectionStats(String text) {
        long startTime = System.currentTimeMillis();
        List<SensitiveMatch> matches = detectSensitiveData(text);
        long processingTime = System.currentTimeMillis() - startTime;
        return new DetectionStats(matches.size(), processingTime, text.length(), matches);
    }

    public static class DetectionStats {
        public final int matchCount;
        public final long processingTimeMs;
        public final int textLength;
        public final List<SensitiveMatch> matches;

        public DetectionStats(int matchCount, long processingTimeMs, int textLength, List<SensitiveMatch> matches) {
            this.matchCount = matchCount;
            this.processingTimeMs = processingTimeMs;
            this.textLength = textLength;
            this.matches = matches;
        }

        @Override
        public String toString() {
            return String.format("DetectionStats{matches=%d, time=%dms, textLength=%d}", matchCount, processingTimeMs, textLength);
        }
    }
}