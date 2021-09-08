# docker build -t tool/swagger2word:1.0.01-release .

FROM openjdk:8-jdk-alpine

RUN \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    mkdir -p /app
# 路径请自行核对
ADD  /target/Swagger2Word-exec.jar /app/swagger2word.jar

ENV JAVA_OPTS="-Duser.timezone=Asia/Shanghai"
EXPOSE 8080
ENV APP_OPTS=""
ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS  -jar /app/swagger2word.jar $APP_OPTS" ]