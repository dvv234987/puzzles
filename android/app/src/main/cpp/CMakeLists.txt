cmake_minimum_required(VERSION 3.22.1)
project(puzzles C)

# The puzzle sources live in the repository root, five levels up from here
# (android/app/src/main/cpp). Same file list as the iOS target in
# Puzzles.xcodeproj (built with -DCOMBINED); svnversion.c is replaced by the
# 'ver' definition in android_frontend.c.
get_filename_component(PUZZLES_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../.." ABSOLUTE)

set(GAMES
    blackbox bridges combi cube divvy dominosa drawing dsf fifteen filling flip
    flood galaxies grid guess inertia keen latin laydomino lightup list loopgen
    loopy magnets malloc map maxflow midend mines misc net netslide nullgame
    pattern pearl pegs penrose printing random range rect samegame signpost
    singles sixteen slant solo tdq tents towers tracks tree234 twiddle undead
    unequal unruly untangle)

set(PUZZLE_SOURCES "")
foreach(name ${GAMES})
    list(APPEND PUZZLE_SOURCES "${PUZZLES_ROOT}/${name}.c")
endforeach()

add_library(puzzles SHARED ${PUZZLE_SOURCES} android_frontend.c)

target_include_directories(puzzles PRIVATE ${PUZZLES_ROOT})
target_compile_definitions(puzzles PRIVATE COMBINED PUZZLES_VER="Revision: android")
target_compile_options(puzzles PRIVATE -w)
target_link_libraries(puzzles log m)
