package com.example.screenscrubber;

import java.util.Arrays;
import java.util.List;

/**
 * Enhanced test cases including Israeli sensitive data patterns
 */
public class TestDataGenerator {

    public static class TestCase {
        public final String description;
        public final String testText;
        public final String[] expectedTypes;
        public final boolean shouldDetect;
        public final String category;

        public TestCase(String description, String testText, String category, boolean shouldDetect, String... expectedTypes) {
            this.description = description;
            this.testText = testText;
            this.expectedTypes = expectedTypes;
            this.shouldDetect = shouldDetect;
            this.category = category;
        }

        public TestCase(String description, String testText, String... expectedTypes) {
            this(description, testText, "General", true, expectedTypes);
        }
    }

    /**
     * Get all test cases including Israeli patterns
     */
    public static List<TestCase> getTestCases() {
        return Arrays.asList(
                // US Credit Card Tests
                new TestCase("Valid Visa Card (spaces)",
                        "My credit card number is 4532 1234 5678 9012",
                        "US Credit Cards", true, "CREDIT_CARD"),

                new TestCase("Valid Visa Card (dashes)",
                        "Card: 4532-1234-5678-9012",
                        "US Credit Cards", true, "CREDIT_CARD"),

                new TestCase("Valid Mastercard",
                        "Payment method: 5555 5555 5555 4444",
                        "US Credit Cards", true, "CREDIT_CARD"),

                new TestCase("Invalid CC - All Same Digits",
                        "Card: 1111 1111 1111 1111",
                        "US Credit Cards", false),

                // US SSN Tests
                new TestCase("Valid US SSN (dashes)",
                        "Social Security Number: 123-45-6789",
                        "US SSN", true, "US_SSN"),

                new TestCase("Valid US SSN (no dashes)",
                        "SSN: 123456789",
                        "US SSN", true, "US_SSN"),

                new TestCase("Invalid US SSN - All zeros",
                        "SSN: 000-00-0000",
                        "US SSN", false),

                // US Phone Number Tests
                new TestCase("Valid US Phone (formatted)",
                        "Call me at (555) 123-4567",
                        "US Phone", true, "US_PHONE"),

                new TestCase("Valid US Phone (dashes)",
                        "Phone: 555-123-4567",
                        "US Phone", true, "US_PHONE"),

                new TestCase("Valid US Phone with country code",
                        "International: +1 555 123 4567",
                        "US Phone", true, "US_PHONE"),

                new TestCase("Invalid US Phone - starts with 0",
                        "Number: 055-123-4567",
                        "US Phone", false),

                // Israeli ID Tests
                new TestCase("Valid Israeli ID (no spaces)",
                        "ID Number: 123456782",
                        "Israeli ID", true, "ISRAELI_ID"),

                new TestCase("Valid Israeli ID (with spaces)",
                        "Teudat Zehut: 123 456 782",
                        "Israeli ID", true, "ISRAELI_ID"),

                new TestCase("Valid Israeli ID (with dashes)",
                        "Identity: 123-456-782",
                        "Israeli ID", true, "ISRAELI_ID"),

                new TestCase("Invalid Israeli ID - Sequential",
                        "ID: 123456789",
                        "Israeli ID", false),

                new TestCase("Invalid Israeli ID - All zeros",
                        "ID: 000000000",
                        "Israeli ID", false),

                // Israeli Phone Tests
                new TestCase("Valid Israeli Mobile (050)",
                        "Call me: 050-123-4567",
                        "Israeli Phone", true, "ISRAELI_PHONE"),

                new TestCase("Valid Israeli Mobile (052)",
                        "Mobile: 052 987 6543",
                        "Israeli Phone", true, "ISRAELI_PHONE"),

                new TestCase("Valid Israeli Landline (02)",
                        "Jerusalem office: 02-567-8901",
                        "Israeli Phone", true, "ISRAELI_PHONE"),

                new TestCase("Valid Israeli Landline (03)",
                        "Tel Aviv office: 03-234-5678",
                        "Israeli Phone", true, "ISRAELI_PHONE"),

                new TestCase("Valid Israeli with country code",
                        "International: +972-50-123-4567",
                        "Israeli Phone", true, "ISRAELI_PHONE"),

                new TestCase("Valid Israeli Mobile (054)",
                        "Contact: 054-876-5432",
                        "Israeli Phone", true, "ISRAELI_PHONE"),

                new TestCase("Valid Israeli Mobile (058)",
                        "Pelephone: 058-111-2222",
                        "Israeli Phone", true, "ISRAELI_PHONE"),

                new TestCase("Invalid Israeli Phone - Wrong area code",
                        "Phone: 060-123-4567",
                        "Israeli Phone", false),

                // Israeli Bank Account Tests
                new TestCase("Valid Bank Hapoalim Account",
                        "Account: 10-123-456789",
                        "Israeli Bank", true, "ISRAELI_BANK_ACCOUNT"),

                new TestCase("Valid Bank Leumi Account",
                        "Bank account: 20 456 789012",
                        "Israeli Bank", true, "ISRAELI_BANK_ACCOUNT"),

                new TestCase("Valid Bank Discount Account",
                        "Account number: 31-789-012345",
                        "Israeli Bank", true, "ISRAELI_BANK_ACCOUNT"),

                new TestCase("Valid Mizrahi-Tefahot Account",
                        "Transfer to: 17 234 567890",
                        "Israeli Bank", true, "ISRAELI_BANK_ACCOUNT"),

                new TestCase("Invalid Bank Account - Wrong bank code",
                        "Account: 99-123-456789",
                        "Israeli Bank", false),

                // Email Tests (Universal)
                new TestCase("Valid Email (simple)",
                        "Contact: john.doe@example.com",
                        "Email", true, "EMAIL"),

                new TestCase("Valid Email (Israeli domain)",
                        "Email: user@walla.co.il",
                        "Email", true, "EMAIL"),

                new TestCase("Valid Email (Hebrew domain)",
                        "Contact: manager@company.org.il",
                        "Email", true, "EMAIL"),

                // Mixed Content Tests
                new TestCase("Mixed US Types",
                        "Call 555-123-4567 about card 4532 1234 5678 9012",
                        "Mixed US", true, "US_PHONE", "CREDIT_CARD"),

                new TestCase("Mixed Israeli Types",
                        "ID: 123456782, Phone: 050-123-4567, Account: 10-123-456789",
                        "Mixed Israeli", true, "ISRAELI_ID", "ISRAELI_PHONE", "ISRAELI_BANK_ACCOUNT"),

                new TestCase("Mixed US and Israeli",
                        "US Phone: (555) 123-4567, Israeli ID: 123456782",
                        "Mixed International", true, "US_PHONE", "ISRAELI_ID"),

                new TestCase("All Types Mixed",
                        "Contact John at john@company.com or 555-123-4567. " +
                                "Israeli contact: 050-987-6543, ID: 123456782",
                        "All Mixed", true, "EMAIL", "US_PHONE", "ISRAELI_PHONE", "ISRAELI_ID"),

                // Realistic Israeli Scenarios
                new TestCase("Israeli Banking Document",
                        "Account Details:\n" +
                                "Bank Hapoalim Account: 10-123-456789\n" +
                                "Contact: 03-567-8901\n" +
                                "Customer ID: 123456782",
                        "Israeli Banking", true, "ISRAELI_BANK_ACCOUNT", "ISRAELI_PHONE", "ISRAELI_ID"),

                new TestCase("Israeli Contact Card",
                        "Yoni Cohen\n" +
                                "ID: 123456782\n" +
                                "Mobile: 050-123-4567\n" +
                                "Email: yoni@tech.co.il\n" +
                                "Office: 02-234-5678",
                        "Israeli Contact", true, "ISRAELI_ID", "ISRAELI_PHONE", "EMAIL"),

                new TestCase("Israeli Government Form",
                        "Citizen Information:\n" +
                                "ID Number: 123-456-782\n" +
                                "Phone: 052 987 6543\n" +
                                "Address: Tel Aviv",
                        "Israeli Government", true, "ISRAELI_ID", "ISRAELI_PHONE"),

                // Edge Cases
                new TestCase("Edge Case - Empty String",
                        "",
                        "Edge Cases", false),

                new TestCase("Edge Case - Israeli vs US Phone Conflict",
                        "Call 052-123-4567 or (555) 123-4567",
                        "Edge Cases", true, "ISRAELI_PHONE", "US_PHONE"),

                new TestCase("Clean Text - No Sensitive Data",
                        "This is just normal text with no sensitive information. " +
                                "The weather is nice today in Tel Aviv.",
                        "Clean", false),

                new TestCase("Clean Text - Numbers but not sensitive",
                        "Meeting room 123, floor 4, building 567 in Tel Aviv",
                        "Clean", false),

                // Performance Tests with Israeli Data
                new TestCase("Performance - Mixed Israeli Content",
                        "Office contacts: 02-123-4567, 03-234-5678, 04-345-6789. " +
                                "Mobile numbers: 050-111-2222, 052-333-4444, 054-555-6666. " +
                                "IDs: 123456782, 234567893, 345678904",
                        "Performance", true, "ISRAELI_PHONE", "ISRAELI_ID"),

                // Security Edge Cases
                new TestCase("Security - Partial Israeli ID",
                        "ID ending in 782",
                        "Security", false),

                new TestCase("Security - Masked Israeli Phone",
                        "Phone: 050-xxx-4567",
                        "Security", false),

                new TestCase("Security - Partial Bank Account",
                        "Account ending in 789",
                        "Security", false),

                // Cross-country confusion tests
                new TestCase("Distinguish Israeli from US",
                        "US: (555) 123-4567, Israeli: 050-123-4567",
                        "Cross-Country", true, "US_PHONE", "ISRAELI_PHONE"),

                new TestCase("Israeli format with US context",
                        "American visiting Israel: 050-123-4567",
                        "Cross-Country", true, "ISRAELI_PHONE"),

                // Format variation tests
                new TestCase("Israeli ID - Multiple Formats",
                        "ID formats: 123456782, 123 456 782, 123-456-782",
                        "Format Variations", true, "ISRAELI_ID"),

                new TestCase("Israeli Phone - Multiple Formats",
                        "Phones: 050-123-4567, 050 123 4567, 0501234567",
                        "Format Variations", true, "ISRAELI_PHONE"),

                new TestCase("Israeli Bank - Multiple Formats",
                        "Accounts: 10-123-456789, 10 123 456789, 10123456789",
                        "Format Variations", true, "ISRAELI_BANK_ACCOUNT")
        );
    }

    /**
     * Get test cases by category including Israeli categories
     */
    public static List<TestCase> getTestCasesByCategory(String category) {
        return getTestCases().stream()
                .filter(testCase -> testCase.category.equals(category))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get all categories including Israeli data types
     */
    public static List<String> getCategories() {
        return Arrays.asList(
                "US Credit Cards", "US SSN", "US Phone",
                "Israeli ID", "Israeli Phone", "Israeli Bank",
                "Email", "Mixed US", "Mixed Israeli", "Mixed International", "All Mixed",
                "Israeli Banking", "Israeli Contact", "Israeli Government",
                "Edge Cases", "Clean", "Performance", "Security",
                "Cross-Country", "Format Variations"
        );
    }

    /**
     * Get Israeli-specific test cases for focused testing
     */
    public static List<TestCase> getIsraeliTestCases() {
        return getTestCases().stream()
                .filter(testCase -> testCase.category.contains("Israeli") ||
                        Arrays.asList(testCase.expectedTypes).stream()
                                .anyMatch(type -> type.startsWith("ISRAELI_")))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get US-specific test cases
     */
    public static List<TestCase> getUSTestCases() {
        return getTestCases().stream()
                .filter(testCase -> testCase.category.contains("US") ||
                        Arrays.asList(testCase.expectedTypes).stream()
                                .anyMatch(type -> type.equals("CREDIT_CARD") ||
                                        type.equals("US_SSN") ||
                                        type.equals("US_PHONE")))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get enhanced smoke test cases including Israeli data
     */
    public static List<TestCase> getSmokeTestCases() {
        return Arrays.asList(
                new TestCase("Smoke - Valid Credit Card",
                        "Card: 4532 1234 5678 9012", "CREDIT_CARD"),

                new TestCase("Smoke - Valid US SSN",
                        "SSN: 123-45-6789", "US_SSN"),

                new TestCase("Smoke - Valid US Phone",
                        "Phone: (555) 123-4567", "US_PHONE"),

                new TestCase("Smoke - Valid Israeli ID",
                        "ID: 123456782", "ISRAELI_ID"),

                new TestCase("Smoke - Valid Israeli Phone",
                        "Mobile: 050-123-4567", "ISRAELI_PHONE"),

                new TestCase("Smoke - Valid Israeli Bank Account",
                        "Account: 10-123-456789", "ISRAELI_BANK_ACCOUNT"),

                new TestCase("Smoke - Valid Email",
                        "Email: test@company.com", "EMAIL"),

                new TestCase("Smoke - Clean Text",
                        "This has no sensitive data", "Clean", false),

                new TestCase("Smoke - Mixed Israeli Types",
                        "ID: 123456782, Phone: 050-123-4567",
                        "ISRAELI_ID", "ISRAELI_PHONE")
        );
    }

    /**
     * Generate test case for specific pattern testing
     */
    public static TestCase createCustomTestCase(String description, String text, String... expectedTypes) {
        return new TestCase(description, text, "Custom", expectedTypes.length > 0, expectedTypes);
    }

    /**
     * Get statistics about test cases including Israeli coverage
     */
    public static TestStats getTestStats() {
        List<TestCase> allCases = getTestCases();
        int shouldDetect = 0;
        int shouldNotDetect = 0;
        int israeliCases = 0;
        int usCases = 0;

        for (TestCase testCase : allCases) {
            if (testCase.shouldDetect) {
                shouldDetect++;
            } else {
                shouldNotDetect++;
            }

            // Count Israeli cases
            if (testCase.category.contains("Israeli") ||
                    Arrays.asList(testCase.expectedTypes).stream()
                            .anyMatch(type -> type.startsWith("ISRAELI_"))) {
                israeliCases++;
            }

            // Count US cases
            if (testCase.category.contains("US") ||
                    Arrays.asList(testCase.expectedTypes).stream()
                            .anyMatch(type -> type.equals("CREDIT_CARD") ||
                                    type.equals("US_SSN") ||
                                    type.equals("US_PHONE"))) {
                usCases++;
            }
        }

        return new TestStats(allCases.size(), shouldDetect, shouldNotDetect,
                getCategories().size(), israeliCases, usCases);
    }

    public static class TestStats {
        public final int totalCases;
        public final int shouldDetectCases;
        public final int shouldNotDetectCases;
        public final int categories;
        public final int israeliCases;
        public final int usCases;

        public TestStats(int totalCases, int shouldDetectCases, int shouldNotDetectCases,
                         int categories, int israeliCases, int usCases) {
            this.totalCases = totalCases;
            this.shouldDetectCases = shouldDetectCases;
            this.shouldNotDetectCases = shouldNotDetectCases;
            this.categories = categories;
            this.israeliCases = israeliCases;
            this.usCases = usCases;
        }

        @Override
        public String toString() {
            return String.format("TestStats{total=%d, detect=%d, clean=%d, categories=%d, israeli=%d, us=%d}",
                    totalCases, shouldDetectCases, shouldNotDetectCases,
                    categories, israeliCases, usCases);
        }
    }
}