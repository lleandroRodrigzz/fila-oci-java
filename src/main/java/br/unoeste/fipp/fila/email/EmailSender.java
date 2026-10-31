package br.unoeste.fipp.fila.email;

/**
 * Abstração do envio de e-mail.
 *
 * <p>O consumidor depende desta interface, e não de uma implementação concreta.
 * Assim é possível trocar o envio simulado por um envio real (SMTP com Jakarta Mail,
 * OCI Email Delivery etc.) sem alterar a lógica de consumo da fila.</p>
 */
public interface EmailSender {

    /**
     * Envia o e-mail.
     *
     * @throws EmailException se o envio falhar (a mensagem deve voltar para a fila)
     */
    void send(EmailMessage message) throws EmailException;

    /** Falha no envio de e-mail. */
    class EmailException extends Exception {
        private static final long serialVersionUID = 1L;

        public EmailException(String message) {
            super(message);
        }
    }
}
