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

#include "JavaClassCache.h"

namespace SambaClient {
jclass JavaClassCache::get(JNIEnv *env, const char *name_) {
  std::string name(name_);
  jclass &value = cache_[name];
  if (value == NULL) {
    jclass localRef = env->FindClass(name_);
    if (localRef == NULL) {
      return NULL;
    }
    value = reinterpret_cast<jclass>(env->NewGlobalRef(localRef));
  }
  return value;
}
}
