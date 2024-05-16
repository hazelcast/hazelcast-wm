package com.hazelcast.wm.test.spring;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

@SuppressWarnings("unused")
@Controller
public class TestController {
    @RequestMapping(value = "/updateAttribute", method = RequestMethod.POST)
    @ResponseBody
    public String updateAttribute(HttpServletRequest req, @RequestParam String key, @RequestParam String value) {
        req.getSession().setAttribute(key, value);
        return "success";
    }

    @RequestMapping(value = "/getAttribute", method = RequestMethod.POST)
    @ResponseBody
    public Object getAttribute(HttpServletRequest req, @RequestParam String key) {
        return req.getSession().getAttribute(key);
    }
}
