# Contributing (private development)

Use a short branch and a pull request. State the user-visible change, validation
and material limitations. Keep commits focused; do not commit device dumps,
credentials, signing keys, APKs or old experiment folders.

Run `python3 scripts/check_repository.py` and `./gradlew assembleDebug lintDebug`.
UI changes need device screenshots and D-pad checks; animation/performance changes
need measured evidence. Retain a local matching-signed APK before device changes.
Do not alter the stock launcher or enable Accessibility automatically.

Dependency additions need a concrete reason and license review. Prefer Android
framework APIs and bounded background work. Update changelog/docs when behavior
changes. Release and repository publication are separate deliberate steps.
