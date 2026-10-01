cmake_minimum_required(VERSION 3.22.1)
project(launcherabi C)
add_library(launcherabi SHARED abi_guard.c)
set_target_properties(launcherabi PROPERTIES C_STANDARD 11 C_STANDARD_REQUIRED ON)
