package br.unoeste.fipp.fila.email;

import java.util.Random;
import java.util.logging.Logger;

/**
 * Envio de e-mail simulado: apenas registra no log.
 *
 * <p>Para demonstrar o reprocessamento (UpdateMessage) e a DLQ, falha de forma
 * aleatória conforme a taxa configurada em {@code email.simulated.failure.rate},
 * imitando um provedor de e-mail instável.</p>
 */
public final class SimulatedEmailSender implements EmailSender {

    private static final Logger LOG = Logger.getLogger(SimulatedEmailSender.class.getName());

    private final double failureRate;
    private final Random random = new Random();

    /** @param failureRate probabilidade de falha, entre 0.0 (nunca) e 1.0 (sempre) */
    public SimulatedEmailSender(double failureRate) {
        if (failureRate < 0.0 || failureRate > 1.0) {
            throw new IllegalArgumentException("A taxa de falha deve estar entre 0.0 e 1.0");
        }
        this.failureRate = failureRate;
    }

    @Override
    public void send(EmailMessage message) throws EmailException {
        if (random.nextDouble() < failureRate) {
            throw new EmailException("Servidor de e-mail indisponível (falha simulada)");
        }
        LOG.info(() -> "E-mail enviado para " + message.email() + ": " + message.texto());
    }
}
