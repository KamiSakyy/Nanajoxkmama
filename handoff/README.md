# AniBeat APK handoff

`AniBeat-release.apk` is the current signed Android release built by GitHub Actions.

- `version.txt` — Android version name and monotonically increasing version code.
- `SHA256SUMS.txt` — checksum of the APK.
- The same persistent signing key is used by every release, so a newer build installs as an update.

The workflow intentionally runs only the signed `release` APK build. It does not launch an emulator and does not run tests or lint.
