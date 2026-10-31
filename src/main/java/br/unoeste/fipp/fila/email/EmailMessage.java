package br.unoeste.fipp.fila.email;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.regex.Pattern;

/**
 * Dados de um e-mail a ser enviado. É o payload que o produtor coloca na fila
 * e que o consumidor lê, serializado como JSON: {"email": "...", "texto": "..."}.
 *
 * @param email destinatário
 * @param texto corpo do e-mail
 */
public record EmailMessage(String email, String texto) {

    private static final Gson GSON = new Gson();
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    /** Valida os campos antes de publicar (evita colocar lixo na fila). */
    public EmailMessage {
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("E-mail inválido: " + email);
        }
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("O texto da mensagem não pode ficar vazio.");
        }
        email = email.trim();
    }

    /** Converte para o JSON que vai no corpo da mensagem da fila. */
    public String toJson() {
        return GSON.toJson(this);
    }

    /**
     * Lê o payload de uma mensagem da fila.
     *
     * @throws IllegalArgumentException se o conteúdo não for um e-mail válido
     */
    public static EmailMessage fromJson(String json) {
        JsonObject obj;
        try {
            obj = JsonParser.parseString(json == null ? "" : json).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new IllegalArgumentException("Conteúdo não é um JSON de e-mail: " + json, e);
        }
        // Passa pelo construtor, que aplica as validações de e-mail e texto.
        return new EmailMessage(field(obj, "email"), field(obj, "texto"));
    }

    private static String field(JsonObject obj, String name) {
        return obj.has(name) && !obj.get(name).isJsonNull() ? obj.get(name).getAsString() : null;
    }
}
