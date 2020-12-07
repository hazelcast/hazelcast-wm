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

package com.hazelcast.wm.test;

import com.hazelcast.map.IMap;
import com.hazelcast.internal.serialization.SerializationService;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.QuickTest;
import com.hazelcast.web.SessionState;
import org.apache.http.client.CookieStore;
import org.apache.http.impl.client.BasicCookieStore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.HashMap;

import static com.hazelcast.test.Accessors.getNode;
import static com.hazelcast.wm.test.AbstractWebFilterTest.RequestType.POST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Tests to basic session methods. getAttribute,setAttribute,isNew,getAttributeNames etc.
 * <p/>
 * This test is classified as "quick" because we start jetty server only once.
 *
 * @since 3.3
 */
@RunWith(HazelcastSerialClassRunner.class)
@Category(QuickTest.class)
public class WebFilterBasicTest extends AbstractWebFilterTest {

    public WebFilterBasicTest() {
        super("node1-node.xml", "node2-node.xml");
    }

    @Test(timeout = 20000)
    public void test_setAttribute() throws Exception {
        CookieStore cookieStore = new BasicCookieStore();
        executeRequest("write", serverPort1, cookieStore);
        assertEquals("value", executeRequest("read", serverPort2, cookieStore));
    }

    @Test(timeout = 20000)
    public void test_getAttribute() throws Exception {
        CookieStore cookieStore = new BasicCookieStore();
        executeRequest("write", serverPort1, cookieStore);
        assertEquals("value", executeRequest("readIfExist", serverPort2, cookieStore));
    }

    @Test(timeout = 20000)
    public void test_getAttributeNames_WhenSessionEmpty() throws Exception {
        CookieStore cookieStore = new BasicCookieStore();
        assertEquals("", executeRequest("names", serverPort1, cookieStore));
    }

    @Test(timeout = 20000)
    public void test_getAttributeNames_WhenSessionNotEmpty() throws Exception {
        CookieStore cookieStore = new BasicCookieStore();
        executeRequest("write", serverPort1, cookieStore);
        assertEquals("key", executeRequest("names", serverPort1, cookieStore));
    }

    @Test(timeout = 20000)
    public void test_removeAttribute() throws Exception {
        CookieStore cookieStore = new BasicCookieStore();
        executeRequest("write", serverPort1, cookieStore);
        executeRequest("remove", serverPort2, cookieStore);
        assertEquals("null", executeRequest("read", serverPort1, cookieStore));
    }

    @Test(timeout = 20000)
    public void test_clusterMapSize() throws Exception {
        CookieStore cookieStore = new BasicCookieStore();
        IMap<String, Object> map = hz.getMap(DEFAULT_MAP_NAME);
        executeRequest("write", serverPort1, cookieStore);
        assertEquals(1, map.size());
    }

    @Test(timeout = 20000)
    public void test_clusterMapSizeAfterRemove() throws Exception {
        CookieStore cookieStore = new BasicCookieStore();
        IMap<String, Object> map = hz.getMap(DEFAULT_MAP_NAME);
        executeRequest("write", serverPort1, cookieStore);
        executeRequest("remove", serverPort2, cookieStore);
        assertEquals(1, map.size());
    }

    @Test(timeout = 20000)
    public void test_updateAttribute() throws Exception {
        IMap<String, Object> map = hz.getMap(DEFAULT_MAP_NAME);
        CookieStore cookieStore = new BasicCookieStore();
        executeRequest("write", serverPort1, cookieStore);
        executeRequest("update", serverPort2, cookieStore);
        assertEquals("value-updated", executeRequest("read", serverPort1, cookieStore));
        String newSessionId = map.keySet().iterator().next();
        SessionState sessionState = (SessionState) map.get(newSessionId);
        SerializationService ss = getNode(hz).getSerializationService();
        assertSizeEventually(1, map);
        assertSizeEventually(1, sessionState.getAttributes());
    }

    @Test(timeout = 20000)
    public void test_getAttributeNames_AfterGetAttribute() throws Exception {
        CookieStore cookieStore = new BasicCookieStore();
        executeRequest("remove_put", serverPort1, cookieStore);
        assertEquals("", executeRequest("names", serverPort1, cookieStore));
    }

    @Test(timeout = 20000)
    public void test_invalidateSession() throws Exception {
        IMap<String, Object> map = hz.getMap(DEFAULT_MAP_NAME);
        CookieStore cookieStore = new BasicCookieStore();
        executeRequest("write", serverPort1, cookieStore);
        assertSizeEventually(1, map);
        executeRequest("invalidate", serverPort2, cookieStore);
        assertSizeEventually(0, map);
    }

    @Test(timeout = 20000)
    public void test_invalidateMultiReferenceSession() throws Exception {
        IMap<String, Object> map = hz.getMap(DEFAULT_MAP_NAME);
        CookieStore cookieStore = new BasicCookieStore();
        executeRequest("write", serverPort1, cookieStore);
        assertSizeEventually(1, map);
        executeRequest("write", serverPort2, cookieStore);
        executeRequest("invalidate", serverPort1, cookieStore);
        assertSizeEventually(0, map);
    }

    @Test(timeout = 20000)
    public void test_isNew() throws Exception {
        CookieStore cookieStore = new BasicCookieStore();
        assertEquals("true", executeRequest("isNew", serverPort1, cookieStore));
        assertEquals("false", executeRequest("isNew", serverPort1, cookieStore));
    }

    @Test(timeout = 40000)
    public void test_sessionTimeout() throws Exception {
        CookieStore cookieStore = new BasicCookieStore();
        IMap<String, Object> map = hz.getMap(DEFAULT_MAP_NAME);
        executeRequest("write", serverPort1, cookieStore);
        executeRequest("timeout", serverPort1, cookieStore);
        assertSizeEventually(0, map);
    }

    @Test(timeout = 20000)
    public void testServerRestart() throws Exception {
        CookieStore cookieStore = new BasicCookieStore();
        executeRequest("write", serverPort1, cookieStore);
        server1.restart();
        assertEquals("value", executeRequest("read", serverPort2, cookieStore));
    }

    @Test(timeout = 20000)
    public void testNoSession() throws Exception {
        CookieStore cookieStore = new BasicCookieStore();
        assertEquals("true", executeRequest("noSession", serverPort1, cookieStore));
    }

    // https://github.com/hazelcast/hazelcast-wm/issues/15
    @Test(timeout = 20000)
 	public void testInputStreamOfRequestNotConsumed() throws Exception {
        CookieStore cookieStore = new BasicCookieStore();

        HashMap<String, String> requestParameters = new HashMap<String, String>();
        requestParameters.put("email", "test@email.com");
        requestParameters.put("password", "password1");

        assertEquals("true", executeRequest(POST, "login", serverPort1, cookieStore, requestParameters));
    }

    @Test(timeout = 20000)
    public void testUseRequestParameterEnabled() throws Exception {
        String hazelcastSessionId1 = executeRequest(POST, "useRequestParameter", serverPort1,
                new BasicCookieStore());

        String hazelcastSessionId2 = executeRequest(POST, "useRequestParameter", serverPort2,
                new BasicCookieStore(), Collections.singletonMap("hazelcast.sessionId", hazelcastSessionId1));

        assertEquals(hazelcastSessionId1, hazelcastSessionId2);
    }

    @Test(timeout = 20000)
    public void testUseRequestParameterDisabled() throws Exception {
        String hazelcastSessionId1 = executeRequest(POST, "useRequestParameter", serverPort2,
                new BasicCookieStore());

        String hazelcastSessionId2 = executeRequest(POST, "useRequestParameter", serverPort1,
                new BasicCookieStore(), Collections.singletonMap("hazelcast.sessionId", hazelcastSessionId1));

        assertNotEquals(hazelcastSessionId1, hazelcastSessionId2);
    }

    @Override
    protected ServletContainer getServletContainer(int port, String sourceDir, String serverXml) throws Exception {
        return new JettyServer(port, sourceDir, serverXml);
    }
}
