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

package com.hazelcast.web;

import com.hazelcast.internal.serialization.DataSerializerHook;
import com.hazelcast.internal.serialization.impl.FactoryIdHelper;
import com.hazelcast.nio.serialization.DataSerializableFactory;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.hazelcast.web.entryprocessor.DeleteSessionEntryProcessor;
import com.hazelcast.web.entryprocessor.GetAttributeEntryProcessor;
import com.hazelcast.web.entryprocessor.GetAttributeNamesEntryProcessor;
import com.hazelcast.web.entryprocessor.GetSessionStateEntryProcessor;
import com.hazelcast.web.entryprocessor.SessionUpdateEntryProcessor;

/**
 * WebDataSerializerHook is used to create IdentifiedDataSerializable instances of serializable classes
 * in Hazelcast Filter Based Session Replication Module
 */
public class WebDataSerializerHook implements DataSerializerHook {

    /**
     * The constant F_ID.
     */
    public static final int F_ID = FactoryIdHelper.getFactoryId(FactoryIdHelper.WEB_DS_FACTORY, F_ID_OFFSET_WEBMODULE);

    /**
     * The constant SESSION_UPDATE.
     */
    public static final int SESSION_UPDATE = 1;
    /**
     * The constant SESSION_DELETE.
     */
    public static final int SESSION_DELETE = 2;
    /**
     * The constant GET_ATTRIBUTE.
     */
    public static final int GET_ATTRIBUTE = 3;
    /**
     * The constant GET_ATTRIBUTE_NAMES.
     */
    public static final int GET_ATTRIBUTE_NAMES = 4;
    /**
     * The constant GET_SESSION_STATE.
     */
    public static final int GET_SESSION_STATE = 5;
    /**
     * The constant SESSION_STATE.
     */
    public static final int SESSION_STATE = 6;

    @Override
    public DataSerializableFactory createFactory() {
        return new DataSerializableFactory() {
            @Override
            public IdentifiedDataSerializable create(final int typeId) {
                return getIdentifiedDataSerializable(typeId);
            }
        };
    }

    private IdentifiedDataSerializable getIdentifiedDataSerializable(int typeId) {
        return switch (typeId) {
            case SESSION_UPDATE -> new SessionUpdateEntryProcessor();
            case SESSION_DELETE -> new DeleteSessionEntryProcessor();
            case GET_ATTRIBUTE -> new GetAttributeEntryProcessor();
            case GET_ATTRIBUTE_NAMES -> new GetAttributeNamesEntryProcessor();
            case GET_SESSION_STATE -> new GetSessionStateEntryProcessor();
            case SESSION_STATE -> new SessionState();
            default -> null;
        };
    }

    @Override
    public int getFactoryId() {
        return F_ID;
    }
}
