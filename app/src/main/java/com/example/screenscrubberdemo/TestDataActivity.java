package com.example.screenscrubberdemo;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.screenscrubber.SensitiveDataDetector;
import com.example.screenscrubber.TestDataGenerator;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class TestDataActivity extends AppCompatActivity {
    private TextView sampleDataText;
    private Button runTestsButton;
    private ExecutorService testExecutor;
    private Handler mainHandler;
    private boolean isTestRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_data);

        initializeViews();
        setupClickListeners();
        displaySampleData();

        testExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    private void initializeViews() {
        sampleDataText = findViewById(R.id.sampleDataText);

        // Create a new button for testing if it doesn't exist
        runTestsButton = findViewById(R.id.runTestsButton);
        if (runTestsButton == null) {
            // If the button doesn't exist in layout, we'll add testing via the existing sample text
            // For now, we'll just use the existing layout structure
        }
    }

    private void setupClickListeners() {
        if (runTestsButton != null) {
            runTestsButton.setOnClickListener(v -> runTests());
        }

        // Add click listener to sample text as a fallback for testing
        sampleDataText.setOnLongClickListener(v -> {
            runTests();
            return true;
        });
    }

    private void displaySampleData() {
        String sampleData = "🧪 TEST DATA FOR SCREENSHOT DETECTION\n\n" +
                "📊 SAMPLE SENSITIVE DATA:\n" +
                "Credit Card: 4532 1234 5678 9012\n" +
                "SSN: 123-45-6789\n" +
                "Phone: (555) 123-4567\n" +
                "Email: john.doe@example.com\n\n" +
                "📱 MIXED CONTENT:\n" +
                "Please call me at 555-987-6543 to discuss your " +
                "account ending in 9012. My SSN is 987-65-4321 " +
                "for verification purposes.\n\n" +
                "📸 INSTRUCTIONS:\n" +
                "1. Take a screenshot of this screen to test detection\n" +
                "2. Long-press this text to run automated tests\n" +
                "3. Check notifications for detection results\n\n" +
                "⚡ TESTING:\n" +
                "• Long-press anywhere on this text to run validation tests\n" +
                "• Total test cases available: " + getTestCaseCount();

        sampleDataText.setText(sampleData);
    }

    private int getTestCaseCount() {
        try {
            return TestDataGenerator.getTestStats().totalCases;
        } catch (Exception e) {
            return 0; // Fallback if TestDataGenerator is not available yet
        }
    }

    private void runTests() {
        if (isTestRunning) {
            showResult("Tests already running...");
            return;
        }

        isTestRunning = true;
        showResult("🚀 Starting validation tests...\n");

        testExecutor.submit(() -> {
            try {
                runTestsInBackground();
            } catch (Exception e) {
                mainHandler.post(() -> {
                    showResult("❌ Test execution failed: " + e.getMessage());
                    isTestRunning = false;
                });
            }
        });
    }

    private void runTestsInBackground() {
        SensitiveDataDetector detector = new SensitiveDataDetector();
        StringBuilder results = new StringBuilder();

        long startTime = System.currentTimeMillis();
        int passed = 0;
        int failed = 0;

        // Get smoke test cases for quick validation
        List<TestDataGenerator.TestCase> testCases = TestDataGenerator.getSmokeTestCases();
        int total = testCases.size();

        results.append("🧪 SMOKE TEST RESULTS\n");
        results.append("═".repeat(30)).append("\n\n");

        for (TestDataGenerator.TestCase testCase : testCases) {
            TestResult result = runSingleTest(detector, testCase);

            if (result.passed) {
                passed++;
                results.append("✅ ");
            } else {
                failed++;
                results.append("❌ ");
            }

            results.append(testCase.description).append("\n");

            if (!result.passed) {
                results.append("   Expected: ").append(Arrays.toString(testCase.expectedTypes)).append("\n");
                results.append("   Got: ").append(result.actualTypes).append("\n");
                if (result.errorMessage != null) {
                    results.append("   Error: ").append(result.errorMessage).append("\n");
                }
            }

            // Update progress
            int finalPassed = passed;
            int finalFailed = failed;
            mainHandler.post(() -> {
                String progress = "Progress: " + (finalPassed + finalFailed) + "/" + total + " completed\n";
                showResult(progress);
            });
        }

        // Final summary
        long totalTime = System.currentTimeMillis() - startTime;
        double successRate = (double) passed / total * 100;

        results.append("\n📊 SUMMARY\n");
        results.append("═".repeat(20)).append("\n");
        results.append("Total: ").append(total).append("\n");
        results.append("Passed: ").append(passed).append(" (").append(String.format("%.1f", successRate)).append("%)\n");
        results.append("Failed: ").append(failed).append("\n");
        results.append("Time: ").append(totalTime).append("ms\n\n");

        if (successRate >= 95.0) {
            results.append("🎉 EXCELLENT! Detection is working perfectly.\n");
        } else if (successRate >= 85.0) {
            results.append("✅ GOOD! Detection is working well.\n");
        } else {
            results.append("⚠️ Issues detected - check implementation.\n");
        }

        results.append("\n💡 Tip: Take a screenshot now to test real detection!");

        // Update UI on main thread
        mainHandler.post(() -> {
            showResult(results.toString());
            isTestRunning = false;
        });
    }

    private TestResult runSingleTest(SensitiveDataDetector detector, TestDataGenerator.TestCase testCase) {
        try {
            long startTime = System.currentTimeMillis();
            List<SensitiveDataDetector.SensitiveMatch> matches = detector.detectSensitiveData(testCase.testText);
            long processingTime = System.currentTimeMillis() - startTime;

            Set<String> expectedTypes = new HashSet<>(Arrays.asList(testCase.expectedTypes));
            Set<String> actualTypes = matches.stream()
                    .map(match -> match.type)
                    .collect(Collectors.toSet());

            boolean passed;
            String errorMessage = null;

            if (testCase.shouldDetect) {
                // Should detect specific types
                passed = expectedTypes.equals(actualTypes);
                if (!passed) {
                    errorMessage = "Type mismatch";
                }
            } else {
                // Should not detect anything
                passed = matches.isEmpty();
                if (!passed) {
                    errorMessage = "Unexpected detection";
                }
            }

            return new TestResult(passed, actualTypes.toString(), errorMessage, processingTime);

        } catch (Exception e) {
            return new TestResult(false, "ERROR", "Exception: " + e.getMessage(), 0);
        }
    }

    private void showResult(String message) {
        // Update the sample text with results
        String currentText = sampleDataText.getText().toString();

        // If this is the first result message, clear the sample data
        if (message.contains("🚀 Starting")) {
            sampleDataText.setText(message);
        } else {
            // Append to existing results
            sampleDataText.setText(currentText + message);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (testExecutor != null && !testExecutor.isShutdown()) {
            testExecutor.shutdown();
        }
    }

    // Helper class for test results
    private static class TestResult {
        final boolean passed;
        final String actualTypes;
        final String errorMessage;
        final long processingTime;

        TestResult(boolean passed, String actualTypes, String errorMessage, long processingTime) {
            this.passed = passed;
            this.actualTypes = actualTypes;
            this.errorMessage = errorMessage;
            this.processingTime = processingTime;
        }
    }
}