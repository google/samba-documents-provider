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

#ifndef SAMBADOCUMENTSPROVIDER_SAMBAPROVIDER_H
#define SAMBADOCUMENTSPROVIDER_SAMBAPROVIDER_H

#include "samba_includes/libsmbclient.h"
#include "base/Callback.h"
#include "jni_helper/JniHelper.h"

#include <sys/types.h>
#include <vector>

namespace SambaClient {

struct CredentialCache;

class SambaClient {
 public:
  ~SambaClient();

  bool Init(const bool debug, const CredentialCache *credentialCache);

  int OpenDir(const char *url);

  int ReadDir(const int dh, const struct smbc_dirent** dirent);

  int CloseDir(const int dh);

  int Stat(const char *url, struct stat *st);

  int Fstat(const int fd, struct stat * const st);

  int CreateFile(const char *url);

  int Mkdir(const char *url);

  int Rename(const char *url, const char *nurl);

  int Unlink(const char *url);

  int Rmdir(const char *url);

  int OpenFile(const char *url, const int flag, const mode_t mode);

  ssize_t ReadFile(const int fd, void *buffer, const size_t maxlen);

  ssize_t WriteFile(const int fd, void *buffer, const size_t length);

  off_t SeekFile(const int fd, const off_t offset, const int whence);

  int CloseFile(const int fd);
 private:
  ::SMBCCTX *sambaContext = NULL;

  static void GetAuthData(const char *server,
                   const char *share,
                   char *workgroup, int maxLenWorkgroup,
                   char *username, int maxLenUsername,
                   char *password, int maxLenPassword);
};

}

#endif //SAMBADOCUMENTSPROVIDER_SAMBAPROVIDER_H
