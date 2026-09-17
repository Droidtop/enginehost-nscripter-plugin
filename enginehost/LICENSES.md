# Licences covering this bundle

The payload is the ONScripter engine (the OnscripterYuri line), the SDL2
libraries it links, and the Enginehost entry activity. Each component keeps its
own licence; nothing here relicenses anything.

| Component | Licence | Notice shipped in the payload |
| --- | --- | --- |
| ONScripter, ONScripter-Jh, OnscripterYuri | GPL-2.0-or-later | `COPYING.onscripter` |
| SDL2, SDL2_image, SDL2_ttf, SDL2_mixer | Zlib | named in `THIRD_PARTY.md` |
| FreeType, vendored inside SDL2_ttf | FreeType Licence or GPL-2, at the user's option | named in `THIRD_PARTY.md` |
| HarfBuzz, vendored inside SDL2_ttf | MIT | named in `THIRD_PARTY.md` |
| libjpeg (libsdl-org v9e-SDL) | Independent JPEG Group licence | named in `THIRD_PARTY.md` |
| Lua 5.4.4 | MIT | named in `THIRD_PARTY.md` |
| bzip2 1.0.8 | BSD-style | named in `THIRD_PARTY.md` |

Because the engine is GPL-2.0-or-later, the bundle as distributed is covered by
that licence. The corresponding source is the branch this bundle was built
from, in this repository, at the commit the release names.
