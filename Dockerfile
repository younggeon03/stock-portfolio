# 빌드와 실행을 나눈 멀티 스테이지 이미지.
# 빌드에는 JDK 와 메이븐 캐시가 필요하지만 실행에는 JRE 와 jar 하나면 된다.
# 나누지 않으면 이미지가 몇 배 커지고, 소스와 빌드 도구가 운영 서버에 같이 올라간다.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성부터 받아 둔다. pom.xml 이 안 바뀌면 이 층은 캐시돼서 소스만 고친 재빌드가 빨라진다
COPY mvnw pom.xml ./
COPY .mvn .mvn
# 윈도우에서 체크아웃하면 mvnw 가 CRLF 가 되어 리눅스 셸이 "bad interpreter" 로 멈춘다
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
# 테스트는 CI 가 따로 돌린다. 이미지 빌드에서 또 돌리면 시간만 두 배가 된다
RUN ./mvnw -B -q -DskipTests package && cp target/portfolio-*.jar app.jar


FROM eclipse-temurin:21-jre
WORKDIR /app

# 루트로 돌리지 않는다. 앱이 뚫려도 컨테이너 안에서 할 수 있는 일을 줄인다
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/app.jar app.jar
USER app

# 로그 시각과 "오늘" 기준(뉴스 캐시, 결산일 계산)을 한국 시간에 맞춘다
ENV TZ=Asia/Seoul
# 무료 서버(1~2GB)에서도 뜨도록 힙을 묶는다. 로컬에서 쓰던 값과 같다
ENV JAVA_OPTS="-Xmx512m"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
