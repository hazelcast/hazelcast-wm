package com.hazelcast.wm.test;

public interface ServletContainer {

    void restart() throws Exception;

    void stop() throws Exception;

    void start() throws Exception;

    boolean isRunning();

}
