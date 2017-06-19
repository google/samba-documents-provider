# Sets the minimum version of CMake required to build the native
# library. You should either keep the default value or only pass a
# value of 3.4.0 or lower.

cmake_minimum_required(VERSION 3.4.1)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds it for you.
# Gradle automatically packages shared libraries with your APK.

add_library(samba_client
            SHARED
            src/main/cpp/jni_helper/JniHelper.cc
            src/main/cpp/jni_helper/JavaClassCache.cc
            src/main/cpp/samba_client/SambaClient.cc
            src/main/cpp/credential_cache/CredentialCache.cc
            )

include_directories(src/main/cpp)

set(CMAKE_CXX_FLAGS
    "${CMAKE_CXX_FLAGS} -std=c++0x -O2 -D_FORTIFY_SOURCE=2 -fstack-protector-all -fPIE")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -pie")

# Searches for a specified prebuilt library and stores the path as a
# variable. Because system libraries are included in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in the
# build script, prebuilt third-party libraries, or system libraries.

set(libfolder "${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}")
add_library(libsmbclient
            SHARED
            IMPORTED)
set_target_properties(libsmbclient
                      PROPERTIES IMPORTED_LOCATION
                      ${libfolder}/libsmbclient.so)

target_link_libraries(samba_client ${log-lib} libsmbclient)
