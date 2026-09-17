# Enginehost NScripter / ONScripter plugin

This repository is the independently installable NScripter runtime in
Enginehost's `nscripter` family. It runs a game authored for NScripter, and the
identical thing a game authored against ONScripter is, straight out of its own
folder: `nscript.dat` or `0.txt` plus whatever `.nsa` and `.sar` archives sit
beside them. Nothing is unpacked, converted or copied.

## Which engine, and why this one

The engine is **[OnscripterYuri](https://github.com/YuriSizuku/OnscripterYuri)
0.7.7**, vendored whole. Its own ancestry is
[ONScripter-Jh](https://github.com/jh10001/ONScripter-Jh) and, under that,
Ogapee's original [ONScripter](https://onscripter.osdn.jp/onscripter.html); the
NScripter language it implements is at compatibility level 2.96
(`src/onsyuri/version.h`).

The other candidate was
[matthewn4444/onscripter-engine-android](https://github.com/matthewn4444/onscripter-engine-android),
which the engine-expansion brief named as a starting point. It was read and
rejected, for reasons that are in its code rather than in its reputation:

- **SDL.** It vendors a fork of the SDL 1.2/1.3-era Android port with its own
  `GLSurfaceView_SDL`, its own Java key table, and its own audio thread — about
  8 MB of a dead SDL line that nobody upstream maintains. OnscripterYuri is
  SDL2, on `org.libsdl.app.SDLActivity`, which is the same surface every other
  native plugin in Enginehost already sits on (Buriko, Ren'Py, mkxp-z), so the
  wrapper here looks like the wrappers there rather than being a one-off.
- **Controllers.** The SDL 1.2 port's Android key table stops at keycode 91, so
  a pad's buttons translate to nothing at all, and the engine underneath it has
  joystick tables only for the PSP, the PS3 and the GP2X. OnscripterYuri opens
  an SDL game controller and has a modern `SDL_CONTROLLER_BUTTON_*` table
  (`src/onsyuri/ONScripter_event.cpp:138-163`) — which is to say, this line of
  the engine already has an opinion about what an Android pad does, and this
  plugin can use the engine's opinion instead of inventing one.
- **Saves.** It has no way to put saves anywhere but the game folder. Ogapee's
  `--save-dir` reached this line and not that one, and a command-line save
  directory is exactly the seam Enginehost's per-game save folder needs.
- **Maintenance and coverage.** Last upstream commit 2023; OnscripterYuri is at
  0.7.7 (June 2026) and carries runtime-selectable Shift_JIS, GBK and UTF-8
  script encodings, half-width English text, Lua, and the builtin-DLL layer
  effects a lot of doujin releases use.
- **House consistency.** OnscripterYuri's author also maintains
  Kirikiroid2Yuri, which is the engine behind this project's KiriKiri plugin,
  so the build idiom (a `thirdparty/port` fetch script, CMake, an Android
  Gradle project under `src/`) is one this repository already knows.

### Why the engine is vendored rather than a submodule

Every other engine plugin here is a fork carrying its engine in-tree, and this
one matches them. A submodule would pin a tree the wrapper cannot touch, and
the wrapper does have to touch it: the Android project under
`src/onsyuri_android` is the standalone app's, and here it has to become an
Enginehost runtime instead — a different activity, a different manifest, two
ABIs, no game browser. Upstream is tracked as a remote and a version bump is a
merge; what upstream shipped is the first commit in this repository's history,
so every later diff is legible as ours.

## What the wrapper does

`src/onsyuri_android/app/java/com/yuri/onscripter/ONScripter.java` is the whole
of it. It stays in upstream's package because the engine's JNI entry points are
exported as `Java_com_yuri_onscripter_ONScripter_*`; renaming the class renames
native symbols, so the wrapper bends instead.

It turns the Enginehost launch into options the engine already had:

| Enginehost | ONScripter |
| --- | --- |
| `dev.enginehost.runtime.PATH` | `--root` |
| `dev.enginehost.runtime.SAVE_PATH` | `--save-dir` |
| `dev.enginehost.runtime.ENGINE_CONTEXT` | validated: `nscripter` or `onscripter` |
| `dev.enginehost.runtime.OPTIONS` | the options in `enginehost/bundle-metadata.json` |
| `dev.enginehost.runtime.CONTROLLER_BINDINGS` | the engine's own keys (below) |

Saves land in Enginehost's folder for the game. That is not a convention the
wrapper imposes: a save directory given on the command line deliberately
outranks both the game's own `savedir` command and the path recorded in
`envdata`, because each of those assigns `save_dir` only `if (!save_dir)`
(`ScriptParser::savedirCommand`, `ONScripter::readEnvData`). So `save<n>.dat`,
`gloval.sav` and screenshots go to the host's folder whatever the game asks
for.

One file does not: `envdata`, the engine's global settings, which
`ScriptParser::saveFileIOBuf` and `loadFileIOBuf` exclude from the save
directory by name (`ScriptParser.cpp:359`, `:384`) and so read and write beside
the game. The engine has a reason -- `envdata` is where a game's `savedir` name
is recorded, so it has to be readable before the save directory is known -- and
that reason does not apply when the save directory arrived on the command line,
which is always the case here. Changing it is a one-line change to the engine's
own semantics, so it is written up as a question rather than made silently; see
the plugin's BRIEF. Until then, a game folder this plugin runs from gains one
53-byte `envdata` file.

The bundle carries no `res/raw/enginehost_capabilities.json`. Other plugins
ship one; nothing in Enginehost reads it, and the signed manifest's
`capabilities` is the one place a capability is declared.

## Controls

ONScripter has an input model of its own, and this plugin uses it rather than
adding a second one.

**Touch works with no pad at all.** SDL delivers touches as mouse events and
ONScripter's own pointer handling reads them: tap to advance, and the game's
own right-click gesture where a game has one.

**With a pad, the engine already knows what to do.** It opens an SDL game
controller (`ONScripter.cpp:157-159`) and maps each button to one of its
keyboard keys in `transControllerButton`
(`ONScripter_event.cpp:138-163`). While Enginehost's **Bypass controller
mappings** is on for this scope — which is the recommended default for this
engine, exactly as it is for Ren'Py, Godot and EasyRPG — no
`CONTROLLER_BINDINGS` extra arrives, this plugin never touches a pad event, and
that table is what a person gets.

**With bypass off**, Enginehost sends a map and the plugin translates each
action into the key the engine's own table would have pressed. The action set
below is what Enginehost should offer for the `nscripter` scope; the default
column is not a preference, it is `transControllerButton` read straight down.

| action id | what the engine does | engine key | default |
| --- | --- | --- | --- |
| `ons_advance` | Advance the text; press the button the cursor is on | RETURN | A |
| `ons_right_click` | Right click: the game's right-click menu, window erase, cancel | ESCAPE | B |
| `ons_ctrl` | Held: run the text forward while it is down | RCTRL | X |
| `ons_space` | Advances; a game's own SPACE function under `useescspc` | SPACE | Y |
| `ons_single_page` | Draw-one-page mode on and off | O | L1 |
| `ons_skip` | Skip mode on and off | S | R1 |
| `ons_auto` | Auto mode on and off | A | Start |
| `ons_text_speed` | Cycle text speed: fast, medium, slow | 0 | Select |
| `ons_cursor_up` | Previous button; cursor up under `getcursor` | UP | D-pad up |
| `ons_cursor_down` | Next button; cursor down under `getcursor` | DOWN | D-pad down |
| `ons_cursor_left` | Wheel up, which opens the backlog; cursor left under `getcursor` | LEFT | D-pad left |
| `ons_cursor_right` | Wheel down, which advances or scrolls the backlog; cursor right under `getcursor` | RIGHT | D-pad right |
| `ons_page_up` | Page up, for a game that asked for it with `getpageup` | PAGEUP | L2 |
| `ons_page_down` | Page down, for a game that asked for it with `getpagedown` | PAGEDOWN | R2 |

Four notes on that table, each from the engine rather than from taste:

- **The four directions are one input each.** The engine decides what a
  direction means where it lands: between buttons it steps the cursor, in text
  it is the mouse wheel that opens and scrolls the backlog, and under a game's
  `getcursor` all four become that game's cursor keys (buttons −40 to −43). One
  action, several meanings, exactly as the engine has it.
- **No stick actions.** The engine reads no axis anywhere in its input model,
  so there is nothing `left_x` could be read as, and an analogue binding is
  dropped rather than quietly turned into a press. A hat D-pad still works: it
  arrives as a signed axis binding and is pressed and released as a button is.
- **There is no fallback table in the plugin.** No map means the engine's own
  table applies; a second set of defaults beside it would be two answers to one
  question.
- **Select and Start carry actions, and Select + Start does not.** That
  combination is Enginehost's in-game menu and never reaches an engine.

The plugin also accepts the shared fallback action ids (`confirm`, `cancel`,
`menu`, `skip`, `auto`, `history`, the four directions, `page_previous`,
`page_next`) as aliases for the rows above, so a person's map works on a host
that has not yet learned the `nscripter` set. `quick_save` and `quick_load` have
no counterpart — ONScripter saves and loads through the game's own right-click
menu and has no key for either — so they do nothing rather than being bent onto
a key that means something else.

## Building

CI (`.github/workflows/android-plugin.yml`) fetches the engine's third-party
sources with the repository's own `script/_fetch.sh`, builds the runtime APK
for `arm64-v8a` and `x86_64`, and packs and signs the Enginehost bundle. The
NDK is pinned to 26.3.11579264: SDL 2.26.3 calls `ALooper_pollAll`, which NDK 27
removed, so a newer NDK does not compile this engine.

## Licence

ONScripter is GPL-2.0-or-later and everything added here is part of that work
under the same terms. `LICENSE` is upstream's GPL-2 text; `THIRD_PARTY.md` lists
what else the bundle carries and under which licence. No restriction is added
that the upstream does not require.
