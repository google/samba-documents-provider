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

#include <vector>

#include "JniHelper.h"

#include "JavaClassCache.h"
#include "JniCallback.h"
#include "logger/logger.h"
#include "samba_client/SambaClient.h"
#include "credential_cache/CredentialCache.h"

#include <stdlib.h>

#define CLASS_PREFIX "com/google/android/sambadocumentsprovider"

#define TAG "JniHelper"

namespace {
SambaClient::JavaClassCache classCache_;
}

jlong
Java_com_google_android_sambadocumentsprovider_nativefacade_NativeSambaFacade_nativeInit(
    JNIEnv *env, jobject instance, jboolean debug, jlong cachePointer) {
  SambaClient::SambaClient
      *client = new SambaClient::SambaClient();

  const SambaClient::CredentialCache *cache =
      reinterpret_cast<SambaClient::CredentialCache *>(cachePointer);
  if (client->Init(debug, cache)) {
    // Successfully initialized.
    return reinterpret_cast<jlong>(client);
  } else {
    // Something wrong with the initialization.
    LOGE(TAG, "Native Samba client failed to initialize.");
    delete client;
    return 0L;
  }
}

void
Java_com_google_android_sambadocumentsprovider_nativefacade_NativeSambaFacade_nativeDestroy(
    JNIEnv *env, jobject instance, jlong pointer) {
  SambaClient::SambaClient *client=
      reinterpret_cast<SambaClient::SambaClient *>(pointer);

  delete client;
}

static jobject
create_directory_entry(JNIEnv* env, const struct smbc_dirent &ent) {
  // Only initialize these variables once to avoid costly calls into JNIEnv.
  static const jclass dirEntryClass =
      classCache_.get(env, CLASS_PREFIX "/base/DirectoryEntry");
  static const jmethodID dirEntryConstructor =
      env->GetMethodID(dirEntryClass,
                       "<init>",
                       "(ILjava/lang/String;Ljava/lang/String;)V");

  jobject entry = NULL;

  const jstring comment = env->NewStringUTF(ent.comment);
  if (comment == NULL) {
    return NULL;
  }
  const jstring name = env->NewStringUTF(ent.name);
  if (name == NULL) {
    goto bail;
  }

  entry = env->NewObject(dirEntryClass,
                         dirEntryConstructor,
                         ent.smbc_type,
                         comment,
                         name);

  env->DeleteLocalRef(name);
  bail:
  env->DeleteLocalRef(comment);

  return entry;
}

static jobject create_array_list(JNIEnv* env) {
  static const jclass arrayListClass =
      classCache_.get(env, "java/util/ArrayList");
  static const jmethodID arrayListConstructor =
      env->GetMethodID(arrayListClass, "<init>", "()V");

  return env->NewObject(arrayListClass, arrayListConstructor);
}

static void
add_object_to_array_list(JNIEnv *env, jobject arrayList, jobject obj) {
  static const jclass arrayListClass =
      classCache_.get(env, "java/util/ArrayList");
  static const jmethodID addMethod =
      env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

  env->CallBooleanMethod(arrayList, addMethod, obj);
}

static int add_dir_entry_to_array_list(
    JniContext<jobject> context, struct smbc_dirent* dirent) {
  jobject entry = create_directory_entry(context.env, *dirent);
  if (entry == NULL) {
    return -1;
  }
  add_object_to_array_list(context.env, context.instance, entry);
  if (context.env->ExceptionCheck()) {
    return -1;
  }
  // We're done with this entry, remove local ref to avoid leak.
  context.env->DeleteLocalRef(entry);
  return 0;
}

static void
throw_new_file_not_found_exception(JNIEnv *env, const char *fmt, ...) {
  char message[256];
  va_list args;
  va_start(args, fmt);
  vsnprintf(message, sizeof(message), fmt, args);
  va_end(args);

  static const jclass fileNotFoundExceptionClass =
      classCache_.get(env, "java/io/FileNotFoundException");
  env->ThrowNew(fileNotFoundExceptionClass, message);
}

static void
throw_new_errno_exception(JNIEnv *env, const char *functionName_, int err) {
  static const jclass errnoExceptionClass =
      classCache_.get(env, "android/system/ErrnoException");
  static const jmethodID errnoExceptionConstructor =
      env->GetMethodID(errnoExceptionClass, "<init>", "(Ljava/lang/String;I)V");

  jstring functionName = env->NewStringUTF(functionName_);
  if (functionName == NULL) {
    return;
  }
  jthrowable errnoException =
      reinterpret_cast<jthrowable>(
          env->NewObject(errnoExceptionClass, errnoExceptionConstructor,
                         functionName, err));
  if (errnoException == NULL) {
    return;
  }
  env->Throw(errnoException);
}

static void
throw_new_auth_failed_exception(JNIEnv* env) {
  static const jclass authFailedExceptionClass =
      classCache_.get(env, CLASS_PREFIX "/base/AuthFailedException");
  static const jmethodID authFailedExceptionConstructor =
      env->GetMethodID(
          authFailedExceptionClass, "<init>", "()V");

  jthrowable authFailedException =
      reinterpret_cast<jthrowable>(
          env->NewObject(
              authFailedExceptionClass,
              authFailedExceptionConstructor));
  if (authFailedException == NULL) {
    return;
  }
  env->Throw(authFailedException);
}

jobject
Java_com_google_android_sambadocumentsprovider_nativefacade_NativeSambaFacade_readDir(
    JNIEnv *env, jobject instance, jlong pointer, jstring uri_) {
  const char *uri = env->GetStringUTFChars(uri_, 0);
  if (uri == NULL) {
    return NULL;
  }

  jobject arrayList = create_array_list(env);
  if (arrayList == NULL) {
    // Java exception happened.
    env->ReleaseStringUTFChars(uri_, uri);
    return NULL;
  }

  JniContext<jobject> context(env, arrayList);
  SambaClient::SambaClient *client =
      reinterpret_cast<SambaClient::SambaClient *>(pointer);
  int result = client->ReadDir(
      uri,
      JniCallback<jobject, struct smbc_dirent*>(
          context, add_dir_entry_to_array_list));

  if (env->ExceptionCheck()) {
    // Java exception happened.
    goto bail;
  }

  if (result < 0) {
    int err = -result;
    switch (err) {
      case ENODEV:
      case ENOENT:
        throw_new_file_not_found_exception(
            env, "Directory at %s can't be found.", uri);
        break;
      case EACCES:
      case EPERM:
        LOGW(TAG, "No access to directory at %s.", uri);
        throw_new_auth_failed_exception(env);
        break;
      default:
        throw_new_errno_exception(env, "readDir", err);
    }
  }

  bail:
  env->ReleaseStringUTFChars(uri_, uri);

  return arrayList;
}

static jobject create_structstat(JNIEnv *env, const struct stat &st) {
  static const jclass structStatClass =
      classCache_.get(env, "android/system/StructStat");
  static const jmethodID structStatConstructor =
      env->GetMethodID(structStatClass, "<init>", "(JJIJIIJJJJJJJ)V");

  return env->NewObject(
      structStatClass,
      structStatConstructor,
      static_cast<jlong>(st.st_dev),
      static_cast<jlong>(st.st_ino),
      static_cast<jint>(st.st_mode),
      static_cast<jlong>(st.st_nlink),
      static_cast<jint>(st.st_uid),
      static_cast<jint>(st.st_gid),
      static_cast<jlong>(st.st_rdev),
      static_cast<jlong>(st.st_size),
      static_cast<jlong>(st.st_atime),
      static_cast<jlong>(st.st_mtime),
      static_cast<jlong>(st.st_ctime),
      static_cast<jlong>(st.st_blksize),
      static_cast<jlong>(st.st_blocks));
}

jobject
Java_com_google_android_sambadocumentsprovider_nativefacade_NativeSambaFacade_stat(
    JNIEnv *env, jobject instance, jlong pointer, jstring uri_) {
  const char *uri = env->GetStringUTFChars(uri_, 0);
  if (uri == NULL) {
    return NULL;
  }

  SambaClient::SambaClient *client =
      reinterpret_cast<SambaClient::SambaClient*>(pointer);

  jobject stat = NULL;

  struct stat st;
  int result = client->Stat(uri, &st);
  if (result < 0) {
    int err = -result;
    switch (err) {
      case ENODEV:
      case ENOENT:
        throw_new_file_not_found_exception(env, "Can't find %s", uri);
        break;
      case EACCES:
        LOGW(TAG, "No access to %s.", uri);
        throw_new_auth_failed_exception(env);
        break;
      default:
        throw_new_errno_exception(env, "stat", err);
    }

    goto bail;
  }

  stat = create_structstat(env, st);

  bail:
  env->ReleaseStringUTFChars(uri_, uri);

  return stat;
}

void
Java_com_google_android_sambadocumentsprovider_nativefacade_NativeSambaFacade_createFile(
    JNIEnv *env, jobject instance, jlong pointer, jstring uri_) {
  const char *uri = env->GetStringUTFChars(uri_, 0);
  if (uri == NULL) {
    return;
  }

  SambaClient::SambaClient *client =
      reinterpret_cast<SambaClient::SambaClient*>(pointer);
  int result = client->CreateFile(uri);
  if (result < 0) {
    int err = -result;
    switch(err) {
      case ENODEV:
      case ENOENT:
        throw_new_file_not_found_exception(
            env, "Missing parent folders of %s", uri);
        break;
      case EACCES:
        LOGW(TAG, "No access to %s.", uri);
        throw_new_auth_failed_exception(env);
        break;
      default:
        throw_new_errno_exception(env, "createFile", err);
    }
  }

  env->ReleaseStringUTFChars(uri_, uri);
}

void
Java_com_google_android_sambadocumentsprovider_nativefacade_NativeSambaFacade_mkdir(
    JNIEnv *env, jobject instance, jlong pointer, jstring uri_) {
  const char *uri = env->GetStringUTFChars(uri_, 0);
  if (uri == NULL) {
    return;
  }

  SambaClient::SambaClient *client =
      reinterpret_cast<SambaClient::SambaClient*>(pointer);
  int result = client->Mkdir(uri);
  if (result < 0) {
    int err = -result;
    switch(err) {
      case ENODEV:
      case ENOENT:
        throw_new_file_not_found_exception(
            env, "Missing parent folders of %s", uri);
        break;
      case EACCES:
        // TODO: Add authentication callback here.
        LOGD(TAG, "No access to %s.", uri);
        throw_new_auth_failed_exception(env);
        break;
      default:
        throw_new_errno_exception(env, "mkdir", err);
    }
  }

  env->ReleaseStringUTFChars(uri_, uri);
}

void
Java_com_google_android_sambadocumentsprovider_nativefacade_NativeSambaFacade_rename(
    JNIEnv *env, jobject instance, jlong pointer, jstring uri_, jstring nuri_) {
  const char *uri = env->GetStringUTFChars(uri_, 0);
  if (uri == NULL) {
    return;
  }
  const char *nuri = env->GetStringUTFChars(nuri_, 0);
  if (nuri == NULL) {
    env->ReleaseStringUTFChars(uri_, uri);
    return;
  }

  SambaClient::SambaClient *client =
      reinterpret_cast<SambaClient::SambaClient*>(pointer);
  int result = client->Rename(uri, nuri);
  if (result < 0) {
    int err = -result;
    switch(err) {
      case ENODEV:
      case ENOENT:
        throw_new_file_not_found_exception(
            env, "%s is not found, or parent of %s is not found.", uri, nuri);
        break;
      case EACCES:
      case EPERM:
        LOGD(TAG, "No access to either %s or %s.", uri, nuri);
        throw_new_auth_failed_exception(env);
        break;
      default:
        throw_new_errno_exception(env, "rename", err);
    }
  }

  env->ReleaseStringUTFChars(nuri_, nuri);
  env->ReleaseStringUTFChars(uri_, uri);
}

void
Java_com_google_android_sambadocumentsprovider_nativefacade_NativeSambaFacade_unlink(
    JNIEnv *env, jobject instance, jlong pointer, jstring uri_) {
  const char *uri = env->GetStringUTFChars(uri_, 0);
  if (uri == NULL) {
    return;
  }

  SambaClient::SambaClient *client =
      reinterpret_cast<SambaClient::SambaClient*>(pointer);
  int result = client->Unlink(uri);
  if (result < 0) {
    int err = -result;
    switch (err) {
      case ENODEV:
      case ENOENT:
        throw_new_file_not_found_exception(env, "%s is not found.", uri);
        break;
      case EACCES:
      case EPERM:
        LOGD(TAG, "No access to %s.", uri);
        throw_new_auth_failed_exception(env);
        break;
      default:
        throw_new_errno_exception(env, "unlink", err);
    }
  }

  env->ReleaseStringUTFChars(uri_, uri);
}

void
Java_com_google_android_sambadocumentsprovider_nativefacade_NativeSambaFacade_rmdir(
    JNIEnv *env, jobject instance, jlong pointer, jstring uri_) {
  const char *uri = env->GetStringUTFChars(uri_, 0);
  if (uri == NULL) {
    return;
  }

  SambaClient::SambaClient *client =
      reinterpret_cast<SambaClient::SambaClient*>(pointer);
  int result = client->Rmdir(uri);
  if (result < 0) {
    int err = -result;
    switch (err) {
      case ENODEV:
      case ENOENT:
        throw_new_file_not_found_exception(env, "%s is not found.", uri);
        break;
      case EACCES:
      case EPERM:
        LOGD(TAG, "No access to %s.", uri);
        throw_new_auth_failed_exception(env);
        break;
      default:
        throw_new_errno_exception(env, "rmdir", err);
    }
  }

  env->ReleaseStringUTFChars(uri_, uri);
}

jint
Java_com_google_android_sambadocumentsprovider_nativefacade_NativeSambaFacade_openFile(
    JNIEnv *env, jobject instance, jlong pointer, jstring uri_, jstring mode_) {
  int fd = -1;

  const char *uri = env->GetStringUTFChars(uri_, 0);
  if (uri == NULL) {
    return fd;
  }
  const char *mode = env->GetStringUTFChars(mode_, 0);
  if (mode == NULL) {
    env->ReleaseStringUTFChars(uri_, uri);
    return fd;
  }

  int flag = -1;
  if (mode[0] == 'r') {
    if (mode[1] == '\0') {
      flag = O_RDONLY;
    } else if (mode[1] == 'w') {
      flag = O_RDWR;
      if (mode[2] == 't' && mode[3] == '\0') {
        flag |= O_TRUNC;
      }
    }
  } else if (mode[0] == 'w') {
    flag = O_WRONLY;
    if (mode[1] == 'a') {
      flag |= O_APPEND;
    } else if (mode[1] =='\0') {
      flag |= O_TRUNC;
    }
  }

  if (flag >= 0) {
    SambaClient::SambaClient *client =
        reinterpret_cast<SambaClient::SambaClient*>(pointer);
    fd = client->OpenFile(uri, flag, 0);
  }

  if (fd < 0) {
    int err = -fd;
    switch (err) {
      case ENODEV:
      case ENOENT:
        throw_new_file_not_found_exception(
            env, "File at %s can't be found.", uri);
        break;
      case EACCES:
        LOGW(TAG, "No access to file at %s.", uri);
        throw_new_auth_failed_exception(env);
        break;
      default:
        throw_new_errno_exception(env, "openFile", err);
    }
  }

  env->ReleaseStringUTFChars(uri_, uri);
  env->ReleaseStringUTFChars(mode_, mode);

  return fd;
}

jlong Java_com_google_android_sambadocumentsprovider_nativefacade_SambaFile_read(
    JNIEnv *env,
    jobject instance,
    jlong pointer,
    jint fd,
    jobject buffer_,
    jint maxlen) {
  void *buffer = env->GetDirectBufferAddress(buffer_);

  SambaClient::SambaClient *client =
      reinterpret_cast<SambaClient::SambaClient*>(pointer);

  ssize_t size = client->ReadFile(
      fd, buffer, static_cast<const size_t>(maxlen));
  if (size < 0) {
    throw_new_errno_exception(env, "read", static_cast<int>(-size));
  }

  return size;
}

jlong Java_com_google_android_sambadocumentsprovider_nativefacade_SambaFile_write(
    JNIEnv *env,
    jobject instance,
    jlong pointer,
    jint fd,
    jobject buffer_,
    jint length) {
  void *buffer = env->GetDirectBufferAddress(buffer_);

  SambaClient::SambaClient *client =
      reinterpret_cast<SambaClient::SambaClient*>(pointer);

  ssize_t size = client->WriteFile(
      fd, buffer, static_cast<const size_t>(length));
  if (size < 0) {
    throw_new_errno_exception(env, "write", static_cast<int>(-size));
  }

  return size;
}

void Java_com_google_android_sambadocumentsprovider_nativefacade_SambaFile_close(
    JNIEnv *env, jobject instance, jlong pointer, jint fd) {
  SambaClient::SambaClient *client =
      reinterpret_cast<SambaClient::SambaClient*>(pointer);

  int result = client->CloseFile(fd);
  if (result < 0) {
    throw_new_errno_exception(env, "close", -result);
  }
}

jlong Java_com_google_android_sambadocumentsprovider_nativefacade_NativeCredentialCache_nativeInit(
    JNIEnv *env, jobject instance) {
  return reinterpret_cast<jlong>(new SambaClient::CredentialCache());
}

void Java_com_google_android_sambadocumentsprovider_nativefacade_NativeCredentialCache_putCredential(
    JNIEnv *env, jobject instance, jlong pointer,
    jstring uri_, jstring workgroup_, jstring username_, jstring password_) {

  const char *uri;
  const char *workgroup;
  const char *username;
  const char *password;
  uri = env->GetStringUTFChars(uri_, 0);
  if (uri == NULL) {
    return;
  }
  workgroup = env->GetStringUTFChars(workgroup_, 0);
  if (workgroup == NULL) {
    goto bail_workgroup;
  }
  username = env->GetStringUTFChars(username_, 0);
  if (username == NULL) {
    goto bail_username;
  }
  password = env->GetStringUTFChars(password_, 0);
  if (password == NULL) {
    goto bail_password;
  }

  {
    SambaClient::CredentialCache *cache =
        reinterpret_cast<SambaClient::CredentialCache *>(pointer);
    SambaClient::CredentialTuple tuple =
        {std::string(workgroup), std::string(username), std::string(password)};
    cache->put(uri, tuple);
  }

  env->ReleaseStringUTFChars(password_, password);
  bail_password:
  env->ReleaseStringUTFChars(username_, username);
  bail_username:
  env->ReleaseStringUTFChars(workgroup_, workgroup);
  bail_workgroup:
  env->ReleaseStringUTFChars(uri_, uri);
}

void Java_com_google_android_sambadocumentsprovider_nativefacade_NativeCredentialCache_removeCredential(
    JNIEnv *env, jobject instance, jlong pointer, jstring uri_) {
  const char *uri = env->GetStringUTFChars(uri_, 0);
  if (uri == NULL) {
    return;
  }
  SambaClient::CredentialCache *cache =
      reinterpret_cast<SambaClient::CredentialCache *>(pointer);
}

void Java_com_google_android_sambadocumentsprovider_SambaConfiguration_setEnv(
    JNIEnv *env, jobject instance, jstring var_, jstring value_) {
  const char *var = env->GetStringUTFChars(var_, 0);
  if (var == NULL) {
    return;
  }

  const char *value = env->GetStringUTFChars(value_, 0);
  if (value == NULL) {
    goto bail;
  }

  if (setenv(var, value, true) < 0) {
    throw_new_errno_exception(env, "setEnv", errno);
  }

  bail:
  env->ReleaseStringUTFChars(var_, var);
}
