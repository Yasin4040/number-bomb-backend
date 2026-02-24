FROM eclipse-temurin:17-jre-alpine

## 创建目录，并使用它作为工作目录
RUN mkdir -p /app

WORKDIR /app

COPY ./target/fat-fish-server.jar app.jar
# 暴露端口
EXPOSE 8888
## 设置 TZ 时区
ENV TZ=Asia/Shanghai
# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
