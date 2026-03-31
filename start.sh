#!/bin/bash

# Inicia Xvfb (servidor X virtual)
Xvfb :99 -screen 0 1920x1080x24 &
export DISPLAY=:99

# Aguarda Xvfb iniciar
sleep 2

# Inicia o bot
echo "Iniciando Bot Vagas..."
java $JAVA_OPTS -jar app.jar
