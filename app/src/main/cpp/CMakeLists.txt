cmake_minimum_required(VERSION 3.22.1)
project("needlebridge")

add_library(needle STATIC IMPORTED)
set_target_properties(needle PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/needle/${ANDROID_ABI}/libneedle.a"
    INTERFACE_INCLUDE_DIRECTORIES "${CMAKE_CURRENT_SOURCE_DIR}/include"
)

add_library(needlebridge SHARED needle_jni.cpp)
target_include_directories(needlebridge PRIVATE include)
target_compile_features(needlebridge PRIVATE cxx_std_17)
target_link_libraries(needlebridge needle log android c++_shared)
