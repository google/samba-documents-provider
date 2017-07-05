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

package com.google.android.sambadocumentsprovider.nativefacade;

import android.os.Looper;
import android.os.Message;
import android.os.storage.StorageManager;

import java.io.IOException;

/**
 * Created by rthakohov on 7/4/17.
 */

public class SambaProxyFacadeClient extends SambaFacadeClient {

    SambaProxyFacadeClient(Looper looper, SmbClient clientImpl) {
        super(looper, clientImpl);
    }

    @Override
    protected SmbFile initSambaFileClient(Looper looper, SambaFile sambaFile) {
        return new SambaProxyFileClient(looper, sambaFile, sambaFile.mSize, sambaFile.mUri);
    }

    @Override
    protected void processMessage(Message msg) {
        mHandler.handleMessage(msg);
    }
}
