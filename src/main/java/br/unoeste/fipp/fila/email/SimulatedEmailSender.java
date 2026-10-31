package br.unoeste.fipp.fila.email;

import java.util.Random;
import java.util.logging.Logger;

/**
 * Envio simulado: só registra no log e falha aleatoriamente conforme a taxa configurada.
 */
public final class SimulatedEmailSender implements EmailSender {

    private static final Logger LOG = Logger.getLogger(SimulatedEmailSender.class.getName());

    private final double failureRate;
    private final Random random = new Random();

    /** @param failureRate de 0.0 (nunca falha) a 1.0 (sempre falha) */
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
