package com.hazelcast.wm.test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;

import jakarta.servlet.ServletException;
import java.io.File;
import java.io.IOException;

public class TomcatServer implements ServletContainer {

    Tomcat tomcat;
    String serverXml;
    String sourceDir;
    int port;
    volatile boolean running;

    public TomcatServer(int port, String sourceDir, String serverXml) throws Exception {
        this.port = port;
        this.serverXml = serverXml;
        this.sourceDir = sourceDir;
        buildTomcat(sourceDir, serverXml);
    }

    @Override
    public void stop() throws Exception {
        if (running) {
            tomcat.stop();
            tomcat.destroy();
            running = false;
        }
    }

    @Override
    public void start() throws Exception {
        buildTomcat(sourceDir, serverXml);
        running = true;
    }

    @Override
    public void restart() throws Exception {
        stop();
        Thread.sleep(5000);
        start();
    }

    public void buildTomcat(String sourceDir, String serverXml) throws LifecycleException, ServletException, IOException {
        tomcat = new Tomcat();
        tomcat.setPort(port);


        File baseDir = new File(System.getProperty("java.io.tmpdir"));
        tomcat.setBaseDir(baseDir.getCanonicalPath());

        Context context = tomcat.addWebapp("/", sourceDir);
        context.setAltDDName(sourceDir + "/WEB-INF/" + serverXml);

        context.setCookies(true);
        context.setBackgroundProcessorDelay(1);
        context.setReloadable(true);
        tomcat.getEngine().setJvmRoute("tomcat" + port);
        tomcat.getEngine().setName("tomcat-test" + port);
        tomcat.start();
        running = true;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
