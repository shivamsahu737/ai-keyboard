# Fleksy SDK Integration Setup

## Prerequisites
1. Obtain a Fleksy SDK license and API key from [Fleksy](https://www.fleksy.com/)
2. Add the Fleksy SDK dependency to `app/build.gradle`

## Setup Steps

### 1. Add Fleksy SDK Dependency
Replace the TODO in `app/build.gradle` with the actual Fleksy SDK dependency:
```gradle
dependencies {
    // ... existing dependencies
    implementation 'com.fleksy.sdk:fleksy-sdk:LATEST_VERSION'
}
```

### 2. Update FleksyIntegration.kt
Replace the placeholder code in `FleksyIntegration.kt` with actual Fleksy SDK calls.

### 3. Configure API Key
Add your Fleksy API key to `strings.xml`:
```xml
<string name="fleksy_api_key">YOUR_API_KEY_HERE</string>
```

### 4. Enable Fleksy Keyboard
In `AIKeyboardService.kt`, change:
```kotlin
private var useFleksyKeyboard = false // Set to true to use Fleksy
```
to:
```kotlin
private var useFleksyKeyboard = true
```

### 5. Update Initialization
In `AIKeyboardService.kt`, update the Fleksy initialization:
```kotlin
fleksyIntegration?.initialize(getString(R.string.fleksy_api_key))
```

## Features
- Full system IME integration
- Advanced AI-powered text processing
- Voice input capabilities
- Customizable themes and layouts

## Troubleshooting
- Ensure you have a valid Fleksy SDK license
- Check that the API key is correctly configured
- Verify internet connectivity for SDK activation