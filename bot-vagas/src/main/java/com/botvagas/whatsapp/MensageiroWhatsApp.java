package com.botvagas.whatsapp;

import com.botvagas.config.BotVagasProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.edge.EdgeDriverService;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class MensageiroWhatsApp implements WhatsAppPort {
    private static final Logger log = LoggerFactory.getLogger(MensageiroWhatsApp.class);

    private static final By PAINEL_LATERAL = By.id("app");
    private static final By CAIXA_BUSCA = By.xpath(
            "//div[@id='side']//input[@role='textbox'][@aria-label or @placeholder]"
                    + " | //div[@id='side']//div[@contenteditable='true' and @role='textbox']"
                    + " | //div[@contenteditable='true'][@data-tab='2']"
    );
    // Seletores atualizados para WhatsApp Web 2024/2025 - Múltiplas estratégias
    private static final By CAIXA_MENSAGEM = By.xpath(
            "//footer//div[@contenteditable='true' and @role='textbox']" +
            " | //footer//div[@contenteditable='true'][@data-tab='10']" +
            " | //div[@contenteditable='true'][@tabindex='0']" +
            " | //footer//div[@class='x1n2onr6 x14yjl9h xudhj91'][@contenteditable='true']"
    );
    private static final By QR_CODE = By.xpath("//canvas");

    private final BotVagasProperties.Whatsapp config;
    private final AtomicBoolean sessaoAtiva = new AtomicBoolean(false);
    private final AtomicBoolean inicializacaoEmAndamento = new AtomicBoolean(false);
    private final AtomicLong loginConcluidoMs = new AtomicLong(0);
    private final ReentrantLock navegadorLock = new ReentrantLock();

    private volatile WebDriver driver;
    private volatile WebDriverWait wait;

    public MensageiroWhatsApp(BotVagasProperties props) {
        this.config = props.getWhatsapp();
    }

    @PostConstruct
    public void iniciarSessao() {
        if (!config.isAutoStart()) {
            log.warn("Auto start do WhatsApp Web desabilitado por configuracao.");
            return;
        }

        iniciarSessaoAsync();
    }

    @Override
    public boolean estaProntoParaEnvio() {
        WebDriver navegador = this.driver;
        if (navegador == null || !sessaoAtiva.get()) {
            return false;
        }

        long loginMs = loginConcluidoMs.get();
        if (loginMs == 0) {
            return false;
        }

        return System.currentTimeMillis() - loginMs >= config.getTempoEstabilizacaoConexaoMs();
    }

    @Override
    public boolean enviarMensagemParaGrupoPrincipal(String mensagem) {
        return enviarMensagemParaGrupo(config.getGrupoNome(), mensagem);
    }

    @Override
    public boolean enviarMensagemParaGrupo(String nomeGrupo, String mensagem) {
        log.info("Recebi solicitacao de envio para '{}' ({} caractere(s)).", nomeGrupo, mensagem == null ? 0 : mensagem.length());

        if (mensagem == null || mensagem.isBlank()) {
            log.warn("Mensagem vazia. Envio ignorado.");
            return false;
        }

        garantirInicializacao();
        if (!estaProntoParaEnvio()) {
            log.warn("WhatsApp Web ainda nao esta pronto. Estado atual: {}", diagnosticoProntidao());
            return false;
        }

        navegadorLock.lock();
        try {
            if (!estaProntoParaEnvio()) {
                log.warn("Sessao ficou indisponivel antes do envio. Estado atual: {}", diagnosticoProntidao());
                return false;
            }

            log.info("Abrindo busca do WhatsApp Web para localizar '{}'.", nomeGrupo);
            WebElement busca = esperarVisivel(CAIXA_BUSCA, config.getTimeoutElementosSegundos());
            limparCampo(busca);
            busca.sendKeys(nomeGrupo);

            log.info("Aguardando a conversa '{}' aparecer na lista lateral.", nomeGrupo);
            abrirConversa(nomeGrupo);

            log.info("Conversa '{}' aberta. Preparando a caixa de mensagem.", nomeGrupo);
            WebElement caixaMensagem = encontrarCaixaMensagem();
            enviarTexto(caixaMensagem, mensagem);
            limparCampo(busca);

            log.info("Mensagem enviada para o grupo '{}'.", nomeGrupo);
            return true;
        } catch (Exception e) {
            sessaoAtiva.set(false);
            log.error("Falha ao enviar mensagem para '{}': {}", nomeGrupo, mensagemRaiz(e), e);
            iniciarSessaoAsync();
            return false;
        } finally {
            navegadorLock.unlock();
        }
    }

    @Override
    public void listarGrupos() {
        log.warn("Listagem automatica de grupos nao esta implementada no modo Selenium. Use botvagas.whatsapp.grupo-nome.");
    }

    @PreDestroy
    public void encerrarSessao() {
        WebDriver navegador = this.driver;
        if (navegador == null) {
            return;
        }

        try {
            navegador.quit();
        } catch (Exception e) {
            log.warn("Falha ao encerrar o navegador do WhatsApp Web: {}", mensagemRaiz(e));
        } finally {
            this.driver = null;
            this.wait = null;
            sessaoAtiva.set(false);
            loginConcluidoMs.set(0);
        }
    }

    private void iniciarSessaoAsync() {
        if (!inicializacaoEmAndamento.compareAndSet(false, true)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                iniciarNavegador();
            } finally {
                inicializacaoEmAndamento.set(false);
            }
        });
    }

    private void garantirInicializacao() {
        if (this.driver == null && !inicializacaoEmAndamento.get()) {
            iniciarSessaoAsync();
        }
    }

    private void iniciarNavegador() {
        navegadorLock.lock();
        try {
            if (this.driver != null) {
                return;
            }

            Path diretorioNavegador = prepararDiretorio(config.getDiretorioNavegador());
            WebDriver navegador = criarDriver(diretorioNavegador);
            navegador.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(config.getTimeoutLoginSegundos()));
            navegador.get(config.getWebUrl());

            this.driver = navegador;
            this.wait = new WebDriverWait(navegador, Duration.ofSeconds(config.getTimeoutElementosSegundos()));

            log.info(
                    "Inicializando WhatsApp Web | navegador='{}' | grupo='{}' | driver='{}' | binario='{}' | diretorio='{}'",
                    config.getNavegador(),
                    config.getGrupoNome(),
                    descreverDriver(),
                    descreverBinario(),
                    diretorioNavegador.toAbsolutePath()
            );

            aguardarLogin(navegador);
        } catch (Exception e) {
            log.error("Falha ao iniciar o navegador do WhatsApp Web: {}", mensagemRaiz(e), e);
            sessaoAtiva.set(false);
            loginConcluidoMs.set(0);
            fecharDriverSilenciosamente();
        } finally {
            navegadorLock.unlock();
        }
    }

    private WebDriver criarDriver(Path diretorioNavegador) {
        String navegador = config.getNavegador().toLowerCase(Locale.ROOT);
        return switch (navegador) {
            case "opera" -> criarOperaDriver(diretorioNavegador);
            case "edge" -> criarEdgeDriver(diretorioNavegador);
            case "chrome" -> criarChromeDriver(diretorioNavegador);
            default -> throw new IllegalStateException("Navegador nao suportado: " + config.getNavegador());
        };
    }

    private WebDriver criarOperaDriver(Path diretorioNavegador) {
        return criarChromeDriverInterno(diretorioNavegador, "Opera GX");
    }

    private WebDriver criarEdgeDriver(Path diretorioNavegador) {
        Path driverPath = resolverDriverPathOpcional();
        EdgeOptions options = new EdgeOptions();
        options.addArguments("--user-data-dir=" + diretorioNavegador.toAbsolutePath());
        options.addArguments("--profile-directory=Default");
        options.addArguments("--start-maximized");
        options.addArguments("--disable-notifications");
        options.addArguments("--remote-allow-origins=*");
        if (!config.getBinarioPath().isBlank()) {
            options.setBinary(resolverArquivo(config.getBinarioPath(), "binario do Edge").toString());
        }
        if (config.isHeadless()) {
            options.addArguments("--headless=new");
        }
        if (driverPath != null) {
            EdgeDriverService service = new EdgeDriverService.Builder()
                    .usingDriverExecutable(driverPath.toFile())
                    .build();
            return new EdgeDriver(service, options);
        }
        return new EdgeDriver(options);
    }

    private WebDriver criarChromeDriver(Path diretorioNavegador) {
        return criarChromeDriverInterno(diretorioNavegador, "Chrome/Chromium");
    }

    private WebDriver criarChromeDriverInterno(Path diretorioNavegador, String descricaoNavegador) {
        Path driverPath = resolverDriverPathObrigatorio(descricaoNavegador);
        ChromeOptions options = new ChromeOptions();
        if (!config.getBinarioPath().isBlank()) {
            options.setBinary(resolverArquivo(config.getBinarioPath(), "binario do navegador").toString());
        }
        options.addArguments("--user-data-dir=" + diretorioNavegador.toAbsolutePath());
        options.addArguments("--profile-directory=Default");
        options.addArguments("--start-maximized");
        options.addArguments("--disable-notifications");
        options.addArguments("--remote-allow-origins=*");
        if (config.isHeadless()) {
            options.addArguments("--headless=new");
        }
        ChromeDriverService service = new ChromeDriverService.Builder()
                .usingDriverExecutable(driverPath.toFile())
                .build();
        return new ChromeDriver(service, options);
    }

    private void aguardarLogin(WebDriver navegador) {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(config.getTimeoutLoginSegundos());
        boolean qrAvisado = false;

        while (System.currentTimeMillis() < deadline) {
            if (elementoVisivel(navegador, PAINEL_LATERAL)) {
                sessaoAtiva.set(true);
                loginConcluidoMs.set(System.currentTimeMillis());
                log.info("Sessao WhatsApp Web autenticada com sucesso.");
                return;
            }

            if (!qrAvisado && elementoVisivel(navegador, QR_CODE)) {
                qrAvisado = true;
                log.info("Aguardando leitura do QR Code no navegador.");
            }

            dormir(2);
        }

        throw new IllegalStateException("Tempo limite excedido aguardando login no WhatsApp Web.");
    }

    private WebElement esperarVisivel(By locator, long timeoutSegundos) {
        WebDriver navegador = obterDriver();
        WebDriverWait localWait = new WebDriverWait(navegador, Duration.ofSeconds(timeoutSegundos));
        return localWait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    private WebElement esperarClicavel(By locator, long timeoutSegundos) {
        WebDriver navegador = obterDriver();
        WebDriverWait localWait = new WebDriverWait(navegador, Duration.ofSeconds(timeoutSegundos));
        return localWait.until(ExpectedConditions.elementToBeClickable(locator));
    }

    /**
     * Encontra a caixa de mensagem usando JavaScript.
     * O WhatsApp Web muda frequentemente os seletores, então JavaScript é mais confiável.
     */
    private WebElement encontrarCaixaMensagem() {
        WebDriver navegador = obterDriver();
        JavascriptExecutor js = (JavascriptExecutor) navegador;
        
        log.info("🔍 Buscando caixa de mensagem do WhatsApp...");
        
        // Estratégia principal: JavaScript buscando TODOS contenteditable
        try {
            List<WebElement> elementos = (List<WebElement>) js.executeScript(
                "return document.querySelectorAll('div[contenteditable=\"true\"][role=\"textbox\"], div[contenteditable=\"true\"][data-tab=\"10\"], div[contenteditable=\"true\"][data-tab=\"2\"][tabindex=\"0\"], footer div[contenteditable=\"true\"]')"
            );
            
            if (elementos != null && !elementos.isEmpty()) {
                for (WebElement elemento : elementos) {
                    try {
                        if (elemento.isDisplayed()) {
                            int height = elemento.getSize().getHeight();
                            int width = elemento.getSize().getWidth();
                            
                            log.debug("Elemento encontrado: {}x{}", width, height);
                            
                            // Caixa de mensagem tem tamanho característico
                            if (height >= 20 && height <= 150 && width >= 100) {
                                log.info("✅ Caixa de mensagem encontrada via JavaScript ({}x{})", width, height);
                                return elemento;
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Erro ao verificar elemento: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("JavaScript falhou: {}", e.getMessage());
        }
        
        // Estratégia 2: Buscar por footer
        try {
            By seletorFooter = By.xpath("//footer//div[@contenteditable='true']");
            WebDriverWait localWait = new WebDriverWait(navegador, Duration.ofSeconds(5));
            WebElement elemento = localWait.until(ExpectedConditions.visibilityOfElementLocated(seletorFooter));
            log.info("✅ Caixa de mensagem encontrada no footer");
            return elemento;
        } catch (TimeoutException e) {
            log.debug("Footer falhou...");
        }
        
        // Estratégia 3: SALVAR HTML PRA DEBUG
        try {
            log.warn("⚠️ Caixa de mensagem não encontrada! Salvando HTML...");
            String html = (String) js.executeScript("return document.body.outerHTML;");
            java.nio.file.Files.writeString(
                java.nio.file.Paths.get("whatsapp-debug-" + System.currentTimeMillis() + ".html"),
                html
            );
            log.warn("HTML salvo em: whatsapp-debug-{}.html", System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("Não foi possível salvar HTML: {}", e.getMessage());
        }
        
        log.error("❌ Caixa de mensagem do WhatsApp não encontrada!");
        throw new IllegalStateException("Caixa de mensagem do WhatsApp não encontrada. HTML salvo pra debug.");
    }

    private void abrirConversa(String nomeGrupo) {
        String titulo = xpathLiteral(nomeGrupo);
        By conversa = By.xpath("//div[@id='pane-side']//span[@title=" + titulo + "]/ancestor::div[@role='gridcell'][1]");
        WebElement itemConversa = esperarVisivel(conversa, config.getTimeoutElementosSegundos());
        rolarParaElemento(itemConversa);

        try {
            esperarClicavel(conversa, config.getTimeoutElementosSegundos()).click();
            log.info("Clique direto na conversa '{}' funcionou", nomeGrupo);
            return;
        } catch (ElementClickInterceptedException e) {
            log.warn("Clique direto na conversa '{}' foi interceptado. Vou usar fallback via JavaScript.", nomeGrupo);
        } catch (Exception e) {
            log.warn("Erro ao clicar na conversa '{}': {}. Tentando JavaScript...", nomeGrupo, e.getMessage());
        }

        clicarViaJavaScript(itemConversa);
        
        // AGUARDAR a conversa carregar ANTES de buscar a caixa de mensagem
        try {
            log.info("Aguardando conversa '{}' carregar...", nomeGrupo);
            Thread.sleep(2000);  // Espera 2 segundos pra conversa carregar
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void limparCampo(WebElement elemento) {
        elemento.click();
        elemento.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        elemento.sendKeys(Keys.DELETE);
    }

    private void enviarTexto(WebElement caixaMensagem, String mensagem) {
        List<String> linhas = List.of(mensagem.split("\\R", -1));
        Actions actions = new Actions(obterDriver());
        actions.click(caixaMensagem);

        for (int i = 0; i < linhas.size(); i++) {
            String linha = linhas.get(i);
            if (!linha.isEmpty()) {
                actions.sendKeys(linha);
            }

            if (i < linhas.size() - 1) {
                actions.keyDown(Keys.SHIFT).sendKeys(Keys.ENTER).keyUp(Keys.SHIFT);
            }
        }

        actions.sendKeys(Keys.ENTER).perform();

        try {
            WebDriverWait localWait = new WebDriverWait(obterDriver(), Duration.ofSeconds(config.getTimeoutElementosSegundos()));
            localWait.until(driver -> campoSemTexto(caixaMensagem));
        } catch (TimeoutException ignored) {
            log.warn("A caixa de mensagem nao esvaziou apos o envio. Vou considerar que o navegador disparou a mensagem.");
        }
    }

    private boolean campoSemTexto(WebElement elemento) {
        try {
            String texto = elemento.getText();
            return texto == null || texto.isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    private void rolarParaElemento(WebElement elemento) {
        WebDriver navegador = obterDriver();
        if (navegador instanceof JavascriptExecutor executor) {
            executor.executeScript("arguments[0].scrollIntoView({block: 'center', inline: 'nearest'});", elemento);
        }
    }

    private void clicarViaJavaScript(WebElement elemento) {
        WebDriver navegador = obterDriver();
        if (navegador instanceof JavascriptExecutor executor) {
            executor.executeScript("arguments[0].click();", elemento);
            return;
        }

        throw new IllegalStateException("O navegador atual nao suporta fallback de clique via JavaScript.");
    }

    private boolean elementoVisivel(WebDriver navegador, By locator) {
        try {
            List<WebElement> elementos = navegador.findElements(locator);
            return elementos.stream().anyMatch(WebElement::isDisplayed);
        } catch (Exception e) {
            return false;
        }
    }

    private WebDriver obterDriver() {
        WebDriver navegador = this.driver;
        if (navegador == null) {
            throw new IllegalStateException("Navegador do WhatsApp Web ainda nao foi inicializado.");
        }
        return navegador;
    }

    private Path prepararDiretorio(Path diretorio) throws IOException {
        Path absoluto = diretorio.toAbsolutePath().normalize();
        Files.createDirectories(absoluto);
        return absoluto;
    }

    private Path resolverDriverPathObrigatorio(String descricaoNavegador) {
        Path driverPath = resolverDriverPathOpcional();
        if (driverPath != null) {
            return driverPath;
        }

        throw new IllegalStateException(
                "Nao encontrei um chromedriver.exe local para automatizar o " + descricaoNavegador
                        + ". Coloque o arquivo na raiz do workspace, no diretorio do modulo, ou configure botvagas.whatsapp.driver-path."
        );
    }

    private Path resolverDriverPathOpcional() {
        List<Path> candidatos = new ArrayList<>();
        if (!config.getDriverPath().isBlank()) {
            candidatos.add(Paths.get(config.getDriverPath()));
        }
        candidatos.add(Paths.get("chromedriver.exe"));
        candidatos.add(Paths.get("..", "chromedriver.exe"));
        candidatos.add(Paths.get(".botvagas", "drivers", "chromedriver.exe"));

        for (Path candidato : candidatos) {
            Path normalizado = candidato.toAbsolutePath().normalize();
            if (Files.isRegularFile(normalizado)) {
                return normalizado;
            }
        }

        return null;
    }

    private Path resolverArquivo(String valor, String descricao) {
        Path arquivo = Paths.get(valor).toAbsolutePath().normalize();
        if (!Files.isRegularFile(arquivo)) {
            throw new IllegalStateException("Nao encontrei o " + descricao + " em '" + arquivo + "'.");
        }
        return arquivo;
    }

    private String descreverDriver() {
        Path driverPath = resolverDriverPathOpcional();
        return driverPath == null ? "automatico" : driverPath.toString();
    }

    private String descreverBinario() {
        return config.getBinarioPath().isBlank() ? "padrao" : Paths.get(config.getBinarioPath()).toAbsolutePath().normalize().toString();
    }

    private String diagnosticoProntidao() {
        WebDriver navegador = this.driver;
        long loginMs = loginConcluidoMs.get();
        long tempoDesdeLogin = loginMs == 0 ? -1 : System.currentTimeMillis() - loginMs;
        return "driver=" + (navegador == null ? "nao" : "sim")
                + ", sessaoAtiva=" + sessaoAtiva.get()
                + ", inicializacaoEmAndamento=" + inicializacaoEmAndamento.get()
                + ", msDesdeLogin=" + tempoDesdeLogin
                + ", estabilizacaoConfiguradaMs=" + config.getTempoEstabilizacaoConexaoMs();
    }

    private void fecharDriverSilenciosamente() {
        WebDriver navegador = this.driver;
        this.driver = null;
        this.wait = null;
        if (navegador == null) {
            return;
        }

        try {
            navegador.quit();
        } catch (Exception ignored) {
        }
    }

    private void dormir(long segundos) {
        try {
            TimeUnit.SECONDS.sleep(segundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread interrompida enquanto aguardava o WhatsApp Web.", e);
        }
    }

    private String mensagemRaiz(Throwable erro) {
        Throwable atual = erro;
        while (atual.getCause() != null && atual.getCause() != atual) {
            atual = atual.getCause();
        }

        return atual.getMessage() == null || atual.getMessage().isBlank()
                ? atual.getClass().getSimpleName()
                : atual.getMessage();
    }

    private String xpathLiteral(String valor) {
        if (!valor.contains("'")) {
            return "'" + valor + "'";
        }

        if (!valor.contains("\"")) {
            return "\"" + valor + "\"";
        }

        StringBuilder builder = new StringBuilder("concat(");
        char[] chars = valor.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (i > 0) {
                builder.append(',');
            }

            char atual = chars[i];
            if (atual == '\'') {
                builder.append("\"'\"");
            } else if (atual == '"') {
                builder.append("'\"'");
            } else {
                builder.append('\'').append(atual).append('\'');
            }
        }
        builder.append(')');
        return builder.toString();
    }
}
