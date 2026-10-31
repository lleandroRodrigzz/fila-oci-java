package br.unoeste.fipp.fila.service;

import br.unoeste.fipp.fila.email.EmailMessage;
import br.unoeste.fipp.fila.queue.QueueClient;

import java.io.IOException;

/**
 * Produtor: publica pedidos de e-mail na fila.
 */
public final class ProducerService {

    private final QueueClient queue;
    private final String channelId;

    public ProducerService(QueueClient queue, String channelId) {
        this.queue = queue;
        this.channelId = channelId;
    }

    /** @param useChannel publica no canal configurado */
    public PublishResult publish(EmailMessage email, boolean useChannel) throws IOException, InterruptedException {
        String channel = useChannel ? channelId : null;
        long messageId = queue.putMessage(email.toJson(), channel);
        return new PublishResult(messageId, channel);
    }

    public String channelId() {
        return channelId;
    }

    /** @param channelId {@code null} se publicada sem canal */
    public record PublishResult(long messageId, String channelId) {
    }
}
