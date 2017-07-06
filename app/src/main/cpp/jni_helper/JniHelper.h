/*
 * Copyright 2017 Google Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

#ifndef SAMBADOCUMENTSPROVIDER_JNI_HELPER_H
#define SAMBADOCUMENTSPROVIDER_JNI_HELPER_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jlong JNICALL
    Java_com_google_android_sambadocumentsprovider_nativefacade_NativeSambaFacade_nativeInit(
    JNIEnv *env, jobject instance, jboolean debug, jlong cachePointer);

JNIEXPORT void JNICALL
    Java_com_google_android_sambadocumentsprovider_nativefacade_NativeSambaFacade_nativeDestroy(
    JNIEnv *env, jobject instance, jlong pointer);

JNIEXPORT jint JNICALL
    Java_com_google_android_sambadocumentsprovider_nativefacade_NativeSambaFacade_openDir(
    JNIEnv *env, jobject instance, jlong pointer, jstring uri_);

JNIEXPORT jobject JNICALL
    Java_com_google_android_sambadocumentsprovider_nativefacade_NativeSambaFacade_stat(
    JNIEnv *env, jobject instance, jlong pointer, jstring uri_);

JNIEXPORT void JNICALL
    Java_com_google_android_sambadocumentsprovider_nativefacade_NativeSambaFacade_createFile(
    JNIEnv *env, jobject instance, jlong pointer, jstring uri_);

JNIEXPORT void JNICALL
    Java_com_google_android_sambadocumentsprovider_nativefacade_NativeSambaFacade_mkdir(
    JNIEnv *env, jobject instance, jlong pointer, jstring uri_);

JNIEXPORT void JNICALL
    Java_com_google_android_sambadocumentsprovider_nativefacade_NativeSambaFacade_rename(
    JNIEnv *env, jobject instance, jlong pointer, jstring uri_, jstring nuri_);

JNIEXPORT void JNICALL
    Java_com_google_android_sambadocumentsprovider_nativefacade_NativeSambaFacade_unlink(
    JNIEnv *env, jobject instance, jlong pointer, jstring uri_);

JNIEXPORT void JNICALL
    Java_com_google_android_sambadocumentsprovider_nativefacade_NativeSambaFacade_rmdir(
    JNIEnv *env, jobject instance, jlong pointer, jstring uri_);

JNIEXPORT jint JNICALL
    Java_com_google_android_sambadocumentsprovider_nativefacade_NativeSambaFacade_openFile(
    JNIEnv *env, jobject instance, jlong pointer, jstring uri_, jstring mode_);

JNIEXPORT jobject JNICALL
    Java_com_google_android_sambadocumentsprovider_nativefacade_SambaDir_readDir(
    JNIEnv *env, jobject instance, jlong pointer, jint fd);

JNIEXPORT void JNICALL
    Java_com_google_android_sambadocumentsprovider_nativefacade_SambaDir_close(
    JNIEnv *env, jobject instance, jlong pointer, jint fd);

JNIEXPORT jlong JNICALL
    Java_com_google_android_sambadocumentsprovider_nativefacade_SambaFile_read(
    JNIEnv *env, jobject instance, jlong pointer, jint fd, jobject buffer, jint maxlen);

JNIEXPORT jlong JNICALL
    Java_com_google_android_sambadocumentsprovider_nativefacade_SambaFile_seek(
    JNIEnv *env, jobject instance, jlong pointer, jint fd, jlong offset, jint whence);

JNIEXPORT jobject JNICALL
    Java_com_google_android_sambadocumentsprovider_nativefacade_SambaFile_fstat(
    JNIEnv *env, jobject instance, jlong pointer, jint fd);

JNIEXPORT jlong JNICALL
    Java_com_google_android_sambadocumentsprovider_nativefacade_SambaFile_write(
    JNIEnv *env, jobject instance, jlong pointer, jint fd, jobject buffer, jint length);

JNIEXPORT void JNICALL
    Java_com_google_android_sambadocumentsprovider_nativefacade_SambaFile_close(
    JNIEnv *env, jobject instance, jlong pointer, jint fd);

JNIEXPORT jlong JNICALL
    Java_com_google_android_sambadocumentsprovider_nativefacade_NativeCredentialCache_nativeInit(
    JNIEnv *env, jobject instance);

JNIEXPORT void JNICALL
    Java_com_google_android_sambadocumentsprovider_nativefacade_NativeCredentialCache_putCredential(
    JNIEnv *env, jobject instance, jlong pointer,
    jstring uri_, jstring workgroup_, jstring username_, jstring password_);

JNIEXPORT void JNICALL
    Java_com_google_android_sambadocumentsprovider_nativefacade_NativeCredentialCache_removeCredential(
    JNIEnv *env, jobject instance, jlong pointer, jstring uri_);

JNIEXPORT void JNICALL
    Java_com_google_android_sambadocumentsprovider_SambaConfiguration_setEnv(
    JNIEnv *env, jobject instance, jstring var_, jstring value_);

#ifdef __cplusplus
}
#endif

#endif //SAMBADOCUMENTSPROVIDER_JNI_HELPER_H
