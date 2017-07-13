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

#ifndef MYAPPLICATION_LOGGER_H
#define MYAPPLICATION_LOGGER_H

#include <android/log.h>

#define LOG(level, tag, args...) \
  __android_log_print((level), (tag), args)

#ifdef DEBUG

#define LOGV(tag, args...) LOG(ANDROID_LOG_VERBOSE, tag, args)

#define LOGD(tag, args...) LOG(ANDROID_LOG_DEBUG, tag, args)

#else // DEBUG

#define LOGV(tag, args...)

#define LOGD(tag, args...)

#endif // DEBUG

#define LOGI(tag, args...) LOG(ANDROID_LOG_INFO, tag, args)

#define LOGW(tag, args...) LOG(ANDROID_LOG_WARN, tag, args)

#define LOGE(tag, args...) LOG(ANDROID_LOG_ERROR, tag, args)

#define LOGF(tag, args...) LOG(ANDROID_LOG_FATAL, tag, args)

#endif //MYAPPLICATION_LOGGER_H
