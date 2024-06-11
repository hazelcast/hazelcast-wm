# Table of Contents

* [Filter Based Web Session Replication](#filter-based-web-session-replication)
* [Session Clustering Requirements](#session-clustering-requirements)
* [Setting Up Session Clustering](#setting-up-session-clustering)
* [Using High-Density Memory Store](#using-high-density-memory-store)
* [Supporting Spring Security](#supporting-spring-security)
* [Client Mode vs. P2P Mode](#client-mode-vs-p2p-mode)
* [Caching Locally with `deferred-write`](#caching-locally-with-deferred-write)
* [SessionId Generation](#sessionid-generation)
* [Defining Session Expiry](#defining-session-expiry)
* [Using Sticky Sessions](#using-sticky-sessions)
* [Marking Transient Attributes](#marking-transient-attributes)
* [Upgrading from v4.0](#upgrading-from-v40)

# Filter Based Web Session Replication


***Sample Code***: *Please see our <a href="https://github.com/hazelcast/hazelcast-code-samples/tree/master/hazelcast-integration/filter-based-session-replication" target="_blank">sample application</a> for Filter Based Web Session Replication.*
<br></br>
*You can also check [Hazelcast Guides: Session Replication with Spring Boot and Hazelcast](https://guides.hazelcast.org/springboot-webfilter-session-replication/).*

***Note***: *Hazelcast 5.0+ is compatible with Hazelcast-WM 5.0. For older Hazelcast versions, use the latest Hazelcast-WM 3.x.x or 4.x.x releases.*
<br></br>

Assume that you have more than one web server (A, B, C) with a load balancer in front of it. If server A goes down, your users on that server will be directed to one of the live servers (B or C), but their sessions will be lost.

We need to have all these sessions backed up somewhere if we do not want to lose the sessions upon server crashes. Hazelcast Web Manager (WM) allows you to cluster user HTTP sessions automatically. 

# Session Clustering Requirements

The following are required before you can enable Hazelcast Session Clustering:

-   Target application or web server should support Java 1.8 or higher.

-   Target application or web server should support Servlet 4.0 or higher spec. Note that this library supports, "javax" namespace, not the new "jakarta" one. 

-   Session objects that need to be clustered have to be Serializable.

-   In the client/server architecture, session classes do not have to be present in the server classpath.

# Setting Up Session Clustering

To set up Hazelcast Session Clustering:

-	Put the `hazelcast` and `hazelcast-wm` jars in your `WEB-INF/lib` folder. Optionally, if you wish to connect to a cluster as a client, add `hazelcast-client` as well.

-	Put the following XML into the `web.xml` file. Make sure Hazelcast filter is placed before all the other filters if any; you can put it at the top.

```xml             
<filter>
  <filter-name>hazelcast-filter</filter-name>
  <filter-class>com.hazelcast.web.WebFilter</filter-class>

  <init-param>
    <param-name>map-name</param-name>
    <param-value>my-sessions</param-value>
  </init-param>
  <init-param>
    <param-name>session-ttl-seconds</param-name>
    <param-value>10</param-value>
  </init-param>
  <init-param>
      <param-name>keep-remote-active</param-name>
      <param-value>false</param-value>
  </init-param>
  <init-param>
    <param-name>sticky-session</param-name>
    <param-value>true</param-value>
  </init-param>
  <init-param>
    <param-name>cookie-name</param-name>
    <param-value>hazelcast.sessionId</param-value>
  </init-param>
  <init-param>  
    <param-name>cookie-domain</param-name>
    <param-value>.mywebsite.com</param-value>
  </init-param>
  <init-param>  
    <param-name>cookie-secure</param-name>
    <param-value>false</param-value>
  </init-param>
  <init-param>  
    <param-name>cookie-http-only</param-name>
    <param-value>false</param-value>
  </init-param>
  <init-param>  
    <param-name>config-location</param-name>
    <param-value>/WEB-INF/hazelcast.xml</param-value>
  </init-param>
  <init-param>  
    <param-name>instance-name</param-name>
    <param-value>default</param-value>
  </init-param>
  <init-param>  
    <param-name>use-client</param-name>
    <param-value>false</param-value>
  </init-param>
  <init-param>  
    <param-name>client-config-location</param-name>
    <param-value>/WEB-INF/hazelcast-client.xml</param-value>
  </init-param>
  <init-param>  
    <param-name>shutdown-on-destroy</param-name>
    <param-value>true</param-value>
  </init-param>
  <init-param>  
    <param-name>deferred-write</param-name>
    <param-value>false</param-value>
  </init-param>
  <init-param>
    <param-name>cookie-path</param-name>
    <param-value>/</param-value>
  </init-param>
  <init-param>
      <param-name>cookie-max-age</param-name>
      <param-value>-1</param-value>
    </init-param>
  <init-param>
    <param-name>use-request-parameter</param-name>
    <param-value>false</param-value>
  </init-param>
</filter>
<filter-mapping>
  <filter-name>hazelcast-filter</filter-name>
  <url-pattern>/*</url-pattern>
  <dispatcher>FORWARD</dispatcher>
  <dispatcher>INCLUDE</dispatcher>
  <dispatcher>REQUEST</dispatcher>
</filter-mapping>

<listener>
  <listener-class>com.hazelcast.web.SessionListener</listener-class>
</listener>
```

- Package and deploy your `war` file as you would normally do.

It is that easy. All HTTP requests will go through Hazelcast `WebFilter` and this `WebFilter` will put the session objects into the Hazelcast distributed map when needed.

Following are the descriptions of parameters included in the above XML.

- `map-name`: Name of the distributed map storing your web session objects.
- `session-ttl-seconds`: Time-to-live value (in seconds) of the distributed map storing your web session objects. It can be any integer between 0 and `Integer.MAX_VALUE`. Its default value is 1800, which is 30 minutes.
- `sticky-session`: If set to true, all requests of a session are routed to the member where the session is first created. This provides better performance. If set to false, when a session is updated on a member, entry for this session on all members is invalidated. You have to know how your load balancer is configured before setting this parameter. Its default value is true.
- `keep-remote-active`: If set to true, it's guaranteed that whenever a session is used the idle-time of this session on the distributed map is reset. The cluster map is already notified for all modification operations but this option might be required when consecutive read-attribute operations are performed without any modification. Since these attributes will be fetched from the local cache (for sticky sessions), the session on the distributed map might be evicted even if the session is active. Note that this is useless when non-sticky sessions are used or max-idle-second is not set for the cluster map. Its default value is false.
- `cookie-name`: Name of the session ID cookie.
- `cookie-domain`: Domain of the session ID cookie. Its default value is based on the incoming request.
- `cookie-path`: Path of the session ID cookie. Its default value is based on the context path of the incoming request.
- `cookie-secure`: Specifies whether the cookie only be sent using a secure protocol. Its default value is false.
- `cookie-http-only`: Specifies whether the attribute `HttpOnly` can be set on cookie. Its default value is false.
- `cookie-max-age`: Specifies the maximum age of the cookie in seconds. Its default value is `-1`, meaning the cookie is not stored persistently and will be deleted when the browser exits.
- `config-location`: Location of Hazelcast configuration. It can be specified as a servlet resource, classpath resource or as a URL. Its default value is `hazelcast-default.xml` or `hazelcast.xml` in the classpath.
- `instance-name`: Name of an existing Hazelcast instance, if you want to use it. Its default value is null. If you do not have an instance, then you should create one.
- `use-client`: Specifies whether you want to connect to an existing cluster as a client. Its default value is false.
- `client-config-location`: Location of the client's configuration. It can be specified as a servlet resource, classpath resource or as a URL. Its default value is null.
- `shutdown-on-destroy`: Specifies whether you want to shut down the Hazelcast instance during the undeployment of your web application. Its default value is true.
- `deferred-write`: Specifies whether the sessions in each instance will be cached locally. Its default value is false.
- `use-request-parameter`: Specifies whether a request parameter can be used by the client to send back the session ID value. Its default value is false.

# Using High-Density Memory Store

<font color="##153F75">**Hazelcast Enterprise HD**</font>
<br></br>

As you see in the above declarative configuration snippet, you provide the name of your map which will store the web session objects:

```
<init-param>
   <param-name>map-name</param-name>
   <param-value>my-sessions</param-value>
</init-param>
```

If you have <font color="##153F75">**Hazelcast Enterprise HD**</font>, you can configure your map to use Hazelcast's High-Density Memory Store. By this way, the filter based web session replication will use a High-Density Memory Store backed map. 

Please refer to the [Using High-Density Memory Store with Map section](#using-high-density-memory-store-with-map) to learn how you can configure a map to use this feature.


# Supporting Spring Security

***Sample Code***: *Please see our <a href="https://github.com/hazelcast/hazelcast-code-samples/tree/master/hazelcast-integration/spring-security" target="_blank">sample application</a> for Spring Security Support.*
<br><br/>

If Spring based security is used for your application, you should use `com.hazelcast.web.spring.SpringAwareWebFilter` instead of `com.hazelcast.web.WebFilter` in your filter definition.

```xml
...

<filter>
  <filter-name>hazelcast-filter</filter-name>
  <filter-class>com.hazelcast.web.spring.SpringAwareWebFilter</filter-class>
    ...
</filter> 

...
```

`SpringAwareWebFilter` notifies Spring by publishing events to Spring context. The `org.springframework.security.core.session.SessionRegistry` instance uses these events.


As before, you must also define `com.hazelcast.web.SessionListener` in your `web.xml`. However, you do not need to define `org.springframework.security.web.session.HttpSessionEventPublisher` in your `web.xml` as before, since `SpringAwareWebFilter` already informs Spring about session based events like `create` or `destroy`. 




# Client Mode vs. P2P Mode

Hazelcast Session Replication works as P2P by default. To switch to Client/Server architecture, you need to set the `use-client` parameter to **true**. P2P mode is more flexible and requires no configuration in advance; in Client/Server architecture, clients need to connect to an existing Hazelcast Cluster. In case of connection problems, clients will try to reconnect to the cluster. The default retry count is 3.
In the client/server architecture, if servers goes down, Hazelcast web manager will keep the updates in the local and after servers come back, the clients will update the distributed map.

Note that, in the client/server mode of session replication, `session-ttl-seconds` configuration does not have any effect. The reason is that the filter based session replication uses IMap and a Hazelcast client cannot change the configuration of a distributed map.
Instead, you should configure the `max-idle-seconds` element in your `hazelcast.xml` on the server side. 

 ```
 ...
   <map name="my-sessions">
         <!--
            How many seconds do you want your session attributes to be stored on server?
            Default is 0.
          -->
        <max-idle-seconds>20</max-idle-seconds>
   </map>
 ...
 ```

 
Also make sure that name of the distributed map is same as the `map-name` parameter defined in your `web.xml` configuration file.


# Caching Locally with `deferred-write`

If the value for `deferred-write` is set as **true**, Hazelcast will cache the session locally and will update the local session when an attribute is set or deleted. At the end of the request, it will update the distributed map with all the updates. It will not update the distributed map upon each attribute update, but will only call it once at the end of the request. It will also cache it, i.e. whenever there is a read for the attribute, it will read it from the cache. 

**Updating an attribute when `deferred-write=false`**:

If `deferred-write` is **false**, any update (i.e. `setAttribute`) on the session will directly be available in the cluster. One exception to this behavior is the changes to the session attribute objects. To update an attribute cluster-wide, `setAttribute` must be called after changes are made to the attribute object.

The following example explains how to update an attribute in the case of `deferred-write=false` setting: 

```
session.setAttribute("myKey", new ArrayList());
List list1 = session.getAttribute("myKey");
list1.add("myValue"); 
session.setAttribute("myKey", list1); // changes updated in the cluster
```

# SessionId Generation

SessionId generation is done by the Hazelcast Web Session Module if session replication is configured in the web application. The default cookie name for the sessionId is `hazelcast.sessionId`. This name is configurable with a `cookie-name` parameter in the `web.xml` file of the application.
`hazelcast.sessionId` is just a UUID prefixed with “HZ” characters and without a “-“ character, e.g. `HZ6F2D036789E4404893E99C05D8CA70C7`.

When called by the target application, the value of `HttpSession.getId()` is the same as the value of `hazelcast.sessionId`.

# Defining Session Expiry

Hazelcast automatically removes sessions from the cluster if sessions are expired on the Web Container. This 
removal is done by `com.hazelcast.web.SessionListener`, which is an implementation of 
`javax.servlet.http.HttpSessionListener`. Session is not removed instantly because it might be active in other nodes of
the cluster. It's done after the period of time specified by `session-ttl-seconds` on `WebFilter` passes (or 
`max-idle-seconds` element in your `hazelcast.xml` on the server side [if client/server mode is used](#client-mode-vs-p2p-mode)). 

Default session expiration configuration depends on the Servlet Container that is being used. You can also define it in your web.xml.

```xml
    <session-config>
        <session-timeout>60</session-timeout>
    </session-config>
```

# Using Sticky Sessions

Hazelcast holds whole session attributes in a distributed map and in a local HTTP session. Local session is required for fast access to data and distributed map is needed for fail-safety.

- If `sticky-session` is not used, whenever a session attribute is updated in a member (in both member local session and clustered cache), that attribute should be invalidated in all other members' local sessions, because now they have dirty values. Therefore, when a request arrives at one of those other members, that attribute value is fetched from clustered cache.

- To overcome the performance penalty of sending invalidation messages during updates, you can use sticky sessions. If Hazelcast knows sessions are sticky, invalidation will not be sent because Hazelcast assumes there is no other local session at the moment. When a server is down, requests belonging to a session hold in that server will routed to other server, and that server will fetch session data from clustered cache. That means that when using sticky sessions, you will not suffer the performance penalty of accessing clustered data and can benefit recover from a server failure.

# Marking Transient Attributes

If you have some attributes that you do not want to be distributed, you can mark those attributes as transient.
Transient attributes are kept in the server and when the server is shutdown, you lose the attribute values.
You can set the transient attributes in your `web.xml` file.
Here is an example:

```
...
   <init-param>
            <param-name>transient-attributes</param-name>
            <param-value>key1,key2,key3</param-value>
   </init-param>
...
```

# Using Request Parameter Instead of Cookie for Sending Back the Session ID

If you have a Servlet that serves REST style `POST` requests and you don't want the clients to use cookies for sending 
back the session ID, you can enable `use-request-parameter` setting and clients can send back the session ID as a 
request parameter. You can enable it in your `web.xml` file:

```
...
   <init-param>
            <param-name>use-request-parameter</param-name>
            <param-value>true</param-value>
   </init-param>
...
```

Note that this causes Hazelcast's `WebFilter` to consume the `ServletRequest#getInputStream` (as it
needs to examine request parameters) so it will not be available to any servlet that is filtered by this `WebFilter`.

# Upgrading from v4.0

There is a guide available to help you upgrade your hazelcast-wm from v4.0 to v5.0. You can find it
[here](./upgrade-guides/v4.0-v5.0.md).