/* -*- C++ -*-
 * 
 *  onscripter_main.cpp -- main function of ONScripter
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

ONScripter ons;
Coding2UTF16 *coding2utf16 = NULL;
std::string g_stdoutpath = "stdout.txt";
std::string g_stderrpath = "stderr.txt";

#if defined(IOS)
#import <Foundation/NSArray.h>
#import <UIKit/UIKit.h>
#import "DataCopier.h"
#import "DataDownloader.h"
#import "ScriptSelector.h"
#import "MoviePlayer.h"
#endif

#if defined(ANDROID)
#include <unistd.h>
#endif

#if defined(WINRT)
#include "ScriptSelector.h"
#endif

#if defined(ANDROID)
extern "C"
{
#include <jni.h>
#include <android/log.h>
#include <errno.h>
static JavaVM *jniVM = NULL;
static jobject JavaONScripter = NULL;
static jmethodID JavaPlayVideo = NULL;
static jmethodID JavaGetFD = NULL;
static jmethodID JavaMkdir = NULL;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
{
    jniVM = vm;
    return JNI_VERSION_1_2;
};

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved)
{
    jniVM = vm;
};

#ifndef SDL_JAVA_PACKAGE_PATH
#error You have to define SDL_JAVA_PACKAGE_PATH to your package path with dots replaced with underscores, for example "com_example_SanAngeles"
#endif
#define JAVA_EXPORT_NAME2(name,package) Java_##package##_##name
#define JAVA_EXPORT_NAME1(name,package) JAVA_EXPORT_NAME2(name,package)
#define JAVA_EXPORT_NAME(name) JAVA_EXPORT_NAME1(name,SDL_JAVA_PACKAGE_PATH)

JNIEXPORT jint JNICALL JAVA_EXPORT_NAME(ONScripter_nativeInitJavaCallbacks) (JNIEnv * jniEnv, jobject thiz)
{
    JavaONScripter = jniEnv->NewGlobalRef(thiz);
    jclass JavaONScripterClass = jniEnv->GetObjectClass(JavaONScripter);
    JavaPlayVideo = jniEnv->GetMethodID(JavaONScripterClass, "playVideo", "([B)V");
    JavaGetFD = jniEnv->GetMethodID(JavaONScripterClass, "getFD", "([BI)I");
    JavaMkdir = jniEnv->GetMethodID(JavaONScripterClass, "mkdir", "([B)I");
    return 0;
}

JNIEXPORT jint JNICALL 
JAVA_EXPORT_NAME(ONScripter_nativeGetWidth) ( JNIEnv*  env, jobject thiz )
{
    return ons.getWidth();
}

JNIEXPORT jint JNICALL 
JAVA_EXPORT_NAME(ONScripter_nativeGetHeight) ( JNIEnv*  env, jobject thiz )
{
    return ons.getHeight();
}

void playVideoAndroid(const char *path)
{
    JNIEnv * jniEnv = NULL;
    jniVM->AttachCurrentThread(&jniEnv, NULL);

    if (!jniEnv){
        __android_log_print(ANDROID_LOG_ERROR, "ONS", "ONScripter::playVideoAndroid: Java VM AttachCurrentThread() failed");
        return;
    }

    jbyte *jb = new jbyte[strlen(path)];
    for (int i=0;i<strlen(path);i++) jb[i] = path[i];
    jbyteArray jba = jniEnv->NewByteArray(strlen(path));
    jniEnv->SetByteArrayRegion(jba, 0, strlen(path), jb);
    jniEnv->CallVoidMethod(JavaONScripter, JavaPlayVideo, jba);
    jniEnv->DeleteLocalRef(jba);
    delete[] jb;
}

#include <sys/stat.h>
int stat_ons(const char *path, struct stat * statbuf){
    int res = stat(path, statbuf);
    if(res!=-1 ) return res;

    JNIEnv * jniEnv = NULL;
    jniVM->AttachCurrentThread(&jniEnv, NULL);
    jbyte *jb = new jbyte[strlen(path)];
    for (int i=0;i<strlen(path);i++) jb[i] = path[i];
    jbyteArray jba = jniEnv->NewByteArray(strlen(path));
    jniEnv->SetByteArrayRegion(jba, 0, strlen(path), jb);
    int fd = jniEnv->CallIntMethod(JavaONScripter, JavaGetFD, jba, 0);
    jniEnv->DeleteLocalRef(jba);
    delete[] jb;
    res = fstat(fd, statbuf);
    close(fd);
    return res;
}

#undef fopen
FILE *fopen_ons(const char *path, const char *mode)
{
    int mode2 = 0;
    if (mode[0] == 'w') mode2 = 1;

    FILE *fp = fopen(path, mode);
    // __android_log_print(ANDROID_LOG_INFO, "## onsyuri",  "fopen_ons %s, %s, %p", path, mode, fp);

    if (fp) return fp;
    
    JNIEnv * jniEnv = NULL;
    jniVM->AttachCurrentThread(&jniEnv, NULL);

    if (!jniEnv){
        __android_log_print(ANDROID_LOG_ERROR, "ONS", "ONScripter::getFD: Java VM AttachCurrentThread() failed");
        return NULL;
    }

    jbyte *jb = new jbyte[strlen(path)];
    for (int i=0;i<strlen(path);i++) jb[i] = path[i];
    jbyteArray jba = jniEnv->NewByteArray(strlen(path));
    jniEnv->SetByteArrayRegion(jba, 0, strlen(path), jb);
    int fd = jniEnv->CallIntMethod(JavaONScripter, JavaGetFD, jba, mode2 );
    jniEnv->DeleteLocalRef(jba);
    delete[] jb;

    return fdopen(fd, mode);
}

#undef mkdir
extern int mkdir(const char *pathname, mode_t mode);
int mkdir_ons(const char *path, mode_t mode)
{
    if (mkdir(path, mode) == 0 || errno != EACCES) return 0;

    JNIEnv * jniEnv = NULL;
    jniVM->AttachCurrentThread(&jniEnv, NULL);

    if (!jniEnv){
        __android_log_print(ANDROID_LOG_ERROR, "ONS", "ONScripter::mkdir: Java VM AttachCurrentThread() failed");
        return -1;
    }

    jbyte *jb = new jbyte[strlen(path)];
    for (int i=0;i<strlen(path);i++) jb[i] = path[i];
    jbyteArray jba = jniEnv->NewByteArray(strlen(path));
    jniEnv->SetByteArrayRegion(jba, 0, strlen(path), jb);
    int ret = jniEnv->CallIntMethod(JavaONScripter, JavaMkdir, jba);
    jniEnv->DeleteLocalRef(jba);
    delete[] jb;

    return ret;
}
}
#endif

#if defined(IOS)
extern "C" void playVideoIOS(const char *filename, bool click_flag, bool loop_flag)
{
    NSString *str = [[NSString alloc] initWithUTF8String:filename];
    id obj = [MoviePlayer alloc];
    [[obj init] play:str click : click_flag loop : loop_flag];
    [obj release];
}
#endif

#if defined(WEB)
#include <emscripten.h>

#undef fopen

/*
* fetch the file from server beforce fopen for lazyload
* value: g_onsyuri_module, g_onsyuri_index, g_onsyuri_filemap
* function: fetch_file
*/
FILE *fopen_ons(const char *path, const char *mode)
{
    // printf("## fopen_ons %s, ", path);
    static int use_lazyload = -1;
    if(use_lazyload==-1)
    {
        use_lazyload = EM_ASM_INT(
            // check enviroment
            if (!g_onsyuri_module) return 0;
            if (!g_onsyuri_index) return 0;
            if (!fetch_file) return 0;

            // check lazyload flag with filemap
            if (!g_onsyuri_filemap) return 0;
            if (Object.keys(g_onsyuri_filemap).length==0) return 0;
            return 1;
        );
    }
    
    FILE *fp = fopen(path, mode);
    if(!fp && use_lazyload==1 && (strcmp(mode, "r") || strcmp(mode, "rb")))
    {   
        int ret = 0;
        ret = EM_ASM_INT( // path is combined after --gamedir
            var path = g_onsyuri_module.UTF8ToString($0);
            var key = path.toLowerCase(); // pay attention to the case sensitive of web
            if(!g_onsyuri_filemap[key]) return 0;
            if(g_onsyuri_filemap[key].loaded) return 2;
            try {
                fetch_file(g_onsyuri_module.FS, key, g_onsyuri_filemap);
                return 1;
            } 
            catch(e) { 
                console.log("## fopen_ons web lazyload ${path} error: ", e);
                return 0;
            }, path);
        switch (ret)
        {
            case 0: 
            {
                // printf(" not found in g_onsyuri_filemap !\n");
                return fp; // use fopen to check for other file mount
                break;
            }
            case 1:
            {
                // printf(" waiting for fetch...\n");
                break;
            }
            case 2:
            {
                // printf(" already loaded!\n");
                return fp;
                break;
            }
        }

        while(!EM_ASM_INT(
            var path = g_onsyuri_module.UTF8ToString($0);
            var key = path.toLowerCase();
            return g_onsyuri_filemap[key].loaded;, path))
        {
            SDL_Delay(5); // wait for async function
        }
        fp = fopen(path, mode); // reload after fetch
    }
    return fp;
}

extern "C" void playVideoWeb(const char *path, bool click_flag, bool loop_flag)
{
    EM_ASM(
        var path = g_onsyuri_module.UTF8ToString($0);
        playVideo(path, $1, $2);
    ,path,click_flag,loop_flag);

    while(EM_ASM_INT(return g_onsyuri_module.wait_video;))
    {
        SDL_Delay(5);
    }
}
#endif

int main(int argc, char *argv[])
{
#if defined(PSP)
    ons.disableRescale();
    ons.enableButtonShortCut();
    SetupCallbacks();
#elif defined(WINRT)
    {
        ScriptSelector ss;
        ons.setArchivePath(ss.selectedPath.c_str());
    }
    ons.disableRescale();
#elif defined(ANDROID)
    ons.enableButtonShortCut();
#endif

#if defined(IOS)
#if defined(HAVE_CONTENTS)
    if ([[[DataCopier alloc] init] copy]) exit(-1);
#endif

    // scripts and archives are stored under /Library/Caches
    NSArray* cpaths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES);
    NSString* cpath = [[cpaths objectAtIndex : 0] stringByAppendingPathComponent:@"ONS"];
    char filename[256];
    strcpy(filename, [cpath UTF8String]);
    ons.setArchivePath(filename);

    // output files are stored under /Documents
    NSArray* dpaths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
    NSString* dpath = [[dpaths objectAtIndex : 0] stringByAppendingPathComponent:@"ONS"];
    strcpy(filename, [dpath UTF8String]);
    ons.setSaveDir(filename);

#if defined(ZIP_URL)
    if ([[[DataDownloader alloc] init] download]) exit(-1);
#endif

#if defined(USE_SELECTOR)
    // scripts and archives are stored under /Library/Caches
    cpath = [[[ScriptSelector alloc] initWithStyle:UITableViewStylePlain] select];
    strcpy(filename, [cpath UTF8String]);
    ons.setArchivePath(filename);

    // output files are stored under /Documents
    dpath = [[dpaths objectAtIndex : 0] stringByAppendingPathComponent:[cpath lastPathComponent]];
    NSFileManager *fm = [NSFileManager defaultManager];
    [fm createDirectoryAtPath : dpath withIntermediateDirectories : YES attributes : nil error : nil];
    strcpy(filename, [dpath UTF8String]);
    ons.setSaveDir(filename);
#endif

#if defined(RENDER_FONT_OUTLINE)
    ons.renderFontOutline();
#endif
#endif

    // ----------------------------------------
    // Parse options
    argv++;
    parseOption(argc - 1, argv);

    utils::printInfo("ONScripter Yuri %s, (Jh%s,Ons%s,NSC%d.%02d)\n", 
        ONS_YURI_VERSION, ONS_JH_VERSION, 
        ONS_VERSION, NSC_VERSION / 100, NSC_VERSION % 100);

    const char *argfilename = "ons_args";
    FILE *fp = NULL;
    if (ons.getArchivePath()) {
        size_t len = strlen(ons.getArchivePath()) + strlen(argfilename) + 1;
        char *full_path = new char[len];
        sprintf(full_path, "%s%s", ons.getArchivePath(), argfilename);
        fp = fopen(full_path, "r");
        delete[] full_path;
    }
    else fp = fopen(argfilename, "r");
    if (fp) {
        char **args = new char*[16];
        int argn = 0;
        args[argn] = new char[64];
        while (argn < 16 && (fscanf(fp, "%s", args[argn]) > 0)) {
            ++argn;
            if (argn < 16) args[argn] = new char[64];
        }
        parseOption(argn, args);
        for (int i = 0; i < argn; ++i) delete[] args[i];
        delete[] args;
    }

    if (coding2utf16 == NULL) coding2utf16 = new GBK2UTF16();

    // ----------------------------------------
    // Run ONScripter
    if (ons.openScript()) exit(-1);
    if (ons.init()) exit(-1);
#if defined(WEB)
    EM_ASM(
        self.postMessage("onsinit");
    );
#endif
    ons.executeLabel();
    exit(0);
}
