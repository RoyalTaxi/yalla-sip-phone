# Yalla SIP Phone

Desktop VoIP softphone for Ildam call center operators. Connects to Asterisk and Oktell PBX via SIP. Kotlin Compose Desktop + PJSIP (SWIG/JNI) + JCEF (embedded Chromium for the dispatcher panel).

Internal tool, LAN distribution only — not a public product, not code-signed.

## Run

```bash
./gradlew run            # production-like, real PJSIP
./gradlew runSipDemo     # fake engines, busy-operator scenarios — no PBX needed
./gradlew runUpdateDemo  # auto-update UI states
./gradlew showcase       # component preview catalog
./gradlew test
./gradlew packageDistributionForCurrentOS   # dmg / msi / deb
```

JDK 21 required. Native libs ship in `libs/` per platform; JCEF binaries are pre-fetched into `app-resources/` by the `installJcefNatives` task.

## Layout

```
src/main/kotlin/uz/yalla/sipphone/
  Main.kt              entry point, Compose Window
  di/                  Koin modules
  domain/              pure interfaces + value types (SipAccountManager, CallEngine, ...)
  data/
    pjsip/             SWIG wrappers + threading discipline (single pjDispatcher)
    jcef/              JS bridge for the dispatcher web UI
    auth/              backend auth + token + logout orchestration
    network/           Ktor CIO client + SafeRequest
    settings/          persisted preferences
    update/            auto-update (manifest poll, hash verify, MSI bootstrap)
  feature/             screens (login, main + toolbar)
  navigation/          Decompose root + component factory
  ui/                  theme, design tokens, i18n strings, shared composables
  util/                phone masking, time format
```

## Docs

| File | Topic |
|------|-------|
| [docs/pjsip-guide.md](docs/pjsip-guide.md) | PJSIP threading rules, SWIG lifecycle (read before touching `data/pjsip/`) |
| [docs/js-bridge-api.md](docs/js-bridge-api.md) | JS bridge contract for the dispatcher web UI |
| [docs/backend-integration/auto-update.md](docs/backend-integration/auto-update.md) | Auto-update server contract |
| [docs/windows-build.md](docs/windows-build.md) | Windows MSI build with PJSIP compilation |
| [docs/releasing/MIN_SUPPORTED_VERSION.md](docs/releasing/MIN_SUPPORTED_VERSION.md) | Force-upgrade floor policy |

## Stack

Kotlin 2.1.20 · Compose Desktop 1.8.2 (Material 3) · PJSIP 2.16 via SWIG · Decompose 3.4.0 · Koin 4.1.1 · Ktor CIO 3.1.2 · JCEF 122 · Logback · multiplatform-settings · Turbine + ktor-client-mock + kotlin.test (JUnit 4 runtime).
