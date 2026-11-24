# NetGuard 部署包说明与快速部署

> 项目：NetGuard 网络故障告警监控平台（Spring Boot 2.7.18 + Thymeleaf + MyBatis-Plus + MySQL 8）
> 构建产物：`target/netguard.jar`（可执行 fat jar，约 47MB）
> 本文档只讲"怎么把包部署起来"；完整运维手参见 `docs/09-部署运维手册.md`。

---

## 一、部署包内容

| 文件 | 作用 | 目标位置 |
| --- | --- | --- |
| `target/netguard.jar` | Spring Boot 可执行 jar（已内嵌 Tomcat，无需另外装容器） | `/opt/netguard/netguard.jar` |
| `deploy/deploy.sh` | 一键部署/回滚/状态/日志脚本（备份 → 停服 → 发布 → 启动 → 健康检查） | `/opt/netguard/deploy.sh` 或就地执行 |
| `deploy/netguard.service` | systemd 托管单元（开机自启、崩溃拉起、journald 收集日志） | `/etc/systemd/system/netguard.service` |
| `deploy/nginx.conf` | Nginx 反向代理 + HTTPS + 静态资源缓存 + 上传目录直读 | `/etc/nginx/conf.d/netguard.conf` |
| `sql/netguard.sql` | 建库 `netguard`、9 张业务表、测试数据、BCrypt 账号 | MySQL 服务器 |
| `src/main/resources/application-prod.yml` | 生产 profile（端口 8080、连接池、Redis/MQ/邮件环境变量） | 已打包进 jar，无需单独上传 |

> 生产连接信息通过 `/opt/netguard/.env` 注入，**不要把密码写进配置文件或提交到仓库**。

### 生产目录结构

```
/opt/netguard/
├── netguard.jar              # 当前运行的应用包（deploy.sh 发布到这里）
├── .env                      # 生产环境变量（权限 600，属主 netguard）
├── deploy.sh                 # 部署脚本（可选，就地执行也可）
├── uploads/                  # 设备图片上传目录（app.upload.dir）
│   └── yyyy/MM/xxx.png       # 应用按年月分目录保存，Nginx 用 alias 直读
├── logs/                     # 应用日志（logback LOG_DIR=./logs）
│   ├── netguard.log
│   ├── netguard.YYYY-MM-DD.N.log
│   └── netguard-error.log
└── backup/                   # 旧 jar 与数据库导出
    ├── netguard.jar.20261124-020000
    └── netguard-sql.20261124-020000.sql.gz
```

---

## 二、快速部署 6 步（Linux，CentOS/Ubuntu 通用）

以下命令默认以 `root` 或带 `sudo` 执行，示例系统用户为 `netguard`。

### 第 1 步：安装 JDK 17

```bash
# CentOS / RHEL
sudo yum install -y java-17-openjdk-headless
# Ubuntu / Debian
sudo apt update && sudo apt install -y openjdk-17-jre-headless

java -version      # 必须输出 17.x，项目 pom.xml 的 java.version=17
```

> 只需 JRE 即可运行；若要在服务器上构建，则装完整 JDK 与 Maven 3.9.x。

### 第 2 步：安装 MySQL 8 并建库导入 SQL

```bash
sudo yum install -y mysql-server        # 或 apt install mysql-server
sudo systemctl enable --now mysqld
sudo mysql_secure_installation          # 设置 root 密码（示例用 123456，生产请改强密码）

# 建库 + 建表 + 测试数据 + 默认账号（脚本开头会 CREATE DATABASE netguard）
mysql -u root -p --default-character-set=utf8mb4 < sql/netguard.sql

# 创建应用专用账号（与 .env 中的 MYSQL_USER/MYSQL_PASSWORD 一致）
mysql -u root -p <<'SQL'
CREATE USER IF NOT EXISTS 'netguard'@'127.0.0.1' IDENTIFIED BY 'netguard123';
GRANT ALL PRIVILEGES ON netguard.* TO 'netguard'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL

mysql -u netguard -p -h 127.0.0.1 netguard -e "SHOW TABLES;"   # 应看到 9 张表
```

> 注意：`application-prod.yml` 的 JDBC URL 固定为 `127.0.0.1:3306/netguard`，用户名 `netguard`，
> 密码取环境变量 `MYSQL_PASSWORD`（未设置时默认 `netguard123`）。
> 若 MySQL 在另一台机器，需要改 `application-prod.yml` 的 URL 后重新打包。

### 第 3 步：安装 Redis / RabbitMQ（可选）

```bash
sudo yum install -y redis rabbitmq-server      # Ubuntu: apt install redis-server rabbitmq-server
sudo systemctl enable --now redis
sudo systemctl enable --now rabbitmq-server

# RabbitMQ 建应用账号（与 .env 一致；不建则用默认 guest/guest，仅限本机）
sudo rabbitmqctl add_user netguard netguard123
sudo rabbitmqctl set_permissions -p / netguard ".*" ".*" ".*"
```

不装也能跑：Redis 连不上会自动降级为**本地内存缓存**（每 60 秒重试恢复）；
RabbitMQ 连不上时发布者会**降级为本地同步通知**（日志出现"RabbitMQ 投递失败，降级为本地同步通知"）；
邮件未配置时发送会被跳过。因此最小演示环境只需要 **JDK 17 + MySQL 8**。

### 第 4 步：上传项目并创建目录与配置

```bash
# 1) 创建专用账号与目录
sudo useradd -r -s /sbin/nologin netguard
sudo mkdir -p /opt/netguard/{uploads,logs,backup}
sudo chown -R netguard:netguard /opt/netguard

# 2) 上传 jar（在本地构建后 scp；jar 名必须是 netguard.jar）
#    本地构建见第五节，产物 target/netguard.jar
scp target/netguard.jar root@服务器IP:/opt/netguard/
scp deploy/netguard.service root@服务器IP:/tmp/
scp deploy/nginx.conf       root@服务器IP:/tmp/
scp deploy/deploy.sh        root@服务器IP:/opt/netguard/

# 3) 写生产环境变量文件（权限 600，属主 netguard）
sudo tee /opt/netguard/.env >/dev/null <<'ENV'
MYSQL_PASSWORD=netguard123
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_PASSWORD=
RABBITMQ_HOST=127.0.0.1
RABBITMQ_PORT=5672
RABBITMQ_USER=netguard
RABBITMQ_PASSWORD=netguard123
RABBITMQ_VHOST=/
MAIL_HOST=smtp.example.com
MAIL_PORT=465
MAIL_USERNAME=alert@example.com
MAIL_PASSWORD=
ENV
sudo chown netguard:netguard /opt/netguard/.env && sudo chmod 600 /opt/netguard/.env

# 4) 安装 systemd 单元
sudo cp /tmp/netguard.service /etc/systemd/system/
sudo systemctl daemon-reload
```

### 第 5 步：执行部署脚本

```bash
cd /opt/netguard
sudo chmod +x deploy.sh

./deploy.sh preflight      # 先预检：root/JDK17/mysqldump/账号/目录/.env
sudo ./deploy.sh deploy    # 备份旧 jar 与数据库 → 停服 → 发布 → 启动 → 健康检查
```

脚本成功时会打印"健康检查通过（HTTP 200），服务已就绪"。
启动后设置开机自启：

```bash
sudo systemctl enable netguard
```

Nginx（需要证书就绪后再启用 443 块，详见 `docs/09` 第二章）：

```bash
sudo cp /tmp/nginx.conf /etc/nginx/conf.d/netguard.conf
sudo nginx -t && sudo systemctl reload nginx
```

### 第 6 步：验证访问

```bash
# 本机
curl -I http://127.0.0.1:8080/login        # 期望 200
# 经 Nginx（域名需解析到服务器 IP）
curl -I https://netguard.example.com/login  # 期望 200
```

浏览器打开 `http://服务器IP:8080` 或 `http://netguard.example.com`，使用默认账号登录：

| 账号 | 密码 | 角色 | 权限 |
| --- | --- | --- | --- |
| `admin` | `admin123` | ADMIN | 全部功能：设备/类型/规则/用户维护 |
| `ops` | `ops123` | OPS | 查看设备状态、确认与处理告警、查看通知 |

---

## 三、构建方式（Linux 服务器或本地）

```bash
# JDK 17 与 Maven 3.9.x 就绪后，在项目根目录执行
mvn -B -DskipTests package
# 产物：target/netguard.jar（finalName 已在 pom.xml 固定为 netguard）
```

首次构建会下载依赖（Spring Boot 2.7.18、MyBatis-Plus 3.5.5 等），耗时取决于网络。
不做单元测试用 `-DskipTests`；课程验收如需跑测试用 `mvn -B test`。

---

## 四、Windows 本机演示启动

本项目课程的**实际验证环境**：

| 组件 | 路径 / 参数 |
| --- | --- |
| JDK | `D:\devtools\jdk17\jdk-17.0.2`（OpenJDK 17.0.2） |
| Maven | `D:\devtools\maven\apache-maven-3.9.9` |
| MySQL | `D:\devtools\mysql\mysql-8.0.28-winx64`，`root/123456`，库 `netguard` |
| Redis / RabbitMQ / SMTP | 未安装，靠应用降级（内存缓存 + 本地同步通知 + 跳过邮件） |

```powershell
# 1) 设置构建环境（避免中文注释在 GBK 下编译乱码）
$env:JAVA_HOME='D:\devtools\jdk17\jdk-17.0.2'
$env:MAVEN_OPTS='-Dfile.encoding=UTF-8'
$env:PATH="$env:JAVA_HOME\bin;D:\devtools\maven\apache-maven-3.9.9\bin;$env:PATH"
cd D:\企业管理
mvn -B -DskipTests package

# 2) 导入数据库（仅首次）
cmd /c "chcp 65001 >nul && \"D:\devtools\mysql\mysql-8.0.28-winx64\bin\mysql.exe\" -u root -p123456 --default-character-set=utf8mb4 < \"sql\netguard.sql\""

# 3) 启动（dev profile：MQ/邮件关闭，Redis 缺失自动降级）
java -jar target\netguard.jar --spring.profiles.active=dev
```

也可以直接双击项目根目录的 `start-dev.bat`（脚本内已固定 JAVA_HOME，并设置 `-Dfile.encoding=UTF-8`）。

访问 <http://localhost:8080>（会自动跳转 `/login`），账号 `admin/admin123`、`ops/ops123`。
首次启动约 10~20 秒，日志出现 `Started NetGuardApplication` 即成功；`Ctrl+C` 停止。

---

## 五、常见问题（FAQ）

### 1. 端口 8080 被占用，启动报 `Port 8080 was already in use`

**现象**：日志末尾 `APPLICATION FAILED TO START ... Web server failed to start. Port 8080 was already in use`。

**排查**：

```bash
# Linux
ss -lntp | grep 8080
# Windows
netstat -ano | findstr :8080
```

**处置**：停掉占用进程（`kill <PID>` / 任务管理器结束该 PID），或临时换端口启动：
`java -jar netguard.jar --server.port=8081`。注意 Nginx 的 upstream 也要同步改端口。

### 2. 数据库连接失败

**现象**：启动日志 `Communications link failure` / `Access denied for user 'netguard'@'localhost'`，
页面登录报 500，异常日志里出现 `CannotGetJdbcConnectionException`。

**排查**：

```bash
systemctl status mysql
mysql -u netguard -p -h 127.0.0.1 netguard -e "SELECT 1;"
```

**处置**：确认 MySQL 已启动、库 `netguard` 已导入、`.env` 中 `MYSQL_PASSWORD` 与数据库一致
（prod 的 URL/用户名写死为 `127.0.0.1:3306` + `netguard`）；改完 `.env` 后
`sudo systemctl restart netguard`。密码含 `#`、`$` 等特殊字符时要加引号：`MYSQL_PASSWORD='p@ss#1'`。

### 3. 上传目录权限不足

**现象**：上传设备图片失败，页面提示"图片保存失败，请稍后重试"，
日志 `保存设备图片失败：... Permission denied` / `创建上传目录失败`。

**原因**：应用以 `netguard` 用户运行，但 `/opt/netguard/uploads` 属主是 root。

**处置**：

```bash
sudo chown -R netguard:netguard /opt/netguard/uploads
sudo chmod 755 /opt/netguard/uploads
# Nginx 直读同一目录，确保 nginx 用户可读（755 即可）
```

### 4. Nginx 502 Bad Gateway

**现象**：浏览器出现 Nginx 的 502 页面，`/var/log/nginx/netguard.error.log` 中
`connect() failed (111: Connection refused) while connecting to upstream`。

**原因**：后端未启动、启动失败或端口不是 8080。

**处置**：

```bash
sudo systemctl status netguard
sudo journalctl -u netguard -n 100 --no-pager
curl -I http://127.0.0.1:8080/login     # 后端自身是否 200
sudo nginx -t && sudo systemctl reload nginx
```

后端恢复后 502 会自动消失；若后端改过端口，需同步修改 `upstream netguard_backend`。

### 5. 中文乱码

**现象**：日志中文变 `????`、页面中文变问号，或 Windows 下 Maven 构建报
`编码 GBK 的不可映射字符`。

**原因**：JVM 默认字符集不是 UTF-8（Windows 中文版默认 GBK）。

**处置**：启动命令必须带 `-Dfile.encoding=UTF-8`（`deploy/netguard.service` 的 ExecStart 已包含；
`start-dev.bat` 也已包含）。Windows 构建时额外设置
`$env:MAVEN_OPTS='-Dfile.encoding=UTF-8'`，并在控制台执行 `chcp 65001`；
数据库导入时加 `--default-character-set=utf8mb4`。已写入的历史乱码数据无法自动修复，需重新导入 SQL。

---

## 六、更多

- 完整部署步骤、.env 变量清单、Nginx 逐段说明、巡检清单、8 类故障排查、升级回滚与备份演练：
  见 `docs/09-部署运维手册.md`。
- 启动/停止/重启/日志速查：

```bash
sudo systemctl start netguard       # 启动
sudo systemctl stop netguard        # 停止（SIGTERM 优雅关闭，退出码 143 属正常）
sudo systemctl restart netguard     # 重启
sudo journalctl -u netguard -f      # 实时日志
tail -f /opt/netguard/logs/netguard.log
sudo ./deploy.sh status             # 状态 + 端口 + 健康检查
```
