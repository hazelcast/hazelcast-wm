/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.web;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class Utils {
    private Utils() {
    }

    static Method getChangeSessionIdMethod() {
        try {
            //noinspection JavaReflectionMemberAccess
            return HttpServletRequest.class.getMethod("changeSessionId");
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    static String invokeChangeSessionId(HttpServletRequest httpServletRequest, Method changeSessionIdMethod) {
        try {
            return ((String) changeSessionIdMethod.invoke(httpServletRequest));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not access changeSessionId method on HttpServletRequest", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Could not invoke changeSessionId method on HttpServletRequest", e);
        }
    }
}
