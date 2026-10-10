# Build context is the repository root:  docker build -f relay-server/Dockerfile .

# ---- build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY mvnw* pom.xml ./
COPY .mvn .mvn
COPY relay-server/pom.xml relay-server/pom.xml
COPY mock-receiver/pom.xml mock-receiver/pom.xml
COPY relay-server/src relay-server/src
RUN ./mvnw -B -q -pl relay-server -am package -DskipTests

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 relay
WORKDIR /app
# The glob must match exactly one jar (COPY refuses several into a single file).
COPY --from=build /build/relay-server/target/relay-server-*.jar app.jar
USER relay
EXPOSE 8080
# Leave RAM for the OS and Postgres. Secrets come from the environment at run time, never baked in.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "app.jar"]
