# What this repository and its bundle carry

Each component keeps the notices it arrived with. The bundle as distributed is covered by GPL-3.0:
the engine is GPL-2.0-or-later, which allows it, and what is added here is ours under GPL-3.0.

## The engine

| Component | Origin | Licence |
| --- | --- | --- |
| ONScripter | Ogapee, 2001-2018, <https://onscripter.osdn.jp/onscripter.html> | GPL-2.0-or-later |
| ONScripter-Jh | jh10001, <https://github.com/jh10001/ONScripter-Jh> | GPL-2.0-or-later |
| OnscripterYuri | YuriSizuku, <https://github.com/YuriSizuku/OnscripterYuri>, 0.7.7 at `08f744b31cc1907b66a15f0402e62321a131ed81` | GPL-2.0-or-later |

The whole engine tree under `src/onsyuri`, the Android project under
`src/onsyuri_android`, `CMakeLists.txt` and `script/` came from OnscripterYuri
at that revision; the first commit in this repository is that snapshot
unmodified, so `git log` separates upstream's work from this project's. Upstream's GPL-2 text is in `src/onsyuri/COPYING`; the root `LICENSE` is the GPL-3.0 text.

The Enginehost integration in this repository — the entry activity, the bundle
metadata, the workflow and the test — is part of the same work and is
GPL-3.0, which GPL-2.0-or-later upstream allows. It adds no condition beyond the GPL.

## Libraries the build fetches and links

These are not stored in this repository. `script/_fetch.sh` downloads each one
from its own upstream at a pinned version, and CI runs it; the versions are
named in `src/onsyuri_android/app/cpp/CMakeLists.txt`.

| Library | Version | Licence |
| --- | --- | --- |
| SDL2 | 2.26.3 | Zlib |
| SDL2_image | 2.6.3 | Zlib |
| SDL2_ttf | 2.20.2 | Zlib |
| SDL2_mixer | 2.6.3 | Zlib |
| FreeType (vendored inside SDL2_ttf) | as shipped with SDL2_ttf 2.20.2 | FreeType Licence or GPL-2, at the user's option |
| HarfBuzz (vendored inside SDL2_ttf) | as shipped with SDL2_ttf 2.20.2 | MIT |
| libjpeg (libsdl-org fork) | v9e-SDL | Independent JPEG Group licence |
| Lua | 5.4.4 | MIT |
| bzip2 | 1.0.8 | BSD-style (Julian Seward) |
| stb_vorbis (inside SDL2_mixer) | as shipped with SDL2_mixer 2.6.3 | Public domain or MIT |

The SDL2 family is Zlib-licensed and so imposes nothing beyond keeping its
notices, which travel in the source each build fetches. FreeType is dual
licensed and is used here under the FreeType Licence; that requires
acknowledgement in documentation, which this table is.

## The corresponding source

The bundle is a GPL work in binary form. The corresponding source is the branch
it was built from, in this repository, at the commit named in the release
notes, together with the pinned upstream archives `script/_fetch.sh` names.
