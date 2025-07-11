package com.example.screenscrubberdemo;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.screenscrubber.SensitiveDataDetector;
import java.util.List;

public class TestDataActivity extends AppCompatActivity {
    private TextView realDataText;
    private TextView fakeDataText;
    private TextView resultsText;
    private Button testRealButton;
    private Button testFakeButton;
    private Button testBothButton;

    private SensitiveDataDetector detector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_data);

        initializeViews();
        setupDetector();
        setupClickListeners();
        displayTestData();
    }

    private void initializeViews() {
        realDataText = findViewById(R.id.realDataText);
        fakeDataText = findViewById(R.id.fakeDataText);
        resultsText = findViewById(R.id.resultsText);
        testRealButton = findViewById(R.id.testRealButton);
        testFakeButton = findViewById(R.id.testFakeButton);
        testBothButton = findViewById(R.id.testBothButton);
    }

    private void setupDetector() {
        detector = new SensitiveDataDetector();
    }

    private void setupClickListeners() {
        testRealButton.setOnClickListener(v -> testRealData());
        testFakeButton.setOnClickListener(v -> testFakeData());
        testBothButton.setOnClickListener(v -> testBothData());
    }

    private void displayTestData() {
        // Real data examples (valid but not actual personal info)
        String realData = "✅ REAL DATA EXAMPLES\n\n" +
                "🇺🇸 US Data:\n" +
                "Credit Card: 4532 1234 5678 9012\n" +
                "SSN: 123-45-6789\n" +
                "Phone: (555) 987-6543\n" +
                "Phone (bare): 5551234567\n" +
                "Email: john.smith@company.com\n\n" +

                "🇮🇱 Israeli Data:\n" +
                "ID: 123456782\n" +
                "Phone: 050-123-4567\n" +
                "Bank: 10-123-456789\n" +
                "Jerusalem: 02-567-8901\n" +
                "Email: yoni@tech.co.il\n\n" +

                "📧 OCR Test:\n" +
                "Spaced email: test@example .com";

        realDataText.setText(realData);

        // Fake data examples that should be rejected
        String fakeData = "❌ FAKE DATA EXAMPLES\n\n" +
                "🇺🇸 US Fakes:\n" +
                "Fake Credit Card: 4444 4444 4444 4444\n" +
                "Fake SSN: 000-00-0000\n" +
                "Fake SSN: 111-11-1111\n" +
                "Fake Phone: 1111111111\n" +
                "Invalid Phone: 1234567890\n\n" +

                "🇮🇱 Israeli Fakes:\n" +
                "Fake ID: 123456789\n" +
                "Fake ID: 000000000\n" +
                "Invalid Phone: 099-123-4567\n" +
                "Invalid Bank: 99-123-456789\n" +
                "Invalid Area: 01-234-5678\n\n" +

                "💳 More CC Fakes:\n" +
                "Repeated: 5555 5555 5555 5555\n" +
                "No Luhn: 4532 1234 5678 9013";

        fakeDataText.setText(fakeData);
    }

    private void testRealData() {
        String testText = realDataText.getText().toString();
        runDetectionTest("REAL DATA", testText);
    }

    private void testFakeData() {
        String testText = fakeDataText.getText().toString();
        runDetectionTest("FAKE DATA", testText);
    }

    private void testBothData() {
        String combinedText = realDataText.getText().toString() + "\n\n" + fakeDataText.getText().toString();
        runDetectionTest("COMBINED DATA", combinedText);
    }

    private void runDetectionTest(String testType, String text) {
        try {
            long startTime = System.currentTimeMillis();

            // Run detection
            List<SensitiveDataDetector.SensitiveMatch> matches = detector.detectSensitiveData(text);

            long processingTime = System.currentTimeMillis() - startTime;

            // Build results
            StringBuilder results = new StringBuilder();
            results.append("🔍 ").append(testType).append(" TEST RESULTS\n");
            results.append("⏱️ Processing time: ").append(processingTime).append("ms\n");
            results.append("📊 Matches found: ").append(matches.size()).append("\n\n");

            if (matches.isEmpty()) {
                results.append("✅ No sensitive data detected\n");
                results.append("(This is expected for fake data)\n");
            } else {
                results.append("🚨 SENSITIVE DATA DETECTED:\n\n");

                for (int i = 0; i < matches.size(); i++) {
                    SensitiveDataDetector.SensitiveMatch match = matches.get(i);

                    results.append(String.format("%d. %s\n", i + 1, getDisplayName(match.type)));
                    results.append(String.format("   📍 Value: %s\n", maskSensitiveValue(match.value, match.type)));
                    results.append(String.format("   🎯 Confidence: %.1f%%\n", match.confidence * 100));
                    results.append(String.format("   📏 Position: %d-%d\n", match.start, match.end));
                    results.append("\n");
                }

                // Summary by category
                results.append("📈 DETECTION SUMMARY:\n");
                results.append(generateSummary(matches));
            }

            resultsText.setText(results.toString());

            // Show toast with key info
            String toastMsg = String.format("Found %d sensitive items in %dms", matches.size(), processingTime);
            Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            resultsText.setText("❌ ERROR: " + e.getMessage());
            Toast.makeText(this, "Detection failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String generateSummary(List<SensitiveDataDetector.SensitiveMatch> matches) {
        int usData = 0, israeliData = 0, creditCards = 0, emails = 0;
        double totalConfidence = 0;

        for (SensitiveDataDetector.SensitiveMatch match : matches) {
            totalConfidence += match.confidence;

            if (match.type.startsWith("US_")) {
                usData++;
            } else if (match.type.startsWith("ISRAELI_")) {
                israeliData++;
            } else if (match.type.equals("CREDIT_CARD")) {
                creditCards++;
            } else if (match.type.equals("EMAIL")) {
                emails++;
            }
        }

        StringBuilder summary = new StringBuilder();
        if (usData > 0) summary.append(String.format("🇺🇸 US Data: %d\n", usData));
        if (israeliData > 0) summary.append(String.format("🇮🇱 Israeli Data: %d\n", israeliData));
        if (creditCards > 0) summary.append(String.format("💳 Credit Cards: %d\n", creditCards));
        if (emails > 0) summary.append(String.format("📧 Emails: %d\n", emails));

        if (matches.size() > 0) {
            double avgConfidence = totalConfidence / matches.size();
            summary.append(String.format("🎯 Avg Confidence: %.1f%%\n", avgConfidence * 100));
        }

        return summary.toString();
    }

    private String getDisplayName(String type) {
        switch (type) {
            case "CREDIT_CARD": return "💳 Credit Card";
            case "US_SSN": return "🇺🇸 US Social Security Number";
            case "US_PHONE": return "🇺🇸 US Phone Number";
            case "ISRAELI_ID": return "🇮🇱 Israeli ID Number";
            case "ISRAELI_PHONE": return "🇮🇱 Israeli Phone Number";
            case "ISRAELI_BANK_ACCOUNT": return "🇮🇱 Israeli Bank Account";
            case "EMAIL": return "📧 Email Address";
            default: return "🔒 " + type.replace("_", " ");
        }
    }

    private String maskSensitiveValue(String value, String type) {
        if (value == null || value.length() < 4) return "***";

        switch (type) {
            case "CREDIT_CARD":
                String digits = value.replaceAll("[^0-9]", "");
                if (digits.length() >= 8) {
                    return digits.substring(0, 4) + " **** **** " + digits.substring(digits.length() - 4);
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
}