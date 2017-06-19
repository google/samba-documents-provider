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

#ifndef SAMBADOCUMENTSPROVIDER_SERVERCACHE_H
#define SAMBADOCUMENTSPROVIDER_SERVERCACHE_H

#include <unordered_map>

namespace SambaClient {
struct CredentialTuple {
  std::string workgroup;
  std::string username;
  std::string password;
};

class CredentialCache {
 public:
  struct CredentialTuple get(const std::string &key) const;
  void put(const char *key, const struct CredentialTuple &tuple);
  void remove(const char *key);
 private:
  std::unordered_map<std::string, CredentialTuple> credentialMap_;
};
}

#endif //SAMBADOCUMENTSPROVIDER_SERVERCACHE_H
