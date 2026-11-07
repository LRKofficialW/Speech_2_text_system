cmake_minimum_required(VERSION 3.22.1)

project("neuroscribe_native")

add_library(
    neuroscribe_native
    SHARED
    dsp_engine.cpp
)

find_library(
    log-lib
    log
)

target_include_directories(
    neuroscribe_native
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(
    neuroscribe_native
    ${log-lib}
)
