package com.botvagas.scheduler;

import com.botvagas.config.BotVagasProperties;
import com.botvagas.scraper.BuscadorDeVagas;
import com.botvagas.whatsapp.WhatsAppPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class AgendadorDeVagas {
    private static final Logger log = LoggerFactory.getLogger(AgendadorDeVagas.class);
    private static final int MAX_TENTATIVAS_AO_INICIAR = 8;
    private static final DateTimeFormatter FORMATO_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter FORMATO_AGENDAMENTO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss z");
    private static final ZoneId FUSO_AGENDAMENTO = ZoneId.of("America/Sao_Paulo");

    private final BuscadorDeVagas buscadorDeVagas;
    private final WhatsAppPort whatsAppPort;
    private final BotVagasProperties.Scheduler schedulerConfig;

    public AgendadorDeVagas(
            BuscadorDeVagas buscadorDeVagas,
            WhatsAppPort whatsAppPort,
            BotVagasProperties props
    ) {
        this.buscadorDeVagas = buscadorDeVagas;
        this.whatsAppPort = whatsAppPort;
        this.schedulerConfig = props.getScheduler();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void informarAgendamento() {
        ZonedDateTime agora = ZonedDateTime.now(FUSO_AGENDAMENTO);
        ZonedDateTime proximaExecucao = agora.withHour(18).withMinute(0).withSecond(0).withNano(0);
        if (!proximaExecucao.isAfter(agora)) {
            proximaExecucao = proximaExecucao.plusDays(1);
        }

        log.info(
                "Agendador carregado. Proxima execucao automatica: {}.",
                proximaExecucao.format(FORMATO_AGENDAMENTO)
        );

        if (!schedulerConfig.isExecutarAoIniciar()) {
            log.info("Para testar sem esperar as 18h, defina botvagas.scheduler.executar-ao-iniciar=true.");
            return;
        }

        log.warn(
                "Teste imediato habilitado. Vou executar um ciclo {}ms apos a inicializacao.",
                schedulerConfig.getDelayAoIniciarMs()
        );

        agendarTentativaAoIniciar(1);
    }

    @Scheduled(cron = "0 0 18 * * *", zone = "America/Sao_Paulo")
    public void executarCicloDiario() {
        if (!whatsAppPort.estaProntoParaEnvio()) {
            log.warn("Ciclo ignorado: WhatsApp ainda nao esta pronto para envio.");
            return;
        }

        String agora = LocalDateTime.now().format(FORMATO_HORA);

        log.info("============================================");
        log.info("Bot disparado em {}", agora);
        log.info("============================================");

        try {
            log.info("[1/2] Buscando vagas no portal...");
            long inicio = System.currentTimeMillis();

            String mensagem = buscadorDeVagas.buscarEFormatarVagas();

            log.info("[1/2] Concluido em {}ms.", System.currentTimeMillis() - inicio);
            log.info("[2/2] Enviando mensagem para o WhatsApp...");

            boolean enviado = whatsAppPort.enviarMensagemParaGrupoPrincipal(mensagem);
            if (!enviado) {
                throw new IllegalStateException("Falha ao enviar a mensagem para o WhatsApp.");
            }

            log.info("Ciclo diario concluido.");
        } catch (Exception e) {
            log.error("Falha no ciclo do bot: {}", e.getMessage(), e);
        }
    }

    private void agendarTentativaAoIniciar(int tentativa) {
        CompletableFuture.runAsync(
                () -> executarTentativaAoIniciar(tentativa),
                CompletableFuture.delayedExecutor(
                        schedulerConfig.getDelayAoIniciarMs(),
                        TimeUnit.MILLISECONDS
                )
        );
    }

    private void executarTentativaAoIniciar(int tentativa) {
        if (whatsAppPort.estaProntoParaEnvio()) {
            log.info("WhatsApp pronto no teste de inicializacao. Executando tentativa {}.", tentativa);
            executarCicloDiario();
            return;
        }

        if (tentativa >= MAX_TENTATIVAS_AO_INICIAR) {
            log.warn(
                    "Teste ao iniciar cancelado apos {} tentativa(s): WhatsApp ainda nao ficou pronto.",
                    tentativa
            );
            return;
        }

        log.warn(
                "WhatsApp ainda nao ficou pronto na tentativa {}. Vou tentar novamente em {}ms.",
                tentativa,
                schedulerConfig.getDelayAoIniciarMs()
        );
        agendarTentativaAoIniciar(tentativa + 1);
    }
}
