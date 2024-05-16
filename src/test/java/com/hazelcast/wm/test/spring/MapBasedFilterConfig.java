package com.hazelcast.wm.test.spring;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class MapBasedFilterConfig implements FilterConfig {
    private final ServletContext emptyServletContext = new ServletContextStub();
    private final Map<String, String> parameters = new HashMap<String, String>();

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
        return parameters.get(name);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        return new Enumeration<String>() {

            private Iterator<String> iterator = parameters.keySet().iterator();

            @Override
            public boolean hasMoreElements() {
                return iterator.hasNext();
            }

            @Override
            public String nextElement() {
                return iterator.next();
            }
        };
    }

    public void setParameter(String paramName, String paramValue) {
        parameters.put(paramName, paramValue);
    }
}
