# Cloud environment setup (Claude Code on the web)

Android builds **cannot run** in a default Claude Code cloud session. This is a network-policy
problem, not a project problem, and it takes two changes to fix — both made in the environment
dialog at [claude.ai/code](https://claude.ai/code), neither of which can be made from inside a
session.

---

## The symptom

```
FAILURE: Build failed with an exception.
* What went wrong:
Plugin [id: 'com.android.application', version: '9.1.1', apply: false] was not found
  could not resolve plugin artifact
  'com.android.application:com.android.application.gradle.plugin:9.1.1'
```

Gradle itself runs fine. It fails at *plugin resolution*, before reading a single Kotlin file, so
every `:app:*` task — `assembleDebug`, `testDebugUnitTest`, `lint` — is unreachable.

## The cause

The Android Gradle Plugin and the Android SDK are served **only** from `dl.google.com`. The default
**Trusted** network level does not allow that host:

| Host | Trusted default | Notes |
|---|---|---|
| `repo1.maven.org` | ✅ allowed | Maven Central resolves fine |
| `plugins.gradle.org` | ✅ allowed | Gradle Plugin Portal fine |
| `services.gradle.org` | ✅ allowed | Wrapper distribution fine |
| `cloud.google.com`, `accounts.google.com`, `*.googleapis.com` | ✅ allowed | Present, but irrelevant here |
| **`dl.google.com`** | ❌ **blocked** | **AGP + Android SDK live here** |

`maven.google.com` is *not* a workaround — it is a 301 redirector to `dl.google.com`, so it dead-ends
at the same denial. There is no route around this from inside the container, and the agent proxy's
own README is explicit that a 403 is an egress-policy denial to report rather than circumvent.

Verify the block from any session:

```bash
curl -sS "$HTTPS_PROXY/__agentproxy/status"   # look for dl.google.com in recentRelayFailures
```

---

## Fix, part 1 — allow `dl.google.com`

At [claude.ai/code](https://claude.ai/code), open the environment selector, hover the environment and
click the settings icon (or **Add cloud environment** for a new one). In the dialog:

1. Set **Network access** to **Custom**.
2. In **Allowed domains**, add:
   ```text
   dl.google.com
   ```
3. Tick **"Also include default list of common package managers"** — without it you lose Maven
   Central, the Gradle Plugin Portal and GitHub, and the build breaks a different way.

Changing allowed hosts invalidates the environment cache, so the setup script below re-runs on the
next session. That is expected.

## Fix, part 2 — install the Android SDK

Allowing the host is necessary but not sufficient: the SDK is not part of the base image. Add both
of these in the same dialog.

**Environment variables** field:

```text
ANDROID_HOME=/opt/android-sdk
ANDROID_SDK_ROOT=/opt/android-sdk
```

Setting these avoids committing a `local.properties`, which is developer-local and gitignored.

**Setup script** field:

```bash
#!/bin/bash
# Android SDK for ChefAI. Requires dl.google.com on the environment's allowlist.
set -u

export ANDROID_HOME=/opt/android-sdk
mkdir -p "$ANDROID_HOME/cmdline-tools"

# Every command is `|| true`-guarded: the docs require the script to exit zero, or the session
# fails to start outright. A broken SDK install should degrade to "no build", not "no session".
curl -fsSL --max-time 240 -o /tmp/cmdline-tools.zip \
  "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip" || true
unzip -q -o /tmp/cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools" || true
mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" 2>/dev/null || true

SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [ -x "$SDKMANAGER" ]; then
  yes | "$SDKMANAGER" --licenses > /dev/null 2>&1 || true
  "$SDKMANAGER" "platform-tools" "platforms;android-36" "build-tools;36.0.0" || true
fi

exit 0
```

Versions match `app/build.gradle.kts` (`compileSdk = 36`, `targetSdk = 36`, `minSdk = 28`). Bump the
`platforms;`/`build-tools;` lines when `compileSdk` moves.

> **Unverified:** the `commandlinetools-linux-13114758_latest.zip` build number could not be checked
> from a session, because the host it lives on is the one being unblocked. Google rotates that
> number. If the first session after the change still can't build, check whether that URL 404s and
> update it from the [command-line tools downloads](https://developer.android.com/studio#command-tools).

### Runtime budget

Setup scripts must finish in roughly five minutes or the environment cache won't build. The SDK
download is a few hundred MB and normally fits, but it is the slowest thing here. If it times out,
move it into a background `SessionStart` hook instead, as the
[cloud-environments docs](https://code.claude.com/docs/en/cloud-environments#setup-scripts) suggest.

The script runs **once** per environment; afterwards Anthropic snapshots the filesystem and later
sessions start with the SDK already on disk. It re-runs only when the script or the allowed-host
list changes, or after the cache expires (~7 days).

---

## Verifying the fix

In a **new** session (resuming an existing one never re-runs setup):

```bash
curl -sS -o /dev/null -w "%{http_code}\n" https://dl.google.com/   # expect 200, not 000
echo "$ANDROID_HOME"                                              # expect /opt/android-sdk
cd /home/user/ChefAI && ./gradlew :app:testDebugUnitTest
```

The third command is the verification `CLAUDE.md` requires after every code change. Until parts 1
and 2 are both in place, that instruction cannot be satisfied in a cloud session, and any claim that
a change "builds" or "passes tests" from such a session is unfounded.

## What this does not fix

`androidTest` (instrumented Compose UI tests) needs an emulator or a physical device. Neither exists
in a cloud session, and KVM acceleration is not available, so `connectedDebugAndroidTest` stays a
local-only step regardless of network policy. Plan UI-test verification for a local machine or CI.
