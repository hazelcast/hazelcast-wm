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

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.config.Config;
import com.hazelcast.config.ListenerConfig;
import com.hazelcast.config.UrlXmlConfig;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.web.listener.ClientLifecycleListener;
import com.hazelcast.web.listener.ServerLifecycleListener;

import jakarta.servlet.ServletException;
import java.io.IOException;

final class HazelcastInstanceLoader {

    private static final ILogger LOGGER = Logger.getLogger(HazelcastInstanceLoader.class);

    private HazelcastInstanceLoader() {
    }

    static HazelcastInstance loadInstance(ClusteredSessionService sessionService, WebFilterConfig filterConfig)
            throws ServletException {

        if (filterConfig.getInstanceName() != null) {
            if (filterConfig.isUseClient()) {
                return loadExistingClient(sessionService, filterConfig.getInstanceName());
            } else {
                return loadExistingInstance(sessionService, filterConfig.getInstanceName());
            }
        } else {
            if (filterConfig.isUseClient()) {
                return createClient(sessionService, filterConfig);
            } else {
                return createInstance(sessionService, filterConfig);
            }
        }
    }

    private static HazelcastInstance createInstance(ClusteredSessionService sessionService, WebFilterConfig filterConfig)
            throws ServletException {

        LOGGER.info("Creating a new HazelcastInstance for session replication");

        Config config;
        if (filterConfig.getConfigUrl() == null) {
            config = new XmlConfigBuilder().build();
        } else {
            try {
                config = new UrlXmlConfig(filterConfig.getConfigUrl());
            } catch (IOException e) {
                throw new ServletException(e);
            }
        }

        config.getMapConfig(filterConfig.getMapName()).setMaxIdleSeconds(filterConfig.getSessionTtlSeconds());
        config.addListenerConfig(new ListenerConfig(new ServerLifecycleListener(sessionService)));

        return Hazelcast.newHazelcastInstance(config);
    }

    private static HazelcastInstance createClient(ClusteredSessionService sessionService, WebFilterConfig filterConfig)
            throws ServletException {

        LOGGER.warning("Creating a new HazelcastClient for session replication...");
        LOGGER.warning("make sure this client has access to an already running cluster...");

        ClientConfig clientConfig;
        if (filterConfig.getConfigUrl() == null) {
            clientConfig = new ClientConfig();
        } else {
            try {
                clientConfig = new XmlClientConfigBuilder(filterConfig.getConfigUrl()).build();
            } catch (IOException e) {
                throw new ServletException("Failed to load client config XML file [" + filterConfig.getConfigUrl()
                        + "]:" + e.getMessage(), e);
            }
        }
        if (filterConfig.isStickySession()) {
            int initialBackoffMillis = clientConfig.getConnectionStrategyConfig()
                    .getConnectionRetryConfig().getInitialBackoffMillis();
            double multiplier = clientConfig
                    .getConnectionStrategyConfig().getConnectionRetryConfig().getMultiplier();

            // Limit connection attempts by 1
            clientConfig.getConnectionStrategyConfig().getConnectionRetryConfig()
                    .setMaxBackoffMillis(initialBackoffMillis * (int) multiplier);
        }

        clientConfig.addListenerConfig(new ListenerConfig(new ClientLifecycleListener(sessionService)));

        return HazelcastClient.newHazelcastClient(clientConfig);
    }

    private static HazelcastInstance loadExistingInstance(ClusteredSessionService sessionService, String instanceName)
            throws ServletException {

        LOGGER.info("Using existing Hazelcast instance with name [" + instanceName + "] for session replication");
        HazelcastInstance instance = Hazelcast.getHazelcastInstanceByName(instanceName);
        if (instance == null) {
            throw new ServletException("Hazelcast instance with name [" + instanceName + "] could not be found.");
        }
        instance.getLifecycleService().addLifecycleListener(new ClientLifecycleListener(sessionService));
        return instance;
    }

    private static HazelcastInstance loadExistingClient(ClusteredSessionService sessionService, String instanceName)
            throws ServletException {

        LOGGER.info("Using existing Hazelcast client instance with name [" + instanceName + "] for session replication");
        HazelcastInstance client = HazelcastClient.getHazelcastClientByName(instanceName);
        if (client == null) {
            throw new ServletException("Hazelcast client instance with name [" + instanceName + "] could not be found.");
        }
        client.getLifecycleService().addLifecycleListener(new ClientLifecycleListener(sessionService));
        return client;
    }
}
