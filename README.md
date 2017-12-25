# 网关设备TCP服务端

### 下载项目
git clone https://github.com/shijian-ws/iot-tcpserver.git

### 编译项目, 需要先安装iot-util依赖到本地仓库
mvn clean package

### 运行项目
java -Dspring.profiles.active=local -jar target\iot-tcpserver-0.1.jar &

### 查看环境, YWRtaW46YWRtaW4=为admin:admin的Base64编码
curl -H "Authorization:Basic YWRtaW46YWRtaW4=" http://127.0.0.1:9999/manager/mappings

### 关闭项目
curl -X POST -H "Authorization:Basic YWRtaW46YWRtaW4=" http://127.0.0.1:9999/manager/shutdown
