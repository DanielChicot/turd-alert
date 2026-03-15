FROM eclipse-temurin:21-jre-alpine
COPY backend/build/libs/backend-all.jar /app/backend.jar
ENTRYPOINT ["java", "-Xmx256m", "-jar", "/app/backend.jar"]
