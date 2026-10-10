/* -*- C++ -*-
 * 
 *  onscripter_options.cpp -- ONScripter's command-line options
 *
 *  Copyright (c) 2001-2018 Ogapee. All rights reserved.
 *            (c) 2014-2019 jh10001 <jh10001@live.cn>
 *            (c) 2022-2023 yurisizuku <https://github.com/YuriSizuku>
 *
 *  ogapee@aqua.dti2.ne.jp
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

#include "ONScripter.h"
#include "Utils.h"
#include "gbk2utf16.h"
#include "sjis2utf16.h"
#include "version.h"
#include "stdlib.h"
#include "onscripter_options.h"

/*
 * The options every front end takes, in one place: main() reads them from
 * argv, and the libretro core from a frontend variable
 * (src/onsyuri_libretro/libretro.cpp), so a game launched either way gets
 * the same settings. Moved out of onscripter_main.cpp unchanged.
 */

extern ONScripter ons;
extern Coding2UTF16 *coding2utf16;

void optionHelp()
{
    printf( "Usage: onsyuri [option ...]\n" );
    printf( "  -h, --help\t\tshow this help and exit\n");
    printf( "  -v, --version\t\tshow the version information and exit\n\n");
    
    printf( " load options: \n");
    printf( "  -f, --font file\tset a TTF font file\n");
    printf( "  -r, --root path\tset the root path to the archives\n");
    printf( "      --save-dir\tset save dir\n");
    printf( "      --debug:1\t\tprint debug info\n");
    printf( "      --enc:[gbk|sjis|utf8]\tdefine the encoding of script\n\n");

    printf( " render options: \n");
    printf( "      --window\t\tstart in windowed mode\n");
    printf( "      --width 1280\tforce window width\n");
    printf( "      --height 720\tforce window height\n");
    printf( "      --fullscreen\tstart in fullscreen mode (alt+f or alt+enter)\n");
    printf( "      --fullscreen2\tstart in fullscreen mode with stretch (f10 toggle stretch)\n");
    printf( "      --sharpness 3.1 \t use gles to make image sharp\n");
    printf( "      --no-video\tdo not decode video\n");
    printf( "      --no-vsync\tturn off vsync\n\n");
    
    printf( " other options: \n");
    printf( "      --cdaudio\t\tuse CD audio if available\n");
    printf( "      --cdnumber no\tchoose the CD-ROM drive number\n");
    printf( "      --registry file\tset a registry file\n");
    printf( "      --dll file\tset a dll file\n");
    printf( "      --enable-wheeldown-advance\tadvance the text on mouse wheel down\n");
    printf( "      --disable-rescale\tdo not rescale the images in the archives\n");
    printf( "      --force-button-shortcut\tignore useescspc and getenter command\n");
    printf( "      --render-font-outline\trender the outline of a text instead of casting a shadow\n");
    printf( "      --edit\t\tenable online modification of the volume and variables when 'z' is pressed\n");
    printf( "      --key-exe file\tset a file (*.EXE) that includes a key table\n");
    printf( "      --fontcache\tcache default font\n");
    exit(0);
}

void optionVersion()
{
    printf("Written by Ogapee <ogapee@aqua.dti2.ne.jp>\n\n");
    printf("Copyright (c) 2001-2018 Ogapee.\n\
                (c) 2014-2018 jh10001<jh10001@live.cn>\n\
                (c) 2022-2023 yurisizuku <https://github.com/YuriSizuku>\n");
    printf("This is free software; see the source for copying conditions.\n");
    exit(0);
}

void parseOption(int argc, char *argv[]) {
    while (argc > 0) {
        if ( argv[0][0] == '-' ){
            // version, help
            if ( !strcmp( argv[0]+1, "h" ) || !strcmp( argv[0]+1, "-help" ) ){
                optionHelp();
            }
            else if ( !strcmp( argv[0]+1, "v" ) || !strcmp( argv[0]+1, "-version" ) ){
                optionVersion();
            }

            // load options
            else if ( !strcmp( argv[0]+1, "f" ) || !strcmp( argv[0]+1, "-font" ) ){
                argc--;
                argv++;
                ons.setFontFile(argv[0]);
            }
            else if ( !strcmp( argv[0]+1, "r" ) || !strcmp( argv[0]+1, "-root" ) ){
                argc--;
                argv++;
                ons.setArchivePath(argv[0]);
            }
            else if ( !strcmp(argv[0]+1, "-save-dir") ){
                argc--;
                argv++;
                ons.setSaveDir(argv[0]);
            }
            else if (!strcmp(argv[0]+1, "-debug:1")){
                ons.setDebugLevel(1);
            }
            else if (!strcmp(argv[0]+1, "-enc:sjis")){
                if(!coding2utf16) coding2utf16 = new SJIS2UTF16();
            }
            else if (!strcmp(argv[0]+1, "-enc:gbk")){
                if(!coding2utf16) coding2utf16 = new GBK2UTF16();
            }
            else if (!strcmp(argv[0]+1, "-enc:utf8")){
                if(!coding2utf16) coding2utf16 = new GBK2UTF16();
                coding2utf16->force_utf8 = true;
            }

            // render options
            else if ( !strcmp( argv[0]+1, "-window" ) ){
                ons.setWindowMode();
            }
            else if ( !strcmp( argv[0]+1, "-width" ) ){
                argc--;
                argv++;
                ons.setWindowWidth(atoi(argv[0]));
            }
            else if ( !strcmp( argv[0]+1, "-height" ) ){
                argc--;
                argv++;
                ons.setWindowHeight(atoi(argv[0]));
            }
            else if ( !strcmp( argv[0]+1, "-fullscreen" ) ){
                ons.setFullscreenMode(1);
            }
            else if ( !strcmp( argv[0]+1, "-fullscreen2" ) ){
                ons.setFullscreenMode(2);
            }
            else if ( !strcmp( argv[0]+1, "-sharpness" ) ){
                argc--;
                argv++;
                ons.setSharpness(atof(argv[0]));
            }
            else if (!strcmp(argv[0]+1, "-no-video")){
			    ons.setVideoOff();
			}
            else if (!strcmp(argv[0]+1, "-no-vsync")){
			    ons.setVsyncOff();
			}

            // other options
            else if ( !strcmp( argv[0]+1, "-cdaudio" ) ){
                ons.enableCDAudio();
            }
            else if ( !strcmp( argv[0]+1, "-cdnumber" ) ){
                argc--;
                argv++;
                ons.setCDNumber(atoi(argv[0]));
            }
            else if ( !strcmp( argv[0]+1, "-registry" ) ){
                argc--;
                argv++;
                ons.setRegistryFile(argv[0]);
            }
            else if ( !strcmp( argv[0]+1, "-dll" ) ){
                argc--;
                argv++;
                ons.setDLLFile(argv[0]);
            }
            else if ( !strcmp( argv[0]+1, "-force-button-shortcut" ) ){
                ons.enableButtonShortCut();
            }
            else if ( !strcmp( argv[0]+1, "-enable-wheeldown-advance" ) ){
                ons.enableWheelDownAdvance();
            }
            else if ( !strcmp( argv[0]+1, "-disable-rescale" ) ){
                ons.disableRescale();
            }
            else if ( !strcmp( argv[0]+1, "-render-font-outline" ) ){
                ons.renderFontOutline();
            }
            else if ( !strcmp( argv[0]+1, "-edit" ) ){
                ons.enableEdit();
            }
            else if ( !strcmp( argv[0]+1, "-key-exe" ) ){
                argc--;
                argv++;
                ons.setKeyEXE(argv[0]);
            }
            else if (!strcmp(argv[0]+1, "-fontcache")){
                ons.setFontCache();
            }
            else{
                utils::printInfo(" unknown option %s\n", argv[0]);
            }
        }
        else{
            optionHelp();
        }
        argc--;
        argv++;
    }
}

