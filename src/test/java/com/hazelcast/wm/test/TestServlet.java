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

package com.hazelcast.wm.test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class TestServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getRequestURI().endsWith("redirect")) {
            //Don't touch session before redirect
            resp.sendRedirect("/");
            return;
        }
        if (req.getRequestURI().endsWith("readIfExist")) {
            HttpSession session = req.getSession(false); //Don't create a session if one does not exist!!!
            Object value = session.getAttribute("key");
            resp.getWriter().write(value.toString());
            return;
        } else if (req.getRequestURI().contains("noSession")) {
            HttpSession session = req.getSession(false);
            resp.getWriter().write(String.valueOf(session == null));
            return;
        }

        HttpSession session = req.getSession();
        if (req.getRequestURI().endsWith("write")) {
            session.setAttribute("key", "value");
            resp.getWriter().write("true");
        } else if (req.getRequestURI().endsWith("write_wait")) {
            session.putValue("key", "value");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            resp.getWriter().write("true");
        } else if (req.getRequestURI().endsWith("putValue")) {
            session.putValue("key", "value");
            resp.getWriter().write("true");
        } else if (req.getRequestURI().endsWith("nullkey")) {
            session.setAttribute(null, "value");
            resp.getWriter().write("true");
        } else if (req.getRequestURI().endsWith("read")) {
            Object value = session.getAttribute("key");
            resp.getWriter().write(value == null ? "null" : value.toString());
        } else if (req.getRequestURI().endsWith("getValue")) {
            Object value = session.getValue("key");
            resp.getWriter().write(value == null ? "null" : value.toString());
        } else if (req.getRequestURI().endsWith("remove")) {
            session.removeAttribute("key");
            resp.getWriter().write("true");
        } else if (req.getRequestURI().endsWith("remove_put")) {
            session.setAttribute("key", "value");
            session.removeAttribute("key");
            resp.getWriter().write("true");
        } else if (req.getRequestURI().endsWith("removeValue")) {
            session.removeValue("key");
            resp.getWriter().write("true");
        } else if (req.getRequestURI().endsWith("remove_set_null")) {
            session.setAttribute("key", null);
            resp.getWriter().write("true");
        } else if (req.getRequestURI().endsWith("invalidate")) {
            session.invalidate();
            resp.getWriter().write("true");
        } else if (req.getRequestURI().endsWith("update")) {
            session.setAttribute("key", "value-updated");
            resp.getWriter().write("true");
        } else if (req.getRequestURI().endsWith("multiplesession")) {
            session.setAttribute("key", "value");
            session = req.getSession();
            Object value = session.getAttribute("key");
            resp.getWriter().write(value == null ? "null" : value.toString());
        } else if (req.getRequestURI().endsWith("update-and-read-same-request")) {
            session.setAttribute("key", "value-updated");
            Object value = session.getAttribute("key");
            resp.getWriter().write(value == null ? "null" : value.toString());
        } else if (req.getRequestURI().endsWith("names")) {
            List<String> names = Collections.list(session.getAttributeNames());
            String nameList = names.toString();
            // Return comma-separated list of attribute names
            resp.getWriter().write(nameList.substring(1, nameList.length() - 1).replace(", ", ","));
        } else if (req.getRequestURI().endsWith("setGet")) {
            session.setAttribute("key", "value");
            Object value = session.getAttribute("key");
            resp.getWriter().write(value == null ? "null" : value.toString());
        } else if (req.getRequestURI().endsWith("reload")) {
            session.invalidate();
            session = req.getSession();
            session.setAttribute("first-key", "first-value");
            session.setAttribute("second-key", "second-value");
            resp.getWriter().write("true");
        } else if (req.getRequestURI().endsWith("timeout")) {
            session = req.getSession();
            session.setMaxInactiveInterval(1);
            resp.getWriter().write("true");
        } else if (req.getRequestURI().endsWith("isNew")) {
            session = req.getSession();
            resp.getWriter().write(session.isNew() == true ? "true" : "false");
        } else if (req.getRequestURI().contains("setAttribute")) {
            Enumeration<String> itParams = req.getParameterNames();
            while (itParams.hasMoreElements()) {
                String param = itParams.nextElement();
                Object value = req.getParameter(param);
                session.setAttribute(param, value);
            }
            resp.getWriter().write(session.getId());
        } else if (req.getRequestURI().contains("getAttributes")) {
            StringBuilder result = new StringBuilder();
            Enumeration<String> itParams = req.getParameterNames();
            while (itParams.hasMoreElements()) {
                String param = itParams.nextElement();
                String value = req.getSession(false).getAttribute(req.getParameter(param)) + "";
                result.append(param).append("=").append(value);
                if (itParams.hasMoreElements()) {
                    result.append("&");
                }
            }
            resp.getWriter().write(result.toString());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession();
        session.setAttribute("attr1", "val1");

        if (req.getRequestURI().endsWith("login")) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(req.getInputStream()));
            StringBuilder result = new StringBuilder();
            String line;
            while((line = reader.readLine()) != null){
                result.append(line);
            }

            resp.getWriter().write(result.length() > 0 ? "true" : "false");
        } else if (req.getRequestURI().endsWith("useRequestParameter")) {
            resp.getWriter().write(session.getId());
        }
    }
}
