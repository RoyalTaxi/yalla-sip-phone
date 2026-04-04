# Yalla SIP Phone — Project Vision & Knowledge Base

## What This Is

Desktop VoIP softphone for Ildam (RoyalTaxi) call center operators. Connects to Oktell PBX via SIP. Built with Kotlin Compose Desktop + pjsip JNI. This is a production app for a "million dollar company" — not a prototype.

## Current State: Phase 1 Complete

Registration works. App launches, operator enters SIP credentials, registers with Oktell via pjsip, navigates to Dialer placeholder. Disconnect returns to Registration. All on production architecture (Decompose + Koin + MaterialKolor).

**Branch:** `feature/sip-registration`
**22 source files, 5 test files, 34 automated tests.**

## Test Environment

- Oktell PBX: `192.168.0.22:5060` (UDP, same LAN required — 192.168.0.x subnet)
- Test user: `102`, password: `1234qwerQQ`
- pjsip source: `/Users/macbookpro/Ildam/pjproject/`

---

## Architecture

```
Main.kt (Koin → pjsip init → Decompose → Compose Window)
  ├── navigation/RootComponent (Child Stack, factory pattern)
  ├── feature/registration/ (RegistrationComponent + Screen)
  ├── feature/dialer/ (DialerComponent + Screen placeholder)
  ├── domain/ (SipEngine interface, RegistrationState, SipCredentials)
  ├── data/pjsip/ (PjsipBridge implements SipEngine)
  ├── data/settings/ (AppSettings — credential persistence)
  └── ui/ (MaterialKolor theme, AppTokens, shared components)
```

**Dependency direction:** UI → Feature → Domain ← Data. Features depend on `SipEngine` interface, never on `PjsipBridge`.

## Tech Stack (Pinned Versions)

| Library | Version | Notes |
|---------|---------|-------|
| Kotlin | 2.1.20 | |
| Compose Desktop | **1.8.2** | Originally 1.7.3, upgraded because Decompose/MaterialKolor pull Compose 1.8.2 transitively, causing Skiko version mismatch |
| Decompose | 3.4.0 | Navigation. Uses `pushNew` (not `push`) to prevent duplicate config crash |
| Essenty lifecycle-coroutines | 2.5.0 | `coroutineScope()` in Decompose components |
| Koin | 4.1.1 | DI |
| MaterialKolor | 2.0.0 | Dynamic M3 color scheme from seed color |
| kotlin-logging + Logback | 7.0.3 / 1.5.16 | Logging |
| multiplatform-settings | 1.3.0 | Credential persistence (JVM Preferences) |
| kotlinx-coroutines-swing | 1.10.1 | Provides `Dispatchers.Main` on JVM Desktop |
| pjsua2 | local JAR + jnilib | 310 SWIG-generated Java classes |

---

## Critical Learnings (Burn These Into Memory)

### pjsip/JNI — The Hardest Part

1. **NEVER call `libDestroy()` on shutdown.** It internally calls `Runtime.gc()` which triggers SWIG finalizers on the JVM finalizer thread — a thread NOT registered with pjsip. Result: SIGSEGV. Instead: call `account.shutdown()` and let the OS clean up on process exit.

2. **`account.delete()` causes crash if pending transactions exist.** When the SIP server is unreachable, pjsip has pending registration transactions. `delete()` frees native memory while transactions still reference it. Only call `delete()` when the account has fully settled (successful register/unregister). In `destroy()`, use `shutdown()` only (wrapped in try-catch), skip `delete()`.

3. **`account.shutdown()` before `libDestroy()` is REQUIRED** (if you ever do call libDestroy). Without it, libDestroy iterates pjsua's internal account list, triggers callbacks that crash on GC-freed Account objects.

4. **SWIG objects must be `delete()`d after use** — EpConfig, TransportConfig, AccountConfig, AuthCredInfo, AccountInfo, Version. Otherwise GC finalizer deletes them from wrong thread → crash. Use `try/finally` for AccountConfig/AuthCredInfo in register() since create() can throw.

5. **LogWriter instance must be kept as a field reference.** If GC collects it, native code calls dead Java object → crash.

6. **Polling loop MUST yield().** `while (isActive) { libHandleEvents(50) }` never suspends — monopolizes the single-thread dispatcher. `register()`, `unregister()`, `destroy()` use `withContext(pjDispatcher)` but can never dispatch. Add `yield()` after each `libHandleEvents`.

7. **`threadCnt = 0`, `mainThreadOnly = false`.** No internal pjsip worker threads (JNI/GC compatible). `mainThreadOnly` is unnecessary with threadCnt=0 and adds 50ms callback latency.

8. **`libRegisterThread("pjsip-poll")` in polling loop** — defense-in-depth. The pjDispatcher thread should already be registered from `libCreate()`, but verify.

9. **`System.load()` with custom property, not `java.library.path`.** Setting `java.library.path` replaces ALL paths, breaking Skiko native lib loading. Use `-Dpjsip.library.path` and `System.load("$path/libpjsua2.jnilib")`.

10. **`prm.code` for registration status, not `regIsActive` alone.** `regIsActive` is true even during un-REGISTER. Check `code / 100 == 2` for success.

11. **`getInfo()` throws in callbacks.** Wrap in try-catch inside `onRegState`. Uncaught exception in JNI callback = native crash.

12. **`isDestroyed()` guard in PjsipAccount.onRegState.** Prevents callback processing during shutdown.

### Compose Desktop

13. **`kotlinx-coroutines-swing` is REQUIRED.** JVM Desktop has no `Dispatchers.Main` by default. Essenty's `coroutineScope()` needs it. Without it: `Module with the Main dispatcher is missing` crash.

14. **RootComponent MUST be created on EDT.** Use `SwingUtilities.invokeAndWait` (`runOnUiThread` helper). Decompose checks `isEventDispatchThread()`.

15. **Compose 1.7.3 + Decompose 3.4.0 = Skiko mismatch.** Decompose pulls Compose 1.8.2 artifacts transitively. Native runtime stays at 0.8.18 but API resolves to 0.9.4.2. `RenderNodeContext` class missing → crash. Fix: upgrade Compose plugin to 1.8.2.

16. **`onCloseRequest` runs on EDT.** `runBlocking` there blocks UI. Acceptable for shutdown (max 3s), but user sees frozen window. Keep destroy logic minimal.

### Decompose Navigation

17. **Use `pushNew`, not `push`.** `push` throws `IllegalStateException` on duplicate configuration. `pushNew` is a no-op if config already on top. Prevents crash on rapid clicks and re-registration timer.

18. **Use `collect`, not `first{}`, for navigation triggers in components that persist in back stack.** Decompose keeps Registration component alive when Dialer is pushed. After pop, same instance is reused. `first{}` already completed — second connect doesn't navigate. `collect` stays active. `StateFlow.distinctUntilChanged` prevents duplicate emissions.

19. **`drop(1)` in DialerComponent disconnect trigger.** Skip the current Registered state so the collector waits for Idle/Failed transitions.

20. **Navigate back on BOTH `Idle` and `Failed`** in DialerComponent. If server goes down, state becomes Failed, not Idle. Without handling Failed, user sees blank screen.

### Coroutines

21. **`coroutineScope()` from Essenty — NO args.** No `Dispatchers.Default`, no `SupervisorJob()`. Default is `Main.immediate` + lifecycle-managed Job. Adding `SupervisorJob()` breaks lifecycle cancellation. Adding `Dispatchers.Default` runs navigation callbacks off Main thread → crash.

22. **Inject `CoroutineDispatcher` for testability.** `Dispatchers.IO` is hardcoded in RegistrationComponent. Tests can't control it. Pass `ioDispatcher: CoroutineDispatcher = Dispatchers.IO` as constructor param. Tests use `UnconfinedTestDispatcher`.

23. **`AtomicBoolean` guard for `destroy()`.** Both `onCloseRequest` and shutdown hook can call it. Without guard, double `libDestroy` = crash. (Though we now skip libDestroy entirely.)

### Design System

24. **MaterialKolor seed color: `0xFF1A5276`** (professional blue). Generates 29 M3 tonal roles.

25. **ExtendedColors for success green** — M3 has no success slot. `LocalExtendedColors` CompositionLocal with `DefaultExtendedColors` constant.

26. **AppTokens via CompositionLocal** — `LocalAppTokens` with `AppTokens` data class. Future-proof for compact mode, responsive tokens.

27. **ConnectionStatusCard hides for Registered state** — navigation handles it, no need to show "Connected" card.

### Code Quality Rules

28. **Action methods: no `on` prefix.** `connect()`, `disconnect()`, `cancelRegistration()` — not `onConnect()`. The `on` prefix is for callbacks/event handlers.

29. **`sealed interface`, not `sealed class`** for stateless hierarchies (Screen, RegistrationState, Child).

30. **No Phase 2/3/4 placeholder comments in code.** Track in issues, not source files.

31. **`SipCredentials.DEFAULT_SIP_PORT = 5060`** — single source of truth, not magic numbers.

---

## Project Structure

```
src/main/kotlin/uz/yalla/sipphone/
├── Main.kt                           # Koin → pjsip → Decompose → Window
├── di/AppModule.kt                   # Koin: SipEngine → PjsipBridge, AppSettings
├── domain/
│   ├── SipEngine.kt                  # Interface: registrationState, init, register, unregister, destroy
│   ├── RegistrationState.kt          # Sealed interface: Idle, Registering, Registered, Failed
│   └── SipCredentials.kt             # Data class with DEFAULT_SIP_PORT
├── data/
│   ├── pjsip/
│   │   ├── PjsipBridge.kt           # SipEngine impl: single pjDispatcher thread, polling, SWIG lifecycle
│   │   ├── PjsipAccount.kt          # Extends pjsua2.Account: onRegState callback
│   │   └── PjsipLogWriter.kt        # Routes pjsip native logs to SLF4J
│   └── settings/AppSettings.kt      # Credential persistence (server/port/username, NOT password)
├── navigation/
│   ├── Screen.kt                     # @Serializable sealed interface: Registration, Dialer
│   ├── RootComponent.kt             # Child Stack + factory pattern (no direct dependency knowledge)
│   └── RootContent.kt               # Compose: slide+fade animation
├── feature/
│   ├── registration/
│   │   ├── RegistrationComponent.kt  # SipEngine interface, collect for nav, injected dispatcher
│   │   ├── RegistrationModel.kt      # FormState, FormErrors, validateForm()
│   │   └── RegistrationScreen.kt     # Compose UI
│   └── dialer/
│       ├── DialerComponent.kt        # Placeholder, handles disconnect + Failed state
│       └── DialerScreen.kt           # Three-zone layout skeleton
└── ui/
    ├── theme/
    │   ├── Theme.kt                  # MaterialKolor + ExtendedColors + Typography
    │   └── AppTokens.kt             # CompositionLocal design tokens
    └── component/
        ├── SipCredentialsForm.kt     # Form with validation, Enter key (KeyDown only)
        ├── ConnectionStatusCard.kt   # Registering + Failed states only, liveRegion a11y
        └── ConnectButton.kt          # State-aware button
```

---

## Shutdown Sequence (Final Working Version)

```
User clicks close → onCloseRequest on EDT:
  1. runBlocking { withTimeoutOrNull(3000) { sipEngine.destroy() } }
     └── destroy() on pjDispatcher:
         a. pollJob.cancel() + join()
         b. account?.shutdown()  ← removes from pjsua list (try-catch, best effort)
         c. account = null, logWriter = null
         d. NO libDestroy() ← causes SIGSEGV from GC finalizer thread
         e. state = Idle
     └── scope.cancel(), pjDispatcher.close()
  2. exitApplication()
  3. OS cleans up all native resources on process exit
```

---

## Phase 2 Plan (Next)

**Goal:** Add calling functionality — outbound calls, incoming call handling, audio.

**What's needed:**
- `PjsipCall` class (extends `pjsua2.Call`) with `onCallState`, `onCallMediaState` callbacks
- Audio media routing: `Call.getAudioMedia()` → `AudDevManager`
- Dial pad UI on DialerScreen
- Incoming call overlay via Decompose Child Slot (already reserved in RootComponent)
- `CallState` sealed interface (was removed as dead code — re-add when needed)
- Mute, hold, DTMF
- Call timer

**pjsip calling is already validated** — the same JNI library supports it. Just need Kotlin wrappers + UI.

---

## Known Limitations

- **Password not persisted** — user re-enters each launch. Secure storage (macOS Keychain) is future work.
- **No dark theme** — MaterialKolor supports it (`isDark = true`), just not wired yet.
- **No system tray** — for background operation.
- **AppSettings uses shared JVM Preferences** — no namespace isolation. Keys prefixed with `sip_`.
- **No re-registration timer UI** — pjsip handles re-registration internally, but no user feedback if it fails silently.
- **`libDestroy()` skipped** — native memory not explicitly freed on shutdown. OS handles it on process exit. This means you can't reinitialize pjsip without restarting the app.
