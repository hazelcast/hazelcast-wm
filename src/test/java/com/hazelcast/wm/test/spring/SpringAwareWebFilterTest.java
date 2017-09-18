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

package com.hazelcast.wm.test.spring;

import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.QuickTest;
import com.hazelcast.wm.test.ServletContainer;
import com.hazelcast.wm.test.TomcatServer;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.cookie.Cookie;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.session.SessionRegistry;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@RunWith(HazelcastSerialClassRunner.class)
@Category(QuickTest.class)
public class SpringAwareWebFilterTest extends SpringAwareWebFilterTestSupport {

    @Override
    protected ServletContainer getServletContainer(int port, String sourceDir, String serverXml) throws Exception {
        return new TomcatServer(port, sourceDir, serverXml);
    }

    // https://github.com/hazelcast/hazelcast-wm/issues/47
    @Test
    public void testSessionFixationProtectionLostTomcatSessionId() throws Exception {
        // Scenario: An initial request is made to the server before authentication that creates a tomcat session ID and
        // a hazlecast session ID (e.g. a login page). Next, an authentication request is made but only the Hazelcast
        // session ID is provided. It is expected that the original hazlecast session should be destroyed.

        // Create a session so that a Tomcat and Hazelcast session ID is created
        SpringSecuritySession sss = createSession(null, this.serverPort1);

        // Remove the Tomcat session ID cookie from the request
        List<Cookie> cookies = sss.cookieStore.getCookies();
        sss.cookieStore.clear();
        for (Cookie cookie : cookies) {
            if (!SESSION_ID_COOKIE_NAME.equals(cookie.getName())) {
                sss.cookieStore.addCookie(cookie);
            }
        }

        String originalHazelcastSessionId = sss.getHazelcastSessionId();

        // Login with only the Hazelcast session ID provided
        sss = login(sss, false);

        String hazelcastSessionId = sss.getHazelcastSessionId();

        // Verify that the original hazelcast session ID was invalidated
        assertNotEquals(originalHazelcastSessionId, hazelcastSessionId);
    }

    // https://github.com/hazelcast/hazelcast-wm/issues/47
    @Test
    public void testStaleLocalCache() throws Exception {
        // Scenario: There are two server nodes (1 & 2) behind a load balancer. Each node handles a request prior to
        // authentication so that both nodes have the Hazlecast session ID cached locally against a Tomcat session ID.
        // Say node '1' performs the authentication on the login request. Node '2' should not attempt to use the
        // original unauthenticated hazelcast session that was destroyed by node '1'.

        // Create initial session on node 1
        SpringSecuritySession sss = createSession(null, this.serverPort1);

        // Get the cookies for the initial request to node 1
        Cookie node1InitialTomcatCookie = getCookie(sss, SESSION_ID_COOKIE_NAME);
        Cookie hazelcastCookiePreAuthentication = getCookie(sss, HZ_SESSION_ID_COOKIE_NAME);

        // Make a request to node 2 with the hazelcast session ID
        sss.cookieStore.clear();
        sss.cookieStore.addCookie(hazelcastCookiePreAuthentication);
        request("hello", this.serverPort2, sss.cookieStore);

        // Get the tomcat cookie for node 2
        Cookie node2InitialTomcatCookie = getCookie(sss, SESSION_ID_COOKIE_NAME);

        // Login using node 1
        sss.cookieStore.clear();
        sss.cookieStore.addCookie(hazelcastCookiePreAuthentication);
        sss.cookieStore.addCookie(node1InitialTomcatCookie);

        sss = login(sss, false);

        // Get the new hazelcast cookie
        Cookie hazelcastAuthPostAuthentication = getCookie(sss, HZ_SESSION_ID_COOKIE_NAME);

        HttpResponse node1Response = request("hello", this.serverPort1, sss.cookieStore);
        // Request should not be re-directed to login
        assertNotEquals(302, node1Response.getStatusLine().getStatusCode());

        // Make a request to node 2
        sss.cookieStore.clear();
        sss.cookieStore.addCookie(node2InitialTomcatCookie);
        sss.cookieStore.addCookie(hazelcastAuthPostAuthentication);

        HttpResponse node2Response = request("hello", this.serverPort2, sss.cookieStore);
        // Request should not be re-directed to login
        assertNotEquals(302, node2Response.getStatusLine().getStatusCode());
    }

    @Test
    public void test_issue_3049() throws Exception {
        Set<ApplicationContext> applicationContextSet =
                SpringApplicationContextProvider.getApplicationContextSet();
        Iterator<ApplicationContext> i = applicationContextSet.iterator();
        ApplicationContext applicationContext1 = i.next();
        ApplicationContext applicationContext2 = i.next();
        SessionRegistry sessionRegistry1 = applicationContext1.getBean(SessionRegistry.class);
        SessionRegistry sessionRegistry2 = applicationContext2.getBean(SessionRegistry.class);

        SpringSecuritySession sss = login(null, false);

        request("hello", serverPort1, sss.cookieStore);

        String sessionId = sss.getSessionId();
        String hazelcastSessionId = sss.getHazelcastSessionId();

        assertTrue(
            "Native session must not exist in both Spring session registry of Node-1 and Node-2 after login",
            sessionRegistry1.getSessionInformation(sessionId) == null &&
                sessionRegistry2.getSessionInformation(sessionId) == null);

        assertTrue(
            "Hazelcast session must exist locally in one of the Spring session registry of Node-1 and Node-2 after login",
            sessionRegistry1.getSessionInformation(hazelcastSessionId) != null ||
                sessionRegistry2.getSessionInformation(hazelcastSessionId) != null);

        logout(sss);

        assertTrue(
            "Native session must not exist in both Spring session registry of Node-1 and Node-2 after logout",
            sessionRegistry1.getSessionInformation(sessionId) == null &&
                sessionRegistry2.getSessionInformation(sessionId) == null);

        assertTrue(
            "Hazelcast session must not exist in both Spring session registry of Node-1 and Node-2 after logout",
            sessionRegistry1.getSessionInformation(hazelcastSessionId) == null &&
                 sessionRegistry2.getSessionInformation(hazelcastSessionId) == null);
    }

    @Test
    public void test_issue_3742() throws Exception {
        SpringSecuritySession sss = login(null, true);
        logout(sss);
        assertEquals(HttpStatus.SC_MOVED_TEMPORARILY, sss.lastResponse.getStatusLine().getStatusCode());
    }

    @Test
    public void test_issue_53() throws Exception {
        SpringSecuritySession sss = login(null, true);
        HttpResponse node2Response = request("hello", this.serverPort2, sss.cookieStore);
        // Request should not be re-directed to login
        assertNotEquals(302, node2Response.getStatusLine().getStatusCode());
    }

    @Test
    public void test_issue_53_2() throws Exception {
        SpringSecuritySession sss = login(null, true);
        logout(sss);
        login(sss, false);

        HashMap<String, String> params = new HashMap<String, String>();
        params.put("key", "someKey");
        params.put("value", "someValue");
        request(RequestType.POST, "updateAttribute", this.serverPort1, sss.cookieStore, params);

        params.remove("value");
        HttpResponse node2Response = request(RequestType.POST, "getAttribute", this.serverPort2, sss.cookieStore, params);
        // Request should not be re-directed to login
        assertNotEquals(302, node2Response.getStatusLine().getStatusCode());
        assertEquals("someValue", responseToString(node2Response));
    }

    // https://github.com/hazelcast/hazelcast-wm/issues/6
    @Test
    public void testChangeSessionIdAfterLogin() throws Exception {
        SpringSecuritySession sss = new SpringSecuritySession();
        request(RequestType.POST,
                SPRING_SECURITY_LOGIN_URL,
                serverPort1, sss.cookieStore);

        String hzSessionIdBeforeLogin = sss.getHazelcastSessionId();
        String jsessionIdBeforeLogin = sss.getSessionId();

        sss = login(sss, false);

        assertNotEquals(jsessionIdBeforeLogin, sss.getSessionId());
        assertNotEquals(hzSessionIdBeforeLogin, sss.getHazelcastSessionId());
    }

    private Cookie getCookie(final SpringSecuritySession sss, final String cookieName) {
        if (sss.cookieStore.getCookies() != null) {
            for (org.apache.http.cookie.Cookie cookie : sss.cookieStore.getCookies()) {
                if (cookie.getName().equals(cookieName)) {
                    return cookie;
                }
            }
        }
        return null;
    }

    private SpringSecuritySession createSession(SpringSecuritySession springSecuritySession, final int serverPort)
            throws Exception {
        if (springSecuritySession == null) {
            springSecuritySession = new SpringSecuritySession();
        }

        request(RequestType.POST, SPRING_SECURITY_LOGIN_URL, serverPort, springSecuritySession.cookieStore);

        return springSecuritySession;
    }
}
