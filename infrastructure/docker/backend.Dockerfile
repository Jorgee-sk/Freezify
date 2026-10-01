# Build context: repository root.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY apps/backend/.mvn .mvn
COPY apps/backend/mvnw apps/backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY apps/backend/src src
# Tests run in CI; the image build only packages.
RUN ./mvnw -B -q -DskipTests package \
    && java -Djarmode=tools -jar target/*.jar extract --layers --destination extracted

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 freezify
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER freezify
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "freezify-backend-0.1.0-SNAPSHOT.jar"]
