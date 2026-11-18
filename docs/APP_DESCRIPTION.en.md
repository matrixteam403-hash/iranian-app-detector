# Application description — Iranian App Detector

> **v1.1.1** (`versionCode 3`) · Android 8.0+ · no ads, no tracking, no root
> Repo: <https://github.com/matrixteam403-hash/iranian-app-detector> ·
> Release: <https://github.com/matrixteam403-hash/iranian-app-detector/releases/tag/v1.1.1>
>
> (Persian version: [`APP_DESCRIPTION.md`](APP_DESCRIPTION.md))

## At a glance

Iranian App Detector is a fully offline Android app that

1. inventories every installed package,
2. decides which ones are published by Iranian companies using **ten independent signals**,
3. shows the **evidence** behind every verdict ("why does it think this?"),
4. and helps you remove unwanted apps **one at a time**, always through Android's official
   confirmation dialog.

It also ships an optional **VPN Hunter** module that finds every VPN / proxy / anti-censorship
client on the device with **five independent methods**, and gives every finding its own
**Remove** button — so each client can be removed individually, without building a queue.

Three design rules hold throughout:

* **Nothing is invented.** Package ids, signing certificates and domains come only from
  verifiable public sources; where verification was not possible the field is left empty.
* **Nothing is removed without consent.** The app never calls `PackageInstaller.uninstall()`;
  it hands an intent to the system, and the system takes the final confirmation.
* **Nothing leaves the device.** The installed-app list, scan results and VPN reports are
  never transmitted.

## Screens

| Screen | Purpose |
|---|---|
| **Dashboard** | Installed / detected / verified / probable / uncertain counters, database info, scan button |
| **Scanner** | Live progress (packages discovered, package being analysed), cancellable |
| **Results** | Icon, name, developer, package id, confidence %, tier, per-app evidence, search, filters, sorting, select-all, "Keep" (exclude) |
| **Uninstall queue** | Sequential system-confirmed uninstalls, live progress, removed/cancelled/failed counters, cancel remaining |
| **VPN Hunter** | Opt-in: finds VPN/proxy clients with five methods; each card has its own **Remove** and **App info** |
| **Settings** | Appearance (3 themes), scan preferences, VPN Hunter (toggle + 2 filters), detection database, uninstall safety (pacing), install aid, privacy, about |

## Detection engine — ten independent signals

| Signal | Base points | Notes |
|---|---|---|
| Exact package id | 55–62 | Primary anchor |
| Historical / alternative package id | 50 | e.g. `cab.snapp.passenger.play` |
| Signing certificate (SHA-256 / SHA-1) | 60 | Capped at 72 without a package match |
| Shared signing key with a known app | 34 | Developer identity |
| English name / aliases | 26 | Persian + Latin normalisation |
| Persian name / aliases | 28 | ي/ی, ك/ک, diacritics, ZWNJ, Arabic-Indic digits |
| Fuzzy name (Levenshtein + containment) | 9–16 | ≥ 0.86 or containment |
| Known publisher's package namespace | 18 | `com.digikala.foo` |
| Iranian installer (Bazaar, Myket, …) | 12 | Supporting only |
| Developer domain inside the package id | 10–12 | `digikala` → `digikala.com` |
| Icon perceptual hash (aHash, 64-bit) | 10–24 | Inert until verified reference hashes exist |

Calibration rules that keep the score honest:

* an `ir.` prefix is **never** evidence (dedicated test);
* Persian text alone is **never** evidence (dedicated test);
* a single non-anchor signal can never exceed **29 %** → stays *Uncertain*;
* a certificate without a package match is capped at **72 %**;
* overlapping name signals are damped when a package anchor already matched;
* records marked `UNCERTAIN` in the database can never produce a *Verified* result.

Tiers: `Verified ≥ 80` · `Probable ≥ 55` · `Uncertain ≥ 30`; below that an app is not reported.

## VPN Hunter

**Opt-in** (Settings → VPN Hunter), off by default — nothing is inspected until you enable it
and run a scan.

| # | Method | What it inspects | Max points |
|---|---|---|---|
| 1 | **Known package** | Curated list of published VPN/proxy project ids (Psiphon, Outline, Shadowsocks, WireGuard, OpenVPN, v2rayNG, SagerNet/SFA, Husi, Happ, NapsternetV, Lantern, Amnezia, Tailscale, ZeroTier, WARP, Proton, Mullvad, NordVPN, Surfshark, ExpressVPN, Astrill, PIA, Windscribe, Orbot, I2P, Shecan, Hiddify, NekoBox …) plus vendor families such as `com.hiddify.*` | 62 |
| 2 | **VPN service declaration** | `BIND_VPN_SERVICE`, `VpnService` subclasses in the manifest, transport cores (wireguard, openvpn, shadowsocks, tun2socks, xray …), captive-portal capture | 45 |
| 3 | **System VPN accounting** | live `tun*/ppp*/utun*/wg*/ipsec*` from `NetworkInterface` + `TRANSPORT_VPN` from `ConnectivityManager` + per-UID `TrafficStats` | 34 |
| 4 | **Routing profile** | Android's always-on VPN (`always_on_vpn_app`, `…_lockdown`), "system VPN up while this app ships the transport" | 30 |
| 5 | **Name & developer** | label/package against VPN vocabulary (including فیلترشکن، پروکسی، تونل) and vendor names | 26 |

Scoring: additive with diminishing returns; a **name-only** finding is capped at **40 %**; two
agreeing methods +6, three or more +4; tiers `Confirmed ≥ 70`, `Likely ≥ 45`, else `Possible`.
A curated id alone scores 62 → *Likely*; *Confirmed* needs a second independent method.

Every finding shows the artefacts it was built from (permission, service class, native library,
interface name, traffic volume) and lists the platform limits it hit — e.g. *"Android does not
expose the package that owns the system VPN"*. Tunnel ownership is only attributed when the
package both declares a VPN service **and** shows transport evidence.

### Per-app removal (new in v1.1.1)

Each finding card has **Remove** and **App info**:

1. **Remove** → the app's own dialog, captioned "VPN client · removes only this app";
2. on confirm → exactly **one** `ACTION_UNINSTALL_PACKAGE` for that one package;
3. the outcome is verified with `PackageManager`; only a confirmed removal drops the finding;
4. everything else on the device — and every other finding — is untouched; no queue is built;
5. if another prompt or queue is active the request is refused rather than stacked;
6. a system/device-admin protected client reports honestly, and **App info** opens its Settings
   page so it can still be disabled or force-stopped.

## One-by-one uninstall flow

The reported bug (requests not reaching the phone one at a time) had three root causes, all
fixed: `collectLatest` cancelling the previous dialog's coroutine; trusting `resultCode` on OEM
builds that return `RESULT_CANCELED` after a successful removal; and a lost activity result
leaving `awaitingResult = true` forever. Success is now verified with `PackageManager`
(250/400/600 ms after the dialog plus `onResume`), the state is persisted in
`onSaveInstanceState`, and `UNINSTALL_PACKAGE` is tried before `DELETE`.

**Guarantees:** one dialog at a time; the next request only after the previous outcome is known;
nothing removed silently; a stale late callback cannot skip an item. All covered by
`UninstallRegressionTest`.

Since v1.1 the app also owns the confirmation: its own dialog for one app (with a hold-to-confirm
guard) → `ACTION_UNINSTALL_PACKAGE` → if **no system UI appeared** and the package is still
installed, it opens that app's Settings page instead of ending the queue with "Cancelled".

## Permissions and what they really mean

| Permission | Why | Implication |
|---|---|---|
| `QUERY_ALL_PACKAGES` | Core function: inventory installed apps | Play needs a declared justification (app-management class); without it Android 11+ hides most packages |
| `REQUEST_DELETE_PACKAGES` | Lets the flow hand a package to the system uninstaller | Still always shows the system confirmation; nothing can be removed silently |
| `INTERNET`, `ACCESS_NETWORK_STATE` | **Optional** database update; VPN Hunter reads live network state | Only the GET you trigger yourself; no telemetry |

Not requested: `INSTALL_PACKAGES`, root, device admin, accessibility services, usage stats,
contacts, SMS, location. VPN Hunter reads only public package metadata — never traffic content.

## Privacy

No analytics, no crash-reporting SDK, no advertising id, no account. The installed-app list never
leaves the device. Scans live in a local Room database you can clear at any time. The only network
request is the update you configure yourself. `allowBackup=false`.

## Detection database

Bundled at `app/src/main/assets/detection_db.json` — **18 records**, 24 package ids, 5
vendor-published certificates. Seeded into Room on first launch and cached in memory with
prebuilt indexes, so detection is O(1) per app. The optional remote update validates the payload
**before** replacing the table, so a bad payload cannot wipe bundled data. Adding an app = adding
one JSON object.

## Install

1. Download `iranian-app-detector-v1.1.1.apk` from Releases (6,751,648 bytes).
2. If the browser asks for "Install unknown apps", allow it for that browser (Settings →
   *Install aid* opens both required screens in one tap).
3. Tap the file → **Package installer** → Install. Or `adb install -r <apk>`.

```
sha256   d102c45f2aba6f524ec203544fc6450562f9d673b531f16278be692bd01ffcec
package  ir.irandetector.app    versionCode 3 / versionName 1.1.1
```

## Build from source

JDK 17, Android SDK 35 (`platforms;android-35`, `build-tools;35.0.0`), Gradle 8.7+.

```bash
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew :app:assembleDebug        # installable debug build
./gradlew :app:assembleRelease      # R8 is opt-in: -PiranDetector.r8=true
./gradlew :app:testDebugUnitTest    # 93 JVM tests, no emulator needed
```

Release builds fall back to the debug keystore when `keystore.properties` is missing; CI picks up
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` from repository secrets.

## Technical facts (v1.1.1)

| Item | Value |
|---|---|
| Stack | 100 % Kotlin · Jetpack Compose · Material 3 · MVVM · Hilt · Room · Kotlinx Flow |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |
| Package | `ir.irandetector.app` · 1.1.1 (`versionCode 3`) |
| Release APK | 6,751,648 bytes · `apksigner verify` OK |
| Unit tests | **93 passing, 0 failures** (DetectionEngine 18, TextNormalizer 17, VpnDetector 21, UninstallManager 13, UninstallRegression 9, DatabaseRecord 8, IconHash 7) |
| Architecture | scoring logic is pure Kotlin; Android access sits behind interfaces (`VpnProbe`, `PackageScanner`) so the whole model is unit tested |
| Licence | MIT |

## Honest limitations

1. Detection is probabilistic; absence from the database is **not** evidence of safety.
2. Most records ship without certificate fingerprints because no vendor-attributable source existed.
3. Icon hashing is implemented and tested but inert until verified reference hashes exist.
4. Only `QUERY_ALL_PACKAGES`-visible packages can be scanned; restricted OEM builds report fewer
   and the app says so instead of guessing.
5. Android does not reveal which package owns the system VPN; ownership is inferred only from
   declarations + transport evidence, and weak matches are labelled `Possible`.
6. `TrafficStats` is unavailable for other UIDs on some ROMs; the method then contributes nothing.
7. Nothing is removed silently — both dialogs can be declined. If an OEM suppresses the system
   uninstall screen entirely, the app falls back to that app's Settings page and tells you.
8. Runtime behaviour was validated by 93 unit tests and static analysis; there is no emulator in
   the build environment, so please report device-specific issues.
9. There is no update server — the URL is user-supplied by design.
10. The UI is English-only (framework RTL support is enabled).
11. The published v1.1.1 APK is signed with the **debug key**: installable everywhere, but a Play
    release must be signed with your own key.

## Store copy

* Short (FA): «برنامه‌های ایرانی را بشناسید، دلیلش را ببینید، یکی‌یکی حذف کنید.»
* Short (EN): "Identify Iranian apps, see the evidence, remove them one at a time."
