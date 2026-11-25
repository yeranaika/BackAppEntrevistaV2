# Backend EntrevistaAPP (Ktor) para Railway, con Tailscale en modo userspace (D49).
# Reemplaza a RailPack: mismo artefacto de antes (gradlew installDist -> build/install/ktor-sample)
# porque el log del contenedor ya apuntaba a /app/build/install/ktor-sample.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY nucleo-vision ./nucleo-vision
COPY motor-vision ./motor-vision
COPY src ./src
# sed quita el CRLF del repo Windows: sin eso, el shebang de gradlew termina en \r y Linux dice "not found".
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew --no-daemon installDist -x test

# Runtime: JRE21 + binarios de Tailscale copiados de la imagen oficial.
FROM eclipse-temurin:21-jre
COPY --from=tailscale/tailscale:stable /usr/local/bin/tailscaled /usr/local/bin/tailscaled
COPY --from=tailscale/tailscale:stable /usr/local/bin/tailscale /usr/local/bin/tailscale
WORKDIR /app
COPY --from=build /src/build/install/ktor-sample /app/install
COPY start.sh /app/start.sh
RUN chmod +x /app/start.sh /app/install/bin/*
EXPOSE 8080
ENTRYPOINT ["/app/start.sh"]
