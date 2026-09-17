#!/usr/bin/env python3
"""Check that the Enginehost wrapper and the engine under it still agree.

The wrapper is thin on purpose: it turns an Enginehost launch into options the
engine already had, and an Enginehost action into a key the engine already
reads. That only holds while the engine's own code says so, and the engine is
vendored from upstream and will be bumped. Everything checked here is a thing
an upstream bump could change silently, where the symptom on a device would be
an option the engine ignores or a button that does nothing -- no crash, no log,
just a game that behaves oddly.

Nothing here needs a device, an APK or a network. Run it from the repository
root:

    python3 tests/check_engine_contract.py
"""

from __future__ import annotations

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
WRAPPER = ROOT / "src/onsyuri_android/app/java/com/yuri/onscripter/ONScripter.java"
ENGINE_MAIN = ROOT / "src/onsyuri/onscripter_main.cpp"
ENGINE_EVENT = ROOT / "src/onsyuri/ONScripter_event.cpp"
METADATA = ROOT / "enginehost/bundle-metadata.json"
ORIGIN = ROOT / "enginehost-origin.json"
DOC = ROOT / "ENGINEHOST.md"
SDL_KEYBOARD = ROOT / "thirdparty/port/SDL2-2.26.3/src/video/android/SDL_androidkeyboard.c"

failures: list[str] = []


def check(condition: bool, message: str) -> None:
    if not condition:
        failures.append(message)


# Android key codes are platform constants and do not change; they are written
# out here so this file can say which key each action presses without parsing
# the Android SDK.
ANDROID_KEYCODES = {
    "KEYCODE_0": 7,
    "KEYCODE_A": 29,
    "KEYCODE_O": 43,
    "KEYCODE_S": 47,
    "KEYCODE_SPACE": 62,
    "KEYCODE_ENTER": 66,
    "KEYCODE_DPAD_UP": 19,
    "KEYCODE_DPAD_DOWN": 20,
    "KEYCODE_DPAD_LEFT": 21,
    "KEYCODE_DPAD_RIGHT": 22,
    "KEYCODE_PAGE_UP": 92,
    "KEYCODE_PAGE_DOWN": 93,
    "KEYCODE_ESCAPE": 111,
    "KEYCODE_CTRL_RIGHT": 114,
}

# The order of SDL_CONTROLLER_BUTTON_*, which is the order of the engine's own
# button table, paired with the Android button each one is.
CONTROLLER_BUTTONS = [
    "A", "B", "X", "Y", "BACK", "GUIDE", "START",
    "LEFTSTICK", "RIGHTSTICK", "LEFTSHOULDER", "RIGHTSHOULDER",
    "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT",
]

# What ENGINEHOST.md calls each of those buttons in its default column.
DOC_BUTTON_NAMES = {
    "A": "A", "B": "B", "X": "X", "Y": "Y",
    "BACK": "Select", "START": "Start",
    "LEFTSHOULDER": "L1", "RIGHTSHOULDER": "R1",
    "DPAD_UP": "D-pad up", "DPAD_DOWN": "D-pad down",
    "DPAD_LEFT": "D-pad left", "DPAD_RIGHT": "D-pad right",
}


def sdlk_for_scancode(scancode: str) -> str:
    """SDL's keycode name for a scancode name, as the engine spells it."""
    name = scancode.removeprefix("SDL_SCANCODE_")
    if len(name) == 1 and name.isalpha():
        return "SDLK_" + name.lower()
    return "SDLK_" + name


def wrapper_engine_keys(source: str) -> dict[str, str]:
    """action id -> KEYCODE_* name, out of the wrapper's ENGINE_KEYS block."""
    pattern = re.compile(
        r'ENGINE_KEYS\.put\("(?P<action>[a-z_]+)",\s*'
        r'Integer\.valueOf\(KeyEvent\.(?P<key>KEYCODE_[A-Z_0-9]+)\)\)'
    )
    return {m["action"]: m["key"] for m in pattern.finditer(source)}


def engine_options(source: str) -> set[str]:
    """Every option string onscripter_main.cpp's parser compares against."""
    options = set()
    for match in re.finditer(r'strcmp\(\s*argv\[0\]\+1,\s*"([^"]+)"\s*\)', source):
        options.add("-" + match.group(1))
    return options


def wrapper_options(source: str) -> set[str]:
    """Every option string the wrapper can put on the command line."""
    options = set()
    for match in re.finditer(r'"(--[a-z0-9:-]+)"', source):
        options.add(match.group(1))
    # --enc: is built by appending the person's choice to the prefix.
    if '"--enc:" + encoding' in source:
        options.discard("--enc:")
        for value in ("sjis", "gbk", "utf8"):
            options.add("--enc:" + value)
    return options


def sdl_android_scancodes(source: str) -> list[str]:
    """SDL's Android keycode table, in keycode order."""
    body = source.split("static SDL_Scancode Android_Keycodes[] = {", 1)
    if len(body) != 2:
        body = re.split(r"Android_Keycodes\[\]\s*=\s*\{", source, maxsplit=1)
    if len(body) != 2:
        raise SystemExit("could not find SDL's Android keycode table")
    table = body[1].split("};", 1)[0]
    return re.findall(r"(SDL_SCANCODE_[A-Z_0-9]+)", table)


def engine_controller_table(source: str) -> list[str]:
    """transControllerButton's table, in SDL_CONTROLLER_BUTTON order."""
    start = source.index("transControllerButton")
    table = source[start:].split("{", 1)[1].split("};", 1)[0]
    return re.findall(r"(SDLK_[A-Za-z_0-9]+)", table)


def doc_defaults(source: str) -> dict[str, str]:
    """action id -> the button ENGINEHOST.md gives it by default."""
    defaults = {}
    for line in source.splitlines():
        match = re.match(r"\|\s*`(ons_[a-z_]+)`\s*\|.*\|\s*([^|]+?)\s*\|\s*$", line)
        if match:
            defaults[match.group(1)] = match.group(2)
    return defaults


def main() -> int:
    wrapper = WRAPPER.read_text(encoding="utf-8")
    main_cpp = ENGINE_MAIN.read_text(encoding="utf-8")
    event_cpp = ENGINE_EVENT.read_text(encoding="utf-8")
    doc = DOC.read_text(encoding="utf-8")
    metadata = json.loads(METADATA.read_text(encoding="utf-8"))
    origin = json.loads(ORIGIN.read_text(encoding="utf-8"))

    # 1. Every option the wrapper can pass is an option this engine parses.
    accepted = engine_options(main_cpp)
    for option in sorted(wrapper_options(wrapper)):
        check(option in accepted,
              f"the wrapper passes {option}, which this engine's option parser "
              f"does not accept (src/onsyuri/onscripter_main.cpp)")

    # 2. Every action presses a key this engine actually reads. The path is
    #    wrapper action -> Android key code -> SDL scancode -> SDL keycode ->
    #    a keysym ONScripter_event.cpp names, and each hop is read from the
    #    source that owns it rather than assumed.
    keys = wrapper_engine_keys(wrapper)
    check(bool(keys), "no ENGINE_KEYS entries were found in the wrapper")
    if SDL_KEYBOARD.exists():
        scancodes = sdl_android_scancodes(SDL_KEYBOARD.read_text(encoding="utf-8"))
        for action, keycode in sorted(keys.items()):
            number = ANDROID_KEYCODES.get(keycode)
            if number is None:
                failures.append(
                    f"{action} presses {keycode}, which this check has no "
                    f"Android key code for; add it to ANDROID_KEYCODES")
                continue
            check(number < len(scancodes),
                  f"{action} presses {keycode}, which is past the end of SDL's "
                  f"Android keycode table")
            if number >= len(scancodes):
                continue
            scancode = scancodes[number]
            check(scancode != "SDL_SCANCODE_UNKNOWN",
                  f"{action} presses {keycode}, which SDL translates to nothing")
            sdlk = sdlk_for_scancode(scancode)
            check(re.search(rf"\b{re.escape(sdlk)}\b", event_cpp) is not None,
                  f"{action} presses {keycode} -> {sdlk}, which this engine's "
                  f"event handling never mentions "
                  f"(src/onsyuri/ONScripter_event.cpp)")
    else:
        failures.append(
            f"{SDL_KEYBOARD} is missing; run script/_fetch.sh first so the key "
            f"translation can be checked against SDL's own table")

    # 3. The documented default for each action is the button the engine's own
    #    controller table gives that key. This is the table a person sees in
    #    Enginehost, and it is supposed to be the engine's, not a preference.
    engine_table = engine_controller_table(event_cpp)
    check(len(engine_table) >= len(CONTROLLER_BUTTONS),
          "the engine's controller table is shorter than SDL's button list")
    by_button = dict(zip(CONTROLLER_BUTTONS, engine_table))
    documented = doc_defaults(doc)
    check(set(documented) == set(keys),
          "ENGINEHOST.md's action table and the wrapper's ENGINE_KEYS list "
          f"different actions: {sorted(set(documented) ^ set(keys))}")
    button_for_doc_name = {name: button for button, name in DOC_BUTTON_NAMES.items()}
    for action, default in sorted(documented.items()):
        button = button_for_doc_name.get(default)
        if button is None:
            # L2 and R2: the engine's table has no trigger entries at all, so
            # those two actions cannot take their default from it. That is only
            # acceptable while the table really is silent about triggers.
            check(default in ("L2", "R2"),
                  f"{action} defaults to {default}, which is not a button this "
                  f"check knows")
            continue
        keycode = keys.get(action)
        scancode_name = ANDROID_KEYCODES.get(keycode)
        expected = by_button.get(button)
        check(expected is not None, f"no engine entry for controller button {button}")
        if expected is None or keycode is None:
            continue
        # The engine names the key; the wrapper names the Android code that
        # produces it. Compare through the same scancode path as above.
        if SDL_KEYBOARD.exists() and scancode_name is not None:
            actual = sdlk_for_scancode(sdl_android_scancodes(
                SDL_KEYBOARD.read_text(encoding="utf-8"))[scancode_name])
            check(actual == expected,
                  f"ENGINEHOST.md gives {action} the {default} button, but this "
                  f"engine's own table presses {expected} there while the "
                  f"wrapper presses {actual}")

    # 4. The contexts are the same three places over.
    wrapper_contexts = set(re.findall(r'CONTEXT_[A-Z]+ = "([a-z-]+)"', wrapper))
    metadata_contexts = {c["engineContext"] for c in metadata["capabilities"]}
    check(wrapper_contexts == metadata_contexts,
          f"the wrapper accepts {sorted(wrapper_contexts)} but the bundle "
          f"declares {sorted(metadata_contexts)}")
    check(set(origin["engineContexts"]) == metadata_contexts,
          f"enginehost-origin.json lists {sorted(origin['engineContexts'])} but "
          f"the bundle declares {sorted(metadata_contexts)}")
    check(origin["engine"] == metadata["engine"],
          "enginehost-origin.json and the bundle metadata name different engines")
    check(metadata["entrypoint"] == "com.yuri.onscripter.ONScripter",
          "the bundle entrypoint is not the wrapper activity")

    # 5. Every option the bundle declares is one the wrapper actually reads.
    declared = {option["key"] for option in metadata.get("declaredOptions", [])}
    for key in sorted(declared):
        check(f'"{key}"' in wrapper,
              f"the bundle declares the option {key}, which the wrapper never reads")

    if failures:
        print("FAILED")
        for failure in failures:
            print("  - " + failure)
        return 1
    print("The wrapper and the engine agree:")
    print(f"  {len(wrapper_options(wrapper))} command-line options, all parsed by the engine")
    print(f"  {len(keys)} actions, each pressing a key the engine reads")
    print(f"  {len(documented)} documented defaults, taken from the engine's own table")
    print(f"  contexts {sorted(metadata_contexts)} in the wrapper, the bundle and the origin")
    print(f"  {len(declared)} declared options, all read by the wrapper")
    return 0


if __name__ == "__main__":
    sys.exit(main())
