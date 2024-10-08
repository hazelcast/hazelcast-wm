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

package com.hazelcast.wm.test.jetty;

import com.hazelcast.map.IMap;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.QuickTest;
import com.hazelcast.wm.test.AbstractWebFilterTest;
import com.hazelcast.wm.test.DelegatedRunWith;
import com.hazelcast.wm.test.ServletContainer;
import com.hazelcast.wm.test.WebTestRunner;
import org.apache.http.client.CookieStore;
import org.apache.http.impl.client.BasicCookieStore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests to verify that sessions are correctly removed from the map when timed out by multiple nodes.
 * <p/>
 * This test is classified as "slow" because the "fastest" session expiration supported by the servlet spec is still
 * 1 minute. That means this test needs to run for close to two minutes to verify cleanup.
 *
 * @since 3.3
 */
@RunWith(WebTestRunner.class)
@DelegatedRunWith(HazelcastSerialClassRunner.class)
@Category(QuickTest.class)
public class WebFilterSessionCleanupTest extends AbstractWebFilterTest {

    public WebFilterSessionCleanupTest() {
        super("session-cleanup.xml", "session-cleanup.xml");
    }

    @Test(timeout = 130000)
    public void testSessionTimeout() throws Exception {
        IMap<String, Object> map = hz.getMap(DEFAULT_MAP_NAME);
        CookieStore cookieStore = new BasicCookieStore();

        // Write a value into the session on one server
        assertEquals("true", executeRequest("write", serverPort1, cookieStore));

        // Find the session in the map and verify that it has one reference
        String sessionId = findHazelcastSessionId(map);
        assertEquals(1, map.size());

        // We want the session lifecycles between the two servers to be offset somewhat, so wait
        // briefly and then read the session state on the second server
        assertEquals("value", executeRequest("read", serverPort2, cookieStore));

        // At this point the session should have two references, one from each server
        assertEquals(1, map.size());

        // Wait for the session to timeout on the other server, at which point it should be
        // fully removed from the map
        Thread.sleep(TimeUnit.SECONDS.toMillis(90L));
        assertTrue("Session timeout on both nodes should have removed the IMap entries", map.isEmpty());
    }

    @Override
    public ServletContainer getServletContainer(int port, String sourceDir, String serverXml) throws Exception {
        return new JettyServer(port,sourceDir,serverXml);
    }
}
