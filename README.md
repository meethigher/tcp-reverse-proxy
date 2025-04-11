# tcp-reverse-proxy
基于[Vert.x](https://vertx.io/)实现的HTTP反向代理与TCP反向代理、内网穿透

开发环境

* jdk: 8
* vertx: 4.5.10

依赖引入，可以访问[mvnrepository.com](https://mvnrepository.com/artifact/top.meethigher/tcp-reverse-proxy)查看版本

```xml
<dependency>
    <groupId>top.meethigher</groupId>
    <artifactId>tcp-reverse-proxy</artifactId>
    <version>${tcp-reverse-proxy.version}</version>
</dependency>
<dependency>
    <groupId>io.vertx</groupId>
    <artifactId>vertx-core</artifactId>
    <version>4.5.10</version>
</dependency>
<!-- 若不使用http反向代理，可不加此依赖 -->
<dependency>
    <groupId>io.vertx</groupId>
    <artifactId>vertx-web</artifactId>
    <version>4.5.10</version>
</dependency>
<!-- 若不想添加日志，可只添加slf4j-api -->
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.2.12</version>
</dependency>
<!-- 若不使用TCP内网穿透，可不加此依赖 -->
<dependency>
    <groupId>com.google.protobuf</groupId>
    <artifactId>protobuf-javalite</artifactId>
    <version>4.30.2</version>
</dependency>
```

## 一、TCP反向代理

实现TCP反向代理：`0.0.0.0:22`↔️`10.0.0.1:8080`

```java
ReverseTcpProxy.create(Vertx.vertx(), "10.0.0.1", 8080)
        .port(22)
        .start();
```

## 二、TCP内网穿透

虚线表示进程内部通信。实线表示外部通信。

一些代码上的设计思路，参考[socket.io-client-java](https://github.com/socketio/socket.io-client-java/blob/socket.io-client-2.1.0/src/main/java/io/socket/client/Socket.java)

```mermaid
sequenceDiagram
    autonumber
    participant u as User
    participant dps as DataProxyServer
    participant ts as TunnelServer
    participant tc as TunnelClient
    participant bs as BackendServer
    
    ts-->ts: 监听44444端口
    tc->>ts: 建立控制连接、发送鉴权密钥、申请启用22端口数据服务
    ts-->ts: 鉴权校验通过
    ts-->>dps: 你要开启22端口
    dps-->dps: 监听22端口
    dps-->>ts: 已开启
    ts->>tc: 成功
    tc-->tc: 开启与控制服务的周期心跳
    loop 控制连接保活
      tc->>ts: 发送心跳
      ts->>tc: 响应心跳
    end
    u->>dps: 建立数据连接
    dps-->>ts: 通知
    ts->>tc: 你需要主动与22数据服务建立数据连接
    tc->>dps: 建立数据连接
    note left of dps: 用户连接和数据连接绑定双向生命周期、双向数据传输
    tc->>bs: 建立后端连接
    dps->>tc: 成功
    bs->>tc: 成功
    note right of tc: 数据连接和后端连接绑定双向生命周期、双向数据传输
    
    u->dps: 双向传输
    dps->tc: 双向传输
    tc->bs: 双向传输 
    

```

假如我有一个内网`SSH`服务`10.0.0.10:22`，需要通过`192.168.0.200:22`穿透出去。并且网络条件受限如下

1. `10.0.0.10`可以主动连接`192.168.0.200`
2. `192.168.0.200`无法主动连接`10.0.0.10`
3. 只要双方建立连接，即可实现双向数据传输

这就需要TCP内网穿透了。假设你内网穿透使用的控制端口为`44444`。

首先，在`192.168.0.200`这台机器，使用如下代码启动`TunnelServer`

```java
ReverseTcpProxyTunnelServer.create(Vertx.vertx())
        .port(44444)
        // 用于用户连接和数据连接的延迟判定，如果网络较差/DNS解析较慢的情况下，建议将该参数调大
        .judgeDelay(2000)
        .start();
```

在`10.0.0.10`这台机器，使用如下代码启动`TunnelClient`

```java
ReverseTcpProxyTunnelClient.create(Vertx.vertx())
        .backendHost("10.0.0.10")
        .backendPort(22)
        .dataProxyName("ssh-proxy")
        .dataProxyHost("192.168.0.200")
        .dataProxyPort(22)
        .connect("192.168.0.200", 44444);
```



## 三、HTTP反向代理

实现HTTP反向代理，代理路由优先级如下

1. `/local/*`↔️`http://127.0.0.1:888`
   * `http://127.0.0.1:8080/local/1`↔️`http://127.0.0.1:888/1`
   * `http://127.0.0.1:8080/local/1/2/3`↔️`http://127.0.0.1:888/1/2/3`
2. `/*`↔️`https://reqres.in`
   * `http://127.0.0.1:8080/api/users?page=2`↔️`https://reqres.in/api/users?page=2`

HTTP反向代理支持如下配置

1. 请求头转发客户端IP: 默认值F
2. 保留响应头Cookie: 默认值T
3. 保留请求头Host: 默认值F
4. 跟随跳转: 默认值T
5. 长连接: 默认值T
6. 日志及日志格式自定义
7. 代理服务完全接管跨域控制: 默认值F

```java
// addRoute第二个参数表示优先级，值越小、优先级越高
ReverseHttpProxy.create(vertx).port(8080)
        .addRoute(new ProxyRoute()
                .setName("proxy")
                .setSourceUrl("/local/*")
                .setTargetUrl("http://127.0.0.1:888"),-1)
        .addRoute(new ProxyRoute()
                .setName("proxy")
                .setSourceUrl("/*")
                .setTargetUrl("https://reqres.in"),1)
        .start();
```

 
