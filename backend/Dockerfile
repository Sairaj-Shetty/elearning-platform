FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN apt-get update \
  && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/* \
  && useradd --system --uid 1001 --create-home lumina \
  && mkdir -p /data/media \
  && chown -R lumina:lumina /data/media
WORKDIR /app
COPY --from=build /src/target/lumina.jar app.jar
USER lumina
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
