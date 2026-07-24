# Release Checklist

- Confirm `local.properties` is not included
- Confirm no API keys are committed
- Confirm no APK/AAB/build cache is included
- Confirm README is up to date
- Confirm LICENSE is present
- Confirm asset redistribution rights
- Confirm build works on a clean environment
- Confirm default config does not rely on local machine paths
- Confirm sensitive debug notes are removed
- Run `./gradlew testDebugUnitTest` successfully
- Run `./gradlew lintDebug` and `./gradlew lintVitalRelease` successfully
- Verify the version name and version code match README and CHANGELOG
- Smoke-test theme, chat display preview, 9:16 splash crop, and personalization persistence after app restart
- Smoke-test desktop shortcut first creation, existing shortcut name update, and custom icon update on a physical device
- Switch through zh-CN, zh-TW, ja, and en; record any untranslated beta UI before release
- Document beta-only limitations and test targets in README or release notes
