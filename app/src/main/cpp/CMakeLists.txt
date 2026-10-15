cmake_minimum_required(VERSION 3.22.1)
project(aether_jni CXX)

add_library(aether_jni SHARED aether_jni.cpp)

target_compile_options(aether_jni PRIVATE
    -O2
    -fvisibility=hidden
    -Wall
    -Wextra
)

# dlopen/dlsym live in libdl on older API levels; the Android logger is
# handy for debugging native library loading.
find_library(log-lib log)
find_library(dl-lib dl)

target_link_libraries(aether_jni
    ${log-lib}
    ${dl-lib}
)
