#!/usr/bin/env python3
"""Check that the Enginehost wrapper and the engine under it still agree.

The wrapper is thin on purpose: it turns an Enginehost launch into options the
engine already had, and an Enginehost action into a RetroPad button whose key
the engine's libretro core already presses. That only holds while the engine's
own code says so, and the engine is vendored from upstream and will be bumped.
Everything checked here is a thing an upstream bump could change silently,
where the symptom on a device would be an option the engine ignores or a
button that does nothing -- no crash, no log, just a game that behaves oddly.

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
WRAPPER = ROOT / "enginehost/android/java/dev/enginehost/plugin/nscripter/OnsyuriPlugin.java"
ENGINE_OPTIONS = ROOT / "src/onsyuri/onscripter_options.cpp"
ENGINE_EVENT = ROOT / "src/onsyuri/ONScripter_event.cpp"
CORE = ROOT / "src/onsyuri_libretro/libretro.cpp"
METADATA = ROOT / "enginehost/bundle-metadata.json"
ORIGIN = ROOT / "enginehost-origin.json"
DOC = ROOT / "ENGINEHOST.md"

failures: list[str] = []


def check(condition: bool, message: str) -> None:
    if not condition:
        failures.append(message)


def engine_options(source: str) -> set[str]:
    """Every option string the shared option parser compares against."""
    options = set()
    for match in re.finditer(r'strcmp\(\s*argv\[0\]\+1,\s*"([^"]+)"\s*\)', source):
        options.add("-" + match.group(1))
    return options


def wrapper_options(source: str) -> set[str]:
    """Every option string the wrapper can put on the command line."""
    return set(re.findall(r'"(--[a-z0-9:-]+)"', source))


def wrapper_buttons(source: str) -> dict[str, str]:
    """action id -> JOYPAD_* name, out of the wrapper's ACTION_BUTTONS block."""
    pattern = re.compile(r'ACTION_BUTTONS\.put\("(?P<action>[a-z_]+)",\s*(?P<button>JOYPAD_[A-Z0-9]+)\)')
    return {m["action"]: m["button"] for m in pattern.finditer(source)}


def core_keys(source: str) -> dict[str, str]:
    """JOYPAD_* name -> the SDLK_* the core presses for it (PumpJoypadEvents)."""
    table = source.split("static const int bkeys[16] = {", 1)
    if len(table) != 2:
        raise SystemExit("could not find the core's RetroPad key table (bkeys)")
    body = table[1].split("};", 1)[0]
    return {m.group(1): m.group(2)
            for m in re.finditer(r"\[RETRO_DEVICE_ID_(JOYPAD_[A-Z0-9]+)\]\s*=\s*(SDLK_[A-Za-z_0-9]+)", body)}


def doc_rows(source: str) -> dict[str, tuple[str, str]]:
    """action id -> (the key ENGINEHOST.md says it presses, its default button)."""
    rows = {}
    for line in source.splitlines():
        match = re.match(r"\|\s*`(ons_[a-z_]+)`\s*\|[^|]*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*$", line)
        if match:
            rows[match.group(1)] = (match.group(2), match.group(3))
    return rows


def sdlk(doc_key: str) -> str:
    """ENGINEHOST.md's key name as SDL spells the keycode."""
    return "SDLK_" + (doc_key.lower() if len(doc_key) == 1 and doc_key.isalpha() else doc_key)


def main() -> int:
    wrapper = WRAPPER.read_text(encoding="utf-8")
    options_cpp = ENGINE_OPTIONS.read_text(encoding="utf-8")
    event_cpp = ENGINE_EVENT.read_text(encoding="utf-8")
    core = CORE.read_text(encoding="utf-8")
    doc = DOC.read_text(encoding="utf-8")
    metadata = json.loads(METADATA.read_text(encoding="utf-8"))
    origin = json.loads(ORIGIN.read_text(encoding="utf-8"))

    # 1. Every option the wrapper can pass is an option this engine parses.
    accepted = engine_options(options_cpp)
    for option in sorted(wrapper_options(wrapper)):
        check(option in accepted,
              f"the wrapper passes {option}, which this engine's option parser "
              f"does not accept (src/onsyuri/onscripter_options.cpp)")

    # 2. Every action presses, through the core's own RetroPad table, the key
    #    ENGINEHOST.md says it presses, and the engine's event handling reads
    #    that key.
    buttons = wrapper_buttons(wrapper)
    check(bool(buttons), "no ACTION_BUTTONS entries were found in the wrapper")
    keys = core_keys(core)
    rows = doc_rows(doc)
    check(set(rows) == set(buttons),
          "ENGINEHOST.md's action table and the wrapper's ACTION_BUTTONS list "
          f"different actions: {sorted(set(rows) ^ set(buttons))}")
    for action, button in sorted(buttons.items()):
        pressed = keys.get(button)
        check(pressed is not None, f"{action} presses {button}, which the core's table leaves empty")
        if pressed is None or action not in rows:
            continue
        expected = sdlk(rows[action][0])
        check(pressed == expected,
              f"ENGINEHOST.md says {action} presses {expected}, but {button} presses {pressed} in the core")
        check(re.search(rf"\b{re.escape(pressed)}\b", event_cpp) is not None,
              f"{action} presses {pressed}, which this engine's event handling never mentions "
              f"(src/onsyuri/ONScripter_event.cpp)")

    # 3. The contexts are the same three places over.
    wrapper_contexts = set(re.findall(r'CONTEXT_[A-Z]+ = "([a-z-]+)"', wrapper))
    metadata_contexts = {c["engineContext"] for c in metadata["capabilities"]}
    check(wrapper_contexts == metadata_contexts,
          f"the wrapper accepts {sorted(wrapper_contexts)} but the bundle declares {sorted(metadata_contexts)}")
    check(set(origin["engineContexts"]) == metadata_contexts,
          f"enginehost-origin.json lists {sorted(origin['engineContexts'])} but the bundle declares "
          f"{sorted(metadata_contexts)}")
    check(origin["engine"] == metadata["engine"],
          "enginehost-origin.json and the bundle metadata name different engines")
    check(metadata["entrypoint"] == "dev.enginehost.plugin.nscripter.OnsyuriPlugin",
          "the bundle entrypoint is not the wrapper class")

    # 4. Every option the bundle declares is one the wrapper actually reads.
    declared = {option["key"] for option in metadata.get("declaredOptions", [])}
    for key in sorted(declared):
        check(f'"{key}"' in wrapper, f"the bundle declares the option {key}, which the wrapper never reads")

    if failures:
        print("FAILED")
        for failure in failures:
            print("  - " + failure)
        return 1
    print("The wrapper and the engine agree:")
    print(f"  {len(wrapper_options(wrapper))} command-line options, all parsed by the engine")
    print(f"  {len(buttons)} actions, each pressing the documented key through the core's table")
    print(f"  contexts {sorted(metadata_contexts)} in the wrapper, the bundle and the origin")
    print(f"  {len(declared)} declared options, all read by the wrapper")
    return 0


if __name__ == "__main__":
    sys.exit(main())
