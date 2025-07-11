package com.example.screenscrubberdemo;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class TestDataActivity extends AppCompatActivity {
    private TextView sampleDataText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_data);

        initializeViews();
        displaySampleData();
    }

    private void initializeViews() {
        sampleDataText = findViewById(R.id.sampleDataText);
    }

    private void displaySampleData() {
        String sampleData = "🧪 TEST DATA FOR SCREENSHOT DETECTION\n\n" +
                "📊 US SENSITIVE DATA:\n" +
                "Credit Card: 4532 1234 5678 9012\n" +
                "SSN: 123-45-6789\n" +
                "Phone: (555) 123-4567\n" +
                "Email: john.doe@example.com\n\n" +
                "🇮🇱 ISRAELI SENSITIVE DATA:\n" +
                "ID Number: 123456782\n" +
                "Phone: 050-123-4567\n" +
                "Bank Account: 10-123-456789\n" +
                "Jerusalem Office: 02-567-8901\n" +
                "Email: yoni@tech.co.il\n\n" +
                "📱 MIXED CONTENT EXAMPLE:\n" +
                "Contact Information:\n" +
                "Name: John Cohen\n" +
                "US Phone: (555) 987-6543\n" +
                "Israeli Mobile: 052-987-6543\n" +
                "ID: 234567893\n" +
                "Credit Card: 5555 5555 5555 4444\n" +
                "Bank Account: 20-456-789012\n" +
                "Email: john.cohen@company.co.il\n\n" +
                "🏢 BUSINESS DOCUMENT:\n" +
                "Invoice #12345\n" +
                "Customer: Sarah Levi\n" +
                "ID: 345678904\n" +
                "Phone: 03-234-5678\n" +
                "Mobile: 054-876-5432\n" +
                "Payment Card: 4111 1111 1111 1111\n" +
                "Account: 31-789-012345\n\n" +
                "📸 INSTRUCTIONS:\n" +
                "1. Enable protection in the main screen\n" +
                "2. Take a screenshot of this screen\n" +
                "3. Check your notifications for detection results\n" +
                "4. Look in Pictures/ScreenScrubber_Censored for safe version\n\n" +
                "💡 TIP: Try different combinations:\n" +
                "• Screenshot with mixed US/Israeli data\n" +
                "• Camera photo of this screen\n" +
                "• Screenshot with only US data\n" +
                "• Screenshot with only Israeli data\n\n" +
                "🔒 All processing happens on your device.\n" +
                "No data is sent anywhere!";

        sampleDataText.setText(sampleData);
    }
}