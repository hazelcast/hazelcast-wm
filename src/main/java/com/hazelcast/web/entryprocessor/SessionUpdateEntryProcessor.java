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

import com.hazelcast.internal.nio.IOUtil;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.map.EntryProcessor;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.hazelcast.web.SessionState;
import com.hazelcast.web.WebDataSerializerHook;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Entry processor which updates SessionState attributes stored in distributed map
 * If value of attribute is set to null. It is removed from SessionState.attribute map.
 * See SessionUpdateEntryProcessor.process
 */

public final class SessionUpdateEntryProcessor
        implements EntryProcessor<String, SessionState, Object>, IdentifiedDataSerializable {

    private Map<String, Data> attributes;

    public SessionUpdateEntryProcessor(int size) {
        this.attributes = new HashMap<>(size);
    }

    public SessionUpdateEntryProcessor(String key, Data value) {
        attributes = new HashMap<>(1);
        attributes.put(key, value);
    }

    public SessionUpdateEntryProcessor() {
        attributes = Collections.emptyMap();
    }

    public Map<String, Data> getAttributes() {
        return attributes;
    }

    @Override
    public int getFactoryId() {
        return WebDataSerializerHook.F_ID;
    }

    @Override
    public int getClassId() {
        return WebDataSerializerHook.SESSION_UPDATE;
    }

    @Override
    public Object process(Map.Entry<String, SessionState> entry) {
        SessionState sessionState = entry.getValue();
        if (sessionState == null) {
            sessionState = new SessionState();
        }
        for (Map.Entry<String, Data> attribute : attributes.entrySet()) {
            String name = attribute.getKey();
            Data value = attribute.getValue();
            if (value == null) {
                sessionState.getAttributes().remove(name);
            } else {
                sessionState.getAttributes().put(name, value);
            }
        }
        entry.setValue(sessionState);
        return Boolean.TRUE;
    }

    @Override
    public void writeData(ObjectDataOutput out) throws IOException {
        out.writeInt(attributes.size());
        for (Map.Entry<String, Data> entry : attributes.entrySet()) {
            out.writeString(entry.getKey());
            IOUtil.writeData(out, entry.getValue());
        }
    }

    @Override
    public void readData(ObjectDataInput in) throws IOException {
        int attCount = in.readInt();
        attributes = new HashMap<>(attCount);
        for (int i = 0; i < attCount; i++) {
            attributes.put(in.readString(), IOUtil.readData(in));
        }
    }
}
