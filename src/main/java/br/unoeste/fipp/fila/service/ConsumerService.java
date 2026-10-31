package br.unoeste.fipp.fila.service;

import br.unoeste.fipp.fila.email.EmailMessage;
import br.unoeste.fipp.fila.email.EmailSender;
import br.unoeste.fipp.fila.queue.QueueClient;
import br.unoeste.fipp.fila.queue.QueueMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Aplicação Consumidora: retira mensagens da fila e dispara os e-mails.
 *
 * <p>Fluxo para cada mensagem (mesma lógica do exemplo original):</p>
 * <ol>
 *   <li>{@code GetMessages}: a mensagem fica invisível para outros consumidores;</li>
 *   <li>Tenta enviar o e-mail;</li>
 *   <li>Sucesso → {@code DeleteMessage}: a mensagem sai da fila definitivamente;</li>
 *   <li>Falha → {@code UpdateMessage}: a mensagem volta para a fila após
 *       {@code retryVisibilitySeconds}. Se falhar mais vezes que o "Máximo de
 *       tentativas de entrega" da fila, a OCI a move para a DLQ.</li>
 * </ol>
 */
public final class ConsumerService {

    private static final Logger LOG = Logger.getLogger(ConsumerService.class.getName());

    private final QueueClient queue;
    private final EmailSender emailSender;
    private final String channelId;
    private final int maxMessages;
    private final int pollTimeoutSeconds;
    private final int retryVisibilitySeconds;

    public ConsumerService(QueueClient queue, EmailSender emailSender, String channelId,
                           int maxMessages, int pollTimeoutSeconds, int retryVisibilitySeconds) {
        this.queue = queue;
        this.emailSender = emailSender;
        this.channelId = channelId;
        this.maxMessages = maxMessages;
        this.pollTimeoutSeconds = pollTimeoutSeconds;
        this.retryVisibilitySeconds = retryVisibilitySeconds;
    }

    /**
     * Consome um lote de mensagens.
     *
     * @param useChannel {@code true} para consumir só do canal configurado ("CONSUMIR - CANAL")
     * @return o que aconteceu com cada mensagem retirada
     */
    public List<ProcessingResult> consume(boolean useChannel) throws IOException, InterruptedException {
        // visibilityInSeconds = null → usa o "Timeout de visibilidade" configurado na fila
        List<QueueMessage> messages = queue.getMessages(
                maxMessages, null, pollTimeoutSeconds, useChannel ? channelId : null);

        List<ProcessingResult> results = new ArrayList<>();
        for (QueueMessage message : messages) {
            results.add(process(message));
        }
        return results;
    }

    /** Processa uma mensagem; uma falha aqui não interrompe as demais do lote. */
    private ProcessingResult process(QueueMessage message) throws InterruptedException {
        String destination = "?";
        try {
            EmailMessage email = EmailMessage.fromJson(message.content());
            destination = email.email();

            emailSender.send(email);

            queue.deleteMessage(message.receipt());
            return new ProcessingResult(message, destination, Status.ENVIADO, "E-mail enviado e mensagem excluída da fila");

        } catch (EmailSender.EmailException | IllegalArgumentException e) {
            // Falha no processamento: devolve para a fila (nova tentativa mais tarde)
            return requeue(message, destination, e.getMessage());

        } catch (IOException e) {
            // Falha ao falar com a OCI no DeleteMessage: a mensagem reaparecerá sozinha
            // quando o timeout de visibilidade acabar.
            LOG.log(Level.WARNING, "Erro ao excluir mensagem " + message.id(), e);
            return new ProcessingResult(message, destination, Status.ERRO, e.getMessage());
        }
    }

    private ProcessingResult requeue(QueueMessage message, String destination, String reason)
            throws InterruptedException {
        try {
            queue.updateMessageVisibility(message.receipt(), retryVisibilitySeconds);
            return new ProcessingResult(message, destination, Status.DEVOLVIDO,
                    reason + " → devolvida à fila, nova tentativa em " + retryVisibilitySeconds + " s");
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Erro ao devolver mensagem " + message.id(), e);
            return new ProcessingResult(message, destination, Status.ERRO, reason + " / " + e.getMessage());
        }
    }

    /** Situação final de uma mensagem após o processamento. */
    public enum Status { ENVIADO, DEVOLVIDO, ERRO }

    /**
     * @param message     mensagem retirada da fila
     * @param destination destinatário do e-mail ("?" se o conteúdo for inválido)
     * @param status      resultado
     * @param detail      descrição para exibir ao usuário
     */
    public record ProcessingResult(QueueMessage message, String destination, Status status, String detail) {
    }
}
