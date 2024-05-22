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

package com.hazelcast.wm.test.tomcat;

import com.hazelcast.test.annotation.QuickTest;
import com.hazelcast.wm.test.DelegatedRunWith;
import com.hazelcast.wm.test.ServletContainer;
import com.hazelcast.wm.test.WebFilterSlowTests;
import com.hazelcast.wm.test.WebTestRunner;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(WebTestRunner.class)
@DelegatedRunWith(Parameterized.class)
@Category(QuickTest.class)
public class TomcatWebFilterTest extends WebFilterSlowTests {

    @Parameterized.Parameters(name = "Executing: {0}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(
                new Object[]{"node - not deferred", "node1-node.xml", "node2-node.xml"}, //
                new Object[]{"node - deferred", "node1-node-deferred.xml", "node2-node-deferred.xml"}, //
                new Object[]{"client - not deferred", "node1-client.xml", "node2-client.xml"}, //
                new Object[]{"client - deferred", "node1-client-deferred.xml", "node2-client-deferred.xml"} //
        );
    }

    public TomcatWebFilterTest(String name, String serverXml1, String serverXml2) {
        super(serverXml1,serverXml2);
    }

    @Override
    protected ServletContainer getServletContainer(int port, String sourceDir, String serverXml) throws Exception {
        return new TomcatServer(port,sourceDir,serverXml);
    }
}
