package br.unoeste.fipp.fila.queue;

/**
 * Mensagem retornada pelo GetMessages.
 *
 * @param receipt       necessário para UpdateMessage e DeleteMessage
 * @param deliveryCount entregas até agora; ao passar do limite da fila, vai para a DLQ
 * @param channelId     {@code null} se publicada sem canal
 */
public record QueueMessage(long id, String content, String receipt, int deliveryCount,
                           String visibleAfter, String expireAfter, String channelId) {
}
