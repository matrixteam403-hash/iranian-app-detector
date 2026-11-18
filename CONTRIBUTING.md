# Contributing to Iranian App Detector

Thanks for helping. This project has one hard rule that overrides everything else:

> **Never invent data.** A package id, a signing certificate, a developer name or a
> piece of detection evidence must come from a public, checkable source. If you cannot
> cite it, it does not go in the database.

## Ways to contribute

| I want to… | Do this |
|---|---|
| Report a bug | Open a **Bug report** issue with device, Android version and steps |
| Suggest a feature | Open a **Feature request** issue |
| Add an app to the detection database | Open a **Detection database addition** issue, or edit `app/src/main/assets/detection_db.json` directly |
| Improve detection accuracy | Add a unit test that shows the current behaviour is wrong, then fix it |
| Translate the UI | Open an issue first — the app is English-only today and RTL work needs coordination |
| Improve the logo / motion identity | Edit `brand/logo.svg` or `brand/render_motion.py`; both are source-controlled |

## Development setup

```bash
git clone <your fork>
cd IranianAppDetector
echo "sdk.dir=/path/to/Android/Sdk" > local.properties   # JDK 17, SDK 35
gradle :app:testDebugUnitTest        # unit tests, no emulator required
gradle :app:assembleDebug            # installable APK
gradle :app:assembleRelease          # release APK (R8 opt-in: -PiranDetector.r8=true)
```

The repo ships a `gradlew` shim rather than the binary wrapper jar. If you prefer the
standard wrapper, run `gradle wrapper --gradle-version 8.7` once and commit the result.

## Code layout

```
app/src/main/kotlin/ir/irandetector/app/
├── core/          Json parser, Persian-aware TextNormalizer, PackageNames
├── data/          KnownApp, DetectionRepository, ScanRepository, RemoteDatabaseClient
├── database/      Room entities, DAOs, AppDatabase
├── detection/     DetectionEngine + DetectionIndex + IconHash (pure Kotlin)
├── domain/        InstalledApp, DetectionResult, signals, tiers, stats
├── scanner/       PackageScanner (PackageManager only), IconHasher
├── uninstall/     UninstallManager sequential queue state machine (pure Kotlin)
├── vpn/           VpnDetector + VpnSignatures + AndroidVpnProbe
├── presentation/  AppViewModel — single source of truth
├── settings/      DataStore preferences
└── ui/            theme, components, AppIcon, Navigation, screens/
```

Rule of thumb: **pure logic belongs in `detection/`, `uninstall/` and `vpn/` and must be
unit-testable without Android.** Platform access lives in `AndroidVpnProbe`,
`PackageScanner`, `IconHasher` and is injected through Hilt.

## Adding a detection signal

1. Add the enum entry to `SignalType` and give it a weight in `DetectionEngine`.
2. Make it *supporting* unless it uniquely identifies the app. A single weak signal must
   never push an app past the `UNCERTAIN` band (see `DetectionEngineTest`).
3. Add two tests: one positive, one where the signal alone must **not** be enough.
4. Update the signal table in `README.md`.

## Adding a VPN signature

`vpn/VpnDetector.kt` → `VpnSignatures`. Every entry needs a public project page or store
listing. Persian keywords (فیلترشکن، پروکسی، تونل) are matched too, but a name alone is
capped at 40 % confidence by design.

## Style

* Kotlin official style, 4-space indent, trailing commas.
* No `!!`, no swallowed exceptions, no `GlobalScope`.
* Comments explain **why**, not what.
* Every user-facing string stays in English for now; do not add hard-coded Persian text to
  Compose layouts (the framework RTL support must keep working).

## Pull requests

* One topic per PR.
* Include the test output in the description if you touched detection, uninstall or VPN code.
* CI (`.github/workflows/android-ci.yml`) must be green.

## Licence

By contributing you agree that your work is released under the MIT licence in `LICENSE`.
