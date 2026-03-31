package com.botvagas.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

@Validated
@ConfigurationProperties(prefix = "botvagas")
public class BotVagasProperties {
    @NotNull
    private Scraper scraper = new Scraper();

    @NotNull
    private Scheduler scheduler = new Scheduler();

    @NotNull
    private Whatsapp whatsapp = new Whatsapp();

    public Scraper getScraper() {
        return scraper;
    }

    public void setScraper(Scraper scraper) {
        this.scraper = scraper;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public Whatsapp getWhatsapp() {
        return whatsapp;
    }

    public void setWhatsapp(Whatsapp whatsapp) {
        this.whatsapp = whatsapp;
    }

    public static class Scheduler {
        private boolean executarAoIniciar = false;

        @Min(value = 0, message = "botvagas.scheduler.delay-ao-iniciar-ms nao pode ser negativo")
        private long delayAoIniciarMs = 15_000;

        public boolean isExecutarAoIniciar() {
            return executarAoIniciar;
        }

        public void setExecutarAoIniciar(boolean executarAoIniciar) {
            this.executarAoIniciar = executarAoIniciar;
        }

        public long getDelayAoIniciarMs() {
            return delayAoIniciarMs;
        }

        public void setDelayAoIniciarMs(long delayAoIniciarMs) {
            this.delayAoIniciarMs = delayAoIniciarMs;
        }
    }

    public static class Scraper {
        @NotBlank(message = "botvagas.scraper.url-base nao pode ser vazio")
        private String urlBase = "https://br.indeed.com/empregos?q=";

        @NotBlank(message = "botvagas.scraper.cargo nao pode ser vazio")
        private String cargo = "Engenharia de Software";

        @NotBlank(message = "botvagas.scraper.localizacao nao pode ser vazio")
        private String localizacao = "Brasil";

        private boolean incluirRemoto = true;
        private boolean incluirPresencial = true;

        @Min(value = 1, message = "botvagas.scraper.maximo-vagas deve ser pelo menos 1")
        private int maximoVagas = 10;

        @Min(value = 1000, message = "botvagas.scraper.timeout-ms deve ser pelo menos 1000")
        private int timeoutMs = 15_000;

        public String getUrlBase() {
            return urlBase;
        }

        public void setUrlBase(String urlBase) {
            this.urlBase = urlBase;
        }

        public String getCargo() {
            return cargo;
        }

        public void setCargo(String cargo) {
            this.cargo = cargo;
        }

        public String getLocalizacao() {
            return localizacao;
        }

        public void setLocalizacao(String localizacao) {
            this.localizacao = localizacao;
        }

        public boolean isIncluirRemoto() {
            return incluirRemoto;
        }

        public void setIncluirRemoto(boolean incluirRemoto) {
            this.incluirRemoto = incluirRemoto;
        }

        public boolean isIncluirPresencial() {
            return incluirPresencial;
        }

        public void setIncluirPresencial(boolean incluirPresencial) {
            this.incluirPresencial = incluirPresencial;
        }

        public int getMaximoVagas() {
            return maximoVagas;
        }

        public void setMaximoVagas(int maximoVagas) {
            this.maximoVagas = maximoVagas;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    public static class Whatsapp {
        @NotBlank(message = "botvagas.whatsapp.grupo-nome nao pode ser vazio")
        private String grupoNome = "Bot Vagas";

        @NotBlank(message = "botvagas.whatsapp.web-url nao pode ser vazio")
        private String webUrl = "https://web.whatsapp.com/";

        @NotBlank(message = "botvagas.whatsapp.navegador nao pode ser vazio")
        private String navegador = "chrome";

        private String driverPath = "";
        private String binarioPath = "";

        @NotNull(message = "botvagas.whatsapp.diretorio-navegador nao pode ser nulo")
        private Path diretorioNavegador = Path.of(".botvagas", "whatsapp", "browser");

        @Min(value = 30, message = "botvagas.whatsapp.timeout-login-segundos deve ser pelo menos 30")
        private long timeoutLoginSegundos = 300;

        @Min(value = 5, message = "botvagas.whatsapp.timeout-elementos-segundos deve ser pelo menos 5")
        private long timeoutElementosSegundos = 30;

        @Min(value = 0, message = "botvagas.whatsapp.tempo-estabilizacao-conexao-ms nao pode ser negativo")
        private long tempoEstabilizacaoConexaoMs = 5_000;

        private boolean autoStart = true;
        private boolean headless = false;

        public String getGrupoNome() {
            return grupoNome;
        }

        public void setGrupoNome(String grupoNome) {
            this.grupoNome = grupoNome;
        }

        public String getWebUrl() {
            return webUrl;
        }

        public void setWebUrl(String webUrl) {
            this.webUrl = webUrl;
        }

        public String getNavegador() {
            return navegador;
        }

        public void setNavegador(String navegador) {
            this.navegador = navegador;
        }

        public String getDriverPath() {
            return driverPath;
        }

        public void setDriverPath(String driverPath) {
            this.driverPath = driverPath;
        }

        public String getBinarioPath() {
            return binarioPath;
        }

        public void setBinarioPath(String binarioPath) {
            this.binarioPath = binarioPath;
        }

        public Path getDiretorioNavegador() {
            return diretorioNavegador;
        }

        public void setDiretorioNavegador(Path diretorioNavegador) {
            this.diretorioNavegador = diretorioNavegador;
        }

        public long getTimeoutLoginSegundos() {
            return timeoutLoginSegundos;
        }

        public void setTimeoutLoginSegundos(long timeoutLoginSegundos) {
            this.timeoutLoginSegundos = timeoutLoginSegundos;
        }

        public long getTimeoutElementosSegundos() {
            return timeoutElementosSegundos;
        }

        public void setTimeoutElementosSegundos(long timeoutElementosSegundos) {
            this.timeoutElementosSegundos = timeoutElementosSegundos;
        }

        public long getTempoEstabilizacaoConexaoMs() {
            return tempoEstabilizacaoConexaoMs;
        }

        public void setTempoEstabilizacaoConexaoMs(long tempoEstabilizacaoConexaoMs) {
            this.tempoEstabilizacaoConexaoMs = tempoEstabilizacaoConexaoMs;
        }

        public boolean isAutoStart() {
            return autoStart;
        }

        public void setAutoStart(boolean autoStart) {
            this.autoStart = autoStart;
        }

        public boolean isHeadless() {
            return headless;
        }

        public void setHeadless(boolean headless) {
            this.headless = headless;
        }
    }
}
