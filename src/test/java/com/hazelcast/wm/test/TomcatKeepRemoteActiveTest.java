package com.hazelcast.wm.test;

import com.hazelcast.test.annotation.QuickTest;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(WebTestRunner.class)
@DelegatedRunWith(Parameterized.class)
@Category(QuickTest.class)
public class TomcatKeepRemoteActiveTest extends KeepRemoteActiveTest {

    @Parameterized.Parameters(name = "Executing: {0}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(
                new Object[]{"node1-node-deferred.xml", "node2-node-deferred.xml"}, //
                new Object[]{"node1-client.xml", "node2-client.xml"} //
        );
    }

    public TomcatKeepRemoteActiveTest(String serverXml1, String serverXml2) {
        super(serverXml1,serverXml2);
    }

    @Override
    protected ServletContainer getServletContainer(int port, String sourceDir, String serverXml) throws Exception {
        return new TomcatServer(port,sourceDir,serverXml);
    }

}
