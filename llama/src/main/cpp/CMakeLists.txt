cmake_minimum_required(VERSION 3.22.1)
project(qwenbridge LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Keep the Android package CPU-only and statically link the upstream llama.cpp
# core into one JNI library. These options avoid desktop/GPU and dynamic-backend
# assumptions in the upstream Android sample while retaining armeabi-v7a.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)

add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp" "${CMAKE_CURRENT_BINARY_DIR}/llama.cpp")

add_library(qwenbridge SHARED qwen_jni.cpp)
target_include_directories(qwenbridge PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include")
target_compile_features(qwenbridge PRIVATE cxx_std_17)
target_link_libraries(qwenbridge PRIVATE llama android log c++_shared)
