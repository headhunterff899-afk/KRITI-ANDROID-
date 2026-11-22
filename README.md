# Kriti Android (starter)
Android Compose chat starter with configurable OpenAI-compatible API provider and encrypted local preference storage.


## Build APK
Use GitHub Actions workflow `Build Kriti APK`, or run `gradle assembleDebug` in an Android/Java environment.

## Provider settings
- Base URL should end with `/v1`, e.g. `https://api.groq.com/openai/v1`.
- Model ID is provider-specific.
- API keys are saved with Android EncryptedSharedPreferences. Avoid sharing keys or committing them.

Provider account usage and rate limits apply.
