# Yalla SIP Phone

Desktop VoIP softphone for call center operators. Built with Kotlin, Compose Desktop, and pjsip.

**Version:** 0.3.0-alpha (Phase 3 complete)

## Tech Stack

| Component        | Technology                             |
|------------------|----------------------------------------|
| Language         | Kotlin 2.1.20                          |
| UI               | Compose Desktop 1.8.2 (Material 3)    |
| SIP engine       | pjsip via SWIG/JNI bindings           |
| Navigation       | Decompose 3.4.0                        |
| DI               | Koin 4.1.1                             |
| Theming          | MaterialKolor 2.0.0                    |
| Embedded browser | JCEF (Chromium, for dispatcher web UI) |
| Logging          | Logback + kotlin-logging               |

## Prerequisites

- JDK 21+
- Gradle 8.x (wrapper included)
- pjsip native library for your platform:
  - macOS: `libs/libpjsua2.dylib`
  - Windows: `libs/pjsua2.dll`
  - Linux: `libs/libpjsua2.so`

## Build and Run

```bash
./gradlew run            # Run the app
./gradlew test           # Run tests
./gradlew build          # Full build
./gradlew runDemo        # Run with fake SIP engines (no pjsip needed)
./gradlew packageDmg     # Package for macOS
./gradlew packageMsi     # Package for Windows
./gradlew packageDeb     # Package for Linux
```

## Architecture

The project follows Clean Architecture with an MVI pattern using StateFlow.

```
uz.yalla.sipphone/
├── domain/        Pure interfaces (RegistrationEngine, CallEngine, SipStackLifecycle)
├── data/
│   ├── pjsip/     pjsip implementation via facade + managers
│   ├── jcef/      Chromium browser bridge for dispatcher web UI
│   ├── settings/  Persistent settings
│   └── auth/      Authentication data layer
├── feature/
│   ├── login/     SIP login screen
│   └── main/      Main screen (toolbar + webview)
├── navigation/    Decompose-based type-safe navigation
├── di/            Koin modules
└── ui/            Theme, components, strings
```

The domain layer defines pure Kotlin interfaces for SIP operations. The data layer provides concrete implementations backed by pjsip (via JNI) and JCEF. Features consume domain interfaces through Koin injection, keeping the UI layer decoupled from SIP internals.

## Project Status

- Phase 1 ✅ SIP Registration
- Phase 2 ✅ Calling (outbound/inbound, answer, hangup)
- Phase 3 ✅ Architecture refactor, UI redesign, JCEF integration, packaging
- Phase 4 -- Auto-reconnect, TLS/SRTP, credential encryption
- Phase 5 -- DTMF, transfer, conferencing, call recording

## Platform Support

| Platform | Status                       |
|----------|------------------------------|
| macOS    | Primary development platform |
| Windows  | Tested, packaged as MSI      |
| Linux    | Deb packaging available      |

## License

Proprietary. Internal tool developed by [Ildam](https://github.com/RoyalTaxi) for call center operations.
