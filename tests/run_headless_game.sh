#!/usr/bin/env bash
# Prove the engine starts and reaches its first text, with no device involved.
#
# This builds the engine's Linux target -- the same sources the Android library
# is built from, in the same CMake project -- and runs it under a virtual X
# server against the smallest real NScripter game there is, in tests/game. That
# game is three lines this repository wrote: a text line, a grab of the screen,
# and end. If the engine starts, finds the script, lays the text out and draws
# it, the grab has ink in it; if any of that broke, it does not.
#
# It is deliberately not a screenshot comparison. What is being checked is that
# the engine gets as far as drawing, and a pixel-exact expectation would fail on
# a different font without anything being wrong.
#
# Usage:  tests/run_headless_game.sh
# Needs:  cmake, a C++ compiler, xvfb-run, and the development packages the
#         Linux target links (SDL2, SDL2_ttf, SDL2_image, SDL2_mixer, Lua 5.4,
#         bzip2, libjpeg). On Debian and Ubuntu:
#           apt-get install cmake g++ xvfb libsdl2-dev libsdl2-ttf-dev \
#             libsdl2-image-dev libsdl2-mixer-dev liblua5.4-dev libbz2-dev \
#             libjpeg-dev pkg-config fonts-dejavu-core

set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
work="${TMPDIR:-/tmp}/onsyuri-headless-test"
build="$work/build"
saves="$work/saves"
shot="$saves/first-text.bmp"

font=""
for candidate in \
    /usr/share/fonts/truetype/dejavu/DejaVuSans.ttf \
    /usr/share/fonts/dejavu/DejaVuSans.ttf \
    /usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf; do
    [ -f "$candidate" ] && font="$candidate" && break
done
if [ -z "$font" ]; then
    # Anything scalable will do: the test reads ink, not glyph shapes. No font
    # is committed to this repository, so one has to already be on the machine.
    font="$(find /usr/share/fonts -name '*.ttf' -print -quit 2>/dev/null || true)"
fi
if [ -z "$font" ]; then
    echo "No TrueType font on this machine; install fonts-dejavu-core." >&2
    exit 1
fi

rm -rf "$work"
mkdir -p "$build" "$saves"

echo "Building the engine's Linux target..."
cmake -S "$root" -B "$build" -DCMAKE_BUILD_TYPE=Release > "$work/cmake.log" 2>&1 \
    || { tail -30 "$work/cmake.log" >&2; exit 1; }
cmake --build "$build" --parallel "$(nproc)" > "$work/build.log" 2>&1 \
    || { grep -n 'error:' "$work/build.log" | head -20 >&2; exit 1; }
test -x "$build/onsyuri"

echo "Running it against tests/game..."
# A dummy audio device: the engine opens one and says so when it cannot, and
# there is no sound card behind a CI runner.
SDL_AUDIODRIVER=dummy timeout 120 xvfb-run -a "$build/onsyuri" \
    --root "$root/tests/game" \
    --save-dir "$saves/" \
    --font "$font" \
    --window \
    > "$work/engine.log" 2>&1 || {
        echo "The engine exited with an error:" >&2
        tail -30 "$work/engine.log" >&2
        exit 1
    }

if [ ! -f "$shot" ]; then
    echo "The engine never reached the getscreenshot line in tests/game/0.txt." >&2
    tail -30 "$work/engine.log" >&2
    exit 1
fi

python3 - "$shot" <<'PY'
import struct, sys
data = open(sys.argv[1], 'rb').read()
if data[:2] != b'BM':
    raise SystemExit("the screenshot is not a BMP")
offset, = struct.unpack_from('<I', data, 10)
width, height = struct.unpack_from('<ii', data, 18)
pixels = data[offset:]
ink = sum(1 for i in range(0, len(pixels) - 3, 4) if pixels[i:i + 3] != b'\x00\x00\x00')
print(f"  screen {width}x{height}, {ink} pixels of text drawn")
if ink < 100:
    raise SystemExit(
        "the engine started and saved a screen, but drew nothing on it: the "
        "text never reached the accumulation surface")
PY

echo "PASSED: the engine started, read tests/game/0.txt and drew its first text."
