# 代理Origin独立验收与交接

最终执行基线：`de71eac7c0ee1ea9eda3266dc2e3497c49410714`；分支`agent/qa`；Java17，2026-10-23。
修改仅`acceptance/ProxyOriginIT.java`及本说明。未改生产过滤器、主控proxy资源、健康类或B1；未接触证书/私钥，不访问远程业务API。

## 真实执行

- 首次使用与拟发布资源相同CLI参数的目标验收：3 IT通过，00:41:27 +08:00。主控同步已提交资源后，删除CLI替代配置，改为实际`--spring.profiles.active=proxy`。
- 实际profile目标验收：`./mvnw -B -Dit.test=ProxyOriginIT verify`，53 unit + 3 IT通过，00:41:59 +08:00；直接断言读取到native策略及严格回环正则。
- 完整`./mvnw -B clean verify`：53 unit + 47 IT = **100通过**，失败/错误/跳过0，00:42:35 +08:00 BUILD SUCCESS。
- Maven真实命令附`-ntp -s /private/tmp/12times-maven-settings.xml -Dmaven.repo.local=/private/tmp/12times-m2`，JAVA_HOME指向Temurin17，MAVEN_USER_HOME=/private/tmp/12times-maven-user-home，仅为本机下载适配。
- 日志：`/private/tmp/12times-qa-proxy-target.log`（首次CLI）、`/private/tmp/12times-qa-proxy-profile-target.log`（真实profile）、`/private/tmp/12times-qa-proxy-verify.log`（最终全回归）。无编译/测试失败，无因期望不符降低断言。

## 独立观测

测试启动生产B2真实应用、随机127.0.0.1端口与临时绝对文件H2。仅测试源码显式导入@TestConfiguration观察过滤器，早于Origin过滤器记录Servlet有效三元组，没有生产调试端点。原始socket报文显式发送Host与Origin，避免客户端自动改写受限头。

| 配置/输入 | 实际有效scheme/host/port | 实际响应 |
| --- | --- | --- |
| 默认，无forwarded，Origin=http同域 | http / text-vector-search.lemoncharger.com / 80 | 202 |
| 默认，Origin=https同域，X-Forwarded-Proto/Host/Port=HTTPS同域443 | http / 同域 / 80 | 403 ORIGIN_NOT_ALLOWED |
| 真实proxy profile，可信回环及相同X-Forwarded组合 | https / 同域 / 443 | 202 |
| 同profile与X-Forwarded组合，但外站Origin | https / 同域 / 443 | 403 ORIGIN_NOT_ALLOWED |
| 同profile，X-Forwarded正确但恶意Forwarded宣称http/evil.example:81 | https / 同域 / 443 | 202；恶意Forwarded未覆盖native三元组 |
| 同profile，只有Forwarded宣称https同域，没有X-Forwarded | http / 同域 / 80 | 403 ORIGIN_NOT_ALLOWED |

这些结果确认默认应用与拟HTTPS代理的部署组合不匹配，启用受控native profile后该组合通过；不是B1算法问题，也不需要放宽Origin过滤器。native配置不采用Spring framework无条件信任Forwarded的路径。

## 非信任地址边界

实际尝试绑定源127.0.0.2时，本Mac返回BindException（未配置该回环别名）；该真实socket来源测试 **未执行完成**，没有伪称公网访问或跳过算通过。
按预先授权的替代路径，第三项测试直接调用Tomcat实际RemoteIpValve：remoteAddr=127.0.0.2，严格回环internal-proxies，携带恶意X-Forwarded-Proto=https/Host=evil.example/Port=443/For头。Valve下游实测仍为http/原域名/80、remoteAddr=127.0.0.2，secure=false。组件反例断言通过；这是组件信任边界证据，不等于实际外部网络验证。测试日志明确输出该分支。

Nginx覆盖客户端转发头及清空Forwarded、外部不可直达8080、TLS入口认证和实际公网发布仍由部署/主控另验，本测试不证明这些已完成。当前仅本机测试，未开启服务器真实API。
B3终态/恢复/关闭及B4搜索仍NOT_RUN；收到B3源码基线后再恢复主验收。本短任务完成后释放槽位供PM轮换，提交SHA见交付消息，合入后主控对最终集成SHA复跑。
