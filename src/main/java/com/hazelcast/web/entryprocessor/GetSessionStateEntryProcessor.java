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
 * Entry processor which return SessionState object stored in distributed map
 */

public final class GetSessionStateEntryProcessor implements EntryProcessor<String, SessionState, SessionState>,
        IdentifiedDataSerializable {

    public GetSessionStateEntryProcessor() {
    }

    @Override
    public int getFactoryId() {
        return WebDataSerializerHook.F_ID;
    }

    @Override
    public int getClassId() {
        return WebDataSerializerHook.GET_SESSION_STATE;
    }

    @Override
    public SessionState process(Map.Entry<String, SessionState> entry) {
        SessionState sessionState = entry.getValue();
        if (sessionState == null) {
            return null;
        }
        entry.setValue(sessionState);
        return sessionState;
    }

    @Override
    public void writeData(ObjectDataOutput out) throws IOException {
    }

    @Override
    public void readData(ObjectDataInput in) throws IOException {
    }
}
