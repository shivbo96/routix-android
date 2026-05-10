package link.routix.example

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import link.routix.sdk.Routix

class MainActivity : AppCompatActivity() {
    private lateinit var codeDisplay: TextView
    private lateinit var sourceText: TextView
    private lateinit var confidenceText: TextView
    private lateinit var resolveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        codeDisplay = findViewById(R.id.code_display)
        sourceText = findViewById(R.id.source_text)
        confidenceText = findViewById(R.id.confidence_text)
        resolveButton = findViewById(R.id.resolve_button)

        // 1. Initialize Routix
        Routix.initialize(this, "rtx_test_key_123")

        // 🌟 THE REACTIVE PATTERN:
        // Set a global listener to handle all attribution events.
        Routix.setAttributionListener { match ->
            runOnUiThread {
                codeDisplay.text = match.shortCode ?: "N/A"
                sourceText.text = "Source: ${match.matchSource}"
                confidenceText.text = "Confidence: ${(match.confidence ?: 0.0) * 100}%"
            }
        }

        // 2. TRIGGER: Check deferred attribution on start
        Routix.resolve { /* Handled by listener */ }

        // 🔗 PRODUCTION INTEGRATION:
        // To handle real system deep links, pass the intent data to Routix:
        // intent?.data?.toString()?.let { Routix.handleDeepLink(it) }

        // 3. TRIGGER: Simulate a direct link click (Flow A)
        Handler(Looper.getMainLooper()).postDelayed({
            Routix.handleDeepLink("https://routix.link/SUMMER24?code=SUMMER24")
        }, 2000)

        resolveButton.setOnClickListener {
            resolveButton.isEnabled = false
            Routix.resolve { 
                runOnUiThread { resolveButton.isEnabled = true }
            }
        }
    }
}
