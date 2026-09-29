# Changelog

All notable changes to this plugin are documented in this file. The format is
based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). The engine
sources are vendored from their upstream project unchanged; this file records
the wrapper, the bundle and the workflow work on top of them, not the engine's
own history.

## [Unreleased]

### Changed

- Bundles are numbered `<declared>-<N>`, counting this repository's own releases instead of the CI run number, so a rerun cannot repeat a version and a newly declared version starts at 1.

## [0.9.0] - 2026-09-28

### Added

- Publishing asks the plugin catalog to reindex, so a new release is listed within a minute instead of on the six-hourly schedule.

### Changed

- Declared the plugin version 0.9.0 for the testing channel, previously 0.1; CI puts its run number in the third component.

## [0.1] - 2026-09-25

### Added

- The plugin itself: the OnscripterYuri 0.7.7 engine vendored whole, so the wrapper and the engine build as one CMake tree and an upstream bump is a merge rather than a pin.
- The engine's main activity as the Enginehost entry point, keeping the engine's own package and class name so its JNI symbols still resolve.
- The host's game folder, engine context, options and controller map arrive as intent extras and are mapped onto options and keys the engine already had.
- Bundle metadata declaring the nscripter and onscripter capabilities, with every declared option citing the engine source line that parses it.
- ENGINEHOST.md, recording which ONScripter line this plugin carries and why, and what the save directory does and does not cover.
- A headless check that builds the engine's Linux target and runs a three-line script under a virtual display, passing when the engine draws.
- A contract check that reads the wrapper and the engine side by side and fails when an option, a key, a context or a documented default drifts between them.
- The origin's public key, certified by the root Enginehost pins.
- A workflow that builds both ABIs, runs both checks, then packs and signs the bundle, the headless job first so an engine break shows in minutes rather than after the Android build.

### Changed

- The standalone player's game browser, storage-access layer and preferences-driven arguments are gone; the project is an Enginehost runtime that loads one activity from the bundle.
- The app builds arm64-v8a and x86_64, each ABI in its own CMake binary tree, with resources compiled at package id 0x80 and R8 left off.
- The NDK is pinned to 26.3.11579264, because NDK 27 removed ALooper_pollAll, which the engine's SDL 2.26.3 calls.
- The Linux target links the Lua library find_package already found instead of asking the linker for a bare "lua" that no distribution ships.

### Fixed

- Gradle build output stays inside the repository; upstream pointed every build directory three levels up, outside the Android Studio project altogether.
- Games save beside themselves again: the wrapper no longer passes Enginehost's save folder as the engine's save directory, which had moved saves out of a game's own folder and hid saves that came with a game.
- A pushed ref name can no longer run commands in build jobs: names reach shell only as environment variables and are escaped before sed, in every platform workflow.
- The bundle signing key no longer sits in the build job alongside Gradle and third-party downloads; the job packs an unsigned payload and a pinned, secret-free signing job signs it.
- Workflows default to read-only permissions, with write access only on the jobs that create releases, and every action is pinned by SHA.
