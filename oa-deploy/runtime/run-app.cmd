@echo off
setlocal
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
set "PATH=C:\Tools\apache-maven-3.9.16\bin;%JAVA_HOME%\bin;%PATH%"
set "HTTP_PROXY=http://127.0.0.1:7899"
set "HTTPS_PROXY=http://127.0.0.1:7899"
set "OA_DB_USERNAME=oa"
set "OA_DB_PASSWORD=oa_dev_pwd"
cd /d "H:\dsh\OA\oa-server"
call "C:\Tools\apache-maven-3.9.16\bin\mvn.cmd" -B -DskipTests spring-boot:run "-Dspring-boot.run.profiles=dev" > "H:\dsh\OA\.cache\oa-server.log" 2>&1
