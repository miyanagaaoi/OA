# 集团OA审批系统 · 后端镜像（多阶段构建）
#
# 本地/联网构建：
#   docker build -t oa-server:local oa-server
# 内网离线交付：
#   在联网机构建后 `docker save oa-server:prod-xxx | gzip > oa-server.tgz`，内网 `docker load -i oa-server.tgz`
#   （基础镜像 maven / temurin 也需一并 save，见 oa-deploy/env-checklist.md §2）

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先只拷 pom 预热依赖，充分利用层缓存
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- 运行阶段 ----------
FROM eclipse-temurin:21-jre-jammy
LABEL org.opencontainers.image.title="oa-server" \
      org.opencontainers.image.description="集团OA审批系统后端（Spring Boot 3.2 / Java 21）"

# 健康检查需要 curl；时区与字符集按私有化要求固定
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl tzdata \
 && ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
 && echo "Asia/Shanghai" > /etc/timezone \
 && rm -rf /var/lib/apt/lists/*

# 非 root 运行（安全基线：最小权限）
RUN groupadd -r oa && useradd -r -g oa -d /app -s /sbin/nologin oa
WORKDIR /app

COPY --from=build /build/target/*.jar /app/app.jar
# 附件与手写签名图的本地私有存储（技术方案 D6）；由 compose 挂载为卷
RUN mkdir -p /data/oa/attachments /data/oa/signatures && chown -R oa:oa /app /data/oa
USER oa

EXPOSE 8080
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms512m -Xmx1536m -XX:+UseG1GC -Duser.timezone=Asia/Shanghai" \
    OA_STORAGE_ROOT=/data/oa

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
