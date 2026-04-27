# yalla-sip-phone

Desktop VoIP softphone for Ildam call center operators. Compose Desktop + PJSIP (SWIG/JNI). Connects to Asterisk and Oktell PBX.

## Three things you have to know

### 1. PJSIP threading

`PjsipEngine.kt` owns `newSingleThreadContext("pjsip-event-loop")` exposed as `pjDispatcher`. **Every public PJSIP API and every SWIG callback must run on it.** Public methods wrap work in `withContext(pjDispatcher) { … }`; callbacks run synchronously on that thread already. SWIG pointers are valid only inside the callback that delivered them — copy fields to plain Kotlin types before any `launch`/`delay`/suspension.

Lifecycle gates: `PjsipEngine.destroyed: AtomicBoolean` (gate exposed to sub-managers as `() -> Boolean`); `PjsipAccount` and `PjsipCall` each carry their own `deleted: AtomicBoolean`. All teardown is idempotent via `compareAndSet(false, true)`. Engine teardown is `shutdown()` / `close()`; per-object teardown is `destroy()`. Full rules in `rules/pjsip-threading.md` and `rules/swig-interop.md`.

### 2. No code signing

This product ships without Authenticode, Apple notarization, or any paid cert. Product-level decision. Cert-free mitigations: SHA256 hash verify, MOTW stripping, LAN-only distribution, force-upgrade floor via `MIN_SUPPORTED_VERSION`. Don't propose code signing in design docs.

### 3. Audio blocker (verify before "fixing")

When audio is broken, default suspect is the **server**: Asterisk SDP advertising public IP instead of LAN. Fix is `localnet=192.168.30.0/24` in the PBX pjsip config, not in our code. Verify with a packet capture before touching `data/pjsip/` audio routing.

## SDLC

```bash
./gradlew test                              # unit + integration (all use fakes)
./gradlew build                             # full build
./gradlew packageDistributionForCurrentOS   # dmg / msi / deb
./gradlew run                               # real PJSIP
./gradlew runSipDemo                        # fake engines, scripted scenarios
./gradlew showcase                          # component preview catalog
```

ktlint / detekt / jacoco are not configured. The `.claude/settings.json` no longer pretends to format on save. Tests are JUnit 4 runtime + `kotlin.test` assertions + Turbine + ktor-client-mock. **Not used:** Arrow, MockK, JUnit 5.

## Servers

| Server | Host | Port | Role |
|--------|------|------|------|
| Asterisk | 192.168.30.103 | 5060 | Test PBX (public 87.237.239.18) |
| Oktell | 192.168.0.22 | 5060 | Production PBX |

Test extensions: `101`, `102`, `103`.

## Layout

```
src/main/kotlin/uz/yalla/sipphone/
  Main.kt              entry, Compose Window, shutdown gate
  di/                  Koin modules
  domain/              pure interfaces + value types (CallEngine, SipAccountManager, ...)
  data/
    pjsip/             SWIG wrappers — DON'T cross pjDispatcher
    jcef/              JS bridge for the dispatcher web UI
    auth/              backend auth + token + logout
    network/           Ktor CIO + SafeRequest
    settings/          persisted prefs
    update/            auto-update (manifest poll, hash verify, MSI bootstrap)
  feature/             screens
  navigation/          Decompose root + factory
  ui/                  theme, tokens, i18n, shared composables
```

## Path-scoped rules (auto-loaded)

- `rules/pjsip-threading.md` — `**/data/pjsip/**` — pjDispatcher, AtomicBoolean gates
- `rules/swig-interop.md` — `**/data/pjsip/**` — SWIG callback safety, lifecycle
- `rules/compose-desktop.md` — `**/ui/**`, `**/feature/**`, `Main.kt` — Popup vs DialogWindow, LocalStrings, LocalYallaColors
- `rules/testing.md` — `**/src/test/**` — JUnit 4, kotlin.test, Turbine

## Skills + sub-agents

Skills: `build-desktop`, `publish-release`, `debug-audio`, `test-sip`, `add-sip-account`. Sub-agents: `pjsip-expert`, `compose-desktop-expert`, `audio-debugger`.

## Long-form reference

- `docs/pjsip-guide.md` — threading + SWIG deep dive
- `docs/js-bridge-api.md` — frontend integration contract
- `docs/windows-build.md` — Windows MSI build
- `docs/releasing/MIN_SUPPORTED_VERSION.md` — force-upgrade floor policy
- `docs/backend-integration/auto-update.md` — server contract for auto-update
