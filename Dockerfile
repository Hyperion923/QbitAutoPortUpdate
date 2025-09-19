FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace/app
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src
RUN chmod +x gradlew \
 && ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=default \
    SPRING_SHELL_INTERACTIVE_ENABLED=false \
    JAVA_OPTS=""

COPY --from=build /workspace/app/build/libs/*.jar /app/app.jar
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]