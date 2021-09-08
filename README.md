# swagger2word
基于 swagger2word对swagger2的json进行解析整理成word的开源工具。
由于其json字符串在请求参数，不好用，所以改成放在请求体中
本服务的dockerfile和yml已经过验证，但是请自行检查jar的文件名和路径

## 功能：
- 一个Swagger API 文档转 Word 文档的工具项目
- 优化传参方式，现在通过请求体传参，避免字符串过大问题
- 现在可以docker或k8s进行容器化部署

# 单服务构建步骤

```shell script
# 打包
mvn clean compile
# 构建镜像（Dockerfile第一句）
docker build -t tool/swagger2word:1.0.01-release .
# k8s部署本服务
kubectl apply -f swagger2word-base.yml

```
# 加上filebeat在k8s部署

```shell script
# 部署
bash build.sh

# 清理
bash clear.sh

```
