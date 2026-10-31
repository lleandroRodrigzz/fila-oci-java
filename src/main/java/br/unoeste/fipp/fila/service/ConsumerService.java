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
 * Consumidor: retira mensagens da fila e envia os e-mails.
 * Sucesso exclui a mensagem; falha a devolve para a fila.
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

    /** useChannel consome só do canal configurado */
    public List<ProcessingResult> consume(boolean useChannel) throws IOException, InterruptedException {
        List<QueueMessage> messages = queue.getMessages(
                maxMessages, null, pollTimeoutSeconds, useChannel ? channelId : null);

        List<ProcessingResult> results = new ArrayList<>();
        for (QueueMessage message : messages) {
            results.add(process(message));
        }
        return results;
    }

    private ProcessingResult process(QueueMessage message) throws InterruptedException {
        String destination = "?";
        try {
            EmailMessage email = EmailMessage.fromJson(message.content());
            destination = email.email();

            emailSender.send(email);

            queue.deleteMessage(message.receipt());
            return new ProcessingResult(message, destination, Status.ENVIADO, "E-mail enviado e mensagem excluída da fila");

        } catch (EmailSender.EmailException | IllegalArgumentException e) {
            return requeue(message, destination, e.getMessage());

        } catch (IOException e) {
            // Falha no DeleteMessage: a mensagem volta sozinha ao fim da visibilidade
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

    public enum Status { ENVIADO, DEVOLVIDO, ERRO }

    public record ProcessingResult(QueueMessage message, String destination, Status status, String detail) {
    }
}
