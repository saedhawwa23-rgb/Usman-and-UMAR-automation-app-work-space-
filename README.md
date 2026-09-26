# USMAN AND UMAR

USMAN AND UMAR is an Android-native accessibility assistant based on the official [OpenDroid](https://github.com/yashab-cyber/opendroid) project. This repository contains the imported OpenDroid foundation and the v1.0.2 device/build configuration for USMAN AND UMAR.

## Runtime scope

The app runs natively on Android and uses Android Accessibility Services for screen reading, UI navigation, and automation. It does not package or launch Linux virtual machines, PRoot, Node.js, Python, Ubuntu, QEMU, or other desktop/server runtime environments. Android-native inference components, where enabled, remain ordinary Android libraries and do not virtualize another operating system. The application does not request a large heap and is configured for devices with approximately 2.7 GB RAM.

## Device and build requirements

| Setting | Value |
|---|---|
| Minimum Android SDK | 28 (Android 9) |
| Target Android SDK | 34 (Android 14) |
| Release version | 1.0.2, versionCode 3 |
| Native ABI | `armeabi-v7a` |
| APK packaging | 32-bit ARM APK |
| Primary control channel | Android Accessibility Service |

## Build

Install JDK 21 and the Android SDK with the required platform/build-tools packages, then run:

```bash
./gradlew assembleRelease -PallowUnsignedRelease=true
```

The unsigned validation APK is written to `app/build/outputs/apk/release/app-release-unsigned.apk`. A distributable release build requires a release keystore configured through Gradle properties; credentials must never be committed to this repository.

For a quick compile check:

```bash
./gradlew assembleDebug
```

## Accessibility setup

After installation, open Android Settings, enable the **USMAN AND UMAR** Accessibility Service, and grant only the permissions needed for the features you choose to use. Accessibility is the app's native UI automation mechanism.

## Project provenance and license

The Android code was imported from the official OpenDroid repository and adapted for this project. The upstream Apache License 2.0 notices are retained in [LICENSE](LICENSE). Project-specific release notes are in [docs/USMAN_AND_UMAR_RELEASE.md](docs/USMAN_AND_UMAR_RELEASE.md).
