# AOSP default directional focus semantics

The default focus simulation in `ProbeKeys.java` is adapted from the Android Open Source Project, copyright (C) 2006 The Android Open Source Project, licensed under Apache License 2.0. A copy is included as `AOSP-FOCUS-LICENSE.txt`.

Sources retrieved through the GitHub connector at tag `android-9.0.0_r1`:

- https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-9.0.0_r1/core/java/android/view/ViewRootImpl.java — `performFocusNavigation`, lines 4931–4995; `processKeyEvent`, lines 5030–5100.
- https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-9.0.0_r1/core/java/android/app/Dialog.java — `dispatchKeyEvent`, lines 807–816.

Modified for an isolated application test: only four unmodified DPAD directions after an unhandled DOWN; explicit live Activity/Dialog/Window/root/token checks; visible/enabled/focusable same-root candidates; public natural search, coordinate conversion and requestFocus; bounded same-root unhandled/default focus; metadata-only receipts. No focus sound, IME, touch-mode conversion, shortcut/fallback policy, hidden API or global input injection is implemented. If all default candidates cannot be prevalidated, default focus uses a natural FocusFinder candidate which must pass the same checks. This is not a complete ViewRoot or system remote-input implementation.
