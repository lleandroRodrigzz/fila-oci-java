package br.unoeste.fipp.fila.email;

/**
 * Envio de e-mail.
 */
public interface EmailSender {

    /** @throws EmailException se o envio falhar */
    void send(EmailMessage message) throws EmailException;

    class EmailException extends Exception {
        private static final long serialVersionUID = 1L;

        public EmailException(String message) {
            super(message);
        }
    }
}
