package com.botvagas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BotApplication {
    private static final Logger log = LoggerFactory.getLogger(BotApplication.class);

    public static void main(String[] args) {
        log.info("Iniciando Bot Agregador de Vagas.");
        SpringApplication.run(BotApplication.class, args);
        log.info("Aplicacao Spring iniciada. O status real do WhatsApp sera exibido nos logs de MensageiroWhatsApp.");
    }
}
