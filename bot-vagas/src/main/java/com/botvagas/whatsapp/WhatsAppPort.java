package com.botvagas.whatsapp;

public interface WhatsAppPort {
    boolean estaProntoParaEnvio();

    boolean enviarMensagemParaGrupoPrincipal(String mensagem);

    boolean enviarMensagemParaGrupo(String idGrupo, String mensagem);

    void listarGrupos();
}
