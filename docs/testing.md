# Testing Guide

## Overview

| Metric | Count |
|--------|-------|
| Test files | 40 |
| Test methods | 167 |
| Test lines | ~4,366 |
| Framework | JUnit 5, Turbine, Compose UI Test, Ktor MockEngine |

## Run Tests

```bash
./gradlew test                  # All tests
./gradlew test --tests "*.CallFlowIntegrationTest"   # Single class
./gradlew test --tests "*.LoginComponentTest.login*"  # Single method
```

## Demo Mode

Visual simulation of a busy operator day — no pjsip or SIP server needed:

```bash
./gradlew runDemo
```

Launches the full app with `ScriptableCallEngine` and `ScriptableRegistrationEngine`. Simulates incoming/outgoing calls, mute/hold, agent status changes with realistic timing.

## Test Architecture

### Fake Engines (domain layer)

Located in `src/test/kotlin/.../domain/` and `src/test/kotlin/.../testing/`:

| Fake | Replaces | Usage |
|------|----------|-------|
| `FakeCallEngine` | `CallEngine` | Direct state manipulation for unit tests |
| `FakeSipStackLifecycle` | `SipStackLifecycle` | Start/stop lifecycle without native pjsip |
| `FakeSipAccountManager` | `SipAccountManager` | Multi-account register/connect simulation |
| `MockAuthRepository` | `AuthRepository` | Hardcoded login responses for component tests |

```kotlin
val fakeCall = FakeCallEngine()
fakeCall.simulateIncomingCall("101", "Alisher")
// callState emits Ringing(...)

fakeCall.simulateConnected()
// callState emits Active(...)
```

### Scriptable Engines (scenario framework)

Located in `src/test/kotlin/.../testing/engine/`:

| Engine | Purpose |
|--------|---------|
| `ScriptableCallEngine` | Plays back `CallScenario` sequences with timing |
| `ScriptableRegistrationEngine` | Simulates registration lifecycle |

### Call Scenario DSL

Located in `src/test/kotlin/.../testing/scenario/`:

```kotlin
val scenario = callScenario {
    ring("+998901234567", isOutbound = false)
    pause(2000)
    active()
    pause(5000)
    mute()
    pause(3000)
    mute(false)
    hold()
    pause(2000)
    hold(false)
    idle()
}
```

### Scenario Runner

`ScenarioRunner` orchestrates full operator simulations:

```kotlin
val runner = ScenarioRunner(scriptableCall, scriptableRegistration)
runner.run {
    register("192.168.30.103")
    incomingCall("+998901234567")
    pause(10_000)
    disconnect()
}
```

### Traffic Patterns

`TrafficPattern` generates realistic call loads:

| Pattern | Description |
|---------|-------------|
| `singleCall` | One incoming call lifecycle |
| `busyOperatorDay` | Random mix of calls over extended period |
| `stressTest` | Rapid-fire calls for stability testing |
| `networkDisruption` | Registration failures and reconnects |

13 scenario types with weighted random selection, burst/breathe/lull patterns.

## Test Organization

```
src/test/kotlin/uz/yalla/sipphone/
├── data/
│   ├── auth/              AuthRepositoryImpl, TokenProvider, MockAuthRepository tests
│   ├── network/           ApiResponse, SafeRequest (Ktor MockEngine) tests
│   ├── jcef/              Bridge protocol, security, audit log tests
│   └── settings/          AppSettings tests
├── domain/                Domain model tests + FakeCallEngine + FakeSipStackLifecycle
├── feature/
│   ├── login/             LoginComponent tests
│   └── main/toolbar/      ToolbarComponent tests
├── integration/           Full-stack integration tests
│   ├── BridgeIntegrationTest.kt       JS bridge command dispatch
│   ├── CallFlowIntegrationTest.kt     Call state machine transitions
│   └── BusyOperatorIntegrationTest.kt Multi-call stress scenarios
├── navigation/            RootComponent navigation tests
├── testing/               Test framework (engines, fakes, scenarios, patterns)
│   ├── FakeSipAccountManager.kt       Multi-account fake with state control
│   ├── engine/            ScriptableCallEngine, ScriptableRegistrationEngine
│   └── scenario/          CallScenario, ScenarioRunner, TrafficPattern
├── ui/                    StringResources, YallaColors, AppTokens tests
├── util/                  PhoneNumberMasker, TimeFormat tests
└── demo/                  DemoMain.kt (visual demo entry point)
```

## What's Tested

- Domain models: CallerInfo, SipError, AgentStatus, SipAccount, CallState accountId routing, PhoneNumber
- Domain fakes: FakeCallEngine, FakeSipStackLifecycle, FakeSipAccountManager (full state machines)
- Auth layer: AuthRepositoryImpl (login→me→token), TokenProvider, MockAuthRepository
- Network layer: ApiResponse parsing, SafeRequest error handling (Ktor MockEngine)
- Bridge layer: Protocol serialization, security (rate limiting, origin), audit logging
- Components: LoginComponent (multi-account manual connect), ToolbarComponent
- Integration: Bridge dispatch, call flows, busy operator multi-call
- UI: StringResources (Uz/Ru), YallaColors palette, AppTokens
- Navigation: RootComponent screen transitions

## What's NOT Tested (known gaps)

- `PjsipEngine`, `PjsipCallManager`, `PjsipAccountManager` — requires native library, no JNI mock
- `BridgeRouter` dispatch logic — needs JCEF mock
- `MainComponent` call state → bridge event mapping
- Ending state + onCallConfirmed race condition
- Rapid register/unregister/register sequence

## Writing New Tests

1. Use `FakeCallEngine` / `FakeSipAccountManager` / `MockAuthRepository` for component tests
2. Use `ScriptableCallEngine` + `CallScenario` DSL for integration tests
3. Use `Turbine` for StateFlow assertions:

```kotlin
@Test
fun `incoming call emits ringing state`() = runTest {
    val engine = FakeCallEngine()
    engine.callState.test {
        assertEquals(CallState.Idle, awaitItem())
        engine.simulateIncomingCall("101", "Test")
        val ringing = awaitItem() as CallState.Ringing
        assertEquals("101", ringing.callerNumber)
        assertFalse(ringing.isOutbound)
    }
}
```
