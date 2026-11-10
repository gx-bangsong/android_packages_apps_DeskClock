# Release workflow

This repository builds and publishes release APKs automatically. The logic lives in
`scripts/ci-build.sh`; `.github/workflows/release.yml` only checks out, sets up the toolchain,
and calls the script. Nothing here changes app functionality.

## What happens automatically

A push to `lineage-23.2` (or a manual **Actions → release → Run workflow**) builds
`assembleRelease` and publishes a GitHub Release with:

- `DeskClock-<versionName>.apk` — the signed release APK
- `DeskClock-<versionName>.apk.sha256` — its SHA-256 checksum
- `build-info.json` — `applicationId`, `versionCode`, `versionName`, `signer_sha256`,
  `apk_sha256`, `apk`, `commit`, `source_url` (read by GKUI-Pack; keep the field names stable)

The release tag is `v<versionName>` and points at the exact commit that was built.
Documentation-only pushes (`docs/**`, `**.md`) do not publish.

### Version scheme

- `versionCode` = `VERSION_CODE_BASE` + `git rev-list --count HEAD`. The default base is
  `1144400000`, chosen so release builds of `com.android.deskclock` outrank vendor builds of
  the same package in version comparisons — Xiaomi's clock reports versionCode `1144400000`,
  and updater apps that catalogue it stop considering ours older. The commit count added on
  top only grows, so every release is newer than the last. Set the `VERSION_CODE_BASE`
  environment variable to override the base (`0` gives the raw commit count); the value must
  keep `base + commit count` within the 32-bit int limit (`2147483647`).
- `versionName` = `yyyyMMdd.<commit count>` (UTC), for example `20261109.11444` — the short
  commit count, **not** the based versionCode, so tags and file names stay compact. The
  `VERSION_NAME` environment variable overrides it when a custom name is needed.
- Raising the base later is a one-line change in `build.gradle`. Lowering or removing it after
  a based release has shipped is a **downgrade** for installed devices: Android refuses to
  install a smaller versionCode over a larger one (uninstall first, losing data, or
  `adb install -d`).
- This needs the full history: the workflow checks out with `fetch-depth: 0`, and both
  `build.gradle` and `ci-build.sh` **refuse to build a release from a shallow clone**.
- No version value is ever written back into the repository; everything is computed at build
  time. `ci-build.sh` fails the release if the build changed any tracked file.

### Signing

Release signing is read **only** from environment variables, never from
`~/.gradle/gradle.properties` or any file in the repository:

| Variable          | Meaning                                             |
|-------------------|-----------------------------------------------------|
| `KEYSTORE_BASE64` | base64-encoded keystore contents (used by CI)      |
| `KEYSTORE_PASS`   | keystore password                                   |
| `KEY_ALIAS`       | key alias                                           |
| `KEY_PASS`        | key password                                        |

Local Gradle builds use `KEYSTORE_FILE=/path/to/release.jks` as a keystore path. The GitHub
Actions release workflow does **not** read a keystore path from secrets; it decodes
`KEYSTORE_BASE64` into a private temporary file and points Gradle at it.

- **GitHub Actions compatibility fallback**: the release workflow uses
  `secrets.KEYSTORE_BASE64` when it is set; if it is empty, it falls back to
  `secrets.KEYSTORE_FILE`, still treating that secret as base64 keystore contents. This keeps
  repositories that stored the base64 keystore under the misleading `KEYSTORE_FILE` name
  working without a secret rename. Creating a properly named `KEYSTORE_BASE64` secret remains
  recommended; the fallback is for compatibility.

- **CI**: `scripts/ci-build.sh` fails immediately when the effective `KEYSTORE_BASE64`,
  `KEYSTORE_PASS`, `KEY_ALIAS`, or `KEY_PASS` is missing; `build.gradle` additionally refuses
  any release packaging task without a complete signing environment. The workflow supplies the
  effective `KEYSTORE_BASE64` from `secrets.KEYSTORE_BASE64`, falling back to
  `secrets.KEYSTORE_FILE` (base64 contents) when needed. A release is never silently skipped.
- **Local**: `./gradlew assembleRelease` without the signing environment falls back to the
  **debug key** and prints a prominent warning ("Do not distribute it"). `assembleDebug` is
  never affected.

### Gates (all must pass before anything is published)

1. **Package identity** — `aapt2 dump badging`: applicationId must be exactly
   `com.android.deskclock`, without a `.dev`/`.debug` suffix; the APK must not be debuggable;
   versionCode must be numeric; versionName non-empty.
2. **Signer fingerprint** — `apksigner verify --print-certs`: exactly one signer, and its
   SHA-256 certificate fingerprint must match `release-signer.sha256` in the repository root.
   A different key fails the release.
3. **Monotonic versionCode** — the new versionCode must be greater than the `versionCode` in
   the most recent GitHub Release's `build-info.json` (skipped when there is no previous
   release or it has no `build-info.json`).
4. **Tag uniqueness** — `v<versionName>` must not already exist.
5. **Clean tree** — the build must not have modified any tracked file.

## Owner checklist (manual, one-time)

1. **Create the release keystore** (or reuse the GKUI release key used by GKUICalculator /
   GKUICalendar — same fingerprint):
   ```sh
   keytool -genkeypair -v -keystore release.jks -alias gkui-release \
     -keyalg RSA -keysize 4096 -validity 10000
   base64 -w0 release.jks > release.jks.b64     # value for the KEYSTORE_BASE64 secret
   ```
   Keep the file and its passwords; losing them means no upgrade path for users.
2. **Add four repository secrets** (Settings → Secrets and variables → Actions):
   `KEYSTORE_BASE64` (contents of `release.jks.b64`), `KEYSTORE_PASS`, `KEY_ALIAS`
   (`gkui-release` in the command above), `KEY_PASS`.
   If a base64 keystore was previously stored as `KEYSTORE_FILE`, the release workflow falls
   back to it for compatibility, but you should create `KEYSTORE_BASE64` and leave the fallback
   unused.
3. **Check `release-signer.sha256`** in the repository root. It currently contains the GKUI
   release-key fingerprint used by GKUICalculator and GKUICalendar:
   `2eaa964c9eae1ee70e131a6ca5f8f06d7c1c152a037cac33d01f585c758f214a`.
   - If you sign with that key, nothing to do.
   - If you use a **different** key, the first release run fails at the signer gate and prints
     the detected fingerprint; put that value into `release-signer.sha256`, commit, and run the
     workflow again. After that the gate blocks every other key.
4. **Run the workflow** — merge a change into `lineage-23.2` (non-docs), or
   Actions → release → Run workflow. Watch the run; on success the release with tag
   `v<yyyyMMdd>.<commit-count>` appears with the three assets listed above.

## Failure modes and what they mean

| Symptom in the run log                                   | Meaning                                                        |
|----------------------------------------------------------|----------------------------------------------------------------|
| `environment variable KEYSTORE_BASE64 is not set`        | secrets not configured (checklist step 2)                      |
| `Release build refused: signing environment incomplete`  | Gradle-side refusal; same cause                                 |
| `shallow clone detected` / `needs full git history`      | checkout was not `fetch-depth: 0`                              |
| `signer SHA-256 ... does not match release-signer.sha256`| the key does not match the committed fingerprint (step 3)      |
| `versionCode ... is not greater than the previous ...`   | history was rewritten or the previous release was built later  |
| `the build changed tracked files; release refused`       | a task wrote into the source tree; must be fixed, not ignored  |

## Local release builds

```sh
KEYSTORE_FILE=/path/release.jks KEYSTORE_PASS=... KEY_ALIAS=gkui-release KEY_PASS=... \
  ./gradlew assembleRelease
```

Without those variables the build is signed with the debug key and warns. The APK lands in
`build/outputs/apk/release/DeskClock-release.apk`. Nothing is published locally; releases only
come from the workflow.
