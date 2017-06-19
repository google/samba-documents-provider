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

#ifndef SAMBADOCUMENTSPROIVDER_JNICALLBACK_H
#define SAMBADOCUMENTSPROIVDER_JNICALLBACK_H

#include "base/Callback.h"

#include <jni.h>
#include <functional>

template<typename T>
struct JniContext {
  JNIEnv * const env;
  const T &instance;
  JniContext(JNIEnv * const env, T &obj)
      : env(env), instance(obj) {}
};

template<typename T, typename... Us>
class JniCallback : public SambaClient::Callback<Us...> {
 public:
  JniCallback(
      const JniContext<T> &context, std::function<int(JniContext<T>, Us...)> callback)
      : context(context), callback(callback) {}

  JniCallback(JniCallback &) = delete;
  JniCallback(JniCallback &&) = delete;

  int operator()(Us... args) const {
    return callback(context, args...);
  }
 private:
  const JniContext<T> context;
  const std::function<int(JniContext<T>, Us...)> callback;
};

#endif //SAMBADOCUMENTSPROIVDER_JNICALLBACK_H
