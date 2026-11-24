#!/usr/bin/env bash
# ============================================================================
# NetGuard 网络故障告警监控平台 —— 部署 / 回滚 / 状态 / 日志脚本
# ----------------------------------------------------------------------------
# 适用：Linux（systemd 发行版，如 CentOS 7+/Ubuntu 20.04+），需 root 或 sudo 执行
# 用法：
#   sudo ./deploy.sh deploy [新 jar 路径]   备份并发布新版本（默认取仓库 target/netguard.jar）
#   sudo ./deploy.sh rollback              回滚到最近一次备份的 jar
#        ./deploy.sh status                查看服务状态、端口与健康检查
#        ./deploy.sh logs [行数]           实时跟踪 journald 日志（默认 200 行）
#   sudo ./deploy.sh help                  显示帮助
# 约定：
#   - 应用目录 /opt/netguard，jar 固定为 /opt/netguard/netguard.jar
#   - 备份目录 /opt/netguard/backup，日志目录 /opt/netguard/logs
#   - systemd 单元名 netguard（/etc/systemd/system/netguard.service）
#   - 健康检查地址 http://127.0.0.1:8080/login（登录页 permitAll，返回 200 即就绪）
# ============================================================================
set -euo pipefail

# ------------------------------- 变量区 -------------------------------------
APP_NAME="netguard"                                   # 应用/服务名
APP_USER="netguard"                                   # 运行应用的专用账号
APP_DIR="/opt/netguard"                               # 部署根目录
JAR_NAME="netguard.jar"                               # 与 pom.xml 的 finalName 一致
JAR="${APP_DIR}/${JAR_NAME}"                          # 线上运行中的 jar
BACKUP_DIR="${APP_DIR}/backup"                        # 备份目录（旧 jar + SQL dump）
LOG_DIR="${APP_DIR}/logs"                             # 日志目录（logback 的 ./logs）
ENV_FILE="${APP_DIR}/.env"                            # 生产环境变量文件
SERVICE_NAME="netguard"                               # systemd 单元名
UNIT_FILE="/etc/systemd/system/${SERVICE_NAME}.service"
HEALTH_URL="http://127.0.0.1:8080/login"              # 健康检查地址
HEALTH_RETRY=30                                       # 健康检查最大重试次数（每次 2 秒 ≈ 60 秒）
KEEP_BACKUPS=5                                        # 保留最近几个 jar 备份
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"   # deploy/ 所在目录
DEFAULT_JAR="${SCRIPT_DIR}/../target/${JAR_NAME}"     # 默认从仓库 target 取新包
NEW_JAR="${NEW_JAR:-}"                                # 可由环境变量 NEW_JAR 覆盖

# ------------------------------- 工具函数 -----------------------------------
# 输出带时间戳的普通日志
log()  { printf '[%s] [INFO ] %s\n' "$(date '+%F %T')" "$*"; }
# 输出带时间戳的告警日志
warn() { printf '[%s] [WARN ] %s\n' "$(date '+%F %T')" "$*" >&2; }
# 输出带时间戳的错误日志并以非零码退出
die()  { printf '[%s] [ERROR] %s\n' "$(date '+%F %T')" "$*" >&2; exit 1; }

# 打印帮助信息（子命令与目录约定）
usage() {
    cat <<'EOF'
NetGuard 部署脚本

用法：
  sudo ./deploy.sh deploy [jar路径]   备份 → 停服 → 发布 → 启动 → 健康检查
  sudo ./deploy.sh rollback           回滚到 backup/ 下最新的 jar 并重启
       ./deploy.sh status             查看 systemd 状态、8080 端口监听与健康检查码
       ./deploy.sh logs [行数]         跟踪 journalctl 日志，默认 200 行
       ./deploy.sh preflight           只做环境预检，不做任何变更
       ./deploy.sh help                显示本帮助

约定：
  应用目录 : /opt/netguard
  运行 jar : /opt/netguard/netguard.jar
  备份目录 : /opt/netguard/backup
  日志目录 : /opt/netguard/logs
  systemd  : /etc/systemd/system/netguard.service
  健康检查 : http://127.0.0.1:8080/login
EOF
}

# 预检：确认 root 权限、JDK17、MySQL 客户端、运行账号、目录与 .env 就绪
preflight() {
    log "开始环境预检…"

    # 1) 必须以 root 执行（要写 /opt、调 systemctl、chown）
    [[ "${EUID}" -eq 0 ]] || die "请用 root 或 sudo 执行：sudo $0 $*"

    # 2) JDK 17 是项目的硬性要求（pom.xml: java.version=17）
    command -v java >/dev/null 2>&1 || die "未找到 java，请先安装 JDK 17 并加入 PATH"
    local java_ver
    java_ver="$(java -version 2>&1 | head -n 1)"
    log "Java 版本：${java_ver}"
    echo "${java_ver}" | grep -Eq '"(1\.)?17[."]' || warn "当前 Java 不是 17，应用可能无法启动（要求 JDK 17）"

    # 3) 备份 SQL 需要 mysqldump；没有则只备份 jar，不中断部署
    command -v mysqldump >/dev/null 2>&1 \
        && log "mysqldump：$(mysqldump --version 2>/dev/null | head -n 1)" \
        || warn "未找到 mysqldump，本次将跳过数据库备份（仅备份 jar）"

    # 4) curl 用于健康检查，必须有
    command -v curl >/dev/null 2>&1 || die "未找到 curl，无法执行健康检查，请先安装 curl"

    # 5) 运行账号必须存在（systemd 单元里 User=netguard）
    id "${APP_USER}" >/dev/null 2>&1 || die "系统用户 ${APP_USER} 不存在，请先执行：useradd -r -s /sbin/nologin ${APP_USER}"

    # 6) 目录规划存在且属主正确
    [[ -d "${APP_DIR}" ]] || die "应用目录 ${APP_DIR} 不存在，请先按部署手册创建并上传文件"
    for d in "${APP_DIR}" "${LOG_DIR}" "${BACKUP_DIR}"; do
        [[ -d "${d}" ]] || { mkdir -p "${d}" && log "已创建目录 ${d}"; }
        chown "${APP_USER}:${APP_USER}" "${d}" 2>/dev/null || warn "无法修改 ${d} 属主（请手动 chown ${APP_USER}:${APP_USER}）"
    done
    [[ -w "${APP_DIR}" ]] || die "${APP_DIR} 不可写，请检查挂载与权限"

    # 7) 上传目录必须可写（应用以 netguard 身份保存图片）
    [[ -d "${APP_DIR}/uploads" ]] || mkdir -p "${APP_DIR}/uploads"
    chown -R "${APP_USER}:${APP_USER}" "${APP_DIR}/uploads" 2>/dev/null || warn "无法修改 uploads 属主"

    # 8) 生产环境变量文件：缺失时给出明确提示（单元文件用了非可选 EnvironmentFile）
    [[ -f "${ENV_FILE}" ]] && log "环境变量文件：${ENV_FILE}（权限 $(stat -c '%a' "${ENV_FILE}" 2>/dev/null || echo '?')）" \
        || warn "${ENV_FILE} 不存在，systemd 将拒绝启动；请按手册 .env 清单创建并 chmod 600"

    # 9) systemd 单元是否已安装
    [[ -f "${UNIT_FILE}" ]] && log "systemd 单元：${UNIT_FILE}" \
        || warn "未找到 ${UNIT_FILE}，请先复制 deploy/netguard.service 并 daemon-reload"

    # 10) 磁盘余量粗检（jar 约 47MB + 备份 + 日志，建议至少 1GB 空闲）
    local avail
    avail="$(df -Pm "${APP_DIR}" | awk 'NR==2 {print $4}')"
    if [[ "${avail}" =~ ^[0-9]+$ ]] && [[ "${avail}" -ge 1024 ]]; then
        log "磁盘可用：${avail}MB"
    else
        warn "磁盘可用仅 ${avail:-未知}MB，日志与备份可能写满磁盘"
    fi

    log "环境预检通过"
}

# 备份：保存当前 jar、导出数据库，并清理超出保留数的旧 jar
backup() {
    local ts; ts="$(date '+%Y%m%d-%H%M%S')"
    mkdir -p "${BACKUP_DIR}"

    # 1) 备份正在运行的 jar（首次部署时不存在，跳过即可）
    if [[ -f "${JAR}" ]]; then
        cp -p "${JAR}" "${BACKUP_DIR}/${JAR_NAME}.${ts}"
        log "已备份 jar → ${BACKUP_DIR}/${JAR_NAME}.${ts}"
    else
        warn "当前无 ${JAR}，按首次部署处理，跳过 jar 备份"
    fi

    # 2) 导出数据库（凭据取自 .env；失败不阻断部署，仅告警）
    if command -v mysqldump >/dev/null 2>&1; then
        # shellcheck disable=SC1090
        [[ -f "${ENV_FILE}" ]] && set +u && . "${ENV_FILE}" && set -u
        local db_host="${MYSQL_HOST:-127.0.0.1}"
        local db_user="${MYSQL_USER:-netguard}"
        local db_pass="${MYSQL_PASSWORD:-}"
        local db_name="${MYSQL_DATABASE:-netguard}"
        local sql_file="${BACKUP_DIR}/netguard-sql.${ts}.sql.gz"
        # 用 MYSQL_PWD 传递密码，避免出现在 ps 进程列表中
        if MYSQL_PWD="${db_pass}" mysqldump -h"${db_host}" -u"${db_user}" \
                --single-transaction --set-gtid-purged=OFF --default-character-set=utf8mb4 \
                "${db_name}" 2>/dev/null | gzip > "${sql_file}"; then
            log "已备份数据库 → ${sql_file}（$(du -h "${sql_file}" | cut -f1)）"
        else
            rm -f "${sql_file}"
            warn "数据库备份失败（检查 .env 中的 MYSQL_* 与 mysqldump 权限），继续部署"
        fi
    fi

    # 3) 只保留最近 KEEP_BACKUPS 个 jar 备份，防止磁盘被撑满
    local old
    old="$(ls -1t "${BACKUP_DIR}"/"${JAR_NAME}".* 2>/dev/null | tail -n +$((KEEP_BACKUPS + 1)) || true)"
    if [[ -n "${old}" ]]; then
        echo "${old}" | xargs -r rm -f
        log "已清理超额备份：$(echo "${old}" | wc -l) 个"
    fi
}

# 停止：优先 systemctl stop，无单元时按 jar 进程 PID 优雅停止
stop() {
    if systemctl is-active --quiet "${SERVICE_NAME}" 2>/dev/null; then
        log "停止服务 systemctl stop ${SERVICE_NAME}"
        systemctl stop "${SERVICE_NAME}"
    else
        # 兜底：手工 java -jar 启动的进程（未走 systemd 的调试场景）
        local pids
        pids="$(pgrep -f "java .*${JAR}" || true)"
        if [[ -n "${pids}" ]]; then
            warn "systemd 未托管，改为按 PID 停止：${pids}"
            # shellcheck disable=SC2086
            kill -TERM ${pids} 2>/dev/null || true
        fi
    fi

    # 最多等 30 秒，确认 8080 已释放（Spring Boot 优雅关闭需要时间）
    local i
    for i in $(seq 1 30); do
        if ! pgrep -f "java .*${JAR}" >/dev/null 2>&1; then
            log "应用已停止"
            return 0
        fi
        sleep 1
    done
    warn "30 秒内未完全退出，强制结束"
    pkill -9 -f "java .*${JAR}" 2>/dev/null || true
    log "已强制停止"
}

# 发布：把新 jar 覆盖到 /opt/netguard 并修正属主与权限
deploy() {
    local src="${1:-${NEW_JAR:-${DEFAULT_JAR}}}"
    [[ -f "${src}" ]] || die "找不到新 jar：${src}（可显式传入路径：./deploy.sh deploy /tmp/netguard.jar）"
    log "新包：${src}（$(du -h "${src}" | cut -f1)）"

    # 先拷到临时文件再 mv，避免 cp 中断留下半截 jar 导致无法启动
    local tmp="${JAR}.new.$$"
    cp -f "${src}" "${tmp}"
    chown "${APP_USER}:${APP_USER}" "${tmp}"
    chmod 0644 "${tmp}"
    mv -f "${tmp}" "${JAR}"
    log "已发布 → ${JAR}"
}

# 启动：systemctl start 后轮询 /login，HTTP 200 才算成功
start() {
    if [[ -f "${UNIT_FILE}" ]]; then
        log "重载 systemd 配置并启动 ${SERVICE_NAME}"
        systemctl daemon-reload
        systemctl start "${SERVICE_NAME}"
    else
        die "未找到 ${UNIT_FILE}，无法启动；请先安装 systemd 单元"
    fi

    log "健康检查 ${HEALTH_URL}（最多 ${HEALTH_RETRY} 次）"
    local i code
    for i in $(seq 1 "${HEALTH_RETRY}"); do
        # -f：HTTP >= 400 视为失败；-s 静默；-o 丢弃响应体；-w 只取状态码
        code="$(curl -fsS -o /dev/null -w '%{http_code}' --max-time 3 "${HEALTH_URL}" 2>/dev/null || true)"
        if [[ "${code}" == "200" ]]; then
            log "健康检查通过（HTTP ${code}），服务已就绪"
            return 0
        fi
        sleep 2
    done

    warn "健康检查失败，最近 50 行日志如下："
    journalctl -u "${SERVICE_NAME}" -n 50 --no-pager 2>/dev/null || true
    die "启动失败，可执行 'sudo $0 rollback' 回滚到上一版本"
}

# 回滚：用 backup/ 下最新的 jar 覆盖并重启（数据库不会自动回滚）
rollback() {
    local latest
    latest="$(ls -1t "${BACKUP_DIR}/${JAR_NAME}".* 2>/dev/null | head -n 1 || true)"
    [[ -n "${latest}" ]] || die "${BACKUP_DIR} 下没有可回滚的 jar 备份"

    log "回滚目标：${latest}"
    stop
    cp -f "${latest}" "${JAR}"
    chown "${APP_USER}:${APP_USER}" "${JAR}" && chmod 0644 "${JAR}"
    start
    log "回滚完成。注意：数据库未回滚；如需恢复请用 ${BACKUP_DIR} 下的 netguard-sql.*.sql.gz 手工导入"
    log "数据库恢复示例：gunzip -c ${BACKUP_DIR}/netguard-sql.<时间戳>.sql.gz | mysql -u<用户> -p netguard"
}

# 状态：systemd 状态 + 8080 监听 + 健康检查码
status() {
    echo "===== systemd 状态 ====="
    systemctl status "${SERVICE_NAME}" --no-pager -l 2>/dev/null || warn "服务未安装或未运行"

    echo
    echo "===== 端口 8080 监听 ====="
    ss -lntp 2>/dev/null | grep -E ':8080\b' || echo "（8080 未监听）"

    echo
    echo "===== 健康检查 ${HEALTH_URL} ====="
    local code
    code="$(curl -fsS -o /dev/null -w '%{http_code}' --max-time 3 "${HEALTH_URL}" 2>/dev/null || true)"
    echo "HTTP 状态码：${code:-无响应}（200 表示正常）"

    echo
    echo "===== 最近 5 个备份 ====="
    ls -lht "${BACKUP_DIR}" 2>/dev/null | head -n 6 || echo "（无备份）"
}

# 日志：跟踪 journald（-f 阻塞，Ctrl+C 退出）
logs() {
    local lines="${1:-200}"
    journalctl -u "${SERVICE_NAME}" -n "${lines}" -f --no-pager
}

# 主流程：解析子命令并调度，各步骤失败即退出（set -e）
main() {
    local cmd="${1:-help}"
    case "${cmd}" in
        deploy)
            shift || true
            preflight
            backup
            stop
            deploy "${1:-${NEW_JAR:-${DEFAULT_JAR}}}"
            start
            log "部署成功：${APP_NAME} 已运行新版本"
            ;;
        rollback)
            preflight
            rollback
            ;;
        preflight)
            preflight
            ;;
        status)
            status
            ;;
        logs)
            shift || true
            logs "${1:-200}"
            ;;
        help|-h|--help)
            usage
            ;;
        *)
            usage
            die "未知子命令：${cmd}"
            ;;
    esac
}

main "$@"
