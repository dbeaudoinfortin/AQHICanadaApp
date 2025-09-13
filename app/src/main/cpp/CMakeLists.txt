cmake_minimum_required(VERSION 3.26.4)
project("jpeg2000decoder")

add_subdirectory(openjpeg)

add_library(jpeg2000decoder SHARED
    jpeg2000decoder.c
)

target_include_directories(jpeg2000decoder PRIVATE
    ${CMAKE_SOURCE_DIR}/openjpeg/src/lib/openjp2
    ${CMAKE_BINARY_DIR}/openjpeg/src/lib/openjp2
)

target_link_libraries(jpeg2000decoder
    openjp2
    log
)