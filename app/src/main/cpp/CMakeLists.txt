# Voltcap's small native bridge to the Discord Social SDK (Rich Presence).
cmake_minimum_required(VERSION 3.18)
project(voltcap_discord CXX)

add_library(voltcap_discord SHARED discord_bridge.cpp)

# Only the header is needed: the SDK library itself is opened at run time
# (it's packaged from Discord's .aar, see app/build.gradle.kts).
target_include_directories(voltcap_discord PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/../../../libs/discord/include)
target_compile_features(voltcap_discord PRIVATE cxx_std_17)

find_library(log-lib log)
target_link_libraries(voltcap_discord ${log-lib} dl)
