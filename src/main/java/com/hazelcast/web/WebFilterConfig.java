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

import com.hazelcast.config.ConfigLoader;
import com.hazelcast.config.InvalidConfigurationException;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.util.StringUtil;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
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
    private static final ILogger LOGGER = Logger.getLogger(WebFilterConfig.class);

    private static final int SESSION_TTL_DEFAULT_SECONDS = 1800;

    private static final String CLIENT_CONFIG_LOCATION = "client-config-location";
    private static final String SESSION_TTL_CONFIG = "session-ttl-seconds";
    private static final String USE_CLIENT = "use-client";
    private static final String INSTANCE_NAME = "instance-name";
    private static final String MAP_NAME = "map-name";
    private static final String STICKY_SESSION_CONFIG = "sticky-session";
    private static final String CONFIG_LOCATION = "config-location";
    private static final String SHUTDOWN_ON_DESTROY = "shutdown-on-destroy";
    private static final String DEFERRED_WRITE = "deferred-write";
    private static final String USE_REQUEST_PARAMETER = "use-request-parameter";
    private static final String TRANSIENT_ATTRIBUTES = "transient-attributes";

    private static final String COOKIE_NAME = "cookie-name";
    private static final String COOKIE_DOMAIN = "cookie-domain";
    private static final String COOKIE_SECURE = "cookie-secure";
    private static final String COOKIE_HTTP_ONLY = "cookie-http-only";
    private static final String COOKIE_PATH = "cookie-path";

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
    private String cookieName;
    private String cookieDomain;
    private boolean cookieSecure;
    private boolean cookieHttpOnly;
    private String cookiePath;

    private WebFilterConfig() {
    }

    public static WebFilterConfig create(FilterConfig filterConfig, Properties properties) {
        boolean useClient = getBoolean(filterConfig, properties, USE_CLIENT, false);

        validateHazelcastConfigParameters(filterConfig, properties, useClient);

        // Client mode parameters
        String clientConfigLocation = getString(filterConfig, properties, CLIENT_CONFIG_LOCATION, null);

        // P2P mode parameters
        int sessionTtlSeconds = getInt(filterConfig, properties, SESSION_TTL_CONFIG, SESSION_TTL_DEFAULT_SECONDS);
        String configLocation = getString(filterConfig, properties, CONFIG_LOCATION, null);

        URL configUrl = validateAndGetConfigUrl(filterConfig.getServletContext(), useClient, configLocation,
                clientConfigLocation);

        // common parameters
        String instanceName = getString(filterConfig, properties, INSTANCE_NAME, null);
        String mapName = getString(filterConfig, properties, MAP_NAME,
                "_web_" + filterConfig.getServletContext().getServletContextName());
        boolean stickySession = getBoolean(filterConfig, properties, STICKY_SESSION_CONFIG, true);
        boolean shutdownOnDestroy = getBoolean(filterConfig, properties, SHUTDOWN_ON_DESTROY, true);
        boolean deferredWrite = getBoolean(filterConfig, properties, DEFERRED_WRITE, false);
        boolean useRequestParameter = getBoolean(filterConfig, properties, USE_REQUEST_PARAMETER, false);
        Set<String> transientAttributes = getStringSet(filterConfig, properties, TRANSIENT_ATTRIBUTES);
        String cookieName = getString(filterConfig, properties, COOKIE_NAME, "hazelcast.sessionId");
        String cookieDomain = getString(filterConfig, properties, COOKIE_DOMAIN, null);
        boolean cookieSecure = getBoolean(filterConfig, properties, COOKIE_SECURE, false);
        boolean cookieHttpOnly = getBoolean(filterConfig, properties, COOKIE_HTTP_ONLY, false);
        String cookiePath = getString(filterConfig, properties, COOKIE_PATH, null);

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
        wfc.cookieName = cookieName;
        wfc.cookieDomain = cookieDomain;
        wfc.cookieSecure = cookieSecure;
        wfc.cookieHttpOnly = cookieHttpOnly;
        wfc.cookiePath = cookiePath;
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
            HashSet<String> transientAttributes = new HashSet<String>();
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
            return properties.getProperty(paramName);
        } else {
            return filterConfig.getInitParameter(paramName);
        }
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
            List<String> wrongParams = parametersExist(filterConfig, properties, SESSION_TTL_CONFIG,
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
            List<String> wrongParams = parametersExist(filterConfig, properties, SESSION_TTL_CONFIG, CONFIG_LOCATION);
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
        ArrayList<String> parameters = new ArrayList<String>(parameterNames.length);
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
