package com.hazelcast.wm.test;

import com.hazelcast.map.IMap;
import org.apache.http.client.CookieStore;
import org.apache.http.impl.client.BasicCookieStore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public abstract class KeepRemoteActiveTest extends AbstractWebFilterTest {

    String testName;

    // Both servers must use sticky sessions
    // since keep-remote-active is not needed for non-sticky sessions.
    // server1 is supposed to use keep-remote-active, server2 is not.
    protected KeepRemoteActiveTest(String serverXml1, String serverXml2) {
        super(serverXml1, serverXml2);
        testName = serverXml1;
    }

    @Test
    public void testKeepRemoteActiveWhenEnabled() throws Exception {
        // max-idle-second is 20 seconds on the map.
        IMap<String, Object> map = hz.getMap(DEFAULT_MAP_NAME);
        CookieStore cookieStore = new BasicCookieStore();
        // keep-remote-active is enabled on server1.xml
        assertEquals("true", executeRequest("write", serverPort1, cookieStore));
        assertEquals(1, map.size());

        // Consecutive get operations will fetch value from local cache.
        for (int i = 0; i < 5; i++) {
            assertEquals("value", executeRequest("read", serverPort1, cookieStore));
            Thread.sleep(5000);
        }
        // Active session must not be evicted from the cluster map.
        assertEquals(1, map.size());
    }

    @Test
    public void testKeepRemoteActiveWhenDisabled() throws Exception {
        // max-idle-second is 20 seconds on the map.
        IMap<String, Object> map = hz.getMap(DEFAULT_MAP_NAME);
        CookieStore cookieStore = new BasicCookieStore();
        // keep-remote-active is disabled on server2.xml
        assertEquals("true", executeRequest("write", serverPort2, cookieStore));
        assertEquals(1, map.size());

        // Consecutive get operations will fetch value from local cache.
        for (int i = 0; i < 5; i++) {
            assertEquals("value", executeRequest("read", serverPort2, cookieStore));
            Thread.sleep(5000);
        }
        // Active session must be evicted from the cluster map.
        assertEquals(0, map.size());
    }
}


