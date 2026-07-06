# 多階段建置：build 階段用 Maven 打包 jar，runtime 階段只留 JRE 與 jar，縮小映像檔
# 好處：docker compose up --build 即可一鍵建置+啟動，開發者本機無需安裝 Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# 先複製 pom 下載依賴，善用 Docker layer cache：原始碼未變時免重新抓依賴
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8050
ENTRYPOINT ["java", "-jar", "app.jar"]
