package com.botvagas.api;

import com.botvagas.whatsapp.WhatsAppPort;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/zap-zap")
public class WhatsAppController {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppController.class);

    private final WhatsAppPort whatsAppPort;

    public WhatsAppController(WhatsAppPort whatsAppPort) {
        this.whatsAppPort = whatsAppPort;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> enviar(@Valid @RequestBody MensagemManualRequest request) {
        log.info("POST /zap-zap recebido para {} destino(s).", request.destinos().size());
        Map<String, Boolean> resultados = new LinkedHashMap<>();
        for (String destino : request.destinos()) {
            log.info("Disparando envio manual para '{}'.", destino);
            resultados.put(destino, whatsAppPort.enviarMensagemParaGrupo(destino, request.conteudo()));
        }

        boolean todosOk = resultados.values().stream().allMatch(Boolean::booleanValue);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sucesso", todosOk);
        body.put("resultados", resultados);

        return ResponseEntity.status(todosOk ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(body);
    }
}
