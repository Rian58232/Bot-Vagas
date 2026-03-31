# ==========================================
# 1. Estágio de Build
# ==========================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copia o pom e baixa dependencias
COPY pom.xml .
RUN mvn dependency:go-offline

# COPIA A PASTA SRC EXPLICITAMENTE
COPY src ./src

# Compila forçando a limpeza
RUN mvn clean package -DskipTests

# ==========================================
# 2. Estágio de Runtime
# ==========================================
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

ENV DISPLAY=:99
ENV JAVA_OPTS="-Xmx512m"

# Instala dependências do Chrome e interface gráfica
RUN apt-get update && apt-get install -y \
    wget gnupg unzip fonts-liberation libasound2 libatk-bridge2.0-0 \
    libdrm2 libxkbcommon0 libxcomposite1 libxdamage1 libxfixes3 \
    libxrandr2 libgbm1 libatspi2.0-0 libxshmfence1 \
    chromium chromium-driver xvfb x11vnc \
    && rm -rf /var/lib/apt/lists/*

# Copia o JAR gerado no estágio anterior
COPY --from=build /app/target/*.jar app.jar

# Configura o script de inicialização
COPY start.sh /start.sh
RUN chmod +x /start.sh

EXPOSE 8080

CMD ["/start.sh"]
