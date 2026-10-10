# OnscripterYuri in Enginehost's sandbox: the engine's own libretro core
# (src/onsyuri_libretro), linked with Enginehost's libretro frontend and file
# layer into the one library the isolated runtime loads (plugin-native/,
# copied verbatim from Droidtop/enginehost). OnsyuriPlugin.java is the entry
# point.
#
# The core's CMake project only works as a top-level project (it writes into
# ${CMAKE_SOURCE_DIR}/deps), so .github/workflows/android-plugin.yml builds it
# first, per ABI, as static archives (LIBRETRO_STATIC), and this project links
# every archive that build made (ONSYURI_BUILD_DIR) into the plugin library.
# The C++ runtime is linked in statically (ANDROID_STL=c++_static).

cmake_minimum_required(VERSION 3.22)
project(nscripter_enginehost C CXX)

if(NOT ONSYURI_BUILD_DIR)
    message(FATAL_ERROR "Set ONSYURI_BUILD_DIR to the build of src/onsyuri_libretro for this ABI")
endif()
# The core itself (the Android suffix rule names its static archive *.so).
file(GLOB ONSYURI_CORE ${ONSYURI_BUILD_DIR}/onsyuri_libretro*)
file(GLOB_RECURSE ONSYURI_ARCHIVES ${ONSYURI_BUILD_DIR}/*.a)
if(NOT ONSYURI_CORE OR NOT ONSYURI_ARCHIVES)
    message(FATAL_ERROR "No onsyuri_libretro build in ${ONSYURI_BUILD_DIR}")
endif()
# Under its .so name CMake would hand the archive to the linker as -l; as .a
# it is linked as the archive it is.
list(GET ONSYURI_CORE 0 ONSYURI_CORE)
file(COPY_FILE ${ONSYURI_CORE} ${CMAKE_CURRENT_BINARY_DIR}/libonsyuri_core.a)
set(ONSYURI_CORE ${CMAKE_CURRENT_BINARY_DIR}/libonsyuri_core.a)

set(PLUGIN_NATIVE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../plugin-native)
add_library(nscripter_enginehost SHARED
    ${PLUGIN_NATIVE_DIR}/libretro/enginehost_libretro.c
    ${PLUGIN_NATIVE_DIR}/enginehost_audio_ring.c)
target_include_directories(nscripter_enginehost PRIVATE ${PLUGIN_NATIVE_DIR} ${PLUGIN_NATIVE_DIR}/libretro)
# One group: the core and SDL, SDL_image/mixer/ttf, FreeType, Lua and bzip2
# reference each other in both directions. GLESv2 only resolves the core's
# optional GLES renderer, which never runs here (an isolated process has no
# GPU; the core draws in software).
target_link_libraries(nscripter_enginehost PRIVATE
    -Wl,--start-group ${ONSYURI_CORE} ${ONSYURI_ARCHIVES} -Wl,--end-group
    GLESv2 android log m)

# The core is C++: link with the C++ driver so the static C++ runtime comes in.
set_target_properties(nscripter_enginehost PROPERTIES LINKER_LANGUAGE CXX)

include(${PLUGIN_NATIVE_DIR}/enginehost_vfs.cmake)
enginehost_vfs(nscripter_enginehost)
