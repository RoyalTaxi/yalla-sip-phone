---
title: "Troubleshooting"
last_verified_sha: bfb118c
last_updated: 2026-04-16
last_author: claude
status: current
tags: [operations, troubleshooting, debug]
---

# Troubleshooting

## MSI Update Failures

### msiexec exit 1603 — generic fatal error

**Check logs at:**
- Bootstrapper: `%LOCALAPPDATA%\YallaSipPhone\updates\install.log`
- msiexec verbose: `%TEMP%\yalla-update-msiexec.log`
- Uninstall: `%TEMP%\yalla-update-uninstall.log`

**Common causes:**

| Symptom in msiexec log | Cause | Fix |
|------------------------|-------|-----|
| `SECREPAIR: Error determining package source type` | MSI source was inside install dir, got deleted during upgrade | Ensure MSI is copied to %TEMP% before msiexec (bootstrapper does this) |
| `Error 1316. The specified account already exists` | Component GUID conflict — old product not fully removed | Use two-step /x then /i (bootstrapper does this) |
| `Error 1406. Could not write value to key` | Registry write failure during nested RemoveExistingProducts transaction | Use two-step /x then /i (bootstrapper does this) |
| `FindRelatedProducts: per-user / per-machine. Skipping` | ALLUSERS mismatch — trying per-machine upgrade on per-user install | Never pass ALLUSERS=1; app is per-user |
| `MsiSystemRebootPending = 1` | Previous failed install left pending file operations | Reboot the machine |

### Ghost "Another version already installed" after failed upgrades

Previous failed ALLUSERS=1 installs can leave entries in HKLM. Clean manually:

```cmd
reg delete "HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\{ProductCode}" /f
reg delete "HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Installer\UserData\<SID>\Products\<packed-code>" /f
```

### App files gone after failed update

The old product gets uninstalled but the new one fails to install. Reinstall manually:

```cmd
msiexec /i YallaSipPhone-<version>.msi /quiet /norestart
```

Related: [[04-features/auto-update]], [[06-sessions/2026-04-16-auto-update-msi-fix]]
