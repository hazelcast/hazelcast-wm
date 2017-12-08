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

import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.util.UuidUtil;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

import static com.hazelcast.util.StringUtil.isNullOrEmptyAfterTrim;
import static com.hazelcast.web.Utils.getChangeSessionIdMethod;
import static com.hazelcast.web.Utils.invokeChangeSessionId;

/**
 * <p>
 * Provides clustered sessions by backing session data with an {@link IMap}.
 * </p>
 * <p>
 * Using this filter requires also registering a {@link SessionListener} to provide session timeout notifications.
 * Failure to register the listener when using this filter will result in session state getting out of sync between
 * the servlet container and Hazelcast.
 * </p>
 * This filter supports the following {@code <init-param>} values:
 * <ul>
 * <li>{@code use-client}: When enabled, a {@link com.hazelcast.client.HazelcastClient HazelcastClient} is
 * used to connect to the cluster, rather than joining as a full node. (Default: {@code false})</li>
 * <li>{@code config-location}: Specifies the location of an XML configuration file that can be used to
 * initialize the {@link HazelcastInstance} (Default: None; the {@link HazelcastInstance} is initialized
 * using its own defaults)</li>
 * <li>{@code client-config-location}: Specifies the location of an XML configuration file that can be
 * used to initialize the {@link HazelcastInstance}. <i>This setting is only checked when {@code use-client}
 * is set to {@code true}.</i> (Default: Falls back on {@code config-location})</li>
 * <li>{@code instance-name}: Names the {@link HazelcastInstance}. This can be used to reference an already-
 * initialized {@link HazelcastInstance} in the same JVM (Default: The configured instance name, or a
 * generated name if the configuration does not specify a value)</li>
 * <li>{@code shutdown-on-destroy}: When enabled, shuts down the {@link HazelcastInstance} when the filter is
 * destroyed (Default: {@code true})</li>
 * <li>{@code map-name}: Names the {@link IMap} the filter should use to persist session details (Default:
 * {@code "_web_" + ServletContext.getServletContextName()}; e.g. "_web_MyApp")</li>
 * <li>{@code session-ttl-seconds}: Sets the {@link MapConfig#setMaxIdleSeconds(int)} (int) time-to-live} for
 * the {@link IMap} used to persist session details (Default: Uses the existing {@link MapConfig} setting
 * for the {@link IMap}, which defaults to infinite)</li>
 * <li>{@code sticky-session}: When enabled, optimizes {@link IMap} interactions by assuming individual sessions
 * are only used from a single node (Default: {@code true})</li>
 * <li>{@code deferred-write}: When enabled, optimizes {@link IMap} interactions by only writing session attributes
 * at the end of a request. This can yield significant performance improvements for session-heavy applications
 * (Default: {@code false})</li>
 * <li>{@code cookie-name}: Sets the name for the Hazelcast session cookie (Default: "hazelcast.sessionId")
 * <li>{@code cookie-domain}: Sets the domain for the Hazelcast session cookie (Default: {@code null})</li>
 * <li>{@code cookie-secure}: When enabled, indicates the Hazelcast session cookie should only be sent over
 * secure protocols (Default: {@code false})</li>
 * <li>{@code cookie-http-only}: When enabled, marks the Hazelcast session cookie as "HttpOnly", indicating
 * it should not be available to scripts (Default: {@code false})
 * <ul>
 * <li>{@code cookie-http-only} requires a Servlet 3.0-compatible container, such as Tomcat 7+ or Jetty 8+</li>
 * </ul>
 * </li>
 * </ul>
 */
public class WebFilter implements Filter {

    /**
     * This is prefix for hazelcast session attributes
     */
    public static final String WEB_FILTER_ATTRIBUTE_KEY = WebFilter.class.getName();

    protected static final ILogger LOGGER = Logger.getLogger(WebFilter.class);
    protected static final LocalCacheEntry NULL_ENTRY = new LocalCacheEntry(false);

    protected ServletContext servletContext;

    private final Properties properties;

    private final ConcurrentMap<String, String> originalSessions = new ConcurrentHashMap<String, String>(1000);
    private final ConcurrentMap<String, HazelcastHttpSession> sessions =
            new ConcurrentHashMap<String, HazelcastHttpSession>(1000);

    private ClusteredSessionService clusteredSessionService;

    private WebFilterConfig config;

    public WebFilter() {
        this.properties = null;
    }

    public WebFilter(Properties properties) {
        this.properties = properties;
    }

    void destroyOriginalSession(HttpSession originalSession) {
        String hazelcastSessionId = originalSessions.remove(originalSession.getId());
        if (hazelcastSessionId != null) {
            HazelcastHttpSession hazelSession = sessions.get(hazelcastSessionId);
            if (hazelSession != null) {
                destroySession(hazelSession, false);
            }
        }
    }

    public ClusteredSessionService getClusteredSessionService() {
        return clusteredSessionService;
    }

    private static String generateSessionId() {
        String id = UuidUtil.newSecureUuidString();
        StringBuilder sb = new StringBuilder("HZ");
        char[] chars = id.toCharArray();
        for (final char c : chars) {
            if (c != '-') {
                if (Character.isLetter(c)) {
                    sb.append(Character.toUpperCase(c));
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    @Override
    public final void init(final FilterConfig filterConfig) throws ServletException {
        this.config = WebFilterConfig.create(filterConfig, this.properties);
        // Register the WebFilter with the ServletContext so SessionListener can look it up. The name
        // here is WebFilter.class instead of getClass() because WebFilter can have subclasses
        servletContext = filterConfig.getServletContext();
        servletContext.setAttribute(WEB_FILTER_ATTRIBUTE_KEY, this);

        clusteredSessionService = new ClusteredSessionService(this.config);

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest(this.config.toString());
        }
    }

    private boolean sessionExistsInTheCluster(String hazelcastSessionId) {
        try {
            return hazelcastSessionId != null && clusteredSessionService.containsSession(hazelcastSessionId);
        } catch (Exception ignored) {
            return false;
        }
    }

    protected HazelcastHttpSession createNewSession(HazelcastRequestWrapper requestWrapper,
                                                    boolean create,
                                                    String existingSessionId) {
        // use existing hazelcast session id for the new session only if the session info exists in the cluster
        boolean sessionExistsInTheCluster = sessionExistsInTheCluster(existingSessionId);
        if (!create && !sessionExistsInTheCluster) {
            return null;
        }
        String id = sessionExistsInTheCluster ? existingSessionId : generateSessionId();

        if (requestWrapper.getOriginalSession(false) != null) {
            LOGGER.finest("Original session exists!!!");
        }
        HttpSession originalSession = requestWrapper.getOriginalSession(true);
        HazelcastHttpSession hazelcastSession = createHazelcastHttpSession(id, originalSession);
        if (existingSessionId == null) {
            hazelcastSession.setClusterWideNew(true);
            // If the session is being created for the first time, add its initial reference in the cluster-wide map.
        }
        updateSessionMaps(originalSession.getId(), hazelcastSession);
        addSessionCookie(requestWrapper, id);
        return hazelcastSession;
    }

    /**
     * {@code HazelcastHttpSession instance} creation is split off to a separate method to allow subclasses to return a
     * customized / extended version of {@code HazelcastHttpSession}.
     *
     * @param id              the session id
     * @param originalSession the original session
     * @return a new HazelcastHttpSession instance
     */
    protected HazelcastHttpSession createHazelcastHttpSession(String id, HttpSession originalSession) {
        return new HazelcastHttpSession(this, id, originalSession, config.isDeferredWrite(),
                config.isStickySession(), config.getTransientAttributes());
    }

    private void updateSessionMaps(String originalSessionId, HazelcastHttpSession hazelcastSession) {
        sessions.put(hazelcastSession.getId(), hazelcastSession);
        String oldHazelcastSessionId = originalSessions.put(originalSessionId, hazelcastSession.getId());
        if (LOGGER.isFinestEnabled()) {
            if (oldHazelcastSessionId != null) {
                LOGGER.finest("!!! Overwrote an existing hazelcastSessionId " + oldHazelcastSessionId);
            }
            LOGGER.finest("Created new session with id: " + hazelcastSession.getId());
            LOGGER.finest(sessions.size() + " is sessions.size and originalSessions.size: " + originalSessions.size());
        }
    }

    /**
     * Destroys a session, determining if it should be destroyed clusterwide automatically or via expiry.
     *
     * @param session    the session to be destroyed <i>locally</i>
     * @param invalidate {@code true} if the session has been invalidated and should be destroyed on all nodes
     *                   in the cluster; otherwise, {@code false} to only remove the session globally if this
     *                   node was the final node referencing it
     */
    protected void destroySession(HazelcastHttpSession session, boolean invalidate) {
        if (LOGGER.isFinestEnabled()) {
            LOGGER.finest("Destroying local session: " + session.getId());
        }
        sessions.remove(session.getId());
        originalSessions.remove(session.getOriginalSession().getId());
        session.destroy(invalidate);
    }

    private HazelcastHttpSession getSessionWithId(final String sessionId) {
        HazelcastHttpSession session = sessions.get(sessionId);
        if (session != null && !session.isValid()) {
            destroySession(session, true);
            session = null;
        }
        return session;
    }

    private void addSessionCookie(final HazelcastRequestWrapper req, final String sessionId) {
        final Cookie sessionCookie = new Cookie(config.getCookieName(), sessionId);

        // Changes Added to take the session path from Init Parameter if passed
        // Context Path will be used as Session Path if the Init Param is not passed to keep it backward compatible
        String path;

        if (!isNullOrEmptyAfterTrim(config.getCookiePath())) {
            path = config.getCookiePath();
        } else {
            path = req.getContextPath();
        }

        if ("".equals(path)) {
            path = "/";
        }
        sessionCookie.setPath(path);
        sessionCookie.setMaxAge(config.getCookieMaxAge());
        if (config.getCookieDomain() != null) {
            sessionCookie.setDomain(config.getCookieDomain());
        }
        if (config.isCookieHttpOnly()) {
            try {
                sessionCookie.setHttpOnly(true);
            } catch (NoSuchMethodError e) {
                LOGGER.info("HttpOnly cookies require a Servlet 3.0+ container. Add the following to the "
                        + getClass().getName() + " mapping in web.xml to disable HttpOnly cookies:\n"
                        + "<init-param>\n"
                        + "    <param-name>cookie-http-only</param-name>\n"
                        + "    <param-value>false</param-value>\n"
                        + "</init-param>");
            }
        }
        sessionCookie.setSecure(config.isCookieSecure());
        req.res.addCookie(sessionCookie);
    }

    @Override
    public final void doFilter(ServletRequest req, ServletResponse res, final FilterChain chain)
            throws IOException, ServletException {

        HazelcastRequestWrapper requestWrapper =
                new HazelcastRequestWrapper((HttpServletRequest) req, (HttpServletResponse) res);

        chain.doFilter(requestWrapper, res);

        HazelcastHttpSession session = requestWrapper.getSession(false);
        if (session != null && session.isValid() && config.isDeferredWrite()) {
            if (LOGGER.isFinestEnabled()) {
                LOGGER.finest("UPDATING SESSION " + session.getId());
            }
            session.sessionDeferredWrite();
        }
    }

    @Override
    public final void destroy() {
        sessions.clear();
        originalSessions.clear();
        if (config.isShutdownOnDestroy()) {
            clusteredSessionService.destroy();
        }
    }

    protected class HazelcastRequestWrapper extends HttpServletRequestWrapper {
        final HttpServletResponse res;
        HazelcastHttpSession hazelcastSession;

        public HazelcastRequestWrapper(final HttpServletRequest req,
                                       final HttpServletResponse res) {
            super(req);
            this.res = res;
        }

        HttpSession getOriginalSession(boolean create) {
            // Find the top non-wrapped Http Servlet request
            HttpServletRequest req = getNonWrappedHttpServletRequest();
            if (req != null) {
                return req.getSession(create);
            } else {
                return super.getSession(create);
            }
        }

        private HttpServletRequest getNonWrappedHttpServletRequest() {
            HttpServletRequest req = (HttpServletRequest) getRequest();
            while (req instanceof HttpServletRequestWrapper) {
                req = (HttpServletRequest) ((HttpServletRequestWrapper) req).getRequest();
            }
            return req;
        }

        @Override
        public RequestDispatcher getRequestDispatcher(final String path) {
            final ServletRequest original = getRequest();
            return new RequestDispatcher() {
                public void forward(ServletRequest servletRequest, ServletResponse servletResponse)
                        throws ServletException, IOException {
                    original.getRequestDispatcher(path).forward(servletRequest, servletResponse);
                }

                public void include(ServletRequest servletRequest, ServletResponse servletResponse)
                        throws ServletException, IOException {
                    original.getRequestDispatcher(path).include(servletRequest, servletResponse);
                }
            };
        }

        @Override
        public HttpSession getSession() {
            return getSession(true);
        }

        @Override
        public HazelcastHttpSession getSession(final boolean create) {
            hazelcastSession = readSessionFromLocal();
            String hazelcastSessionId = findHazelcastSessionIdFromRequest();
            if (hazelcastSession == null && !res.isCommitted() && (create || hazelcastSessionId != null)) {
                hazelcastSession = createNewSession(HazelcastRequestWrapper.this, create, hazelcastSessionId);
            }
            return hazelcastSession;
        }

        @Override
        public boolean isRequestedSessionIdValid() {
            return hazelcastSession != null && hazelcastSession.isValid();
        }

        // DO NOT DELETE THIS METHOD. USED IN SERVLET 3.1+ environments
        public String changeSessionId() {
            Method changeSessionIdMethod = getChangeSessionIdMethod();
            if (changeSessionIdMethod == null) {
                return "";
            }
            HttpServletRequest nonWrappedHttpServletRequest = getNonWrappedHttpServletRequest();
            if (nonWrappedHttpServletRequest.getSession() == null) {
                throw new IllegalStateException("changeSessionId requested for request with no session");
            }
            originalSessions.remove(nonWrappedHttpServletRequest.getSession().getId());

            HazelcastHttpSession hazelcastHttpSession = getSession(false);
            sessions.remove(hazelcastHttpSession.getId());
            hazelcastHttpSession.destroy(true);

            String newHazelcastSessionId = generateSessionId();
            String newJSessionId = invokeChangeSessionId(nonWrappedHttpServletRequest, changeSessionIdMethod);
            HttpSession originalSession = nonWrappedHttpServletRequest.getSession();

            HazelcastHttpSession hazelcastSession = createHazelcastHttpSession(newHazelcastSessionId, originalSession);
            hazelcastSession.setClusterWideNew(true);
            updateSessionMaps(newJSessionId, hazelcastSession);
            addSessionCookie(this, newHazelcastSessionId);

            return newJSessionId;
        }

        private HazelcastHttpSession readSessionFromLocal() {
            // following chunk is executed _only_ when session is invalidated and getSession is called on the request
            String invalidatedOriginalSessionId = null;
            if (hazelcastSession != null && !hazelcastSession.isValid()) {
                LOGGER.finest("Session is invalid!");
                destroySession(hazelcastSession, true);
                invalidatedOriginalSessionId = hazelcastSession.invalidatedOriginalSessionId;
                hazelcastSession = null;
            } else if (hazelcastSession != null) {
                return hazelcastSession;
            }

            HttpSession originalSession = getOriginalSession(false);
            if (originalSession != null) {
                String hazelcastSessionId = originalSessions.get(originalSession.getId());
                String hazelcastSessionIdFromRequest = findHazelcastSessionIdFromRequest();
                // hazelcast.sessionId from the request overrides hazelcast.sessionId corresponding to jsessionid from
                // the request
                if (hazelcastSessionIdFromRequest != null && !hazelcastSessionIdFromRequest.equals(hazelcastSessionId)) {
                    hazelcastSessionId = hazelcastSessionIdFromRequest;
                }

                if (hazelcastSessionId != null) {
                    hazelcastSession = getSessionWithId(hazelcastSessionId);

                    if (hazelcastSession != null && !hazelcastSession.isStickySession()) {
                        hazelcastSession.updateReloadFlag();
                    }
                    return hazelcastSession;
                }
                // Even though session can be taken from request, it might be already invalidated.
                // For example, in Wildfly (uses Undertow), taken wrapper session might be valid
                // but its underlying real session might be already invalidated after redirection
                // due to its request/url based wrapper session (points to same original session) design.
                // Therefore, we check the taken session id and
                // ignore its invalidation if it is already invalidated inside Hazelcast's session.
                // See issue on Wildfly https://github.com/hazelcast/hazelcast/issues/6335
                if (!originalSession.getId().equals(invalidatedOriginalSessionId)) {
                    originalSession.invalidate();
                }
            }
            return readFromCookie();
        }

        private HazelcastHttpSession readFromCookie() {
            String existingHazelcastSessionId = findHazelcastSessionIdFromRequest();
            if (existingHazelcastSessionId != null) {
                hazelcastSession = getSessionWithId(existingHazelcastSessionId);
                if (hazelcastSession != null && !hazelcastSession.isStickySession()) {
                    hazelcastSession.updateReloadFlag();
                    return hazelcastSession;
                }
            }
            return null;
        }


        private String findHazelcastSessionIdFromRequest() {
            String hzSessionId = null;

            final Cookie[] cookies = getCookies();
            if (cookies != null) {
                for (final Cookie cookie : cookies) {
                    final String name = cookie.getName();
                    final String value = cookie.getValue();
                    if (name.equalsIgnoreCase(config.getCookieName())) {
                        hzSessionId = value;
                        break;
                    }
                }
            }
            // if hazelcast session id is not found on the cookie and using request parameter is enabled, look into
            // request parameters
            if (hzSessionId == null && config.isUseRequestParameter()) {
                hzSessionId = getParameter(config.getCookieName());
            }

            return hzSessionId;
        }
    }
}
