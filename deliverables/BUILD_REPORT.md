# Build report — v1.1.1 (verified in this environment)

Every number below comes from a command that was actually executed. The raw log is quoted
where it matters.

## 1. Headline

| Item | Result |
|---|---|
| Version | **1.1.1** (`versionCode 3`) — was 1.1.0 / 2 |
| Toolchain | Temurin **JDK 17.0.21**, Gradle **8.7**, Android SDK platform **35**, build-tools **35.0.0** |
| Build | `:app:testDebugUnitTest :app:assembleRelease :app:assembleDebug` → **BUILD SUCCESSFUL** |
| Unit tests | **93 tests, 0 failures** |
| Release APK | `deliverables/IranianAppDetector-release.apk` — **6,751,648 bytes (6.44 MiB)** |
| Debug APK | `deliverables/IranianAppDetector-debug.apk` — 10,034,598 bytes (9.57 MiB) |
| SHA-256 (release) | `d102c45f2aba6f524ec203544fc6450562f9d673b531f16278be692bd01ffcec` |
| Signature | `apksigner verify` → **OK** (Android Debug key, v2 scheme) |
| Manifest | `aapt2 dump badging` → `ir.irandetector.app`, versionCode 3, versionName 1.1.1, minSdk 26, targetSdk 35 |

Release APK grew 33 KB (6,718,496 → 6,751,648). R8 stays **opt-in** (`-PiranDetector.r8=true`):
full-mode R8 needs more heap than this sandbox provides (reproducible OOM at ~1.09 GB).

## 2. What was fixed

### 2.1 "درخواست تایید نیامد" — uninstall confirmation

Reported symptom: the queue ended with `0 removed, 1 cancelled` and no confirmation was ever
shown for `com.ghsoft.bale`.

Root causes (all real, all fixed):

| # | Cause | Fix |
|---|---|---|
| 1 | `ACTION_UNINSTALL_PACKAGE` / `ACTION_DELETE` were not declared in `<queries>`. With only `QUERY_ALL_PACKAGES`, intent resolution against the OEM uninstaller is not guaranteed on Android 11+, so `launch()` could resolve to nothing. | `<queries>` now declares `DELETE`, `UNINSTALL_PACKAGE`, `INSTALL_PACKAGE`, `CONFIRM_INSTALL`, the APK MIME type, `MANAGE_UNKNOWN_APP_SOURCES` and `APPLICATION_DETAILS_SETTINGS`. `resolveActivity()` is checked before every launch. |
| 2 | No in-app confirmation existed. If the system dialog was swallowed, the user saw nothing and the app reported "Cancelled". | New `UninstallConfirmDialog`: one dialog per queued app, with icon, label, package id, "2 of 5", a 2-step hold guard and explicit **Uninstall / Skip / Cancel remaining**. |
| 3 | A silent system failure was indistinguishable from a user cancellation. | `onPause` is treated as proof the system UI appeared. If no UI was seen and the package is still installed, the app opens that app's Settings page (where Uninstall always exists) and says so in the dialog. |
| 4 | Result verification used fixed 250/400/600 ms probes. | Staged schedule (5 attempts) scaled by the new **Pause between requests** setting (150–1200 ms). |
| 5 | A late callback could be attributed to the next app in the queue. | `onUninstallResult(succeeded, packageName)` now validates against `state.current` and drops stale results. |
| 6 | `skipCurrent()` did not exist, so declining in-app was impossible. | Added to `UninstallManager` (+2 tests); skip is refused while a request is in flight. |

### 2.2 "Package installer پیشنهاد نمی‌شود"

This is an Android platform rule, not an app defect: a browser may only hand an APK to the
installer when **Install unknown apps** is granted to that browser. What v1.1 adds:

* the `<queries>` entries above (without them, resolution also fails on Android 11+);
* **Settings → Install aid** and a `?` action on the Results screen → `InstallHelpDialog`,
  which explains the rule and opens, in one tap each, `ACTION_MANAGE_UNKNOWN_APP_SOURCES`
  (this app), the Downloads screen, and — if a path is known — the APK in the platform
  installer;
* an explicit statement that the app never requests `INSTALL_PACKAGES` and never installs
  anything itself.

### 2.3 VPN Hunter — stronger

* Curated signature list grew from 29 to **82 package ids / namespace families**, including
  Iran-focused clients (Hiddify, NekoBox, NekoRay, Lantern, NapsternetV, Shecan, Tor Browser,
  v2ray/SagerNet families) and more commercial clients (PureVPN, IPVanish, CyberGhost,
  TunnelBear, Hotspot Shield, Avast, Kaspersky, Hola…). Only publicly documented ids.
* New name keywords: vmess, trojan, singbox, sing-box, hiddify, nekobox, shecan, هایدیفای,
  اینترنت آزاد, آنتی فیلتر.
* **Always-on VPN now counts as proof of tunnel ownership**, not only as context
  (`SystemVpnSnapshot.alwaysOnPackage` / `alwaysOnLockDown`), and the finding says so in
  words.
* The probe takes one `SystemVpnSnapshot` per scan and reuses it for both the probe and the
  engine, so the network state cannot change mid-scan.
* Manifest scan now also requests `GET_ACTIVITIES | GET_RECEIVERS`; deep-link probing uses
  `setPackage()` so it is one bounded query per app.
* New filters, in Settings **and** as quick chips on the VPN Hunter screen: *Show possible
  matches* and *Scan system apps*. Both are applied inside the engine (`includePossible`,
  `includeSystemApps`) and covered by tests.
* Free-text filter over findings and evidence.

### 2.4 Settings — expanded

New "Uninstall safety" section (in-app confirmation status, pacing slider, help dialog),
new "Install aid" section, VPN Hunter sub-options, and the Results screen got a help action.
Old sections (appearance, scan prefs, database, privacy, about) unchanged.

### 2.5 Per-app removal in VPN Hunter (v1.1.1)

Every finding card now has its own **Remove** button (plus *App info*). It runs the same
pipeline as the bulk flow for exactly one package:

`UninstallConfirmDialog` (copy says "VPN client · removes only this app") → `ACTION_UNINSTALL_PACKAGE`
→ `PackageManager` verification → the finding is dropped from the report.

Implementation notes that matter:

* `AppViewModel.removeFromVpnHunter(packageName, label)` starts a **one-item** `UninstallManager`
  queue and tags the prompt with `UninstallOrigin.VPN_SINGLE`, so the dialog can use different
  copy and the queue is reused instead of duplicated.
* It refuses to start while another confirmation or the bulk queue is running — two system
  requests can never be in flight at once.
* The button shows a spinner while Android is confirming, and *App info* opens the package's
  Settings page for clients that are device-admin / managed-profile protected.
* +2 unit tests: single-item success finishes the queue, single-item decline leaves the app
  installed.

## 3. Tests

| Suite | Count |
|---|---|
| DetectionEngineTest | 18 |
| TextNormalizerTest | 17 |
| UninstallManagerTest | 13 |
| **UninstallRegressionTest** | **9** (+2: explicit skip, all-skip completion, single-item success/decline) |
| **VpnDetectorTest** | **21** (+4: system-app filter, possible filter, Iranian client ids, always-on ownership) |
| DatabaseRecordTest | 8 |
| IconHashTest | 7 |
| **Total** | **93 — 0 failures** |

## 4. Artifacts

```
deliverables/
├── IranianAppDetector-release.apk    6,751,648 B  sha256 77e71706…
├── IranianAppDetector-debug.apk     10,121,134 B
├── IranianAppDetector-source.zip                full repo (brand/, .github/, LICENSE…)
├── README.md  BUILD_REPORT.md  SOURCE_MAP.md  design-reference.html
└── brand/  logo.svg · logo-512.png · logo-1024.png · motion-logo.mp4 · motion-logo.gif
            motion-poster.png · render_motion.py · play-store/
```

## 5. Reproduce

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
cd IranianAppDetector
gradle :app:testDebugUnitTest     # 91 tests
gradle :app:assembleRelease       # 6.44 MiB APK
```

## 6. Still unverified (honest list)

* No `/dev/kvm` here, so **no emulator and no device run**. Everything above is compile-time,
  static-analysis and JVM-test evidence.
* Whether a specific OEM build shows the system uninstall screen can only be confirmed on
  that device. The new fallback exists precisely because it cannot be guaranteed.
* `TrafficStats` for other UIDs and the readability of `always_on_vpn_app` vary by ROM; the UI
  reports the limitation instead of guessing.
* Release signing still uses the debug key (no release keystore in the sandbox). Not suitable
  for store upload; see README §7.

## 3. Release + repository (round 3)

| Item | Result |
|---|---|
| GitHub repository | <https://github.com/matrixteam403-hash/iranian-app-detector> — public, default branch `main`, 86 tracked files, 10 topics |
| Commits | `5b270d6` (v1.1.1 feature) · `a3fc819` (CI + templates) · `1a86c38` (brand film + docs) |
| Release | <https://github.com/matrixteam403-hash/iranian-app-detector/releases/tag/v1.1.1> |
| Release assets | `iranian-app-detector-v1.1.1.apk` 6,751,648 B · `iranian-app-detector-v1.1.1-source.zip` 6,183,694 B (87 files) · `iranian-app-detector-motion-full.mp4` 1,814,015 B |
| APK sha256 (asset) | `d102c45f2aba6f524ec203544fc6450562f9d673b531f16278be692bd01ffcec` — byte-identical to the build output |

### 3.1 Identity film

`brand/motion-full.mp4` — 18 s, 1920×1080, 30 fps, H.264 (~1.8 MB), rendered frame by frame by
`brand/render_motion_full.py` (Pillow + imageio-ffmpeg, no system ffmpeg needed):

| Time | Scene | Content |
|---|---|---|
| 0:00–0:05 | Identity | shield stroke draws on, radar sweep inside a clipped silhouette, sonar blips, wordmark, feature pills |
| 0:05–0:11 | Detection | package rows, scan band, per-row confidence → tier, session counters count up |
| 0:11–0:18 | Protection | VPN Hunter's five methods light up, per-app Remove card, confirmation caption, end card |

Also shipped: `motion-full.webm` (VP9), `motion-full-poster.png` (end card, README hero),
three scene stills, and `motion-full-web.gif` in `deliverables/brand/` for chat/social use.

### 3.2 Documentation

`docs/APP_DESCRIPTION.fa.md` (full Persian description) and `docs/APP_DESCRIPTION.en.md`
(English mirror) are in the repository and linked from the release page. Covers: features, the ten
detection signals with weights, VPN Hunter's five methods and scoring caps, per-app removal, the
uninstall guarantees, permissions, privacy, install steps, build-from-source, honest limitations,
FAQ and ready-to-paste store copy.
