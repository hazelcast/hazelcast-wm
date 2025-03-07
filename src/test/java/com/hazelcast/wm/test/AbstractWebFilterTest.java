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

package com.hazelcast.wm.test;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.config.FileSystemXmlConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.TestEnvironment;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.HttpEntity;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.junit.AfterClass;
import org.junit.Before;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import static org.awaitility.Awaitility.await;

public abstract class AbstractWebFilterTest extends HazelcastTestSupport {

    public enum RequestType {
        GET,
        POST
    }

    static {
        final String logging = "hazelcast.logging.type";
        if (System.getProperty(logging) == null) {
            System.setProperty(logging, "log4j2");
        }
        if (System.getProperty(TestEnvironment.HAZELCAST_TEST_USE_NETWORK) == null) {
            System.setProperty(TestEnvironment.HAZELCAST_TEST_USE_NETWORK, "false");
        }
        System.setProperty("hazelcast.phone.home.enabled", "false");
        System.setProperty("hazelcast.mancenter.enabled", "false");
        System.setProperty("hazelcast.wait.seconds.before.join", "0");
        System.setProperty("hazelcast.local.localAddress", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        // randomize multicast group...
        Random rand = new Random();
        int g1 = rand.nextInt(255);
        int g2 = rand.nextInt(255);
        int g3 = rand.nextInt(255);
        System.setProperty("hazelcast.multicast.group", "224." + g1 + "." + g2 + "." + g3);
        try {
            final URL root = new URL(TestServlet.class.getResource("/"), "../test-classes");
            URI uri = root.toURI();
            Path path = Paths.get(uri);
            Path baseDir = path.toAbsolutePath();

            sourceDir = baseDir.resolve("../../src/test/webapp").normalize().toString();
        } catch (Exception e) {
            throw new IllegalStateException("Couldn't initialize AbstractWebFilterTest", e);
        }
    }

    public static final String HAZELCAST_SESSION_ATTRIBUTE_SEPARATOR = "::hz::";

    public static final RequestType DEFAULT_REQUEST_TYPE = RequestType.GET;

    public static final String DEFAULT_MAP_NAME = "default";

    public static final Map<Class<? extends AbstractWebFilterTest>, ContainerContext> CONTAINER_CONTEXT_MAP =
            new HashMap<>();

    public static final String sourceDir;

    public String serverXml1;
    public String serverXml2;

    public int serverPort1;
    public int serverPort2;
    public ServletContainer server1;
    public ServletContainer server2;
    public volatile HazelcastInstance hz;

    public AbstractWebFilterTest(String serverXml1) {
        this.serverXml1 = serverXml1;
    }

    public AbstractWebFilterTest(String serverXml1, String serverXml2) {
        this.serverXml1 = serverXml1;
        this.serverXml2 = serverXml2;
    }

    @Before
    public void setup() throws Exception {
        ContainerContext cc = CONTAINER_CONTEXT_MAP.get(getClass());

        // if configuration is changed, we need to stop containers and cc should equals to null
        if (cc != null) {
            if (!cc.serverXml1.equals(serverXml1) || !cc.serverXml2.equals(serverXml2)) {
                cc.server1.stop();
                cc.server2.stop();
                cc.server1 = null;
                cc.server2 = null;
                cc = null;
            }
        }


        // If container is not exist yet or
        // Hazelcast instance is not active (because of such as server shutdown)
        if (cc == null) {
            // Build a new instance
            ensureInstanceIsUp();
            CONTAINER_CONTEXT_MAP.put(
                    getClass(),
                    new ContainerContext(
                            this,
                            serverXml1,
                            serverXml2,
                            serverPort1,
                            serverPort2,
                            server1,
                            server2,
                            hz));
        } else {
            // For every test method a different test class can be constructed for parallel runs by JUnit.
            // So container can exist, but configurations of current test may not be exist.
            // For this reason, we should copy container context information (such as ports, servers, ...)
            // to current test.
            cc.copyInto(this);
            // Ensure that instance is up and running
            ensureInstanceIsUp();
            // After ensuring that system is up, new containers or instance may be created.
            // So, we should copy current information from test to current context.
            cc.copyFrom(this);
        }
        // Clear map
        IMap<String, Object> map = hz.getMap(DEFAULT_MAP_NAME);
        map.clear();
    }

    public void ensureInstanceIsUp() throws Exception {
        if (isInstanceNotActive(hz)) {
            hz = Hazelcast.newHazelcastInstance(
                    new FileSystemXmlConfig(new File(sourceDir + "/WEB-INF/", "hazelcast.xml")));
        }
        if (serverXml1 != null) {
            if (server1 == null) {
                serverPort1 = availablePort();
                server1 = getServletContainer(serverPort1, sourceDir, serverXml1);
            } else if (!server1.isRunning()) {
                server1.start();
            }
        }
        if (serverXml2 != null) {
            if (server2 == null) {
                serverPort2 = availablePort();
                server2 = getServletContainer(serverPort2, sourceDir, serverXml2);
            } else if (!server2.isRunning()) {
                server2.start();
            }
        }
    }

    protected void waitForCluster(int expectedClusterSize) {
        await()
                .atMost(Duration.ofMinutes(5))
                .pollInterval(Duration.ofSeconds(1))
                .logging()
                .until(() -> hz.getCluster().getMembers().size() == expectedClusterSize);
    }

    protected void waitForCluster() {
        waitForCluster(2);
    }

    public boolean isInstanceNotActive(HazelcastInstance hz) {
        if (hz == null) {
            return true;
        }
        return !hz.getLifecycleService().isRunning();
    }

    @AfterClass
    public static void teardownClass() {
        for (Entry<Class<? extends AbstractWebFilterTest>, ContainerContext> ccEntry :
                CONTAINER_CONTEXT_MAP.entrySet()) {
            ContainerContext cc = ccEntry.getValue();
            try {
                // Stop servers
                cc.server1.stop();
                if (cc.server2 != null) {
                    cc.server2.stop();
                }
            } catch (Throwable t) {
                //noinspection CallToPrintStackTrace
                t.printStackTrace();
            }
        }
        // Shutdown all instances
        Hazelcast.shutdownAll();
        HazelcastClient.shutdownAll();
    }

    public int availablePort() {
        while (true) {
            int port = (int) (65536 * Math.random());
            try {
                ServerSocket socket = new ServerSocket(port);
                socket.close();
                return port;
            } catch (Exception ignore) {
                // try next port
            }
        }
    }

    public String findHazelcastSessionId(IMap<String, Object> map) {
        for (Entry<String, Object> entry : map.entrySet()) {
            if (!entry.getKey().contains(HAZELCAST_SESSION_ATTRIBUTE_SEPARATOR)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public String responseToString(HttpResponse response) throws IOException {
        HttpEntity entity = response.getEntity();
        return EntityUtils.toString(entity);
    }

    public String executeRequest(String context,
                                    int serverPort,
                                    CookieStore cookieStore) throws Exception {
        return responseToString(request(context, serverPort, cookieStore));
    }

    public HttpResponse request(String context,
                                   int serverPort,
                                   CookieStore cookieStore) throws Exception {
        return request(DEFAULT_REQUEST_TYPE, context, serverPort, cookieStore);
    }

    public String executeRequest(RequestType reqType,
                                    String context,
                                    int serverPort,
                                    CookieStore cookieStore) throws Exception {
        return responseToString(request(reqType, context, serverPort, cookieStore));
    }

    public HttpResponse request(RequestType reqType,
                                   String context,
                                   int serverPort,
                                   CookieStore cookieStore) throws Exception {
        return request(reqType, context, serverPort, cookieStore, Collections.emptyMap());
    }

    public String executeRequest(RequestType reqType,
                                   String context,
                                   int serverPort,
                                   CookieStore cookieStore,
                                   Map<String, String> requestParams) throws Exception {
        return responseToString(request(reqType, context, serverPort, cookieStore, requestParams));
    }

    public HttpResponse request(RequestType reqType,
                                   String context,
                                   int serverPort,
                                   CookieStore cookieStore,
                                   Map<String, String> requestParams) throws Exception {
        if (reqType == null) {
            throw new IllegalArgumentException("Request type paramater cannot be empty !");
        }
        try (var client = HttpClientBuilder.create().disableRedirectHandling().setDefaultCookieStore(cookieStore).build()) {
            HttpUriRequest request;
            switch (reqType) {
                case GET:
                    request = new HttpGet("http://localhost:" + serverPort + "/" + context);
                    break;
                case POST:
                    request = new HttpPost("http://localhost:" + serverPort + "/" + context);
                    List<NameValuePair> params = new ArrayList<>(requestParams.size());
                    for (Entry<String, String> reqParamEntry : requestParams.entrySet()) {
                        params.add(new BasicNameValuePair(reqParamEntry.getKey(), reqParamEntry.getValue()));
                    }
                    ((HttpPost)request).setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
                    break;
                default:
                    throw new IllegalArgumentException(reqType + " typed request is not supported");
            }
            return client.execute(request);
        }
    }

    public abstract ServletContainer getServletContainer(int port,
                                                            String sourceDir,
                                                            String serverXml) throws Exception;

    public String getHazelcastSessionId(CookieStore cookieStore) {
        for (Cookie cookie : cookieStore.getCookies()) {
            String name = cookie.getName();
            if ("hazelcast.sessionId".equals(name)) return cookie.getValue();
        }
        return null;
    }

    public static class ContainerContext {

        public AbstractWebFilterTest test;

        public String serverXml1;
        public String serverXml2;
        public int serverPort1;
        public int serverPort2;
        public ServletContainer server1;
        public ServletContainer server2;
        public HazelcastInstance hz;

        public ContainerContext(AbstractWebFilterTest test,
                                String serverXml1,
                                String serverXml2,
                                int serverPort1,
                                int serverPort2,
                                ServletContainer server1,
                                ServletContainer server2,
                                HazelcastInstance hz) {
            this.test = test;
            this.serverXml1 = serverXml1;
            this.serverXml2 = serverXml2;
            this.serverPort1 = serverPort1;
            this.serverPort2 = serverPort2;
            this.server1 = server1;
            this.server2 = server2;
            this.hz = hz;
        }

        public void copyInto(AbstractWebFilterTest awft) {
            awft.serverXml1 = serverXml1;
            awft.serverXml2 = serverXml2;
            awft.serverPort1 = serverPort1;
            awft.serverPort2 = serverPort2;
            awft.server1 = server1;
            awft.server2 = server2;
            awft.hz = hz;
        }

        public void copyFrom(AbstractWebFilterTest awft) {
            serverXml1 = awft.serverXml1;
            serverXml2 = awft.serverXml2;
            serverPort1 = awft.serverPort1;
            serverPort2 = awft.serverPort2;
            server1 = awft.server1;
            server2 = awft.server2;
            hz = awft.hz;
        }
    }
}
