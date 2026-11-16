cmake_minimum_required(VERSION 3.22.1)
project(meettwoled C)

add_library(meettwoled SHARED ledjni.c)
target_compile_options(meettwoled PRIVATE -Wall -Wextra -Werror)
