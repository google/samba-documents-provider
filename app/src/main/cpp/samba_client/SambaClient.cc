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

#include "logger/logger.h"
#include "SambaClient.h"
#include "credential_cache/CredentialCache.h"

#include <stdlib.h>
#include <string>

#define TAG "NativeSambaClient"

namespace SambaClient {

const CredentialCache *credentialCache_;

SambaClient::~SambaClient() {
  LOGD(TAG, "Destroying SambaClient.");
  if (sambaContext && smbc_free_context(sambaContext, true)) {
    LOGE(TAG, "Failed to free Samba context. Exit to avoid leaking.");
    exit(1);
  }
  sambaContext = NULL;
  LOGD(TAG, "Destroyed SambaClient.");
}

bool SambaClient::Init(const bool debug, const CredentialCache *credentialCache) {
  LOGD(TAG, "Initializing SambaClient. Debug: %d CredentialCache: %x HOME: %s",
       debug, credentialCache, getenv("HOME"));

  sambaContext = smbc_new_context();
  if (!sambaContext) {
    LOGE(TAG, "Failed to create a Samba context.");
    return false;
  }

  LOGD(TAG, "Setting debug level to %d.", debug);
  smbc_setDebug(sambaContext, debug);

  LOGD(TAG, "Setting up auth callback.");
  smbc_setFunctionAuthData(sambaContext, GetAuthData);
  smbc_setOptionUseKerberos(sambaContext, true);
  smbc_setOptionFallbackAfterKerberos(sambaContext, true);

  LOGD(TAG, "Initializing Samba context.");
  if (!smbc_init_context(sambaContext)) {
    LOGE(TAG, "Failed to initialize Samba context.");
    smbc_free_context(sambaContext, 0);
    return false;
  }

  LOGD(TAG, "Setting Samba context.");
  smbc_set_context(sambaContext);

  LOGD(TAG, "Set up Samba context.");

  credentialCache_ = credentialCache;
  return true;
}

void SambaClient::GetAuthData(const char *server,
                                const char *share,
                                char *workgroup,
                                int maxLenWorkgroup,
                                char *username,
                                int maxLenUsername,
                                char *password,
                                int maxLenPassword) {

  LOGV(TAG, "Requesting authentication data for server: %s and share: %s.",
                      server, share);
  const std::string key = "smb://" + std::string(server) + "/" + share;
  const struct CredentialTuple tuple = credentialCache_->get(key);

  if ((tuple.workgroup.length() + 1 > maxLenWorkgroup)
      || (tuple.username.length() + 1 > maxLenUsername)
      || (tuple.password.length() + 1 > maxLenPassword)) {
    LOGE(TAG, "Credential buffer is too small for input."
        "Ignore auth request for server %s and share %s.", server, share);
    return;
  }

  strncpy(workgroup, tuple.workgroup.c_str(), tuple.workgroup.length());
  workgroup[tuple.workgroup.length()] = '\0';

  strncpy(username, tuple.username.c_str(), tuple.username.length());
  username[tuple.username.length()] = '\0';

  strncpy(password, tuple.password.c_str(), tuple.password.length());
  password[tuple.password.length()] = '\0';
}

static const char* getTypeName(unsigned int smbc_type) {
  switch (smbc_type) {
    case SMBC_WORKGROUP:
      return "WORKGROUP";
    case SMBC_SERVER:
      return "SERVER";
    case SMBC_FILE_SHARE:
      return "FILE_SHARE";
    case SMBC_PRINTER_SHARE:
      return "PRINTER_SHARE";
    case SMBC_COMMS_SHARE:
      return "PRINTER_SHARE";
    case SMBC_IPC_SHARE:
      return "IPC_SHARE";
    case SMBC_DIR:
      return "DIR";
    case SMBC_FILE:
      return "FILE";
    case SMBC_LINK:
      return "LINK";
    default:
      return "UNKNOWN";
  }
}

int
SambaClient::OpenDir(const char *url) {
  LOGD(TAG, "Opening dir at %s.", url);
  const int fd = smbc_opendir(url);
  if (fd < 0) {
    int err = errno;
    LOGE(TAG, "Failed to open dir at %s. Errno: %x", url, err);
    return -err;
  }

  return fd;
}

int
SambaClient::ReadDir(const int dh, const struct smbc_dirent ** dirent) {
  LOGD(TAG, "Reading dir for %x.", dh);
  *dirent = smbc_readdir(dh);
  if (*dirent == NULL) {
    LOGV(TAG, "Finished reading dir ent for %x.", dh);
  } else {
    LOGV(TAG, "Found entry name: %s, comment: %s, type: %s.",
         (*dirent)->name, (*dirent)->comment, getTypeName((*dirent)->smbc_type));
  }
  return 0;
}

int
SambaClient::CloseDir(const int dh) {
  LOGD(TAG, "Close dir for %x.", dh);
  const int ret = smbc_closedir(dh);

  if (ret) {
    int err = errno;
    LOGW(TAG, "Failed to close dir with dh %x. Errno: %x.", dh, err);
    return -err;
  }

  return 0;
}

int
SambaClient::Fstat(const int fd, struct stat * const st) {
  LOGD(TAG, "Getting stat for %x.", fd);
  int result = smbc_fstat(fd, st);
  if (result < 0) {
    int err = errno;
    LOGE(TAG, "Failed to obtain stat for %x. Errno: %x.", fd, err);
    return -err;
  }
  LOGV(TAG, "Got stat for %x.", fd);
  return 0;
}

int
SambaClient::Stat(const char *url, struct stat * const st) {
  LOGD(TAG, "Getting stat for %s.", url);
  int result = smbc_stat(url, st);
  if (result < 0) {
    int err = errno;
    LOGE(TAG, "Failed to obtain stat for %s. Errno: %x.", url, err);
    return -err;
  }
  LOGV(TAG, "Got stat for %s.", url);
  return 0;
}

int
SambaClient::CreateFile(const char *url) {
  LOGD(TAG, "Creating a file at %s.", url);
  int fd = smbc_creat(url, 0755);
  if (fd < 0) {
    int err = errno;
    LOGE(TAG, "Failed to create a file at %s. Errno: %x.", url, err);
    return -err;
  }

  if (smbc_close(fd) < 0) {
    LOGW(TAG, "Failed to close the created file at %s.", url);
  }
  return 0;
}

int
SambaClient::Mkdir(const char *url) {
  LOGD(TAG, "Making dir at %s.", url);
  int result = smbc_mkdir(url, 0755);
  if (result < 0) {
    int err = errno;
    LOGE(TAG, "Failed to make dir at %s. Errno: %x.", url, err);
    return -err;
  }

  return result;
}

int
SambaClient::Rename(const char *url, const char *nurl) {
  LOGD(TAG, "Renaming %s to %s.", url, nurl);
  int result = smbc_rename(url, nurl);
  if (result < 0) {
    int err = errno;
    LOGE(TAG, "Failed to rename %s to %s. Errno: %x.", url, nurl, err);
    return -err;
  }
  return result;
}

int
SambaClient::Unlink(const char *url) {
  LOGD(TAG, "Unlinking %s.", url);
  int result = smbc_unlink(url);
  if (result < 0) {
    int err = errno;
    LOGE(TAG, "Failed to unlink %s. Errno: %x.", url, err);
    return -err;
  }
  return result;
}

int
SambaClient::Rmdir(const char *url) {
  LOGD(TAG, "Removing dir at %s.", url);
  int result = smbc_rmdir(url);
  if (result < 0) {
    int err = errno;
    LOGE(TAG, "Failed to remove dir at %s. Errno: %x.", url, err);
    return -err;
  }
  return result;
}

int SambaClient::OpenFile(const char *url, const int flag, const mode_t mode) {
  LOGD(TAG, "Opening file at %s with flag %x.", url, flag);
  int fd = smbc_open(url, flag, mode);
  if (fd < 0) {
    int err = errno;
    LOGE(TAG, "Failed to open file at %s. Errno: %x", url, err);
    return -err;
  } else {
    LOGV(TAG, "Opened file at %s with fd %x.", url, fd);
  }
  return fd;
}

off_t
SambaClient::SeekFile(const int fd, const off_t offset, const int whence) {
  LOGV(TAG, "Set offset to %x for file with fd %x", offset, fd);

  off_t result = smbc_lseek(fd, offset, whence);
  if (result < 0) {
    int err = errno;
    LOGE(TAG, "Failed to seek in file %x. Errno: %x", fd, err);
    return -err;
  }

  return result;
}

ssize_t
SambaClient::ReadFile(const int fd, void *buffer, const size_t maxlen) {
  LOGV(TAG, "Reading max %lu bytes from file with fd %x", maxlen, fd);
  const ssize_t size = smbc_read(fd, buffer, maxlen);
  if (size < 0) {
    int err = errno;
    LOGE(TAG, "Failed to read file with fd %x. Errno: %x", fd, err);
    return -err;
  } else {
    LOGV(TAG, "Read %ld bytes.", size);
  }
  return size;
}

ssize_t
SambaClient::WriteFile(const int fd, void *buffer, const size_t length) {
  LOGV(TAG, "Writing %lu bytes to file with fd %x.", length, fd);
  const ssize_t size = smbc_write(fd, buffer, length);
  if (size < 0) {
    int err = errno;
    LOGE(TAG, "Failed to write file with fd %x. Errno: %x", fd, err);
    return -err;
  } else {
    LOGV(TAG, "Wrote %ld bytes.", size);
  }
  return size;
}

int SambaClient::CloseFile(const int fd) {
  LOGD(TAG, "Closing file with fd: %x", fd);
  int result = smbc_close(fd);
  if (result < 0) {
    int err = errno;
    LOGE(TAG, "Failed to close file with fd: %x with errno: %x", fd, err);
    return -err;
  }
  return 0;
}

}