# CLAUDE.md — NetGuard 项目工作说明与标准索引

> 本文件是 AI 助手与开发者进入本仓库的**第一入口**。动手改代码前，先读这里的路径索引，再读对应的 `docs/` 标准文件。
> 项目：NetGuard 网络故障告警监控平台（Spring Boot 2.7.18 + Thymeleaf + MyBatis-Plus + MySQL 8 + Redis + RabbitMQ + Spring Security）
> 根包：`com.netguard`　构建产物：`target/netguard.jar`

---

## 一、常用命令

```powershell
# 一键启动（推荐）：起 MySQL → 需要时编译 → 启动 → 打开浏览器
#   双击 一键启动.bat；强制重编：一键启动.bat build；停止：一键停止.bat
#   JDK 自动查找（JAVA_HOME → 注册表 → PATH → 常见目录 → 全盘），无需写死路径
#   只想看本机有哪些 JDK：双击 tools\查找JDK.bat 或
#     powershell -NoProfile -ExecutionPolicy Bypass -File tools\find-jdk.ps1
#   Maven/MySQL 也自动识别（默认目录 → PATH）；换机器可用 NG_JAVA_HOME /
#   NG_MVN_HOME / NG_MYSQL_HOME 覆盖，无需改脚本；没有 Maven 时若 jar 已存在则跳过编译
#   只需看本机有没有 MySQL：双击 tools\查找MySQL.bat（find-mysql.ps1 -Quiet 供 bat 捕获）
#   登录不上：tools\修复登录.bat（走 mysql < 文件，避免 $ 展开）或 tools\重置密码并启动.bat
#   注意：机器上没有 MySQL 时登录必然失败（账号在库里，登录页本身不查库）
#   控制台为 GBK+CRLF，改动时勿转 UTF-8

# 构建（Windows 本机需先设置环境变量，注意中文注释乱码问题）
$env:JAVA_HOME='D:\devtools\jdk17\jdk-17.0.2'
$env:MAVEN_OPTS='-Dfile.encoding=UTF-8'
$env:PATH="$env:JAVA_HOME\bin;D:\devtools\maven\apache-maven-3.9.9\bin;$env:PATH"
mvn -B -DskipTests package

# 启动（开发环境：MQ 与邮件关闭，Redis 缺失自动降级内存缓存）
java -jar target/netguard.jar --spring.profiles.active=dev
# 或双击 start-dev.bat（同样自动查找 JDK）

# 数据库初始化（MySQL 已启动、root/123456）
cmd /c "chcp 65001 >nul && \"D:\devtools\mysql\mysql-8.0.28-winx64\bin\mysql.exe\" -u root -p123456 --default-character-set=utf8mb4 < \"sql\netguard.sql\""

# 访问
# http://localhost:8080     admin/admin123（ADMIN）   ops/ops123（OPS）

# IDEA 打开：先双击 start-mysql.bat，再运行共享配置「NetGuardApplication（dev）」
# （.run/NetGuardApplication.run.xml 已带 --spring.profiles.active=dev 与 -Dfile.encoding=UTF-8）
```

**环境事实**：JDK 17 位于 `D:\devtools\jdk17\jdk-17.0.2`；Maven 3.9.9 位于 `D:\devtools\maven\apache-maven-3.9.9`（`conf/settings.xml` → 本地仓库 `D:\devtools\m2repo`、阿里云镜像）；MySQL 8.0.28 位于 `D:\devtools\mysql\mysql-8.0.28-winx64`（端口 3306，库 `netguard`）；本机**无 Redis / RabbitMQ / SMTP**，dev 环境走降级实现。

---

## 二、标准文件索引（按需阅读，勿凭记忆写代码）

| 需要了解 | 读这份文件 |
| --- | --- |
| 项目做什么、角色能做什么、验收标准 | `docs/01-需求分析说明书.md` |
| 技术选型、分层架构、关键设计决策、包结构 | `docs/02-技术架构与选型说明.md` |
| 表结构、字段含义、ER 图、索引与初始化数据 | `docs/03-数据库设计说明书.md` |
| URL 路由、页面清单、JSON 契约、表单校验、模板写法 | `docs/04-接口与页面设计规范.md` |
| 命名、分层职责、异常/日志/注释/提交规范 | `docs/05-编码规范.md` |
| 认证授权、CSRF、注入与 XSS、上传安全、权限矩阵 | `docs/06-安全设计规范.md` |
| 开发阶段划分、里程碑、模块顺序、风险应对 | `docs/07-开发执行步骤与里程碑.md` |
| 测试用例、验收清单、缺陷记录 | `docs/08-测试用例与验收标准.md` |
| Linux/Nginx/systemd 部署、备份、故障排查 | `docs/09-部署运维手册.md` |
| **部署到公网服务器**（买机、安全组、MySQL、域名、HTTPS、验收清单） | `docs/11-公网部署指南.md` |
| 交付物清单、模块分工、答辩要点 | `docs/10-项目开发文档.md` |
| 答辩口播稿（5 分钟标准版 / 2.5 分钟极简版）与提问应对 | `docs/答辩演讲稿.md` |
| 每日进展与待办 | `开发日志/README.md`、`开发日志/YYYY-MM-DD.md` |

**部署相关文件**：`deploy/nginx.conf`、`deploy/netguard.service`、`deploy/deploy.sh`、`deploy/README.md`。
**数据库脚本**：`sql/netguard.sql`（建库 + 9 表 + 测试数据 + BCrypt 账号）。

---

## 三、代码结构导览

```
src/main/java/com/netguard
├── common/        统一响应 Result/ResultCode、枚举 enums、缓存键 constant、事件 event
├── config/        SecurityConfig、MybatisPlusConfig、WebMvcConfig、AsyncConfig、
│                  SchedulingConfig、RabbitConfig、AppProperties、GlobalModelAdvice
├── controller/    页面控制器（Auth/Dashboard/Device/DeviceType/Alarm/Rule/Inspect/Notify/Profile）
│   └── api/       JSON 接口（DashboardApiController）
├── dto/           表单对象（JSR-303 校验）：DeviceForm/RuleForm/PasswordForm/ProfileForm/AlarmConfirmForm/DeviceTypeForm
├── entity/        9 张表对应实体（MyBatis-Plus 注解）
├── exception/     BusinessException、GlobalExceptionHandler
├── mapper/        Mapper 接口（继承 BaseMapper）+ resources/mapper/*.xml 动态 SQL
├── mq/            AlarmMessage、发布者（Rabbit / Local 双实现）、AlarmNotifyListener
├── query/         DeviceQuery、AlarmQuery、NotifyQuery
├── security/      LoginUser、UserDetailsServiceImpl、SecurityUtils、LoginSuccessHandler
├── service/       业务接口与实现（Device/Inspect/Alarm/Notify/Dashboard/AlarmRule/DeviceType/SysUser/Cache/Mail/File）
├── task/          DynamicInspectScheduler（动态巡检）、DailyStatJob（每日汇总）
├── utils/         ProbeUtils（TCP/ICMP）、JsonUtils、DurationUtils、ProbeResult
└── vo/            视图对象（DeviceVO/AlarmRecordVO/DashboardVO/CountItemVO/DeviceFaultRankVO）

src/main/resources
├── application.yml / application-dev.yml / application-test.yml / application-prod.yml
├── logback-spring.xml
├── mapper/*.xml                       动态 SQL
├── templates/                         15 个页面 + fragments/layout.html（共 16 个 HTML）
└── static/css/style.css、static/js/{app.js,dashboard.js,echarts.min.js}

tools/
├── find-jdk.ps1                       自动查找 JDK（-Quiet 供 bat 捕获路径，-Json 供脚本消费）
├── 查找JDK.bat                        双击查看本机 JDK 列表（GBK+CRLF）
├── find-mysql.ps1                     自动查找 MySQL（环境变量/注册表/服务/常见目录/盘符，缺 bin 两个 exe 不算）
├── 查找MySQL.bat                      双击查看本机 MySQL 列表；找不到时给出安装与 NG_MYSQL_HOME 指引
├── 修复登录.bat                       用 mysql < 文件 跑 维护-重置管理员密码.sql（绕开 shell 的 $ 展开）
└── 重置密码并启动.bat                 带 --app.reset-admin=true 启动，开机强制重置 admin/ops 密码
```

---

## 四、开发工作说明（必须遵守）

### 1. 每次开始工作前
1. 读 `开发日志/` 最新一篇的「明日待办」，确认本次目标；
2. 涉及数据结构的改动先读 `docs/03`，涉及页面/接口先读 `docs/04`；
3. 改动安全相关代码（`SecurityConfig`、登录、上传、SQL）必读 `docs/06`。

### 2. 每次结束工作前（强制）
1. 在 `开发日志/YYYY-MM-DD.md` 追加：**今日完成 / 关键决策 / 遇到问题与解决 / 明日待办**（四个区块，缺一不可）；
2. 若改了表结构，同步更新 `sql/netguard.sql` 与 `docs/03`；
3. 若新增路由或表单，同步更新 `docs/04` 的路由表与校验表；
4. 提交前跑一次 `mvn -B -DskipTests package`，确保可构建。

### 3. 编码硬性约束
- **技术栈锁定**：Spring Boot 2.7.x + Thymeleaf 服务端渲染，禁止引入 Vue/React 或任何前端构建工具；
- **分层**：`controller → service → mapper`，禁止 controller 直接调 mapper，禁止 service 返回 entity 给页面时带出 `password`；
- **SQL**：MyBatis 一律 `#{}`，禁止 `${}` 拼接用户输入；模糊查询用 `CONCAT('%',#{kw},'%')`；
- **密码**：只存 BCrypt（强度 10），禁止明文与可逆加密；
- **写操作**：只允许 POST + CSRF（`th:action` 自动注入），GET 不得产生副作用；
- **异常**：业务失败抛 `BusinessException(ResultCode, msg)`，禁止 `e.printStackTrace()` 与吞异常；
- **日志**：关键操作记录操作人，禁止打印密码、完整邮箱；
- **注释率 ≥15%**：类头 Javadoc + 公共方法注释 + 复杂逻辑行内注释。

### 4. Thymeleaf 三条铁律（踩过坑）
1. **禁止在一个属性里写两个 `${}` 参与三元**，否则原样输出。正确：`th:text="${a != null ? #temporals.format(a,'yyyy-MM-dd HH:mm:ss') : '—'}"`；错误：`th:text="${a != null} ? ${...} : '—'"`。
2. 三元的条件与取值都写变量名，不要嵌套 `${}`；链接动态化用 `th:with` 先算好路径再 `th:action="@{${formAction}}"`。
3. 用户输入一律 `th:text`（自动转义），禁止 `th:utext`。

### 5. 关键业务规则（改代码前必须知道）
- **巡检间隔**：只允许 5/10/30 分钟，保存规则后通过 `RuleChangedEvent` 热重排 `ThreadPoolTaskScheduler`，无需重启；
- **告警级别**：设备 `core_flag=1` 且规则 `urgent_enabled=1` → `URGENT`，否则 `NORMAL`；
- **告警只触发一次**：`device.offline_count == fail_threshold` 时创建告警（用 `==` 不是 `>=`）；
- **恢复闭环**：探测成功且此前为 `OFFLINE` → 原告警置 `RECOVERED` 并写 `duration_minutes`，同时新增一条 `RECOVER` 通知记录；
- **降级开关**：`app.cache-mode`（redis/memory）、`app.mq.enabled`、`app.mail.enabled`，dev 环境关闭 MQ 与邮件以保证可演示；
- **演示账号**：`admin/admin123`（唯一有设备增删改、类型与规则配置权限）、`ops/ops123`（查看 + 确认告警）。

### 6. 目录卫生
- 上传文件落 `uploads/yyyy/MM/`，不进版本库；构建产物 `target/`、日志 `logs/` 同样忽略；
- 临时脚本放 `D:\devtools\tmp`，不要污染项目根目录。

---

## 五、快速自检清单（提测前）

- [ ] `mvn -B -DskipTests package` 成功
- [ ] dev 启动无 ERROR，Redis 降级日志符合预期
- [ ] 登录 → 看板 → 设备/巡检/告警/统计/通知/规则/个人中心 全部可打开
- [ ] ADMIN 与 OPS 权限边界正确（OPS 访问 `/types`、`/rule` 得 403）
- [ ] 新增/编辑/删除设备后列表与看板数据一致
- [ ] 手动巡检生成批次与明细，离线设备触发告警
- [ ] 告警可确认，恢复后状态闭环
- [ ] `开发日志/` 当天文件已更新，`docs/` 相关标准已同步
