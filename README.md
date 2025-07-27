# tcp-reverse-proxy
基于[Vert.x](https://vertx.io/)实现的网络库。支持HTTP反向代理、TCP反向代理、TCP内网穿透、TCP单端口多路复用

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

## 一、HTTP反向代理

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





## 二、TCP反向代理

实现TCP反向代理：`0.0.0.0:22`↔️`10.0.0.1:8080`

```java
ReverseTcpProxy.create(Vertx.vertx(), "10.0.0.1", 8080)
        .port(22)
        .start();
```

## 三、TCP内网穿透

虚线表示控制连接通信，实线表示非控制连接通信。

一些代码上的设计思路，参考[socket.io-client-java](https://github.com/socketio/socket.io-client-java/blob/socket.io-client-2.1.0/src/main/java/io/socket/client/Socket.java)

```mermaid
sequenceDiagram
    autonumber
    participant u as User
    participant dps as DataProxyServer
    participant ts as TunnelServer
    participant tc as TunnelClient
    participant bs as BackendServer
    bs->bs: 监听22端口
    ts->ts: 监听44444端口
    tc-->>ts: 建立控制连接、发送鉴权密钥、申请启用2222端口数据服务
    ts->ts: 鉴权校验通过
    ts->>dps: 你要开启2222端口
    dps->dps: 监听2222端口
    dps->>ts: 已开启
    ts-->>tc: 端口已启用
    tc->tc: 开启与控制服务的周期心跳
    loop 控制连接保活
      tc-->>ts: 发送心跳
      ts-->>tc: 响应心跳
    end
    u->>dps: 建立用户连接
    dps->>ts: 
    ts-->>tc: 你需要主动与2222数据服务端口建立数据连接
    tc->>dps: 建立数据连接，并告知对方“我是数据连接”
    tc-->>ts: 连接已建立
    note left of dps: 用户连接和数据连接绑定双向生命周期、双向数据传输
    dps->>dps: 绑定用户连接与数据连接
    dps->>tc: 用户连接与数据连接已绑定
    tc->>bs: 建立后端连接
    bs->>tc: 后端连接已建立
    note right of tc: 数据连接和后端连接绑定双向生命周期、双向数据传输
    tc->>tc: 绑定数据连接和后端连接 
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

## 四、TCPMux单端口多路复用

参考[RFC 1078 - TCP port service Multiplexer (TCPMUX)](https://datatracker.ietf.org/doc/html/rfc1078)

现有场景如下



```mermaid
flowchart LR
    subgraph Network-A
      TMC-1[<b>TCPMux Client-1</b><br>Listen :6666]
      TMC-2[<b>TCPMux Client-2</b><br>Listen :6667]
      TMC-3[<b>TCPMux Client-3</b><br>Listen :6668]
    end

    TMS[<b>TCPMux Server</b></br>Listen :44444]

    subgraph Network-B
      SSH[<b>SSH</b><br>Listen :22]
      PSQL[<b>PostgreSQL</b><br>Listen :5432]
      MYSQL[<b>MySQL</b><br>Listen :3306]
      HTTP-1[<b>HTTP-1</b><br>Listen :80]
      HTTP-2[<b>HTTP-2</b><br>Listen :80]
    end


    TMC-1 -->|service HTTP-1|TMS -->HTTP-1
    TMC-2 -->|service HTTP-2|TMS -->HTTP-2
    TMC-3 -->|service SSH|TMS -->SSH
```



上述场景代码实践

```java
// TCPMux Server
ReverseTcpProxyMuxServer.create(Vertx.vertx())
        .port(44444)
        .start();

// TCPMux Client
Map<MuxNetAddress, NetAddress> map = new LinkedHashMap<>();
map.put(new MuxNetAddress(6666, "HTTP-1"), new NetAddress("HTTP-1", 80));
map.put(new MuxNetAddress(6667, "HTTP-2"), new NetAddress("HTTP-2", 80));
map.put(new MuxNetAddress(6668, "SSH"), new NetAddress("SSG", 22));
NetAddress muxServerAddress = new NetAddress("10.0.0.1", 44444);
ReverseTcpProxyMuxClient.create(Vertx.vertx(), map, muxServerAddress)
        .start();
```



TCPMux实现思路

```mermaid
sequenceDiagram
  participant u as User
  participant c as TcpMuxClient
  participant s as TcpMuxServer
  participant b1 as Backend1
  participant b2 as Backend2
  
  autonumber

  s->>s: 监听44444端口
  c->>c: 监听22222端口
  u->>c: 1. 建立用户连接 2. 发送数据
  c->>s: 1. 建立数据连接 2. 发送数据（包括加密的mux配置信息、用户连接的数据包）
  note left of c: 用户连接和数据连接绑定双向生命周期、双向数据传输
  s->>s: 对mux配置信息进行解密，获取实际的后端地址
  s->>b2: 1. 建立后端连接 2. 转发用户连接的数据包
  note left of s: 数据连接和后端连接绑定双向生命周期、双向数据传输
  b2->>s: 
  s->>c: 
  c->>u: 
```



