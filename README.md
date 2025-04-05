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

```mermaid
sequenceDiagram
    participant user as User
    participant ts as TunnelServer
    participant tc as TunnelClient
    participant rs as RealServer
    
    ts->>ts: 1. 启动ts，监听tcp控制端口
    tc->>tc: 2. 启动tc
    tc->>ts: 3. 与ts控制端口建立控制连接，并传入tcp数据端口
    ts-->>tc: 4. ts认证通过后，监听tcp数据端口，并返回响应
    loop 控制连接实现长连接
      tc-->>ts: 发送心跳
      ts-->>tc: 响应心跳
    end
    
    user->>ts: 5. 与数据端口建立连接，传输数据
    ts-->>tc: 6. 通过控制连接发送：有新的请求进来，需要你主动与我建立数据连接
    tc->>ts: 7. 主动建立数据连接
    ts-->>tc: 8. 通过数据连接转发用户请求
    tc->>rs: 9. 请求真实服务
    rs-->>tc: 10. 服务响应
    tc-->>ts: 11. 隧道响应
    ts-->user: 12. 返回最终结果
   
   

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

 
