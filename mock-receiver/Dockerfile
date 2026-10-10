# Build context is the repository root:  docker build -f mock-receiver/Dockerfile .

FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY mvnw* pom.xml ./
COPY .mvn .mvn
COPY relay-server/pom.xml relay-server/pom.xml
COPY mock-receiver/pom.xml mock-receiver/pom.xml
COPY mock-receiver/src mock-receiver/src
RUN ./mvnw -B -q -pl mock-receiver -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 mock
WORKDIR /app
COPY --from=build /build/mock-receiver/target/mock-receiver-*.jar app.jar
USER mock
EXPOSE 9000
ENTRYPOINT ["java", "-jar", "app.jar"]
