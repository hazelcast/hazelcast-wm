package com.hazelcast.wm.test;

import com.hazelcast.config.InvalidConfigurationException;
import com.hazelcast.web.WebFilterConfig;
import com.hazelcast.wm.test.spring.FilterConfigStub;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import javax.servlet.FilterConfig;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

public class WebFilterConfigTest {
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private FilterConfig emptyFilterConfig = new FilterConfigStub();

    @Test
    public void testInstanceName_withConfigLocation() throws Exception {
        expectedException.expect(InvalidConfigurationException.class);
        expectedException.expectMessage(
                allOf(containsString("session-ttl-seconds"), containsString("config-location")));

        Properties properties = new Properties();
        properties.setProperty("instance-name", "instance-1");
        properties.setProperty("session-ttl-seconds", "20");
        properties.setProperty("config-location", "some.xml");

        WebFilterConfig.create(emptyFilterConfig, properties);
    }

    @Test
    public void testInstanceName_withoutConfigLocation() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("instance-name", "instance-1");
        properties.setProperty("map-name", "map-1");

        WebFilterConfig.create(emptyFilterConfig, properties);
    }

    @Test
    public void testUseClient_withClientConfigLocation() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("use-client", "true");
        properties.setProperty("client-config-location", "some.xml");

        WebFilterConfig.create(emptyFilterConfig, properties);
    }

    @Test
    public void testUseClient_withConfigLocation() throws Exception {
        expectedException.expect(InvalidConfigurationException.class);
        expectedException.expectMessage(containsString("config-location"));

        Properties properties = new Properties();
        properties.setProperty("use-client", "true");
        properties.setProperty("config-location", "some.xml");

        WebFilterConfig.create(emptyFilterConfig, properties);
    }
}
