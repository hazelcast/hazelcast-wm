package com.hazelcast.wm.test;

import com.hazelcast.config.InvalidConfigurationException;
import com.hazelcast.web.WebFilterConfig;
import com.hazelcast.wm.test.spring.MapBasedFilterConfig;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import jakarta.servlet.FilterConfig;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

public class WebFilterConfigTest {
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private final FilterConfig emptyFilterConfig = new MapBasedFilterConfig();

    @Test
    public void testInstanceName_withConfigLocation() throws Exception {
        expectedException.expect(InvalidConfigurationException.class);
        expectedException.expectMessage(
                allOf(containsString(WebFilterConfig.SESSION_TTL_SECONDS), containsString(WebFilterConfig.CONFIG_LOCATION)));

        Properties properties = new Properties();
        properties.setProperty(WebFilterConfig.INSTANCE_NAME, "instance-1");
        properties.setProperty(WebFilterConfig.SESSION_TTL_SECONDS, "20");
        properties.setProperty(WebFilterConfig.CONFIG_LOCATION, "some.xml");

        WebFilterConfig.create(emptyFilterConfig, properties);
    }

    @Test
    public void testInstanceName_withoutConfigLocation() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(WebFilterConfig.INSTANCE_NAME, "instance-1");
        properties.setProperty(WebFilterConfig.MAP_NAME, "map-1");

        WebFilterConfig.create(emptyFilterConfig, properties);
    }

    @Test
    public void testUseClient_withClientConfigLocation() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(WebFilterConfig.USE_CLIENT, "true");
        properties.setProperty(WebFilterConfig.CLIENT_CONFIG_LOCATION, "some.xml");

        WebFilterConfig.create(emptyFilterConfig, properties);
    }

    @Test
    public void testUseClient_withConfigLocation() throws Exception {
        expectedException.expect(InvalidConfigurationException.class);
        expectedException.expectMessage(containsString(WebFilterConfig.CONFIG_LOCATION));

        Properties properties = new Properties();
        properties.setProperty(WebFilterConfig.USE_CLIENT, "true");
        properties.setProperty(WebFilterConfig.CONFIG_LOCATION, "some.xml");

        WebFilterConfig.create(emptyFilterConfig, properties);
    }

    @Test
    public void bothServletFilterConfigAndPropertiesAreUsed() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(WebFilterConfig.COOKIE_NAME, "customcookiename");

        MapBasedFilterConfig servletFilterConfig = new MapBasedFilterConfig();
        servletFilterConfig.setParameter(WebFilterConfig.USE_CLIENT, "true");

        WebFilterConfig webFilterConfig = WebFilterConfig.create(servletFilterConfig, properties);
        Assert.assertEquals(true, webFilterConfig.isUseClient());
        Assert.assertEquals("customcookiename", webFilterConfig.getCookieName());
    }

    @Test
    public void propertiesOverrideServletFilterConfiguration() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(WebFilterConfig.COOKIE_NAME, "cookie1");

        MapBasedFilterConfig servletFilterConfig = new MapBasedFilterConfig();
        servletFilterConfig.setParameter(WebFilterConfig.COOKIE_NAME, "cookie2");

        WebFilterConfig webFilterConfig = WebFilterConfig.create(servletFilterConfig, properties);
        Assert.assertEquals("cookie1", webFilterConfig.getCookieName());
    }

    @Test
    public void testNonStringConfigValues() {
        Properties properties = new Properties();
        properties.put(WebFilterConfig.COOKIE_HTTP_ONLY, true);
        properties.put(WebFilterConfig.COOKIE_MAX_AGE, 160);
        WebFilterConfig config = WebFilterConfig.create(emptyFilterConfig, properties);
        Assert.assertEquals(true, config.isCookieHttpOnly());
        Assert.assertEquals(160, config.getCookieMaxAge());
    }
}
