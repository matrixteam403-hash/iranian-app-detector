# Security policy

## What this app does with your data

* Everything happens on the device. There is no backend, no account, no analytics SDK.
* The only network request the app can make is the **optional** detection-database update
  you configure yourself in Settings. It is a plain `GET` to the URL you typed.
* The app never uploads the list of installed apps, never reads message content, contacts,
  or media, and does not request accessibility, device-admin, usage-stats or root access.
* Scans are stored locally in Room; you can clear them from Settings.

## Reporting a vulnerability

Open a private security advisory on GitHub (Security → Advisories → *Report a vulnerability*)
rather than a public issue if the report involves:

* a way to make the app remove a package **without** the system confirmation dialog,
* a path where the remote database update could execute code or wipe local data,
* a permission escalation.

Please include the app version, Android version, device model and reproduction steps.

## Supported versions

Only the latest release receives fixes.

## Verifying a release APK

```bash
sha256sum IranianAppDetector-release.apk          # compare with the release notes
apksigner verify --print-certs IranianAppDetector-release.apk
```
