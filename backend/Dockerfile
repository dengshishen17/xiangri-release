# ==========================================================================
# 巷里 XIANGRI · 后端镜像
# ==========================================================================
# 构建方式（在 hm-dianping 目录下，先执行 mvn package 生成 jar）：
#   docker build -t xiangri-backend:latest .
# ==========================================================================
FROM eclipse-temurin:17-jre

WORKDIR /app

# 拷贝打包好的应用 jar（构建前需先执行：mvn -DskipTests package）
COPY target/hm-dianping-0.0.1-SNaAPSHOT.jar app.jar

# 所有敏感配置通过环境变量注入，镜像内不含任何真实密码
ENV SERVER_PORT=8081 \
    TZ=Asia/Shanghai

EXPOSE 8081

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
