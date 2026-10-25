# ---- Estágio 1: build ----
# Imagem com JDK completo (compilador + Maven Wrapper) só para gerar o .jar.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Copiamos primeiro só o pom e o wrapper: enquanto o pom.xml não mudar, o Docker
# reaproveita a camada com as dependências baixadas (build bem mais rápido).
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline

COPY src/ src/
# Os testes rodam antes do commit/CI; aqui só empacotamos.
RUN ./mvnw -q -DskipTests package

# ---- Estágio 2: runtime ----
# Só o JRE (sem compilador): imagem menor e com menos superfície de ataque.
FROM eclipse-temurin:17-jre
WORKDIR /app

# Não rodar como root dentro do container.
RUN useradd --system --uid 1001 spring
COPY --from=build /app/target/pix-api-*.jar app.jar
USER spring

EXPOSE 8080
# MaxRAMPercentage: a JVM usa até 75% da memória do container (o plano free do Render tem 512 MB).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "app.jar"]
