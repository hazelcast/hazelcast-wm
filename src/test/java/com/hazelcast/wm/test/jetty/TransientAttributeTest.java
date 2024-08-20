package com.hazelcast.wm.test.jetty;

import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.QuickTest;
import com.hazelcast.wm.test.AbstractWebFilterTest;
import com.hazelcast.wm.test.ServletContainer;
import com.hazelcast.wm.test.jetty.JettyServer;
import org.apache.http.client.CookieStore;
import org.apache.http.impl.client.BasicCookieStore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(HazelcastSerialClassRunner.class)
@Category(QuickTest.class)
public class TransientAttributeTest extends AbstractWebFilterTest {

    public TransientAttributeTest() {
        super("node1-node-deferred.xml", "node2-node-deferred.xml");
    }

    @Test
    public void testExcludeTransientAttributesFromCluster() throws Exception {
        CookieStore cookieStore = new BasicCookieStore();
        assertEquals("true", executeRequest("write", serverPort1, cookieStore));
        assertEquals("value", executeRequest("read", serverPort2, cookieStore));
        assertEquals("true", executeRequest("writeTransient", serverPort1, cookieStore));
        assertEquals("null", executeRequest("readTransient", serverPort2, cookieStore));
    }

    @Test
    public void test_issue_62() throws Exception {
        // Reproducer case for the issue#62.
        CookieStore cookieStore = new BasicCookieStore();
        assertEquals("null", executeRequest("readTransient", serverPort1, cookieStore));
        assertEquals("true", executeRequest("writeTransient", serverPort1, cookieStore));
        assertEquals("value", executeRequest("readTransient", serverPort1, cookieStore));
        assertEquals("null", executeRequest("readTransient", serverPort2, cookieStore));
    }

    @Override
    public ServletContainer getServletContainer(int port, String sourceDir, String serverXml) throws Exception {
        return new JettyServer(port, sourceDir, serverXml);
    }

}
