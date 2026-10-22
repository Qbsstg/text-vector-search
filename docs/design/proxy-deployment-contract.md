# HTTPS代理与Origin校验契约

2026-10-23，目标域名`text-vector-search.lemoncharger.com`。主控负责应用代理配置，服务器角色负责Nginx覆盖转发头；ApiRequestFilter仍按Spring识别的有效scheme/host/port校验Origin，不放宽跨源规则。

## 已复现的部署组合风险

以原B2可执行JAR、独立本地H2和端口18081实际请求：同源HTTP直连202；Host为新域名、Origin为同域HTTPS，并携带X-Forwarded-Proto=https/Port=443/Host头时，默认未启用转发头识别的应用返回403 ORIGIN_NOT_ALLOWED。此结果证明默认应用配置与拟HTTPS代理组合存在不匹配；不是B1算法缺陷，也不能在未启用该发布组合前声称公网业务已发生故障。

加载真实`application-proxy.yml`于另一独立本地B2实例18082后，相同HTTPS同源请求202，跨源Origin仍403。有效scheme/host/port的独立观测及不信任来源反例由QA继续验收，不能把响应码单独描述为所有信任边界均通过。

## 配置与信任边界

- 部署显式启用`proxy`配置；直连开发默认不信任代理头。采用Tomcat native转发头识别，内部代理仅回环127.0.0.1与IPv6回环；后端仍绑定127.0.0.1:8080。
- Nginx必须覆盖Host、X-Forwarded-Host、X-Forwarded-Proto、X-Forwarded-Port、X-Forwarded-For，并清空外来Forwarded；不得把客户端提供的协议/Host原样转入。
- 外部不能直达8080；本机管理用户/回环代理属于受信边界。不能把internal-proxies扩为任意私网/公网来解决403。
- TLS业务代理和入口认证模板尚未启用；当前TLS站点仅检查端点，所有真实API/H2/管理路径拒绝。正式发布需业务测试门禁及用户另行确认。

新增显式配置前后默认Java17全量53unit+44IT通过，原application.yml/B1规则未改。私钥、入口凭据及服务器env不进入此文档或仓库。
