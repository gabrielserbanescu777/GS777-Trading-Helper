# Build GS777 Trading APK using GitHub Actions

1. Create a **private** GitHub repository.
2. Upload the **contents** of this ZIP to the repository root, including `.github/workflows/android-apk.yml`.
3. Open **Actions > Build GS777 Android APK > Run workflow**.
4. After a successful run, download the `GS777-Trading-Android-APK` artifact.
5. Extract the artifact and install `GS777Trading-Help-v0.70.apk` on an Android device for testing.

The workflow builds a **debug/test APK**, not a Play Store release. The signing key is created at build time; do not use this setup for production updates. Compilation has not been tested in this environment.
