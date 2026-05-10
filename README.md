# Routix Android SDK 🚀

[![JitPack](https://jitpack.io/v/shivbo96/routix-android.svg)](https://jitpack.io/#shivbo96/routix-android)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

The official **Routix SDK** for native Android applications. Professional-grade attribution, deep linking, and conversion tracking.

---

## 📦 Installation

Add the JitPack repository and the dependency to your `build.gradle`:

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.shivbo96:routix-android:1.0.0'
    implementation 'com.android.installreferrer:installreferrer:2.2'
}
```

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

### 2. Resolve Deep Links
```kotlin
Routix.resolve { match ->
    if (match.success) {
        Log.d("Routix", "Attributed to: ${match.shortCode}")
    }
}
```

### 3. Track Events
```kotlin
Routix.trackSale("SUMMER_24", 29.99, "USD")
```

---

## 📄 License
MIT License.
