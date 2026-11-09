cmake_minimum_required(VERSION 3.22.1)
project(scanly_native C)

add_library(scanly_affinity SHARED scanly_affinity.c)
target_compile_options(scanly_affinity PRIVATE -Wall -Wextra -Werror)
# 16 KB page alignment (required for Android 15+ devices with 16 KB pages).
target_link_options(scanly_affinity PRIVATE "-Wl,-z,max-page-size=16384")
