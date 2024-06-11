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

package com.hazelcast.web;

import com.hazelcast.internal.config.ConfigLoader;
import com.hazelcast.config.InvalidConfigurationException;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.internal.util.StringUtil;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Contains all configuration parameters for Hazelcast session replication
 */
public final class WebFilterConfig {
    /**
     * Location of the client's configuration. It can be specified as a servlet
     * resource, classpath resource or as a URL.
     * Its default value is null.
     */
    public static final String CLIENT_CONFIG_LOCATION = "client-config-location";

    /**
     * Time-to-live value (in seconds) of the distributed map storing web session
     * objects. It can be any integer between 0 and {@link Integer#MAX_VALUE}.
     * Its default value is 1800, which is 30 minutes and cannot be set when
     * USE_CLIENT is true.
     */
    public static final String SESSION_TTL_SECONDS = "session-ttl-seconds";

    /**
     * Specifies whether WebFilter will connect to an existing cluster as a
     * client. Its default value is false.
     */
    public static final String USE_CLIENT = "use-client";

    /**
     * Name of an existing Hazelcast instance to be used by WebFilter.
     * A new instance will be created if a name is not provided.
     */
    public static final String INSTANCE_NAME = "instance-name";

    /**
     * Name of the distributed map storing web session objects.
     */
    public static final String MAP_NAME = "map-name";

    /**
     * If set to true, all requests of a session are routed to the member where
     * the session is first created. If set to false, when a session is updated
     * on a member, the updated entry for this session on all members is
     * invalidated. This option must be used in a compatible way with load
     * balancer behavior.
     * Its default value is true.
     */
    public static final String STICKY_SESSION = "sticky-session";

    /**
     * Location of Hazelcast configuration.
     * It can be specified as a servlet resource, classpath resource or as a URL.
     * Its default value is hazelcast-default.xml or hazelcast.xml in the
     * classpath.
     */
    public static final String CONFIG_LOCATION = "config-location";

    /**
     * Specifies whether Hazelcast instance will be shutdown during the
     * undeployment of web application.
     * Its default value is true.
     */
    public static final String SHUTDOWN_ON_DESTROY = "shutdown-on-destroy";

    /**
     * Specifies whether the sessions in each instance will be cached locally.
     * Its default value is false.
     */
    public static final String DEFERRED_WRITE = "deferred-write";

    /**
     * Specifies whether a request parameter can be used by clients to send
     * back the session ID value.
     * Its default value is false.
     */
    public static final String USE_REQUEST_PARAMETER = "use-request-parameter";

    /**
     * Comma separated attributes not to be written to distributed map
     * but only to be kept locally in a server, not visible to other servers.
     * The default value is an empty list.
     */
    public static final String TRANSIENT_ATTRIBUTES = "transient-attributes";

    /**
     * If set to true, it's guaranteed that whenever a session is used the
     * idle-time of this session on the distributed map is reset. Note that
     * this is useless when non-sticky sessions are used or max-idle-second
     * is not set for the cluster map.
     * Its default value is false.
     */
    public static final String KEEP_REMOTE_ACTIVE = "keep-remote-active";

    /**
     * Name of the session ID cookie.
     */
    public static final String COOKIE_NAME = "cookie-name";

    /**
     * Domain of the session ID cookie. Its default value is based on the
     * incoming request.
     */
    public static final String COOKIE_DOMAIN = "cookie-domain";

    /**
     * Specifies whether the cookie only be sent using a secure protocol.
     * Its default value is false.
     */
    public static final String COOKIE_SECURE = "cookie-secure";

    /**
     * Specifies whether the attribute HttpOnly can be set on cookie.
     * Its default value is false.
     */
    public static final String COOKIE_HTTP_ONLY = "cookie-http-only";

    /**
     * Path of the session ID cookie.
     * Its default value is based on the context path of the incoming request.
     */
    public static final String COOKIE_PATH = "cookie-path";

    /**
     * Specifies the maximum age of the cookie in seconds. Its default value is
     * -1, meaning the cookie is not stored persistently and will be deleted
     * when the browser exits.
     */
    public static final String COOKIE_MAX_AGE = "cookie-max-age";

    private static final ILogger LOGGER = Logger.getLogger(WebFilterConfig.class);
    private static final int SESSION_TTL_DEFAULT_SECONDS = 1800;

    private boolean useClient;
    private URL configUrl;
    private int sessionTtlSeconds;
    private String instanceName;
    private String mapName;
    private boolean stickySession;
    private boolean shutdownOnDestroy;
    private boolean deferredWrite;
    private boolean useRequestParameter;
    private Set<String> transientAttributes;
    private boolean keepRemoteActive;
    private String cookieName;
    private String cookieDomain;
    private boolean cookieSecure;
    private boolean cookieHttpOnly;
    private String cookiePath;
    private int cookieMaxAge;

    private WebFilterConfig() {
    }

    public static WebFilterConfig create(FilterConfig filterConfig, Properties properties) {
        boolean useClient = getBoolean(filterConfig, properties, USE_CLIENT, false);

        validateHazelcastConfigParameters(filterConfig, properties, useClient);

        // Client mode parameters
        String clientConfigLocation = getString(filterConfig, properties, CLIENT_CONFIG_LOCATION, null);

        // P2P mode parameters
        int sessionTtlSeconds = getInt(filterConfig, properties, SESSION_TTL_SECONDS, SESSION_TTL_DEFAULT_SECONDS);
        String configLocation = getString(filterConfig, properties, CONFIG_LOCATION, null);

        URL configUrl = validateAndGetConfigUrl(filterConfig.getServletContext(), useClient, configLocation,
                clientConfigLocation);

        // common parameters
        String instanceName = getString(filterConfig, properties, INSTANCE_NAME, null);
        String mapName = getString(filterConfig, properties, MAP_NAME,
                "_web_" + filterConfig.getServletContext().getServletContextName());
        boolean stickySession = getBoolean(filterConfig, properties, STICKY_SESSION, true);
        boolean shutdownOnDestroy = getBoolean(filterConfig, properties, SHUTDOWN_ON_DESTROY, true);
        boolean deferredWrite = getBoolean(filterConfig, properties, DEFERRED_WRITE, false);
        boolean useRequestParameter = getBoolean(filterConfig, properties, USE_REQUEST_PARAMETER, false);
        Set<String> transientAttributes = getStringSet(filterConfig, properties, TRANSIENT_ATTRIBUTES);
        boolean keepRemoteActive = getBoolean(filterConfig, properties, KEEP_REMOTE_ACTIVE, false);
        String cookieName = getString(filterConfig, properties, COOKIE_NAME, "hazelcast.sessionId");
        String cookieDomain = getString(filterConfig, properties, COOKIE_DOMAIN, null);
        boolean cookieSecure = getBoolean(filterConfig, properties, COOKIE_SECURE, false);
        boolean cookieHttpOnly = getBoolean(filterConfig, properties, COOKIE_HTTP_ONLY, false);
        String cookiePath = getString(filterConfig, properties, COOKIE_PATH, null);
        int cookieMaxAge = getInt(filterConfig, properties, COOKIE_MAX_AGE, -1);

        WebFilterConfig wfc = new WebFilterConfig();
        wfc.useClient = useClient;
        wfc.configUrl = configUrl;
        wfc.sessionTtlSeconds = sessionTtlSeconds;
        wfc.instanceName = instanceName;
        wfc.mapName = mapName;
        wfc.stickySession = stickySession;
        wfc.shutdownOnDestroy = shutdownOnDestroy;
        wfc.deferredWrite = deferredWrite;
        wfc.useRequestParameter = useRequestParameter;
        wfc.transientAttributes = transientAttributes;
        wfc.keepRemoteActive = keepRemoteActive;
        wfc.cookieName = cookieName;
        wfc.cookieDomain = cookieDomain;
        wfc.cookieSecure = cookieSecure;
        wfc.cookieHttpOnly = cookieHttpOnly;
        wfc.cookiePath = cookiePath;
        wfc.cookieMaxAge = cookieMaxAge;
        return wfc;
    }

    public boolean isUseClient() {
        return useClient;
    }

    public URL getConfigUrl() {
        return configUrl;
    }

    public int getSessionTtlSeconds() {
        return sessionTtlSeconds;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public String getMapName() {
        return mapName;
    }

    public boolean isStickySession() {
        return stickySession;
    }

    public boolean isShutdownOnDestroy() {
        return shutdownOnDestroy;
    }

    public boolean isDeferredWrite() {
        return deferredWrite;
    }

    public boolean isUseRequestParameter() {
        return useRequestParameter;
    }

    public Set<String> getTransientAttributes() {
        return transientAttributes;
    }

    public String getCookieName() {
        return cookieName;
    }

    public String getCookieDomain() {
        return cookieDomain;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public boolean isCookieHttpOnly() {
        return cookieHttpOnly;
    }

    public String getCookiePath() {
        return cookiePath;
    }

    public int getCookieMaxAge() {
        return cookieMaxAge;
    }

    public boolean isKeepRemoteActive() {
        return keepRemoteActive;
    }

    private static boolean getBoolean(FilterConfig filterConfig, Properties properties, String paramName, boolean defaultValue) {
        String value = getValue(filterConfig, properties, paramName);
        if (StringUtil.isNullOrEmptyAfterTrim(value)) {
            return defaultValue;
        } else {
            return Boolean.valueOf(value);
        }
    }

    private static int getInt(FilterConfig filterConfig, Properties properties, String paramName, int defaultValue) {
        String value = getValue(filterConfig, properties, paramName);
        if (StringUtil.isNullOrEmptyAfterTrim(value)) {
            return defaultValue;
        } else {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new InvalidConfigurationException(paramName + " needs to be an integer: (" + value + ")");
            }
        }
    }

    private static String getString(FilterConfig filterConfig, Properties properties, String paramName, String defaultValue) {
        String value = getValue(filterConfig, properties, paramName);
        if (StringUtil.isNullOrEmptyAfterTrim(value)) {
            return defaultValue;
        } else {
            return value;
        }
    }

    private static Set<String> getStringSet(FilterConfig filterConfig, Properties properties, String paramName) {
        String value = getValue(filterConfig, properties, paramName);
        if (StringUtil.isNullOrEmptyAfterTrim(value)) {
            return Collections.emptySet();
        } else {
            HashSet<String> transientAttributes = new HashSet<>();
            StringTokenizer st = new StringTokenizer(value, ",");
            while (st.hasMoreTokens()) {
                String token = st.nextToken();
                transientAttributes.add(token.trim());
            }
            return transientAttributes;
        }
    }

    private static URL getConfigUrl(final ServletContext ctx, final String configLocation) {
        URL configUrl = null;
        try {
            configUrl = ctx.getResource(configLocation);
        } catch (MalformedURLException ignore) {
            LOGGER.info("Ignored MalformedURLException");
        }
        if (configUrl == null) {
            configUrl = ConfigLoader.locateConfig(configLocation);
        }
        if (configUrl == null) {
            throw new InvalidConfigurationException("Could not load configuration '" + configLocation + "'");
        }
        return configUrl;
    }

    private static String getValue(FilterConfig filterConfig, Properties properties, String paramName) {
        if (properties != null && properties.containsKey(paramName)) {
            Object property = properties.get(paramName);
            if (property != null) {
                return property.toString();
            }
        }
        return filterConfig.getInitParameter(paramName);
    }

    private static URL validateAndGetConfigUrl(ServletContext ctx, boolean useClient, String configLocation,
                                               String clientConfigLocation) {
        if (!useClient && configLocation != null) {
            return getConfigUrl(ctx, configLocation);
        } else if (useClient && clientConfigLocation != null) {
            return getConfigUrl(ctx, clientConfigLocation);
        } else {
            return null;
        }
    }

    private static void validateHazelcastConfigParameters(FilterConfig filterConfig, Properties properties, boolean useClient) {
        if (paramExists(filterConfig, properties, INSTANCE_NAME)) {
            List<String> wrongParams = parametersExist(filterConfig, properties, SESSION_TTL_SECONDS,
                    CONFIG_LOCATION, CLIENT_CONFIG_LOCATION);

            if (!wrongParams.isEmpty()) {
                StringBuilder errorMsgBuilder = new StringBuilder("The following parameters cannot be used when "
                        + INSTANCE_NAME + " is set to 'true' because an existing Hazelcast");
                if (useClient) {
                    errorMsgBuilder.append("Client");
                }
                errorMsgBuilder.append(" instance is being used: [");
                for (int i = 0; i < wrongParams.size(); i++) {
                    errorMsgBuilder.append(wrongParams.get(i));
                    if (i != wrongParams.size() - 1) {
                        errorMsgBuilder.append(", ");
                    }
                }
                errorMsgBuilder.append("]");
                throw new InvalidConfigurationException(errorMsgBuilder.toString());
            }
        }

        if (useClient) {
            List<String> wrongParams = parametersExist(filterConfig, properties, SESSION_TTL_SECONDS, CONFIG_LOCATION);
            if (!wrongParams.isEmpty()) {
                StringBuilder errorMsgBuilder = new StringBuilder("The following parameters cannot be used when "
                        + USE_CLIENT + " is set to 'true': [");
                for (int i = 0; i < wrongParams.size(); i++) {
                    errorMsgBuilder.append(wrongParams.get(i));
                    if (i != wrongParams.size() - 1) {
                        errorMsgBuilder.append(", ");
                    }
                }
                errorMsgBuilder.append("]");
                throw new InvalidConfigurationException(errorMsgBuilder.toString());
            }
        } else {
            if (paramExists(filterConfig, properties, CLIENT_CONFIG_LOCATION)) {
                throw new InvalidConfigurationException(CLIENT_CONFIG_LOCATION + " cannot be used with P2P mode.");
            }
        }
    }

    private static List<String> parametersExist(FilterConfig filterConfig, Properties properties,
                                                String... parameterNames) {
        ArrayList<String> parameters = new ArrayList<>(parameterNames.length);
        for (String pName : parameterNames) {
            if (paramExists(filterConfig, properties, pName)) {
                parameters.add(pName);
            }
        }
        return parameters;
    }

    private static boolean paramExists(FilterConfig filterConfig, Properties properties, String paramName) {
        return (filterConfig != null && filterConfig.getInitParameter(paramName) != null)
                || (properties != null && properties.getProperty(paramName) != null);
    }
}
