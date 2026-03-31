package com.botvagas.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record MensagemManualRequest(
        @NotEmpty(message = "destinos nao pode ser vazio")
        List<@NotBlank(message = "destino nao pode ser vazio") String> destinos,
        @NotBlank(message = "conteudo nao pode ser vazio")
        String conteudo
) {
}
