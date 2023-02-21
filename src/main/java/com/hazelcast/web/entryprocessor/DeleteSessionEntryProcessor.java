/*
 * Copyright 2020 Hazelcast Inc.
 *
 * Licensed under the Hazelcast Community License (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://hazelcast.com/hazelcast-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.hazelcast.web.entryprocessor;

import com.hazelcast.map.EntryProcessor;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.hazelcast.web.SessionState;
import com.hazelcast.web.WebDataSerializerHook;

import java.io.IOException;
import java.util.Map;

/**
 * Entry processor which removes SessionState values if
 * invalidate is true. See DeleteSessionEntryProcessor.process
 */

public final class DeleteSessionEntryProcessor
        implements EntryProcessor<String, SessionState, Boolean>, IdentifiedDataSerializable {

    private boolean invalidate;
    private boolean removed;

    public DeleteSessionEntryProcessor(boolean invalidate) {
        this.invalidate = invalidate;
    }

    public DeleteSessionEntryProcessor() {
    }

    @Override
    public int getFactoryId() {
        return WebDataSerializerHook.F_ID;
    }

    @Override
    public int getClassId() {
        return WebDataSerializerHook.SESSION_DELETE;
    }

    @Override
    public Boolean process(Map.Entry<String, SessionState> entry) {
        SessionState sessionState = entry.getValue();
        if (sessionState == null) {
            return Boolean.FALSE;
        }

        if (invalidate) {
            entry.setValue(null);
            removed = true;
        } else {
            entry.setValue(sessionState);
        }
        return Boolean.TRUE;
    }

    @Override
    public void writeData(ObjectDataOutput out) throws IOException {
        out.writeBoolean(invalidate);
    }

    @Override
    public void readData(ObjectDataInput in) throws IOException {
        invalidate = in.readBoolean();
    }
}
