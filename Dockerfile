# ==========================================
# 1. Estágio de Build
# ==========================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copia os arquivos do projeto e compila
COPY . .
RUN mvn clean package -DskipTests

# ==========================================
# 2. Estágio de Runtime
# ==========================================
FROM eclipse-temurin:21-jre

# Variáveis de ambiente
ENV DISPLAY=:99
ENV JAVA_OPTS="-Xmx512m"

# Instala todas as dependências em uma única camada (Layer)
# Isso inclui bibliotecas de interface, XVFB/VNC e o Chromium com seu Driver
RUN apt-get update && apt-get install -y \
    wget \
    unzip \
    fonts-liberation \
    libasound2t64 \
    libatk-bridge2.0-0 \
    libdrm2 \
    libxkbcommon0 \
    libxcomposite1 \
    libxdamage1 \
    libxfixes3 \
    libxrandr2 \
    libgbm1 \
    libatspi2.0-0 \
    libxshmfence1 \
    xvfb \
    x11vnc \
    tigervnc-standalone-server \
    chromium \
    chromium-driver \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copia o JAR do estágio de build
# ATENÇÃO: Verifique se o caminho '/app/bot-vagas/target/*.jar' está correto. 
# Se o seu pom.xml estiver na raiz, o caminho costuma ser apenas '/app/target/*.jar'
COPY --from=build /app/bot-vagas/target/*.jar app.jar

# Cria um link simbólico do chromedriver para a pasta /app (mantendo a sua lógica original)
RUN ln -s /usr/bin/chromedriver /app/chromedriver

# Copia e configura o script de inicialização
COPY start.sh /start.sh
RUN chmod +x /start.sh

EXPOSE 8080

CMD ["/start.sh"]
