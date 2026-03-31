package com.botvagas.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Classe central de configuração do Spring.
 *
 * DECISÃO DE ARQUITETURA: Centralizar as anotações de configuração aqui
 * em vez de espalhar na BotApplication. Isso segue o SRP: a classe principal
 * só inicializa a app, esta classe define como o Spring se configura.
 *
 * @EnableConfigurationProperties: registra BotVagasProperties para ser
 * injetada como bean em qualquer lugar da aplicação.
 *
 * @EnableScheduling: habilita o suporte a @Scheduled globalmente.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(BotVagasProperties.class)
public class AppConfig {
    // Classe intencional e deliberadamente vazia.
    // Seu valor está nas anotações, não em código dentro dela.
}