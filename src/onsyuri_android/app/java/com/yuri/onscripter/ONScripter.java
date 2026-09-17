/*
 * The Enginehost entry point for the ONScripter engine in this repository.
 *
 * ONScripter is GPL-2.0-or-later (Ogapee, 2001-2018; ONScripter-Jh; and this
 * OnscripterYuri line). This file is part of that work and carries the same
 * terms; see LICENSE and THIRD_PARTY.md.
 *
 * The class stays com.yuri.onscripter.ONScripter on purpose. The engine's JNI
 * entry points are exported as Java_com_yuri_onscripter_ONScripter_* -- the
 * package comes from SDL_JAVA_PACKAGE_PATH in the engine's own CMakeLists and
 * the class name is written into onscripter_main.cpp -- so a rename here is a
 * rename in the native library, and the wrapper is the thing that should bend.
 */
package com.yuri.onscripter;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import org.json.JSONObject;
import org.libsdl.app.SDLActivity;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Turns an Enginehost launch into ONScripter's own command line, and an
 * Enginehost controller map into ONScripter's own keys.
 *
 * <p><b>The launch.</b> Enginehost passes the game folder, the folder saves
 * belong in, the engine context and a JSON options object as intent extras.
 * Every one of them becomes an option the engine already had: {@code --root}
 * for the folder, {@code --save-dir} for the saves, {@code --enc:} for the
 * script encoding and so on (src/onsyuri/onscripter_main.cpp:357-470). Nothing
 * is copied and nothing in the game folder is written to.
 *
 * <p><b>The controls.</b> The engine has a controller model of its own: it
 * opens an SDL game controller (src/onsyuri/ONScripter.cpp:157-159) and turns
 * each button into one of its keyboard keys through the table in
 * transControllerButton (src/onsyuri/ONScripter_event.cpp:138-163). So when
 * Enginehost's bypass is on, which is to say when no CONTROLLER_BINDINGS extra
 * arrives, this class does nothing at all with the pad and the engine's own
 * table applies unchanged. When a map does arrive, this class takes every pad
 * event before SDL sees it and presses the key the engine's own table would
 * have pressed for that action. The mapping from action to key is ours; which
 * button carries which action is the person's.
 *
 * <p><b>Touch</b> never goes through any of this: SDL delivers it as mouse
 * events and ONScripter's own pointer handling reads them, so a game is
 * playable with no pad at all.
 */
public final class ONScripter extends SDLActivity {
    private static final String TAG = "ONScripter[Enginehost]";

    private static final String EXTRA_CONTEXT = "dev.enginehost.runtime.ENGINE_CONTEXT";
    private static final String EXTRA_PATH = "dev.enginehost.runtime.PATH";
    private static final String EXTRA_SAVE_PATH = "dev.enginehost.runtime.SAVE_PATH";
    private static final String EXTRA_OPTIONS = "dev.enginehost.runtime.OPTIONS";
    private static final String EXTRA_BINDINGS = "dev.enginehost.runtime.CONTROLLER_BINDINGS";

    /**
     * Contexts this plugin answers to. Both name the same engine and the same
     * games: "nscripter" is the original Windows engine a game was authored
     * for, "onscripter" the free reimplementation that reads it. The script,
     * the archives and the save format are identical either way, which is why
     * one runtime serves both rather than two capabilities pretending to
     * differ.
     */
    private static final String CONTEXT_NSCRIPTER = "nscripter";
    private static final String CONTEXT_ONSCRIPTER = "onscripter";

    /** Fonts on stock Android that can draw a Japanese script, best first. */
    private static final String[] SYSTEM_FONT_FALLBACKS = {
        "/system/fonts/NotoSansCJK-Regular.ttc",
        "/system/fonts/NotoSansJP-Regular.otf",
        "/system/fonts/DroidSansJapanese.ttf",
        "/system/fonts/DroidSansFallback.ttf",
    };

    private String[] arguments = new String[0];
    private boolean launched;

    // ------------------------------------------------------------- launching

    @Override
    protected void onCreate(Bundle state) {
        // The theme has to be chosen before AppCompat initialises, and
        // AppCompat initialises inside super.onCreate. Behind Enginehost's
        // manifest proxy there is no ActivityInfo of ours for it to read a
        // theme off, so the activity selects one itself; the bundle's
        // resources are already attached by the host's component factory here.
        setTheme(R.style.EnginehostONScripterActivity);

        String context = getIntent().getStringExtra(EXTRA_CONTEXT);
        File game = directory(getIntent().getStringExtra(EXTRA_PATH));
        JSONObject options = null;
        String failure = null;
        if (!CONTEXT_NSCRIPTER.equals(context) && !CONTEXT_ONSCRIPTER.equals(context)) {
            failure = "Unsupported NScripter engine context: " + context;
        } else if (game == null) {
            failure = "Enginehost did not provide a readable game folder";
        } else {
            try {
                String raw = getIntent().getStringExtra(EXTRA_OPTIONS);
                options = raw == null || raw.trim().isEmpty()
                    ? new JSONObject() : new JSONObject(raw);
            } catch (Exception error) {
                failure = "ONScripter options must be a JSON object";
            }
        }
        File saves = null;
        if (failure == null) {
            saves = saveDirectory(game, options);
            if (saves == null) failure = "Unable to create the save folder for this game";
        }

        // SDLActivity.onCreate has to run whatever happens: an Activity that
        // returns from onCreate without calling through dies with
        // SuperNotCalledException instead of showing its message. It is still
        // safe to refuse a launch after it, because the engine thread does not
        // start here -- SDLActivity starts it only once its surface reaches
        // the RESUMED state (handleNativeState), which a finish() in onCreate
        // never lets happen.
        if (failure == null) {
            arguments = buildArguments(game, saves, options);
            Log.i(TAG, "Launching ONScripter with " + join(arguments));
            loadControllerBindings(getIntent().getStringExtra(EXTRA_BINDINGS));
            launched = true;
        }
        super.onCreate(state);
        if (failure != null) {
            Log.e(TAG, failure);
            Toast.makeText(this, failure, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        nativeInitJavaCallbacks();
        fullscreen();
    }

    /** The engine's command line, in the engine's own options. */
    private String[] buildArguments(File game, File saves, JSONObject options) {
        ArrayList<String> args = new ArrayList<String>();
        // --root: where the script and the .nsa/.sar archives are read from
        // (onscripter_main.cpp:370, ONScripter::setArchivePath).
        args.add("--root");
        args.add(game.getPath());
        // --save-dir: where save<n>.dat and gloval.sav go. A save directory
        // given on the command line deliberately wins over both the game's own
        // `savedir` command and the one recorded in envdata -- each of those
        // sets save_dir only "if (!save_dir)", in
        // ScriptParser::savedirCommand and ONScripter::readEnvData -- so a
        // game's saves land in Enginehost's folder for it whatever the game
        // asks for.
        //
        // One file is not covered: `envdata`, the engine's global settings,
        // which ScriptParser::saveFileIOBuf and loadFileIOBuf exclude from the
        // save directory by name (ScriptParser.cpp:359, :384) and therefore
        // read and write beside the game. That is the engine's own design --
        // envdata is where a game's `savedir` name is recorded, so it has to
        // be readable before the save directory is known -- and it is left
        // alone here rather than quietly given different semantics.
        args.add("--save-dir");
        args.add(saves.getPath() + File.separator);

        String font = fontFile(game, options);
        if (font != null) {
            args.add("--font");
            args.add(font);
        }

        // --enc: the script's character set. The engine guesses, and guesses
        // wrong on plenty of releases; a person who sees mojibake sets this
        // (onscripter_main.cpp:383-392).
        String encoding = options.optString("encoding", "");
        if (isOneOf(encoding, "sjis", "gbk", "utf8")) args.add("--enc:" + encoding);

        // The window is always the whole screen here; which of the two
        // fullscreen modes is the person's taste, because --fullscreen2
        // stretches to the display and --fullscreen keeps the game's own
        // aspect ratio (onscripter_main.cpp:408-413).
        args.add(options.optBoolean("stretchFullscreen", false) ? "--fullscreen2" : "--fullscreen");
        addNumber(args, options, "width", "--width");
        addNumber(args, options, "height", "--height");
        addNumber(args, options, "sharpness", "--sharpness");
        addFlag(args, options, "disableVideo", "--no-video");
        addFlag(args, options, "disableVsync", "--no-vsync");
        addFlag(args, options, "disableRescale", "--disable-rescale");
        addFlag(args, options, "renderFontOutline", "--render-font-outline");
        addFlag(args, options, "forceButtonShortcut", "--force-button-shortcut");
        addFlag(args, options, "enableWheeldownAdvance", "--enable-wheeldown-advance");
        addFlag(args, options, "fontCache", "--fontcache");
        addFlag(args, options, "debugLog", "--debug:1");
        addValue(args, options, "registryFile", "--registry");
        addValue(args, options, "dllFile", "--dll");
        addValue(args, options, "keyExeFile", "--key-exe");
        return args.toArray(new String[0]);
    }

    /**
     * Which font the engine draws text with.
     *
     * <p>ONScripter has one rule of its own here: without {@code --font} it
     * opens "default.ttf" relative to the working directory and stops with
     * "can't open font file" when there is none (ONScripter.cpp:42, :565-573,
     * :680-682), and its fontconfig fallback is compiled out on Android. So
     * the game's own font is used when the game ships one, the person's choice
     * wins over that, and a game that ships no font falls back to a Japanese
     * font already on the device rather than refusing to start.
     */
    private String fontFile(File game, JSONObject options) {
        String chosen = options.optString("fontFile", "");
        if (!chosen.trim().isEmpty() && new File(chosen).isFile()) return chosen;
        File shipped = new File(game, "default.ttf");
        if (shipped.isFile()) return shipped.getPath();
        for (String candidate : SYSTEM_FONT_FALLBACKS) {
            if (new File(candidate).isFile()) {
                Log.i(TAG, "The game ships no default.ttf; falling back to " + candidate);
                return candidate;
            }
        }
        Log.w(TAG, "No default.ttf in the game and no Japanese system font found; "
            + "the engine will look for default.ttf itself");
        return null;
    }

    private File saveDirectory(File game, JSONObject options) {
        String path = getIntent().getStringExtra(EXTRA_SAVE_PATH);
        if (path == null || path.trim().isEmpty()) path = options.optString("savePath", "");
        if (path.trim().isEmpty()) return null;
        File saves = new File(path);
        if (!saves.isDirectory() && !saves.mkdirs()) return null;
        return saves;
    }

    private static File directory(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        File file = new File(path);
        return file.isDirectory() ? file.getAbsoluteFile() : null;
    }

    private static String join(String[] parts) {
        StringBuilder joined = new StringBuilder();
        for (String part : parts) {
            if (joined.length() > 0) joined.append(' ');
            joined.append(part);
        }
        return joined.toString();
    }

    private static boolean isOneOf(String value, String... allowed) {
        for (String candidate : allowed) if (candidate.equals(value)) return true;
        return false;
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

    // ------------------------------------------------------------ SDL wiring

    private native int nativeInitJavaCallbacks();

    static {
        System.loadLibrary("lua");
        System.loadLibrary("jpeg");
        System.loadLibrary("bz2");
        System.loadLibrary("onsyuri");
    }

    @Override
    protected String[] getLibraries() {
        return new String[] { "SDL2", "SDL2_image", "SDL2_mixer", "SDL2_ttf", "onsyuri" };
    }

    @Override
    protected String[] getArguments() {
        return arguments;
    }

    @Override
    protected String getMainSharedObject() {
        // SDLActivity looks for the engine in the application's own native
        // library directory, which under Enginehost is the host APK's and not
        // this bundle's. The class loader that loaded this class knows where
        // the bundle put lib/<abi>; ask it.
        ClassLoader loader = ONScripter.class.getClassLoader();
        if (loader instanceof dalvik.system.BaseDexClassLoader) {
            String path = ((dalvik.system.BaseDexClassLoader) loader).findLibrary("onsyuri");
            if (path != null) return path;
        }
        return super.getMainSharedObject();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (launched) fullscreen();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (launched && hasFocus) fullscreen();
    }

    private void fullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    // ------------------------------------------------------ engine callbacks

    /**
     * The engine asks Java for a file descriptor only when plain fopen has
     * already failed (onscripter_main.cpp fopen_ons, stat_ons). That fallback
     * exists for the standalone app's Storage Access Framework trees;
     * Enginehost hands over an ordinary readable path, so there is nothing
     * here to fall back to and -1 is the honest answer.
     */
    public int getFD(byte[] path, int mode) {
        Log.w(TAG, "The engine could not open " + new String(path, StandardCharsets.UTF_8)
            + " directly, and this plugin has no second way to reach it");
        return -1;
    }

    /** Same story as getFD: the engine calls this only after mkdir failed. */
    public int mkdir(byte[] path) {
        return new File(new String(path, StandardCharsets.UTF_8)).mkdirs() ? 0 : -1;
    }

    /**
     * A game's video, handed to whatever app the person plays videos with.
     *
     * <p>The engine has no video decoder on Android and calls out for one (its
     * movie and avi commands). A file Uri is all this can offer: a bundle's
     * manifest is never installed, so this plugin cannot register a
     * FileProvider of its own and cannot make a content Uri for a file in the
     * game folder. On Android 7 and later the platform refuses a file Uri in
     * an intent, which is caught here and logged; the engine carries on with
     * the scene after the video either way. Enginehost exposing a game file as
     * a content Uri is what would fix this properly.
     */
    public void playVideo(byte[] pathBytes) {
        String path = new String(pathBytes, StandardCharsets.UTF_8).replace('\\', '/');
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(new File(path)), "video/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception error) {
            Log.w(TAG, "Cannot hand " + path + " to a video player from inside a bundle: " + error);
        }
    }

    // -------------------------------------------------------- the controller

    /**
     * What each action presses, as an Android key code SDL translates back
     * into the key ONScripter reads.
     *
     * <p>Every entry is the engine's own: the left column is an action named
     * after what the engine does, and the right is the key
     * ONScripter::keyPressEvent acts on
     * (src/onsyuri/ONScripter_event.cpp:690-1010). Nothing here decides what a
     * button does; it decides what an action does, which is the engine's
     * business, and Enginehost decides which button raises the action.
     */
    private static final Map<String, Integer> ENGINE_KEYS = new HashMap<String, Integer>();
    static {
        // RETURN: advance the text, press the button the cursor is on
        // (keyPressEvent's WAIT_BUTTON_MODE and WAIT_INPUT_MODE arms).
        ENGINE_KEYS.put("ons_advance", Integer.valueOf(KeyEvent.KEYCODE_ENTER));
        // SPACE: advances like RETURN, except in a game that asked for
        // useescspc, where it is that game's own SPACE function (button -11).
        ENGINE_KEYS.put("ons_space", Integer.valueOf(KeyEvent.KEYCODE_SPACE));
        // ESCAPE: the engine's right click. Opens the game's right-click menu,
        // erases the message window, and cancels (button -1, "RCLICK").
        ENGINE_KEYS.put("ons_right_click", Integer.valueOf(KeyEvent.KEYCODE_ESCAPE));
        // RCTRL: held, the engine runs text forward for as long as it is down
        // (ctrl_pressed_status in keyDownEvent and keyUpEvent).
        ENGINE_KEYS.put("ons_ctrl", Integer.valueOf(KeyEvent.KEYCODE_CTRL_RIGHT));
        // s, a, o and 0: the engine's own toggles, in its own letters.
        ENGINE_KEYS.put("ons_skip", Integer.valueOf(KeyEvent.KEYCODE_S));
        ENGINE_KEYS.put("ons_auto", Integer.valueOf(KeyEvent.KEYCODE_A));
        ENGINE_KEYS.put("ons_single_page", Integer.valueOf(KeyEvent.KEYCODE_O));
        ENGINE_KEYS.put("ons_text_speed", Integer.valueOf(KeyEvent.KEYCODE_0));
        // The four directions are one input each, and the engine decides what
        // that input means where it lands: up and down step the cursor between
        // buttons, left is the wheel-up that opens the backlog, right is the
        // wheel-down that advances or scrolls it, and under a game's getcursor
        // all four are its cursor keys instead (buttons -40 to -43).
        ENGINE_KEYS.put("ons_cursor_up", Integer.valueOf(KeyEvent.KEYCODE_DPAD_UP));
        ENGINE_KEYS.put("ons_cursor_down", Integer.valueOf(KeyEvent.KEYCODE_DPAD_DOWN));
        ENGINE_KEYS.put("ons_cursor_left", Integer.valueOf(KeyEvent.KEYCODE_DPAD_LEFT));
        ENGINE_KEYS.put("ons_cursor_right", Integer.valueOf(KeyEvent.KEYCODE_DPAD_RIGHT));
        // PAGEUP and PAGEDOWN: read only by a game that asked for them with
        // getpageup or getpagedown (buttons -12 and -13).
        ENGINE_KEYS.put("ons_page_up", Integer.valueOf(KeyEvent.KEYCODE_PAGE_UP));
        ENGINE_KEYS.put("ons_page_down", Integer.valueOf(KeyEvent.KEYCODE_PAGE_DOWN));
    }

    /**
     * The shared action set read as this engine's.
     *
     * <p>Enginehost picks an action set per engine, and until it carries one
     * for nscripter it sends the shared fallback set instead. Those ids name
     * the same inputs in other words, so they are accepted here as well as the
     * engine's own: a person's map then works on the host they actually have,
     * rather than the plugin going silent until the host catches up. An id
     * neither table knows is ignored, which is what the bundle format says a
     * plugin does with an action it does not understand.
     */
    private static final Map<String, String> SHARED_ACTION_ALIASES = new HashMap<String, String>();
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
        // quick_save and quick_load have no counterpart: ONScripter saves and
        // loads through the game's own right-click menu and has no key for
        // either, so those two do nothing here rather than being bent onto a
        // key that means something else.
    }

    /** One axis binding: which axis, and which way it has to be pushed. */
    private static final class AxisBinding {
        final int axis;
        final int direction;

        AxisBinding(int axis, int direction) {
            this.axis = axis;
            this.direction = direction;
        }
    }

    private final Map<Integer, String> padKeyActions = new HashMap<Integer, String>();
    private final Map<String, AxisBinding> padAxisActions = new HashMap<String, AxisBinding>();
    private final Map<String, Boolean> digitalAxisHeld = new HashMap<String, Boolean>();
    private boolean mapped;

    /**
     * Reads the host's map, and only the host's map.
     *
     * <p>No map at all is the contract for "the engine handles the pad
     * itself": Enginehost leaves the extra out entirely while a scope's bypass
     * is on, and this engine is one worth bypassing, because its own SDL
     * game-controller table already presses the right keys. So an absent or
     * unreadable map means this class keeps its hands off every pad event and
     * SDL delivers them to the engine as it would standalone. There is
     * deliberately no default table here: a second set of defaults beside the
     * engine's own would be two answers to one question.
     */
    private void loadControllerBindings(String json) {
        padKeyActions.clear();
        padAxisActions.clear();
        digitalAxisHeld.clear();
        mapped = false;
        if (json == null || json.trim().isEmpty()) {
            Log.i(TAG, "No controller map from Enginehost; the engine's own pad table applies");
            return;
        }
        try {
            JSONObject map = new JSONObject(json);
            Iterator<String> actions = map.keys();
            while (actions.hasNext()) {
                String action = actions.next();
                String engineAction = SHARED_ACTION_ALIASES.containsKey(action)
                    ? SHARED_ACTION_ALIASES.get(action) : action;
                // An action this engine has no input for: ignored, as the
                // bundle format says.
                if (!ENGINE_KEYS.containsKey(engineAction)) continue;
                JSONObject binding = map.getJSONObject(action);
                String type = binding.getString("type");
                if ("key".equals(type)) {
                    padKeyActions.put(Integer.valueOf(binding.getInt("code")), engineAction);
                } else if ("axis".equals(type)) {
                    int direction = binding.optInt("direction", 0);
                    // An analogue binding is a value, and this engine reads no
                    // axis anywhere in its input model, so there is nothing it
                    // could be read as. It is dropped rather than quietly
                    // turned into a press.
                    if (direction == 0) continue;
                    padAxisActions.put(engineAction,
                        new AxisBinding(binding.getInt("axis"), direction));
                }
                // "none" is an action bound to nothing: it belongs in neither
                // map, which is all being unbound is here.
                mapped = true;
            }
        } catch (Exception error) {
            Log.w(TAG, "Ignoring an unreadable controller map: " + error);
            padKeyActions.clear();
            padAxisActions.clear();
            mapped = false;
            return;
        }
        Log.i(TAG, "Controller map: " + padKeyActions.size() + " buttons, "
            + padAxisActions.size() + " signed axes");
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!launched || !mapped) return super.dispatchKeyEvent(event);
        String action = padKeyActions.get(Integer.valueOf(event.getKeyCode()));
        if (action == null) return super.dispatchKeyEvent(event);
        // Android repeats a held key on its own clock; the engine wants one
        // press held down, which is what its ctrl fast-forward reads.
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() > 0) return true;
        press(action, event.getAction() == KeyEvent.ACTION_DOWN);
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        boolean fromPad = (event.getSource()
            & (InputDevice.SOURCE_JOYSTICK | InputDevice.SOURCE_GAMEPAD)) != 0;
        if (!launched || !mapped || !fromPad
                || event.getAction() != MotionEvent.ACTION_MOVE
                || padAxisActions.isEmpty()) {
            return super.dispatchGenericMotionEvent(event);
        }
        for (int sample = 0; sample < event.getHistorySize(); sample++) {
            applyDigitalAxes(event, sample);
        }
        applyDigitalAxes(event, -1);
        return true;
    }

    /**
     * Actions bound to an axis with a sign, pressed and released by where that
     * axis stands.
     *
     * <p>This is where a hat D-pad lives. A console pad's D-pad arrives as
     * AXIS_HAT_X and AXIS_HAT_Y on a motion event and never as KEYCODE_DPAD_*
     * at all, and Enginehost captures it as an axis binding with a direction,
     * so the four directions reach the engine the same way a button does.
     */
    private void applyDigitalAxes(MotionEvent event, int sample) {
        for (Map.Entry<String, AxisBinding> bound : padAxisActions.entrySet()) {
            AxisBinding binding = bound.getValue();
            float value = sample < 0 ? event.getAxisValue(binding.axis)
                : event.getHistoricalAxisValue(binding.axis, sample);
            boolean down = sign(value) == binding.direction;
            Boolean before = digitalAxisHeld.get(bound.getKey());
            if ((before != null && before.booleanValue()) == down) continue;
            digitalAxisHeld.put(bound.getKey(), Boolean.valueOf(down));
            press(bound.getKey(), down);
        }
    }

    private static int sign(float value) {
        if (value > 0.5f) return 1;
        if (value < -0.5f) return -1;
        return 0;
    }

    /** Presses or releases the engine's key for an action. */
    private void press(String action, boolean down) {
        Integer code = ENGINE_KEYS.get(action);
        if (code == null) return;
        if (down) SDLActivity.onNativeKeyDown(code.intValue());
        else SDLActivity.onNativeKeyUp(code.intValue());
    }
}
