cmake_minimum_required(VERSION 3.22.1)
project(lumina_native)

add_library(lumina_native SHARED native_bridge.cpp)

find_library(log-lib log)
target_compile_features(lumina_native PRIVATE cxx_std_17)
target_compile_options(lumina_native PRIVATE -Wall -Wextra -Werror=return-type)
target_link_libraries(lumina_native ${log-lib})
