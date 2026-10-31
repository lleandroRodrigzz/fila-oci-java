package br.unoeste.fipp.fila.queue;

/**
 * Mensagem retirada da fila pelo GetMessages.
 *
 * @param id            identificador da mensagem na fila
 * @param content       conteúdo (payload) enviado pelo produtor
 * @param receipt       comprovante de recebimento; necessário para UpdateMessage e DeleteMessage
 * @param deliveryCount quantas vezes a mensagem já foi entregue (ao atingir o máximo, vai para a DLQ)
 * @param visibleAfter  momento em que a mensagem volta a ficar visível para outros consumidores
 * @param expireAfter   momento em que a mensagem expira (período máximo de retenção)
 * @param channelId     canal da mensagem, ou {@code null} se foi publicada sem canal
 */
public record QueueMessage(long id, String content, String receipt, int deliveryCount,
                           String visibleAfter, String expireAfter, String channelId) {
}
