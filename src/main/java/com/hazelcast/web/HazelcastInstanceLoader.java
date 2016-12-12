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

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.config.Config;
import com.hazelcast.config.ConfigLoader;
import com.hazelcast.config.InvalidConfigurationException;
import com.hazelcast.config.ListenerConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.UrlXmlConfig;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.DuplicateInstanceNameException;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.util.ExceptionUtil;
import com.hazelcast.web.listener.ClientLifecycleListener;
import com.hazelcast.web.listener.ServerLifecycleListener;

import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import static com.hazelcast.util.StringUtil.isNullOrEmptyAfterTrim;

final class HazelcastInstanceLoader {

    public static final String INSTANCE_NAME = "instance-name";
    public static final String CONFIG_LOCATION = "config-location";
    public static final String USE_CLIENT = "use-client";
    public static final String CLIENT_CONFIG_LOCATION = "client-config-location";
    public static final String STICKY_SESSION_CONFIG = "sticky-session";
    public static final String SESSION_TTL_CONFIG = "session-ttl-seconds";
    public static final String MAP_NAME = "map-name";

    private static final String SESSION_TTL_DEFAULT_SECONDS = "1800";

    private static final ILogger LOGGER = Logger.getLogger(HazelcastInstanceLoader.class);

    private HazelcastInstanceLoader() {
    }

    public static HazelcastInstance createInstance(final ClusteredSessionService sessionService)
            throws ServletException {

        final Properties properties = sessionService.getProperties();
        final String instanceName = properties.getProperty(INSTANCE_NAME);
        final String configLocation = properties.getProperty(CONFIG_LOCATION);
        final String useClientProp = properties.getProperty(USE_CLIENT);
        final String clientConfigLocation = properties.getProperty(CLIENT_CONFIG_LOCATION);
        final boolean useClient = !isNullOrEmptyAfterTrim(useClientProp) && Boolean.parseBoolean(useClientProp);
        final String mapName = properties.getProperty(MAP_NAME);
        final String sessionTTL = properties.getProperty(SESSION_TTL_CONFIG);

        URL configUrl = getConfigUrl(sessionService, configLocation, clientConfigLocation, useClient);

        if (useClient) {
            if (!isNullOrEmptyAfterTrim(sessionTTL)) {
                throw new InvalidConfigurationException("session-ttl-seconds cannot be used with client/server mode.");
            }
            boolean isSticky = Boolean.valueOf(properties.getProperty(STICKY_SESSION_CONFIG));
            return createClientInstance(sessionService, configUrl, instanceName, isSticky);
        }

        if (!isNullOrEmptyAfterTrim(instanceName)) {
            HazelcastInstance existingInstance = Hazelcast.getHazelcastInstanceByName(instanceName);
            if (existingInstance != null) {
                LOGGER.info("Using existing HazelcastInstance [" + instanceName + "] for session replication");

                if (!isNullOrEmptyAfterTrim(configLocation)) {
                    LOGGER.warning(CONFIG_LOCATION + " setting ignored because existing Hazelcast instance ["
                            + instanceName + "] is being used.");
                }

                if (!isNullOrEmptyAfterTrim(sessionTTL)) {
                    LOGGER.warning(SESSION_TTL_CONFIG + " setting ignored because existing Hazelcast instance ["
                            + instanceName + "] is being used.");
                }

                return existingInstance;
            }
        }

        return createServerInstance(sessionService, instanceName, mapName, configUrl, sessionTTL);
    }

    private static URL getConfigUrl(ClusteredSessionService sessionService, String configLocation,
                                    String clientConfigLocation, boolean useClient) throws ServletException {

        if (useClient && !isNullOrEmptyAfterTrim(clientConfigLocation)) {
            return getConfigURL(sessionService.getFilterConfig(), clientConfigLocation);
        } else if (!isNullOrEmptyAfterTrim(configLocation)) {
            return getConfigURL(sessionService.getFilterConfig(), configLocation);
        } else {
            return null;
        }
    }

    private static HazelcastInstance createServerInstance(ClusteredSessionService sessionService,
                                                          String instanceName, String mapName,
                                                          URL configUrl, String sessionTTL) throws ServletException {
        Config config;
        if (configUrl == null) {
            config = new XmlConfigBuilder().build();
        } else {
            try {
                config = new UrlXmlConfig(configUrl);
            } catch (IOException e) {
                throw new ServletException(e);
            }
        }

        if (!isNullOrEmptyAfterTrim(instanceName)) {
            config.setInstanceName(instanceName);
        }

        MapConfig mapConfig = config.getMapConfig(mapName);
        try {
            if (isNullOrEmptyAfterTrim(sessionTTL)) {
                sessionTTL = SESSION_TTL_DEFAULT_SECONDS;
            }

            mapConfig.setMaxIdleSeconds(Integer.parseInt(sessionTTL));
        } catch (NumberFormatException e) {
            ExceptionUtil.rethrow(new InvalidConfigurationException("session-ttl-seconds must be a numeric value"));
        }

        config.addListenerConfig(new ListenerConfig(new ServerLifecycleListener(sessionService)));

        LOGGER.info("Creating a new HazelcastInstance for session replication");
        return Hazelcast.newHazelcastInstance(config);
    }

    private static HazelcastInstance createClientInstance(ClusteredSessionService sessionService,
                                                          URL configUrl, String instanceName,
                                                          boolean isSticky) throws ServletException {
        LOGGER.warning("Creating HazelcastClient for session replication...");
        LOGGER.warning("make sure this client has access to an already running cluster...");
        ClientConfig clientConfig;
        if (configUrl == null) {
            clientConfig = new ClientConfig();
        } else {
            try {
                clientConfig = new XmlClientConfigBuilder(configUrl).build();
            } catch (IOException e) {
                throw new ServletException(e);
            }
        }
        if (isSticky) {
            clientConfig.getNetworkConfig().setConnectionAttemptLimit(1);
        }
        ListenerConfig listenerConfig = new ListenerConfig(new ClientLifecycleListener(sessionService));
        clientConfig.addListenerConfig(listenerConfig);

        if (!isNullOrEmptyAfterTrim(instanceName)) {
            HazelcastInstance instance = HazelcastClient.getHazelcastClientByName(instanceName);
            if (instance != null) {
                return instance;
            }
            clientConfig.setInstanceName(instanceName);
            try {
                return HazelcastClient.newHazelcastClient(clientConfig);
            } catch (DuplicateInstanceNameException e) {
                return HazelcastClient.getHazelcastClientByName(instanceName);
            }
        }
        return HazelcastClient.newHazelcastClient(clientConfig);
    }

    private static URL getConfigURL(final FilterConfig filterConfig, final String configLocation) throws ServletException {
        URL configUrl = null;
        try {
            configUrl = filterConfig.getServletContext().getResource(configLocation);
        } catch (MalformedURLException ignore) {
            LOGGER.info("ignored MalformedURLException");
        }
        if (configUrl == null) {
            configUrl = ConfigLoader.locateConfig(configLocation);
        }
        if (configUrl == null) {
            throw new ServletException("Could not load configuration '" + configLocation + "'");
        }
        return configUrl;
    }
}
