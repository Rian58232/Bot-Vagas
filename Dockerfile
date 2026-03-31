# Build do projeto
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Runtime com Chrome e Java
FROM eclipse-temurin:21-jre

# Instala dependências do Chrome
RUN apt-get update && apt-get install -y \
    wget \
    unzip \
    fonts-liberation \
    libasound2 \
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
    && rm -rf /var/lib/apt/lists/*

# Instala Chrome
RUN wget -q https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb && \
    apt-get install -y ./google-chrome-stable_current_amd64.deb && \
    rm google-chrome-stable_current_amd64.deb

# Instala ChromeDriver
RUN CHROME_VERSION=$(google-chrome --version | awk '{print $3}' | cut -d'.' -f1) && \
    wget -q https://storage.googleapis.com/chrome-for-testing-public/${CHROME_VERSION}.0.${CHROME_VERSION}/linux64/chromedriver-linux64.zip && \
    unzip chromedriver-linux64.zip && \
    mv chromedriver-linux64/chromedriver /usr/local/bin/ && \
    chmod +x /usr/local/bin/chromedriver && \
    rm -rf chromedriver-linux64.zip chromedriver-linux64

# Instala VNC e Xvfb pra WhatsApp Web funcionar
RUN apt-get install -y xvfb vnc4server x11vnc

WORKDIR /app

# Copia o JAR do build
COPY --from=build /app/bot-vagas/target/*.jar app.jar

# Copia chromedriver pra pasta do app
COPY --from=build /usr/local/bin/chromedriver /app/chromedriver
RUN chmod +x /app/chromedriver

# Variáveis de ambiente
ENV DISPLAY=:99
ENV JAVA_OPTS=-Xmx512m

# Script de inicialização
COPY start.sh /start.sh
RUN chmod +x /start.sh

EXPOSE 8080

CMD ["/start.sh"]
