package br.unoeste.fipp.fila.service;

import br.unoeste.fipp.fila.email.EmailMessage;
import br.unoeste.fipp.fila.queue.QueueClient;

import java.io.IOException;

/**
 * Aplicação Produtora: recebe os dados do e-mail e publica na fila.
 *
 * <p>Quem envia o e-mail de fato é o consumidor. O produtor só registra o pedido
 * e retorna imediatamente — é o desacoplamento descrito na introdução sobre filas:
 * o usuário não espera o provedor de e-mail responder.</p>
 */
public final class ProducerService {

    private final QueueClient queue;
    private final String channelId;

    /**
     * @param queue     cliente da fila
     * @param channelId canal usado na opção "PUBLICAR - CANAL"
     */
    public ProducerService(QueueClient queue, String channelId) {
        this.queue = queue;
        this.channelId = channelId;
    }

    /**
     * Publica o pedido de e-mail na fila.
     *
     * @param email     dados do e-mail (já validados no construtor de {@link EmailMessage})
     * @param useChannel {@code true} para publicar no canal configurado ("PUBLICAR - CANAL")
     * @return resultado da publicação
     */
    public PublishResult publish(EmailMessage email, boolean useChannel) throws IOException, InterruptedException {
        String channel = useChannel ? channelId : null;
        long messageId = queue.putMessage(email.toJson(), channel);
        return new PublishResult(messageId, channel);
    }

    /** Canal usado na opção "PUBLICAR - CANAL". */
    public String channelId() {
        return channelId;
    }

    /**
     * @param messageId id da mensagem criada na fila
     * @param channelId canal usado, ou {@code null}
     */
    public record PublishResult(long messageId, String channelId) {
    }
}
