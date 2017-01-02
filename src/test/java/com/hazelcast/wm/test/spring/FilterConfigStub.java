package com.hazelcast.wm.test.spring;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import java.util.Enumeration;

public class FilterConfigStub implements FilterConfig {
    private final ServletContext emptyServletContext = new ServletContextStub();

    @Override
    public String getFilterName() {
        return "filter-1";
    }

    @Override
    public ServletContext getServletContext() {
        return emptyServletContext;
    }

    @Override
    public String getInitParameter(String name) {
        return null;
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        return null;
    }
}
