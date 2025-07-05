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
            Pattern.compile("\\b(?:\\d{4}[\\s-]?){3}\\d{4}\\b|\\b\\d{4}\\s+\\d{4}\\s+\\d{4}\\s+\\d{4}\\b");

    // More restrictive US SSN - avoid Israeli ID conflicts
    private static final Pattern US_SSN_PATTERN =
            Pattern.compile("\\b\\d{3}[-\\s]\\d{2}[-\\s]\\d{4}\\b");

    // Enhanced US Phone - handle parentheses and various formats
    private static final Pattern US_PHONE_PATTERN =
            Pattern.compile("\\b(?:\\+?1[\\s-]?)?(?:\\(?[2-9]\\d{2}\\)?[\\s.-]?)[2-9]\\d{2}[\\s.-]?\\d{4}\\b|\\b\\([2-9]\\d{2}\\)\\s?[2-9]\\d{2}-\\d{4}\\b");

    // Enhanced EMAIL - handle "example .com" OCR issue
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("\\b[a-zA-Z0-9][a-zA-Z0-9._%+-]*@[a-zA-Z0-9][a-zA-Z0-9.-]*\\s?\\.\\s?[a-zA-Z]{2,6}\\b");

    // Israeli patterns - more specific to avoid US conflicts
    private static final Pattern ISRAELI_ID_PATTERN =
            Pattern.compile("\\b\\d{9}\\b|\\b\\d{3}[\\s-]\\d{3}[\\s-]\\d{3}\\b");

    private static final Pattern ISRAELI_PHONE_PATTERN =
            Pattern.compile("\\b(?:\\+972[\\s-]?|0)?(?:2|3|4|5[0-9]|7[2-9]|8|9)[\\s-]?\\d{3}[\\s-]?\\d{4}\\b");

    private static final Pattern ISRAELI_BANK_ACCOUNT_PATTERN =
            Pattern.compile("\\b(?:0[1-9]|[1-9][0-9])[\\s-]?\\d{3}[\\s-]?\\d{6}\\b");

    // Validation sets (unchanged)
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
        Log.d(TAG, "🔍 PROCESSING TEXT (" + cleanText.length() + " chars):");
        Log.d(TAG, "📝 FIRST 200 CHARS: " + cleanText.substring(0, Math.min(200, cleanText.length())));

        try {
            // CRITICAL: Order matters! Check Israeli patterns FIRST to avoid US conflicts
            Log.d(TAG, "🇮🇱 === ISRAELI DETECTION PHASE ===");
            findIsraeliIDs(cleanText, matches);
            findIsraeliPhones(cleanText, matches);
            findIsraeliBankAccounts(cleanText, matches);

            Log.d(TAG, "🇺🇸 === US DETECTION PHASE ===");
            // Then check US patterns
            findCreditCards(cleanText, matches);
            findUSSSNs(cleanText, matches);
            findUSPhones(cleanText, matches);

            Log.d(TAG, "🌐 === UNIVERSAL DETECTION PHASE ===");
            // Universal patterns last
            findEmails(cleanText, matches);

            // Remove overlapping matches with better logic
            Log.d(TAG, "🔧 === OVERLAP RESOLUTION ===");
            List<SensitiveMatch> originalMatches = new ArrayList<>(matches);
            matches = removeOverlappingMatches(matches);

            Log.d(TAG, "📊 DETECTION SUMMARY:");
            Log.d(TAG, "   Original matches: " + originalMatches.size());
            Log.d(TAG, "   Final matches: " + matches.size());

            for (SensitiveMatch match : matches) {
                Log.d(TAG, "✅ FINAL: " + match.type + " = '" + maskValue(match.value, match.type) + "' at " + match.start + "-" + match.end);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in detection", e);
        }

        return matches;
    }

    private void findIsraeliIDs(String text, List<SensitiveMatch> matches) {
        Matcher matcher = ISRAELI_ID_PATTERN.matcher(text);
        while (matcher.find()) {
            String match = matcher.group();
            Log.d(TAG, "Israeli ID candidate: " + match);

            if (isValidIsraeliID(match)) {
                // High confidence for Israeli ID
                matches.add(new SensitiveMatch("ISRAELI_ID", match, matcher.start(), matcher.end(), 0.95));
                Log.d(TAG, "✅ Israeli ID confirmed: " + match);
            }
        }
    }

    private void findUSSSNs(String text, List<SensitiveMatch> matches) {
        Matcher matcher = US_SSN_PATTERN.matcher(text);
        while (matcher.find()) {
            String match = matcher.group();
            Log.d(TAG, "US SSN candidate: " + match);

            // Check if this overlaps with any Israeli ID
            boolean overlapsIsraeliID = false;
            for (SensitiveMatch existing : matches) {
                if (existing.type.equals("ISRAELI_ID") &&
                        matcher.start() < existing.end && matcher.end() > existing.start) {
                    overlapsIsraeliID = true;
                    Log.d(TAG, "❌ US SSN rejected - overlaps Israeli ID");
                    break;
                }
            }

            if (!overlapsIsraeliID) {
                String cleanSSN = match.replaceAll("[-\\s]", "");
                if (cleanSSN.length() == 9 && !cleanSSN.equals("000000000") && !cleanSSN.equals("111111111")) {
                    matches.add(new SensitiveMatch("US_SSN", match, matcher.start(), matcher.end(), 0.9));
                    Log.d(TAG, "✅ US SSN confirmed: " + match);
                }
            }
        }
    }

    private void findUSPhones(String text, List<SensitiveMatch> matches) {
        Matcher matcher = US_PHONE_PATTERN.matcher(text);
        while (matcher.find()) {
            String match = matcher.group();
            Log.d(TAG, "US Phone candidate: '" + match + "'");

            // Skip if it's Israeli format
            if (isIsraeliPhoneFormat(match)) {
                Log.d(TAG, "❌ US Phone rejected - Israeli format");
                continue;
            }

            // Check for overlap with Israeli phones
            boolean overlapsIsraeliPhone = false;
            for (SensitiveMatch existing : matches) {
                if (existing.type.equals("ISRAELI_PHONE") &&
                        matcher.start() < existing.end && matcher.end() > existing.start) {
                    overlapsIsraeliPhone = true;
                    break;
                }
            }

            if (!overlapsIsraeliPhone) {
                String cleanPhone = match.replaceAll("[\\s\\-\\(\\)\\.]", "");
                Log.d(TAG, "Clean US Phone: '" + cleanPhone + "' (length: " + cleanPhone.length() + ")");

                if (cleanPhone.startsWith("+1")) cleanPhone = cleanPhone.substring(2);
                if (cleanPhone.startsWith("1") && cleanPhone.length() == 11) cleanPhone = cleanPhone.substring(1);

                if (cleanPhone.length() == 10 && cleanPhone.charAt(0) >= '2' && cleanPhone.charAt(0) <= '9') {
                    matches.add(new SensitiveMatch("US_PHONE", match, matcher.start(), matcher.end(), 0.8));
                    Log.d(TAG, "✅ US Phone confirmed: '" + match + "'");
                } else {
                    Log.d(TAG, "❌ US Phone rejected - invalid format: length=" + cleanPhone.length() + ", first=" + (cleanPhone.length() > 0 ? cleanPhone.charAt(0) : "none"));
                }
            }
        }
    }

    private void findIsraeliPhones(String text, List<SensitiveMatch> matches) {
        Matcher matcher = ISRAELI_PHONE_PATTERN.matcher(text);
        while (matcher.find()) {
            String match = matcher.group();
            Log.d(TAG, "Israeli Phone candidate: " + match);

            if (isValidIsraeliPhone(match)) {
                matches.add(new SensitiveMatch("ISRAELI_PHONE", match, matcher.start(), matcher.end(), 0.9));
                Log.d(TAG, "✅ Israeli Phone confirmed: " + match);
            }
        }
    }

    private void findIsraeliBankAccounts(String text, List<SensitiveMatch> matches) {
        Matcher matcher = ISRAELI_BANK_ACCOUNT_PATTERN.matcher(text);
        while (matcher.find()) {
            String match = matcher.group();
            Log.d(TAG, "Israeli Bank candidate: " + match);

            if (isValidIsraeliBankAccount(match)) {
                matches.add(new SensitiveMatch("ISRAELI_BANK_ACCOUNT", match, matcher.start(), matcher.end(), 0.85));
                Log.d(TAG, "✅ Israeli Bank confirmed: " + match);
            }
        }
    }

    private void findCreditCards(String text, List<SensitiveMatch> matches) {
        Matcher matcher = CREDIT_CARD_PATTERN.matcher(text);
        while (matcher.find()) {
            String match = matcher.group();
            Log.d(TAG, "Credit Card candidate: '" + match + "'");

            String cleanNumber = match.replaceAll("[\\s-]", "");
            Log.d(TAG, "Clean CC number: '" + cleanNumber + "' (length: " + cleanNumber.length() + ")");

            if (cleanNumber.length() >= 13 && cleanNumber.length() <= 19) {
                // Basic validation - starts with valid prefixes
                char firstDigit = cleanNumber.charAt(0);
                if (firstDigit == '4' || firstDigit == '5' || firstDigit == '3' || firstDigit == '6') {
                    matches.add(new SensitiveMatch("CREDIT_CARD", match, matcher.start(), matcher.end(), 0.9));
                    Log.d(TAG, "✅ Credit Card confirmed: '" + match + "'");
                } else {
                    Log.d(TAG, "❌ Credit Card rejected - invalid prefix: " + firstDigit);
                }
            } else {
                Log.d(TAG, "❌ Credit Card rejected - invalid length: " + cleanNumber.length());
            }
        }
    }

    private void findEmails(String text, List<SensitiveMatch> matches) {
        // Special handling for OCR-damaged emails like "example .com"
        String preprocessedText = text.replaceAll("(\\w)\\s+\\.\\s+(\\w)", "$1.$2");

        Matcher matcher = EMAIL_PATTERN.matcher(preprocessedText);
        while (matcher.find()) {
            String match = matcher.group();
            Log.d(TAG, "Email candidate: " + match);

            // Normalize the email
            String normalizedEmail = match.replaceAll("\\s+", "");

            if (normalizedEmail.contains("@") && normalizedEmail.contains(".") &&
                    normalizedEmail.length() > 5 && isValidEmailFormat(normalizedEmail)) {

                // Find original position in unmodified text
                int originalStart = findOriginalPosition(text, match, matcher.start());
                int originalEnd = originalStart + match.length();

                matches.add(new SensitiveMatch("EMAIL", match, originalStart, originalEnd, 0.95));
                Log.d(TAG, "✅ Email confirmed: " + match);
            }
        }
    }

    private boolean isValidCreditCard(String number) {
        // Basic Luhn algorithm check
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(number.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }

    private boolean isValidEmailFormat(String email) {
        return email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$");
    }

    private int findOriginalPosition(String originalText, String match, int approximatePos) {
        // Simple search around the approximate position
        int start = Math.max(0, approximatePos - 10);
        int end = Math.min(originalText.length(), approximatePos + match.length() + 10);
        String searchArea = originalText.substring(start, end);

        int found = searchArea.indexOf(match);
        return found >= 0 ? start + found : approximatePos;
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

        if (cleanID.length() != 9) return false;
        if (cleanID.equals("000000000") || cleanID.equals("111111111") || cleanID.equals("123456789")) return false;

        return isValidIsraeliIDChecksum(cleanID);
    }

    private boolean isValidIsraeliIDChecksum(String id) {
        try {
            int sum = 0;
            for (int i = 0; i < 9; i++) {
                int digit = Character.getNumericValue(id.charAt(i));
                if (i % 2 == 1) {
                    digit *= 2;
                    if (digit > 9) {
                        digit = digit / 10 + digit % 10;
                    }
                }
                sum += digit;
            }
            return sum % 10 == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isValidIsraeliPhone(String phone) {
        if (phone == null) return false;
        String cleanPhone = phone.replaceAll("[\\s\\-\\(\\)\\.]", "");

        if (cleanPhone.startsWith("+972")) {
            cleanPhone = cleanPhone.substring(4);
        }

        if (cleanPhone.startsWith("0")) {
            cleanPhone = cleanPhone.substring(1);
        }

        if (cleanPhone.length() < 8 || cleanPhone.length() > 9) {
            return false;
        }

        String areaCode = cleanPhone.substring(0, 2);
        return VALID_ISRAELI_AREA_CODES.contains(areaCode);
    }

    private boolean isValidIsraeliBankAccount(String account) {
        if (account == null) return false;
        String cleanAccount = account.replaceAll("[\\s-]", "");

        if (cleanAccount.length() != 11) {
            return false;
        }

        String bankCode = cleanAccount.substring(0, 2);
        return VALID_ISRAELI_BANK_CODES.contains(bankCode);
    }

    private List<SensitiveMatch> removeOverlappingMatches(List<SensitiveMatch> matches) {
        if (matches.size() <= 1) return matches;

        List<SensitiveMatch> result = new ArrayList<>();

        // Sort by position first, then by confidence (higher confidence first)
        matches.sort((a, b) -> {
            int posCompare = Integer.compare(a.start, b.start);
            return posCompare != 0 ? posCompare : Double.compare(b.confidence, a.confidence);
        });

        for (SensitiveMatch current : matches) {
            boolean shouldAdd = true;

            // Check against existing results
            for (int i = 0; i < result.size(); i++) {
                SensitiveMatch existing = result.get(i);

                // Check for overlap
                if (current.start < existing.end && current.end > existing.start) {
                    // There's an overlap
                    if (current.confidence > existing.confidence) {
                        // Replace existing with current
                        result.set(i, current);
                        shouldAdd = false;
                        break;
                    } else {
                        // Keep existing, don't add current
                        shouldAdd = false;
                        break;
                    }
                }
            }

            if (shouldAdd) {
                result.add(current);
            }
        }

        return result;
    }

    private String maskValue(String value, String type) {
        if (value == null || value.length() < 4) return "***";

        switch (type) {
            case "CREDIT_CARD":
                if (value.length() >= 8) {
                    return value.substring(0, 4) + " **** **** " + value.substring(value.length() - 4);
                }
                return "****";
            case "US_SSN":
                return "***-**-****";
            case "US_PHONE":
                return "***-***-****";
            case "ISRAELI_ID":
                return "***-***-***";
            case "ISRAELI_PHONE":
                return "***-***-****";
            case "ISRAELI_BANK_ACCOUNT":
                return "**-***-******";
            case "EMAIL":
                int atIndex = value.indexOf('@');
                if (atIndex > 2) {
                    return value.substring(0, 2) + "***" + value.substring(atIndex);
                }
                return "***@***";
            default:
                return "***";
        }
    }

    // Public methods for stats and testing (unchanged)
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

        public DetectionStats(int matchCount, long processingTimeMs, int textLength,
                              List<SensitiveMatch> matches) {
            this.matchCount = matchCount;
            this.processingTimeMs = processingTimeMs;
            this.textLength = textLength;
            this.matches = matches;
        }

        @Override
        public String toString() {
            return String.format("DetectionStats{matches=%d, time=%dms, textLength=%d}",
                    matchCount, processingTimeMs, textLength);
        }
    }
}