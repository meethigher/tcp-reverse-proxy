# tcp-reverse-proxy
基于[Vert.x](https://vertx.io/)实现的TCP反向代理与HTTP反向代理。

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
<dependency>
    <groupId>io.vertx</groupId>
    <artifactId>vertx-web</artifactId>
    <version>4.5.10</version>
</dependency>
```

## TCP反向代理

实现TCP反向代理：`0.0.0.0:22`↔️`10.0.0.1:8080`

```java
ReverseTcpProxy.create(Vertx.vertx(), "10.0.0.1", 8080)
        .port(22)
        .start();
```

## TCP内网穿透

实现表示建立连接/监听端口等等。

虚线表示连接通信。

一些代码上的设计思路，参考[socket.io-client-java](https://github.com/socketio/socket.io-client-java/blob/socket.io-client-2.1.0/src/main/java/io/socket/client/Socket.java)

```mermaid
sequenceDiagram
    participant user as User
    participant ts as TunnelServer
    participant tc as TunnelClient
    participant rs as RealServer
    
    ts->>ts: 1. 启动ts，监听tcp控制端口
    note left of ts: 明确tc与ts之间的编解码
    tc->>ts: 2. 建立控制连接，并进行认证
    ts-->>tc: 3. 控制连接：认证成功
    loop 控制连接实现长连接
      tc-->>ts: 控制连接：发送心跳
      ts-->>tc: 控制连接：响应心跳
    end
    tc-->>ts: 4. 控制连接：要求ts监听数据传输端口。如2222端口
    ts->>ts: 5. 监听2222数据端口
    ts-->>tc: 6. 控制连接：已监听2222数据端口

    
    user->>ts: 7. 与2222端口建立用户连接，传输数据
    ts-->>tc: 8. 控制连接：有新的请求进来，需要你主动与我的2222端口建立数据连接
    tc->>ts: 9. 与2222端口建立数据连接
    tc-->>ts: 10. 控制连接：已建立
    ts->>ts: 11. 将连入2222端口的user用户连接和tc数据连接进行绑定
    note left of ts: 区分用户连接和数据连接
    ts-->>tc: 12. 数据连接：传输用户请求数据
    tc->>rs: 13. 请求真实服务
    rs-->>tc: 14. 服务响应
    tc-->>ts: 15. 数据连接：隧道响应
    ts-->user: 16. 返回最终结果
```



## HTTP反向代理

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

 
