package br.unoeste.fipp.fila.email;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.regex.Pattern;

/**
 * Conteúdo da mensagem da fila: {@code {"email": "...", "texto": "..."}}.
 */
public record EmailMessage(String email, String texto) {

    private static final Gson GSON = new Gson();
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    public EmailMessage {
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("E-mail inválido: " + email);
        }
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("O texto da mensagem não pode ficar vazio.");
        }
        email = email.trim();
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    /** @throws IllegalArgumentException se o conteúdo for inválido */
    public static EmailMessage fromJson(String json) {
        JsonObject obj;
        try {
            obj = JsonParser.parseString(json == null ? "" : json).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new IllegalArgumentException("Conteúdo não é um JSON de e-mail: " + json, e);
        }
        return new EmailMessage(field(obj, "email"), field(obj, "texto"));
    }

    private static String field(JsonObject obj, String name) {
        return obj.has(name) && !obj.get(name).isJsonNull() ? obj.get(name).getAsString() : null;
    }
}
