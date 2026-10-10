/*
 * The Enginehost entry point for the ONScripter engine in this repository.
 *
 * ONScripter is GPL-2.0-or-later (Ogapee, 2001-2018; ONScripter-Jh; and this
 * OnscripterYuri line). This file is part of that work and carries the same
 * terms; see LICENSE and THIRD_PARTY.md.
 */
package dev.enginehost.plugin.nscripter;

import dev.enginehost.api.EngineFileBroker;
import dev.enginehost.api.EnginePluginSession;
import dev.enginehost.libretro.LibretroPlugin;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/**
 * NScripter and ONScripter games through OnscripterYuri's own libretro core
 * (src/onsyuri_libretro), in Enginehost's sandbox (LibretroPlugin,
 * plugin-native/libretro/).
 *
 * <p><b>The launch.</b> The game folder is the content: the core enters it
 * and reads the script and archives beside it. The game's options become
 * ONScripter's own command line, the options parser every front end shares
 * (src/onsyuri/onscripter_options.cpp), passed through the core's
 * onsyuri_enginehost_arguments variable; the script encoding is the core's
 * own option. Saves go where the engine puts them, beside the game (the
 * bundle declares writesGameFolder).
 *
 * <p><b>The controls.</b> The core turns RetroPad buttons into ONScripter's
 * keys (PumpJoypadEvents in src/onsyuri_libretro/libretro.cpp), so each host
 * action presses the RetroPad button whose key is the one ENGINEHOST.md's
 * table gives that action. Touch reaches the core as its pointer and becomes
 * the mouse ONScripter reads.
 */
public final class OnsyuriPlugin extends LibretroPlugin {
    /** Both name the same engine, the same games, the same runtime. */
    static final String CONTEXT_NSCRIPTER = "nscripter";
    static final String CONTEXT_ONSCRIPTER = "onscripter";

    /** Fonts on stock Android that can draw a Japanese script, best first. */
    private static final String[] SYSTEM_FONT_FALLBACKS = {
        "/system/fonts/NotoSansCJK-Regular.ttc",
        "/system/fonts/NotoSansJP-Regular.otf",
        "/system/fonts/DroidSansJapanese.ttf",
        "/system/fonts/DroidSansFallback.ttf",
    };

    /** Each action's RetroPad button; the key it presses is the core's table's. */
    static final Map<String, Integer> ACTION_BUTTONS = new HashMap<>();

    static {
        ACTION_BUTTONS.put("ons_advance", JOYPAD_A);          // RETURN
        ACTION_BUTTONS.put("ons_right_click", JOYPAD_X);      // ESCAPE
        ACTION_BUTTONS.put("ons_ctrl", JOYPAD_Y);             // RCTRL
        ACTION_BUTTONS.put("ons_space", JOYPAD_B);            // SPACE
        ACTION_BUTTONS.put("ons_single_page", JOYPAD_L);      // o
        ACTION_BUTTONS.put("ons_skip", JOYPAD_R);             // s
        ACTION_BUTTONS.put("ons_auto", JOYPAD_START);         // a
        ACTION_BUTTONS.put("ons_text_speed", JOYPAD_SELECT);  // 0
        ACTION_BUTTONS.put("ons_cursor_up", JOYPAD_UP);
        ACTION_BUTTONS.put("ons_cursor_down", JOYPAD_DOWN);
        ACTION_BUTTONS.put("ons_cursor_left", JOYPAD_LEFT);
        ACTION_BUTTONS.put("ons_cursor_right", JOYPAD_RIGHT);
        ACTION_BUTTONS.put("ons_page_up", JOYPAD_L2);         // PAGEUP
        ACTION_BUTTONS.put("ons_page_down", JOYPAD_R2);       // PAGEDOWN
    }

    @Override protected boolean supports(String engineContext) {
        return CONTEXT_NSCRIPTER.equals(engineContext) || CONTEXT_ONSCRIPTER.equals(engineContext);
    }

    @Override protected String contentPath(EnginePluginSession session) {
        return session.gamePath();
    }

    /**
     * The host's shared action ids, for a person's map made where the
     * nscripter set is not offered. quick_save and quick_load have no
     * counterpart: ONScripter saves and loads through the game's own menu.
     */
    static final Map<String, String> SHARED_ACTION_ALIASES = new HashMap<>();

    static {
        SHARED_ACTION_ALIASES.put("confirm", "ons_advance");
        SHARED_ACTION_ALIASES.put("cancel", "ons_right_click");
        SHARED_ACTION_ALIASES.put("menu", "ons_right_click");
        SHARED_ACTION_ALIASES.put("skip", "ons_skip");
        SHARED_ACTION_ALIASES.put("auto", "ons_auto");
        SHARED_ACTION_ALIASES.put("history", "ons_cursor_left");
        SHARED_ACTION_ALIASES.put("up", "ons_cursor_up");
        SHARED_ACTION_ALIASES.put("down", "ons_cursor_down");
        SHARED_ACTION_ALIASES.put("left", "ons_cursor_left");
        SHARED_ACTION_ALIASES.put("right", "ons_cursor_right");
        SHARED_ACTION_ALIASES.put("page_previous", "ons_page_up");
        SHARED_ACTION_ALIASES.put("page_next", "ons_page_down");
    }

    @Override protected int joypadButton(String action) {
        Integer button = ACTION_BUTTONS.get(SHARED_ACTION_ALIASES.getOrDefault(action, action));
        return button != null ? button : -1;
    }

    @Override protected Map<String, String> options(EnginePluginSession session) {
        JSONObject options;
        try {
            String raw = session.optionsJson();
            options = raw == null || raw.trim().isEmpty() ? new JSONObject() : new JSONObject(raw);
        } catch (Exception error) {
            options = new JSONObject();
        }
        ArrayList<String> args = new ArrayList<>();
        // --root: where the script and the .nsa/.sar archives are read from.
        args.add("--root");
        args.add(session.gamePath());
        String font = fontFile(session, options);
        if (font != null) {
            args.add("--font");
            args.add(font);
        }
        addNumber(args, options, "width", "--width");
        addNumber(args, options, "height", "--height");
        addNumber(args, options, "sharpness", "--sharpness");
        addFlag(args, options, "disableVideo", "--no-video");
        addFlag(args, options, "disableRescale", "--disable-rescale");
        addFlag(args, options, "renderFontOutline", "--render-font-outline");
        addFlag(args, options, "forceButtonShortcut", "--force-button-shortcut");
        addFlag(args, options, "enableWheeldownAdvance", "--enable-wheeldown-advance");
        addFlag(args, options, "fontCache", "--fontcache");
        addFlag(args, options, "debugLog", "--debug:1");
        addValue(args, options, "registryFile", "--registry");
        addValue(args, options, "dllFile", "--dll");
        addValue(args, options, "keyExeFile", "--key-exe");

        Map<String, String> core = new HashMap<>();
        core.put("onsyuri_enginehost_arguments", String.join("\n", args));
        // The script's character set is the core's own option.
        switch (options.optString("encoding", "")) {
            case "sjis": core.put("onsyuri_script_encoding", "SHIFTJIS"); break;
            case "gbk": core.put("onsyuri_script_encoding", "GBK"); break;
            case "utf8": core.put("onsyuri_script_encoding", "UTF-8"); break;
            default: break;
        }
        return core;
    }

    /**
     * ONScripter opens "default.ttf" in the game folder when told nothing and
     * stops without one. The person's choice wins, then the game's own font,
     * then a Japanese font on the device. The game folder is asked through
     * the host's broker, the one view of it this process has.
     */
    private static String fontFile(EnginePluginSession session, JSONObject options) {
        String chosen = options.optString("fontFile", "");
        if (!chosen.trim().isEmpty()) return chosen;
        EngineFileBroker game = session.host().gameBroker();
        if (game != null) {
            try {
                for (String name : game.list("")) {
                    if (name.equalsIgnoreCase("default.ttf")) return new File(session.gamePath(), name).getPath();
                }
            } catch (Exception ignored) {
                // Fall through to a system font.
            }
        }
        for (String candidate : SYSTEM_FONT_FALLBACKS) {
            if (new File(candidate).isFile()) return candidate;
        }
        return null;
    }

    private static void addFlag(List<String> args, JSONObject options, String key, String flag) {
        if (options.optBoolean(key, false)) args.add(flag);
    }

    private static void addValue(List<String> args, JSONObject options, String key, String flag) {
        String value = options.optString(key, "");
        if (value.trim().isEmpty()) return;
        args.add(flag);
        args.add(value);
    }

    private static void addNumber(List<String> args, JSONObject options, String key, String flag) {
        if (!options.has(key)) return;
        String value = options.optString(key, "");
        if (value.trim().isEmpty()) return;
        args.add(flag);
        args.add(value);
    }
}
