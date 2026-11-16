cmake_minimum_required(VERSION 3.22.1)
project(lumina_native)

add_library(lumina_native SHARED native_bridge.cpp lumina_game.cpp csharp_bridge.cpp)

find_library(log-lib log)
find_library(android-lib android)
find_library(dl-lib dl)

target_compile_features(lumina_native PRIVATE cxx_std_17)
target_compile_options(lumina_native PRIVATE -Wall -Wextra -Werror=return-type)
target_compile_definitions(lumina_native PRIVATE
        LUMINA_ANDROID_ABI="${CMAKE_ANDROID_ARCH_ABI}")
target_link_libraries(lumina_native ${log-lib} ${android-lib} ${dl-lib})

# ---------------------------------------------------------------------------
# FBX / OBJ / BLEND import (converted to GLB for the runtime's gdx-gltf loader)
#
# Only the importers and the glTF2 exporter are built: full Assimp is much
# slower to compile and adds formats the editor cannot use. The sources are
# fetched at configure time (CI has network); the generated GLB keeps bones,
# skins and animation clips.
# ---------------------------------------------------------------------------
include(FetchContent)

set(ASSIMP_BUILD_TESTS            OFF CACHE BOOL "" FORCE)
set(ASSIMP_BUILD_SAMPLES          OFF CACHE BOOL "" FORCE)
set(ASSIMP_BUILD_ASSIMP_TOOLS     OFF CACHE BOOL "" FORCE)
set(ASSIMP_BUILD_DOCS             OFF CACHE BOOL "" FORCE)
set(ASSIMP_INSTALL                OFF CACHE BOOL "" FORCE)
set(ASSIMP_WARNINGS_AS_ERRORS     OFF CACHE BOOL "" FORCE)
set(ASSIMP_BUILD_ZLIB             ON  CACHE BOOL "" FORCE)
set(ASSIMP_BUILD_DRACO            OFF CACHE BOOL "" FORCE)
set(ASSIMP_IGNORE_GIT_HASH         ON  CACHE BOOL "" FORCE)

set(ASSIMP_BUILD_ALL_IMPORTERS_BY_DEFAULT OFF CACHE BOOL "" FORCE)
set(ASSIMP_BUILD_OBJ_IMPORTER             ON  CACHE BOOL "" FORCE)
set(ASSIMP_BUILD_FBX_IMPORTER             ON  CACHE BOOL "" FORCE)
set(ASSIMP_BUILD_BLEND_IMPORTER           ON  CACHE BOOL "" FORCE)

set(ASSIMP_BUILD_ALL_EXPORTERS_BY_DEFAULT OFF CACHE BOOL "" FORCE)
set(ASSIMP_BUILD_GLTF_EXPORTER            ON  CACHE BOOL "" FORCE)

FetchContent_Declare(
        assimp
        URL https://github.com/assimp/assimp/archive/refs/tags/v6.0.6.tar.gz
)
FetchContent_MakeAvailable(assimp)

add_library(lumina_assimp SHARED assimp_bridge.cpp)
target_compile_features(lumina_assimp PRIVATE cxx_std_17)
target_compile_options(lumina_assimp PRIVATE -Wall -Wextra)
target_link_libraries(lumina_assimp assimp::assimp ${log-lib} ${dl-lib})
