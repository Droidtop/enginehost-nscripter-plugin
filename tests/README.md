# Tests

Two checks, neither of which needs a device, a rig or an installed bundle.

## `check_engine_contract.py`

Reads the wrapper and the engine side by side and fails if they have drifted
apart: an option the wrapper passes that the engine no longer parses, an action
that presses a key the engine no longer reads, a documented default that is no
longer what the engine's own controller table says, a context that differs
between the wrapper, the bundle metadata and the origin document, or a declared
option nothing reads. Every one of those is something an upstream engine bump
could change without a build failing, and every one would show up on a device
as a setting that does nothing or a button that does nothing.

    python3 tests/check_engine_contract.py

It needs `thirdparty/port/SDL2-2.26.3` present, because it checks the Android
key translation against SDL's own table rather than assuming it. Run
`script/_fetch.sh` first, or run it after a build. CI runs it after the APK.

## `run_headless_game.sh`

Builds the engine's Linux target and runs it under a virtual X server against
`tests/game`, the smallest complete NScripter game there is: one line of text,
a grab of the screen, `end`. It passes when the grab has ink in it, which means
the engine started, found the script, laid the text out and drew it.

    tests/run_headless_game.sh

`tests/game/0.txt` is three lines written for this test. No game content is
copied into this repository, and no font either: the script uses whichever
TrueType font the machine already has, because what is being checked is that
the engine draws, not which glyphs it draws.
