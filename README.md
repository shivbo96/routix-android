[![Maven Central](https://img.shields.io/maven-central/v/link.routix.sdk/routix)](https://central.sonatype.com/artifact/link.routix.sdk/routix)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

The official **Routix SDK** for native Android applications. Professional-grade attribution, deep linking, and conversion tracking.

---

## 📦 Installation

Ensure you have `mavenCentral()` in your root `build.gradle` repositories, then add the dependency to your module's `build.gradle`:

```gradle
dependencies {
    implementation 'link.routix.sdk:routix:1.0.4'
    implementation 'com.android.installreferrer:installreferrer:2.2'
}
```

## 🚀 Technical Capabilities

The Routix Android SDK provides industry-standard attribution reliability:

- **Google Play Install Referrer**: 100% deterministic attribution for new installs via the official Google Play referrer API.
- **Android App Links**: Seamless app opening for existing users without browser redirects.
- **Deferred Deep Linking**: Carriers campaign metadata through the Play Store to the first app open.
- **Probabilistic Matching**: High-accuracy fallback matching for browsers and non-store environments.
- **Low Footprint**: Optimized binary size with minimal impact on your APK.

---

## 🛠️ Usage

### 1. Initialize the SDK
Initialize in your `Application` class.

```kotlin
import link.routix.sdk.Routix

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Routix.initialize(this, "your_api_key")
    }
}
```

### 2. Resolve Deep Links (Deferred)
```kotlin
// On first app open (deferred deep linking)
Routix.resolve { match ->
    if (match.success) {
        Log.d("Routix", "Attributed to: ${match.shortCode}")
        // Handle your navigation or attribution logic
    }
}
```

### 3. Handle App Links (Direct)
```kotlin
// In your Activity's onNewIntent or onCreate
val data: Uri? = intent.data
if (data != null) {
    Routix.handleDeepLink(data) { match ->
        // Handle active deep link
    }
}
```
### 4. Track Events
Tie conversion events directly to a campaign short code for ROI analysis.
```kotlin
Routix.trackSale("SUMMER_24", 29.99, "USD")
```

### 5. Track Custom Events
Track workspace-level actions like signups or tutorial completions.
```kotlin
Routix.trackCustomEvent("user_signup", mapOf("method" to "google"))
```

---

## 📄 License
MIT License.
