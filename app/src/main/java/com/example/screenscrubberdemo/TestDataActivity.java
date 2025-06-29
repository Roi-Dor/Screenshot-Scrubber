package com.example.screenscrubberdemo;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class TestDataActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_data);

        // Display sample sensitive data for testing
        TextView sampleDataText = findViewById(R.id.sampleDataText);

        String sampleData = "Sample Sensitive Data for Testing:\n\n" +
                "Credit Card: 4532 1234 5678 9012\n" +
                "SSN: 123-45-6789\n" +
                "Phone: (555) 123-4567\n" +
                "Email: john.doe@example.com\n\n" +
                "Mixed Text:\n" +
                "Please call me at 555-987-6543 to discuss your " +
                "account ending in 9876. My SSN is 987-65-4321 " +
                "for verification purposes.\n\n" +
                "Take a screenshot of this screen to test the detection!";

        sampleDataText.setText(sampleData);
    }
}