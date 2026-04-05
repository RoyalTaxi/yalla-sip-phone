# YallaSIP Bridge API — Frontend Integration Guide

**Version**: 1.0.0
**Last updated**: 2026-04-05
**Audience**: Frontend developers integrating with Yalla SIP Phone desktop app

---

## Overview

The Yalla SIP Phone is a desktop application (Kotlin/JVM) that embeds your React dispatcher panel inside a Chromium-based webview (JCEF). The native app handles all SIP/VoIP operations — your web app communicates with it through a JavaScript bridge exposed on `window.YallaSIP`.

```
┌─────────────────────────────────────────────────┐
│  Native Toolbar (56px) — call controls, status  │
├─────────────────────────────────────────────────┤
│                                                 │
│          Your React App (webview)               │
│          Full Chromium — no restrictions         │
│                                                 │
│          window.YallaSIP ← bridge object        │
│                                                 │
└─────────────────────────────────────────────────┘
```

**You do NOT need to build any call UI.** The native toolbar handles all call controls (answer, reject, mute, hold, hangup). Your app receives call events and reacts to them (e.g., open customer record when call arrives).

---

## Architecture

```
┌──────────────────────┐                 ┌──────────────────────┐
│   Native App (Kotlin) │                 │   Your App (React)    │
│                       │                 │                       │
│  ┌─────────────────┐ │   Events        │  ┌─────────────────┐ │
│  │  SIP Engine      │─┼────────────────▶│  │  Event Listeners │ │
│  │  (pjsip)        │ │   (fire & forget)│  │  on('event', fn) │ │
│  └─────────────────┘ │                 │  └─────────────────┘ │
│                       │                 │                       │
│  ┌─────────────────┐ │   Commands      │  ┌─────────────────┐ │
│  │  Command Handler │◀┼────────────────│  │  API calls       │ │
│  │  (validates,     │ │   (Promise)     │  │  makeCall()      │ │
│  │   executes)      │─┼────────────────▶│  │  .then(result)   │ │
│  └─────────────────┘ │   (response)    │  └─────────────────┘ │
│                       │                 │                       │
│  ┌─────────────────┐ │   Queries       │  ┌─────────────────┐ │
│  │  State Provider  │◀┼────────────────│  │  getState()      │ │
│  │                  │─┼────────────────▶│  │  getVersion()    │ │
│  └─────────────────┘ │   (response)    │  └─────────────────┘ │
└──────────────────────┘                 └──────────────────────┘
```

### Communication Types

| Type | Direction | Pattern | Use case |
|------|-----------|---------|----------|
| **Events** | Native → Web | Fire-and-forget, multiple listeners | Call state notifications |
| **Commands** | Web → Native | Promise-based, request/response | Trigger actions (makeCall, hangup) |
| **Queries** | Web → Native | Promise-based, read-only | Get current state snapshot |

---

## Getting Started

### 1. Wait for the bridge

The native app injects `window.YallaSIP` after your page loads. Poll for it:

```javascript
function waitForBridge() {
  return new Promise((resolve) => {
    if (window.YallaSIP) {
      resolve(window.YallaSIP);
      return;
    }
    const interval = setInterval(() => {
      if (window.YallaSIP) {
        clearInterval(interval);
        resolve(window.YallaSIP);
      }
    }, 50);
  });
}
```

### 2. Signal readiness

Once you detect the bridge, call `ready()`. The native app will respond with initialization data and any buffered events that occurred before your app loaded.

```javascript
const bridge = await waitForBridge();
const initData = await bridge.ready();

console.log('Bridge version:', initData.version);
console.log('Agent:', initData.agent.name);
console.log('Capabilities:', initData.capabilities);

// Process any events that happened before we were ready
if (initData.bufferedEvents) {
  initData.bufferedEvents.forEach(event => {
    // handle each buffered event
  });
}
```

### 3. Subscribe to events

```javascript
// Subscribe — returns an unsubscribe function
const unsub = window.YallaSIP.on('incomingCall', (data) => {
  console.log('Incoming call from:', data.number);
  openCustomerRecord(data.number);
});

// Later, to unsubscribe:
unsub();

// Or use off():
window.YallaSIP.off('incomingCall', myHandler);
```

### 4. Send commands

```javascript
const result = await window.YallaSIP.makeCall('+998901234567');
if (result.success) {
  console.log('Call started:', result.callId);
} else {
  console.error('Call failed:', result.error.message);
}
```

---

## Complete React Integration Example

```tsx
// hooks/useYallaSIP.ts
import { useEffect, useRef, useState, useCallback } from 'react';
import type { YallaSIPBridge, CallInfo, AgentStatus, ConnectionState } from '../types/yalla-sip';

interface SIPState {
  isReady: boolean;
  agentName: string;
  agentStatus: AgentStatus;
  connection: ConnectionState;
  activeCall: CallInfo | null;
  callQuality: string | null;
}

export function useYallaSIP() {
  const bridgeRef = useRef<YallaSIPBridge | null>(null);
  const [state, setState] = useState<SIPState>({
    isReady: false,
    agentName: '',
    agentStatus: 'ready',
    connection: 'disconnected',
    activeCall: null,
    callQuality: null,
  });

  useEffect(() => {
    let mounted = true;

    async function init() {
      // Wait for bridge injection
      while (!window.YallaSIP && mounted) {
        await new Promise(r => setTimeout(r, 50));
      }
      if (!mounted) return;

      bridgeRef.current = window.YallaSIP!;
      const bridge = bridgeRef.current;

      // Signal readiness
      const initData = await bridge.ready();
      if (!mounted) return;

      setState(prev => ({
        ...prev,
        isReady: true,
        agentName: initData.agent.name,
      }));

      // Subscribe to all events
      const unsubs: Array<() => void> = [];

      unsubs.push(bridge.on('incomingCall', (data) => {
        if (!mounted) return;
        setState(prev => ({
          ...prev,
          activeCall: {
            callId: data.callId,
            number: data.number,
            direction: data.direction,
            state: 'incoming',
            isMuted: false,
            isOnHold: false,
            duration: 0,
          },
        }));
      }));

      unsubs.push(bridge.on('outgoingCall', (data) => {
        if (!mounted) return;
        setState(prev => ({
          ...prev,
          activeCall: {
            callId: data.callId,
            number: data.number,
            direction: data.direction,
            state: 'outgoing',
            isMuted: false,
            isOnHold: false,
            duration: 0,
          },
        }));
      }));

      unsubs.push(bridge.on('callConnected', (data) => {
        if (!mounted) return;
        setState(prev => ({
          ...prev,
          activeCall: prev.activeCall ? {
            ...prev.activeCall,
            state: 'active',
          } : null,
        }));
      }));

      unsubs.push(bridge.on('callEnded', (data) => {
        if (!mounted) return;
        setState(prev => ({
          ...prev,
          activeCall: null,
          callQuality: null,
        }));
      }));

      unsubs.push(bridge.on('callMuteChanged', (data) => {
        if (!mounted) return;
        setState(prev => ({
          ...prev,
          activeCall: prev.activeCall ? {
            ...prev.activeCall,
            isMuted: data.isMuted,
          } : null,
        }));
      }));

      unsubs.push(bridge.on('callHoldChanged', (data) => {
        if (!mounted) return;
        setState(prev => ({
          ...prev,
          activeCall: prev.activeCall ? {
            ...prev.activeCall,
            isOnHold: data.isOnHold,
            state: data.isOnHold ? 'on_hold' : 'active',
          } : null,
        }));
      }));

      unsubs.push(bridge.on('agentStatusChanged', (data) => {
        if (!mounted) return;
        setState(prev => ({ ...prev, agentStatus: data.status }));
      }));

      unsubs.push(bridge.on('connectionChanged', (data) => {
        if (!mounted) return;
        setState(prev => ({ ...prev, connection: data.state }));
      }));

      unsubs.push(bridge.on('callQualityUpdate', (data) => {
        if (!mounted) return;
        setState(prev => ({ ...prev, callQuality: data.quality }));
      }));

      unsubs.push(bridge.on('themeChanged', (data) => {
        if (!mounted) return;
        document.documentElement.setAttribute('data-theme', data.theme);
      }));

      unsubs.push(bridge.on('error', (data) => {
        console.error(`[YallaSIP] ${data.severity}: ${data.code} — ${data.message}`);
      }));

      // Process buffered events
      initData.bufferedEvents?.forEach((event: any) => {
        // Re-emit through the bridge so listeners process them
      });

      return () => {
        mounted = false;
        unsubs.forEach(fn => fn());
      };
    }

    const cleanup = init();
    return () => { cleanup.then(fn => fn?.()); };
  }, []);

  // Command wrappers
  const makeCall = useCallback(async (number: string) => {
    return bridgeRef.current?.makeCall(number);
  }, []);

  const hangup = useCallback(async (callId: string) => {
    return bridgeRef.current?.hangup(callId);
  }, []);

  const answer = useCallback(async (callId: string) => {
    return bridgeRef.current?.answer(callId);
  }, []);

  const reject = useCallback(async (callId: string) => {
    return bridgeRef.current?.reject(callId);
  }, []);

  return {
    ...state,
    makeCall,
    hangup,
    answer,
    reject,
  };
}
```

### Using the hook in a component:

```tsx
// components/DispatcherPanel.tsx
import { useYallaSIP } from '../hooks/useYallaSIP';

export function DispatcherPanel() {
  const { isReady, activeCall, agentName, makeCall } = useYallaSIP();

  if (!isReady) {
    return <div>Connecting to SIP Phone...</div>;
  }

  return (
    <div>
      <h1>Welcome, {agentName}</h1>

      {activeCall && (
        <div className="active-call-banner">
          <span>Call with {activeCall.number}</span>
          <span>{activeCall.state}</span>
          {activeCall.isMuted && <span>🔇 Muted</span>}
          {activeCall.isOnHold && <span>⏸ On Hold</span>}
        </div>
      )}

      <button onClick={() => makeCall('+998901234567')}>
        Call +998 90 123 45 67
      </button>
    </div>
  );
}
```

---

## Events Reference

All events include `seq` (monotonic sequence number) and `timestamp` (epoch milliseconds). Use `seq` to detect out-of-order delivery. Use `timestamp` for logging and analytics.

### Call Lifecycle Events

#### `incomingCall`

Fired when an inbound call is received. The native toolbar shows Answer/Reject buttons — your app should prepare to display customer information.

```typescript
window.YallaSIP.on('incomingCall', (data: {
  callId: string;        // UUID, use this for all subsequent commands
  number: string;        // Caller phone number, e.g. "+998901234567"
  direction: "inbound";
  seq: number;
  timestamp: number;     // Date.now() format (epoch ms)
}) => {
  // Example: look up customer by phone number
  const customer = await customerService.findByPhone(data.number);
  openCustomerCard(customer);
});
```

#### `outgoingCall`

Fired when the operator initiates an outbound call (from toolbar or via `makeCall()` command).

```typescript
window.YallaSIP.on('outgoingCall', (data: {
  callId: string;
  number: string;        // Dialed number
  direction: "outbound";
  seq: number;
  timestamp: number;
}) => {
  // Example: create a call record
  callLog.create({ callId: data.callId, number: data.number, type: 'outbound' });
});
```

#### `callConnected`

Fired when the call is answered (either direction). For inbound: operator clicked Answer. For outbound: remote party picked up.

```typescript
window.YallaSIP.on('callConnected', (data: {
  callId: string;
  number: string;
  direction: "inbound" | "outbound";
  seq: number;
  timestamp: number;
}) => {
  // Example: start order creation timer
  startCallTimer(data.callId);
});
```

#### `callEnded`

Fired when a call terminates for any reason.

```typescript
window.YallaSIP.on('callEnded', (data: {
  callId: string;
  number: string;
  direction: "inbound" | "outbound";
  duration: number;      // Call duration in SECONDS (integer)
  reason: "hangup"       // Normal hangup (either party)
        | "rejected"     // Operator rejected incoming call
        | "missed"       // Incoming call not answered (timed out)
        | "busy"         // Remote party was busy
        | "error";       // SIP/network error
  seq: number;
  timestamp: number;
}) => {
  // Example: save call record
  callLog.update(data.callId, {
    duration: data.duration,
    reason: data.reason,
    endedAt: new Date(data.timestamp),
  });
});
```

#### `callMuteChanged`

Fired when mute state changes (operator clicked Mute/Unmute or via `setMute()` command).

```typescript
window.YallaSIP.on('callMuteChanged', (data: {
  callId: string;
  isMuted: boolean;
  seq: number;
  timestamp: number;
}) => {
  // Example: show mute indicator in your UI
  updateCallStatus(data.callId, { muted: data.isMuted });
});
```

#### `callHoldChanged`

Fired when hold state changes.

```typescript
window.YallaSIP.on('callHoldChanged', (data: {
  callId: string;
  isOnHold: boolean;
  seq: number;
  timestamp: number;
}) => {
  updateCallStatus(data.callId, { onHold: data.isOnHold });
});
```

### System Events

#### `agentStatusChanged`

Fired when the operator changes their availability status from the toolbar dropdown.

```typescript
window.YallaSIP.on('agentStatusChanged', (data: {
  status: "ready" | "away" | "break" | "wrap_up" | "offline";
  previousStatus: string;
  seq: number;
  timestamp: number;
}) => {
  // Example: sync status to your backend
  agentService.updateStatus(data.status);
});
```

#### `connectionChanged`

Fired when the SIP connection state changes. **No server IP is exposed for security.**

```typescript
window.YallaSIP.on('connectionChanged', (data: {
  state: "connected" | "reconnecting" | "disconnected";
  attempt: number;       // Reconnect attempt count (0 when connected)
  seq: number;
  timestamp: number;
}) => {
  if (data.state === 'reconnecting') {
    showBanner(`Reconnecting... attempt ${data.attempt}`);
  }
});
```

#### `callQualityUpdate`

Fired every 5 seconds during an active call. Quality is derived from MOS score — raw metrics stay native-side for security.

```typescript
window.YallaSIP.on('callQualityUpdate', (data: {
  callId: string;
  quality: "excellent" | "good" | "fair" | "poor";
  seq: number;
  timestamp: number;
}) => {
  if (data.quality === 'poor') {
    showWarning('Call quality is poor');
  }
});
```

#### `themeChanged`

Fired when the operator toggles dark/light mode in the native toolbar settings. Your app should match the theme.

```typescript
window.YallaSIP.on('themeChanged', (data: {
  theme: "light" | "dark";
  seq: number;
  timestamp: number;
}) => {
  document.documentElement.setAttribute('data-theme', data.theme);
});
```

#### `error`

Global error not tied to a specific command. Example: audio device disconnected, SIP stack crash.

```typescript
window.YallaSIP.on('error', (data: {
  code: string;
  message: string;
  severity: "warning" | "error" | "fatal";
  seq: number;
  timestamp: number;
}) => {
  if (data.severity === 'fatal') {
    showErrorScreen(data.message);
  }
});
```

#### `callRejectedBusy`

Fired when a second incoming call was automatically rejected because the operator is already on a call. Not a user action — the native app handles this automatically.

```typescript
window.YallaSIP.on('callRejectedBusy', (data: {
  number: string;
  seq: number;
  timestamp: number;
}) => {
  // Example: log missed call
  missedCallLog.add(data.number, data.timestamp);
});
```

---

## Commands Reference

All commands return a `Promise`. Always `await` them and check the `success` field.

### `makeCall(number)`

Initiate an outbound call. The native app validates the number format before dialing.

```typescript
const result = await window.YallaSIP.makeCall('+998901234567');

if (result.success) {
  // result: { success: true, callId: "uuid-here" }
  console.log('Dialing...', result.callId);
} else {
  // result: { success: false, error: { code, message, recoverable } }
  if (result.error.code === 'ALREADY_IN_CALL') {
    alert('Please end the current call first');
  }
}
```

**Valid number format**: `[+]` followed by digits, `*`, or `#`. Max 20 characters. Examples: `+998901234567`, `101`, `*72#`.

### `answer(callId)`

Answer an incoming call. Get `callId` from the `incomingCall` event.

```typescript
await window.YallaSIP.answer(callId);
```

### `reject(callId)`

Reject an incoming call.

```typescript
await window.YallaSIP.reject(callId);
```

### `hangup(callId)`

End an active call.

```typescript
await window.YallaSIP.hangup(callId);
```

### `setMute(callId, muted)`

Explicitly set mute state. **Not a toggle** — avoids race conditions.

```typescript
// Mute
await window.YallaSIP.setMute(callId, true);
// { success: true, isMuted: true }

// Unmute
await window.YallaSIP.setMute(callId, false);
// { success: true, isMuted: false }
```

### `setHold(callId, onHold)`

Explicitly set hold state.

```typescript
// Hold
await window.YallaSIP.setHold(callId, true);
// { success: true, isOnHold: true }

// Resume
await window.YallaSIP.setHold(callId, false);
// { success: true, isOnHold: false }
```

### `setAgentStatus(status)`

Change the operator's availability status. Idempotent — setting the same status twice is a no-op success.

```typescript
await window.YallaSIP.setAgentStatus('away');
// { success: true, status: "away" }
```

Valid statuses: `"ready"`, `"away"`, `"break"`, `"wrap_up"`, `"offline"`.

---

## Queries Reference

### `getState()`

Get a full snapshot of the current state. Useful for initializing your UI or recovering after a page reload.

```typescript
const state = await window.YallaSIP.getState();
```

Response:

```typescript
{
  connection: {
    state: "connected" | "reconnecting" | "disconnected",
    attempt: number
  },
  agentStatus: "ready" | "away" | "break" | "wrap_up" | "offline",
  call: null | {
    callId: string,
    number: string,
    direction: "inbound" | "outbound",
    state: "incoming" | "outgoing" | "active" | "on_hold",
    isMuted: boolean,
    isOnHold: boolean,
    duration: number  // seconds since callConnected
  }
}
```

### `getVersion()`

Get bridge API version and available capabilities.

```typescript
const info = await window.YallaSIP.getVersion();
// { version: "1.0.0", capabilities: ["call", "agentStatus", "callQuality"] }
```

Use `capabilities` to check if a feature is available before using it:

```typescript
const { capabilities } = await window.YallaSIP.getVersion();
if (capabilities.includes('callQuality')) {
  window.YallaSIP.on('callQualityUpdate', handleQuality);
}
```

---

## Error Handling

### Command Error Format

All command failures return the same structure:

```typescript
{
  success: false,
  error: {
    code: string,          // Machine-readable, SCREAMING_SNAKE_CASE
    message: string,       // Human-readable, for logging
    recoverable: boolean   // Can the user retry this action?
  }
}
```

### Error Codes

| Code | Meaning | Recoverable | What to do |
|------|---------|-------------|------------|
| `ALREADY_IN_CALL` | There's already an active call | No | Hangup current call first |
| `NO_ACTIVE_CALL` | No call to hangup/mute/hold | No | Ignore, state is already idle |
| `NO_INCOMING_CALL` | No incoming call to answer/reject | No | Ignore, call already ended |
| `INVALID_NUMBER` | Phone number format invalid | Yes | Show validation error to user |
| `NOT_REGISTERED` | SIP not connected to server | No | Wait for reconnection |
| `RATE_LIMITED` | Too many commands sent | Yes | Wait 1-2 seconds and retry |
| `NETWORK_TIMEOUT` | SIP operation timed out | Yes | Retry once |
| `INTERNAL_ERROR` | Unexpected native error | No | Log and report |

### Example error handling:

```typescript
async function placeCall(number: string) {
  const result = await window.YallaSIP.makeCall(number);

  if (result.success) {
    return result.callId;
  }

  switch (result.error.code) {
    case 'ALREADY_IN_CALL':
      toast.warn('Please end the current call before dialing');
      break;
    case 'INVALID_NUMBER':
      toast.error('Invalid phone number format');
      break;
    case 'NOT_REGISTERED':
      toast.error('Phone disconnected — waiting for reconnection');
      break;
    case 'RATE_LIMITED':
      await sleep(2000);
      return placeCall(number); // retry once
    default:
      toast.error(result.error.message);
  }
  return null;
}
```

---

## TypeScript Definitions

Copy this file to your project as `yalla-sip.d.ts`:

```typescript
// types/yalla-sip.d.ts

export type AgentStatus = 'ready' | 'away' | 'break' | 'wrap_up' | 'offline';
export type ConnectionState = 'connected' | 'reconnecting' | 'disconnected';
export type CallDirection = 'inbound' | 'outbound';
export type CallStateType = 'incoming' | 'outgoing' | 'active' | 'on_hold';
export type CallQuality = 'excellent' | 'good' | 'fair' | 'poor';
export type CallEndReason = 'hangup' | 'rejected' | 'missed' | 'busy' | 'error';
export type ErrorSeverity = 'warning' | 'error' | 'fatal';

export type ErrorCode =
  | 'ALREADY_IN_CALL'
  | 'NO_ACTIVE_CALL'
  | 'NO_INCOMING_CALL'
  | 'INVALID_NUMBER'
  | 'NOT_REGISTERED'
  | 'RATE_LIMITED'
  | 'NETWORK_TIMEOUT'
  | 'INTERNAL_ERROR';

// --- Event Payloads ---

interface BaseEvent {
  seq: number;
  timestamp: number;
}

export interface IncomingCallEvent extends BaseEvent {
  callId: string;
  number: string;
  direction: 'inbound';
}

export interface OutgoingCallEvent extends BaseEvent {
  callId: string;
  number: string;
  direction: 'outbound';
}

export interface CallConnectedEvent extends BaseEvent {
  callId: string;
  number: string;
  direction: CallDirection;
}

export interface CallEndedEvent extends BaseEvent {
  callId: string;
  number: string;
  direction: CallDirection;
  duration: number;
  reason: CallEndReason;
}

export interface CallMuteChangedEvent extends BaseEvent {
  callId: string;
  isMuted: boolean;
}

export interface CallHoldChangedEvent extends BaseEvent {
  callId: string;
  isOnHold: boolean;
}

export interface AgentStatusChangedEvent extends BaseEvent {
  status: AgentStatus;
  previousStatus: AgentStatus;
}

export interface ConnectionChangedEvent extends BaseEvent {
  state: ConnectionState;
  attempt: number;
}

export interface CallQualityUpdateEvent extends BaseEvent {
  callId: string;
  quality: CallQuality;
}

export interface ThemeChangedEvent extends BaseEvent {
  theme: 'light' | 'dark';
}

export interface BridgeErrorEvent extends BaseEvent {
  code: string;
  message: string;
  severity: ErrorSeverity;
}

export interface CallRejectedBusyEvent extends BaseEvent {
  number: string;
}

// --- Event Map ---

export interface YallaSIPEventMap {
  incomingCall: IncomingCallEvent;
  outgoingCall: OutgoingCallEvent;
  callConnected: CallConnectedEvent;
  callEnded: CallEndedEvent;
  callMuteChanged: CallMuteChangedEvent;
  callHoldChanged: CallHoldChangedEvent;
  agentStatusChanged: AgentStatusChangedEvent;
  connectionChanged: ConnectionChangedEvent;
  callQualityUpdate: CallQualityUpdateEvent;
  themeChanged: ThemeChangedEvent;
  error: BridgeErrorEvent;
  callRejectedBusy: CallRejectedBusyEvent;
}

// --- Command Results ---

export interface CommandSuccess<T = void> {
  success: true;
  data?: T;
}

export interface CommandError {
  success: false;
  error: {
    code: ErrorCode;
    message: string;
    recoverable: boolean;
  };
}

export type CommandResult<T = void> = CommandSuccess<T> | CommandError;

export interface MakeCallResult {
  success: true;
  callId: string;
}

export interface MuteResult {
  success: true;
  isMuted: boolean;
}

export interface HoldResult {
  success: true;
  isOnHold: boolean;
}

export interface StatusResult {
  success: true;
  status: AgentStatus;
}

// --- State ---

export interface CallInfo {
  callId: string;
  number: string;
  direction: CallDirection;
  state: CallStateType;
  isMuted: boolean;
  isOnHold: boolean;
  duration: number;
}

export interface SIPState {
  connection: {
    state: ConnectionState;
    attempt: number;
  };
  agentStatus: AgentStatus;
  call: CallInfo | null;
}

export interface VersionInfo {
  version: string;
  capabilities: string[];
}

export interface InitData {
  version: string;
  capabilities: string[];
  agent: { id: string; name: string };
  bufferedEvents: any[];
}

// --- Bridge Interface ---

export interface YallaSIPBridge {
  // Initialization
  ready(): Promise<InitData>;

  // Events
  on<K extends keyof YallaSIPEventMap>(
    event: K,
    handler: (data: YallaSIPEventMap[K]) => void
  ): () => void;  // returns unsubscribe function

  off<K extends keyof YallaSIPEventMap>(
    event: K,
    handler: (data: YallaSIPEventMap[K]) => void
  ): void;

  // Commands
  makeCall(number: string): Promise<MakeCallResult | CommandError>;
  answer(callId: string): Promise<CommandResult>;
  reject(callId: string): Promise<CommandResult>;
  hangup(callId: string): Promise<CommandResult>;
  setMute(callId: string, muted: boolean): Promise<MuteResult | CommandError>;
  setHold(callId: string, onHold: boolean): Promise<HoldResult | CommandError>;
  setAgentStatus(status: AgentStatus): Promise<StatusResult | CommandError>;

  // Queries
  getState(): Promise<SIPState>;
  getVersion(): Promise<VersionInfo>;
}

// --- Window augmentation ---

declare global {
  interface Window {
    YallaSIP?: YallaSIPBridge;
  }
}
```

---

## Versioning & Compatibility

| Rule | Detail |
|------|--------|
| New events added | Minor version bump. Your app MUST ignore unknown events. |
| New fields in existing events | Your app MUST tolerate unknown fields (additive only). |
| New commands added | Check `capabilities` before calling. |
| Breaking changes | Major version bump. 2-version deprecation window. |

### Forward compatibility checklist:

- [ ] Ignore events you don't recognize (don't crash on unknown event names)
- [ ] Tolerate unknown fields in event payloads (don't use strict schema validation)
- [ ] Check `capabilities` from `getVersion()` before using optional features
- [ ] Always check `success` field on command results — never assume success

---

## Testing & Local Development

### Mock bridge for development without the native app:

```typescript
// test/mock-yalla-sip.ts
import type { YallaSIPBridge, YallaSIPEventMap } from '../types/yalla-sip';

type Listeners = { [K in keyof YallaSIPEventMap]?: Set<Function> };

export function createMockBridge(): YallaSIPBridge & {
  simulate: <K extends keyof YallaSIPEventMap>(event: K, data: YallaSIPEventMap[K]) => void;
} {
  const listeners: Listeners = {};

  const bridge: any = {
    ready: async () => ({
      version: '1.0.0',
      capabilities: ['call', 'agentStatus', 'callQuality'],
      agent: { id: 'mock-agent', name: 'Test Operator' },
      bufferedEvents: [],
    }),

    on: (event: string, handler: Function) => {
      if (!listeners[event]) listeners[event] = new Set();
      listeners[event]!.add(handler);
      return () => listeners[event]?.delete(handler);
    },

    off: (event: string, handler: Function) => {
      listeners[event]?.delete(handler);
    },

    makeCall: async (number: string) => ({
      success: true,
      callId: crypto.randomUUID(),
    }),

    answer: async () => ({ success: true }),
    reject: async () => ({ success: true }),
    hangup: async () => ({ success: true }),
    setMute: async (_: string, muted: boolean) => ({ success: true, isMuted: muted }),
    setHold: async (_: string, onHold: boolean) => ({ success: true, isOnHold: onHold }),
    setAgentStatus: async (status: string) => ({ success: true, status }),

    getState: async () => ({
      connection: { state: 'connected', attempt: 0 },
      agentStatus: 'ready',
      call: null,
    }),

    getVersion: async () => ({
      version: '1.0.0',
      capabilities: ['call', 'agentStatus', 'callQuality'],
    }),

    // Test helper
    simulate: (event: string, data: any) => {
      listeners[event]?.forEach(fn => fn(data));
    },
  };

  return bridge;
}

// Usage in tests:
const mock = createMockBridge();
window.YallaSIP = mock;

// Simulate an incoming call:
mock.simulate('incomingCall', {
  callId: 'test-123',
  number: '+998901234567',
  direction: 'inbound',
  seq: 1,
  timestamp: Date.now(),
});
```

---

## Page Reload Behavior

If your page reloads (F5, navigation, crash):

1. The native app detects the reload
2. Your page loads fresh
3. `window.YallaSIP` is re-injected
4. You call `ready()` again
5. Native sends `_init` with current state (including active call info if any)
6. Use `getState()` to sync your UI with the current call state

**Your app must handle being initialized mid-call.** Always check `getState()` after `ready()` to see if a call is already active.

```typescript
const initData = await bridge.ready();
const state = await bridge.getState();

if (state.call) {
  // We reloaded during an active call — restore UI
  restoreCallUI(state.call);
}
```

---

## Security Notes

- Phone numbers in events are real customer data (PII) — do not log them to third-party services
- The bridge is only available in the main frame — iframes cannot access it
- All commands are rate-limited on the native side — excessive calls return `RATE_LIMITED`
- Server IP addresses are never exposed through the bridge
- Raw call quality metrics (MOS, jitter, RTT) stay native-side — only quality grades are shared

---

## FAQ

**Q: Do I need to build call UI (answer/reject/mute/hold buttons)?**
A: No. The native toolbar handles all call controls. Your app only receives events and reacts.

**Q: Can I make calls from my React app?**
A: Yes, use `window.YallaSIP.makeCall(number)`. The native toolbar will show the call controls.

**Q: What happens if the bridge isn't available?**
A: Your app loads in a normal browser without the native app. `window.YallaSIP` will be `undefined`. Always check for its existence.

**Q: Can multiple React components listen to the same event?**
A: Yes. The bridge uses an EventEmitter pattern — multiple `on()` calls for the same event all fire.

**Q: What's the `seq` field for?**
A: Monotonically increasing sequence number. If you receive event with `seq: 5` after `seq: 7`, event 5 arrived out of order. In practice this shouldn't happen, but the field is there for debugging.

**Q: How do I test without the desktop app?**
A: Use the mock bridge from the "Testing" section above. Set `window.YallaSIP = createMockBridge()` before your app initializes.
