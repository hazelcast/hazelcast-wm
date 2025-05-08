/*
 * Copyright 2024 Hazelcast Inc.
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

package com.hazelcast.web;

import com.hazelcast.internal.nio.IOUtil;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Wrapper class which holds session attributes
 */

public class SessionState implements IdentifiedDataSerializable {

    @Nonnull
    private final Map<String, Data> attributes = new HashMap<>(1);

    @Override
    public int getFactoryId() {
        return WebDataSerializerHook.F_ID;
    }

    @Override
    public int getClassId() {
        return WebDataSerializerHook.SESSION_STATE;
    }

    public Map<String, Data> getAttributes() {
        return attributes;
    }

    public void setAttribute(String key, Data value) {
        attributes.put(key, value);
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
        for (int i = 0; i < attCount; i++) {
            attributes.put(in.readString(), IOUtil.readData(in));
        }
    }

    public void set(Map<String, Data> attributes) {
        requireNonNull(attributes, "attributes should not be null, please report a bug if you got this error");
        this.attributes.putAll(attributes);
    }

    @Override
    public String toString() {
        requireNonNull(attributes, "attributes should not be null, please report a bug if you got this error");
        StringBuilder sb = new StringBuilder("SessionState {");
        sb.append(", attributes=").append(attributes.size());
        for (Map.Entry<String, Data> entry : attributes.entrySet()) {
            Data data = entry.getValue();
            int len = (data == null) ? 0 : data.dataSize();
            sb.append("\n\t");
            sb.append(entry.getKey()).append("[").append(len).append("]");
        }
        sb.append("\n}");
        return sb.toString();
    }
}
