package br.unoeste.fipp.fila.queue;

import br.unoeste.fipp.fila.oci.OciHttpClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Cliente da API de Filas da OCI (versão 20210201), com as quatro operações usadas
 * no exemplo: PutMessages (produtor), GetMessages, UpdateMessage e DeleteMessage (consumidor).
 *
 * <p>Centralizar os endpoints aqui evita espalhar URLs montadas "na mão" pelo código.</p>
 *
 * <p>Documentação: https://docs.oracle.com/iaas/api/#/en/queue/20210201/</p>
 */
public final class QueueClient {

    private static final String API_VERSION = "20210201";

    private final OciHttpClient http;
    private final String messagesBaseUrl;

    /**
     * @param http             cliente HTTP que assina as requisições
     * @param messagesEndpoint "Endpoint de mensagens" mostrado na página da fila
     * @param queueOcid        OCID da fila
     */
    public QueueClient(OciHttpClient http, String messagesEndpoint, String queueOcid) {
        this.http = http;
        String base = messagesEndpoint.endsWith("/")
                ? messagesEndpoint.substring(0, messagesEndpoint.length() - 1)
                : messagesEndpoint;
        this.messagesBaseUrl = base + "/" + API_VERSION + "/queues/" + queueOcid + "/messages";
    }

    /**
     * PutMessages — publica uma mensagem na fila.
     *
     * @param content   conteúdo (payload) da mensagem
     * @param channelId canal de destino, ou {@code null} para publicar sem canal
     * @return id atribuído pela OCI à mensagem
     */
    public long putMessage(String content, String channelId) throws IOException, InterruptedException {
        JsonObject message = new JsonObject();
        message.addProperty("content", content);
        if (channelId != null && !channelId.isBlank()) {
            JsonObject metadata = new JsonObject();
            metadata.addProperty("channelId", channelId);
            message.add("metadata", metadata);
        }
        JsonArray messages = new JsonArray();
        messages.add(message);
        JsonObject body = new JsonObject();
        body.add("messages", messages);

        JsonElement response = http.request("POST", URI.create(messagesBaseUrl), body);

        // Resposta: {"messages":[{"id": 123, ...}]}
        JsonObject first = response.getAsJsonObject().getAsJsonArray("messages").get(0).getAsJsonObject();
        if (first.has("error") && !first.get("error").isJsonNull()) {
            throw new IOException("A OCI recusou a mensagem: " + first.get("errorMessage"));
        }
        return first.get("id").getAsLong();
    }

    /**
     * GetMessages — retira até {@code limit} mensagens da fila.
     * As mensagens retornadas ficam invisíveis para outros consumidores por
     * {@code visibilityInSeconds} segundos.
     *
     * @param limit               quantidade máxima de mensagens (1 a 20)
     * @param visibilityInSeconds tempo de invisibilidade; {@code null} usa o padrão da fila
     * @param timeoutInSeconds    long polling: quanto tempo esperar por mensagens (0 a 30)
     * @param channelFilter       consome apenas deste canal; {@code null} para qualquer mensagem
     */
    public List<QueueMessage> getMessages(int limit, Integer visibilityInSeconds, int timeoutInSeconds,
                                          String channelFilter) throws IOException, InterruptedException {
        StringBuilder url = new StringBuilder(messagesBaseUrl)
                .append("?limit=").append(limit)
                .append("&timeoutInSeconds=").append(timeoutInSeconds);
        if (visibilityInSeconds != null) {
            url.append("&visibilityInSeconds=").append(visibilityInSeconds);
        }
        if (channelFilter != null && !channelFilter.isBlank()) {
            url.append("&channelFilter=").append(encode(channelFilter));
        }

        JsonElement response = http.request("GET", URI.create(url.toString()), null);

        List<QueueMessage> result = new ArrayList<>();
        for (JsonElement el : response.getAsJsonObject().getAsJsonArray("messages")) {
            result.add(toQueueMessage(el.getAsJsonObject()));
        }
        return result;
    }

    /**
     * UpdateMessage — altera o tempo de invisibilidade da mensagem.
     * Usado para "devolver" a mensagem à fila quando o processamento falha:
     * depois de {@code visibilityInSeconds} ela poderá ser consumida de novo.
     */
    public void updateMessageVisibility(String receipt, int visibilityInSeconds)
            throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("visibilityInSeconds", visibilityInSeconds);
        http.request("PUT", URI.create(messagesBaseUrl + "/" + encode(receipt)), body);
    }

    /**
     * DeleteMessage — remove definitivamente a mensagem da fila.
     * Deve ser chamado após o processamento com sucesso; caso contrário,
     * a mensagem volta para a fila quando o timeout de visibilidade acabar.
     */
    public void deleteMessage(String receipt) throws IOException, InterruptedException {
        http.request("DELETE", URI.create(messagesBaseUrl + "/" + encode(receipt)), null);
    }

    // ---------- Auxiliares ----------

    private static QueueMessage toQueueMessage(JsonObject obj) {
        String channelId = null;
        if (obj.has("metadata") && obj.get("metadata").isJsonObject()) {
            JsonObject metadata = obj.getAsJsonObject("metadata");
            if (metadata.has("channelId") && !metadata.get("channelId").isJsonNull()) {
                channelId = metadata.get("channelId").getAsString();
            }
        }
        return new QueueMessage(
                obj.get("id").getAsLong(),
                text(obj, "content"),
                text(obj, "receipt"),
                obj.has("deliveryCount") ? obj.get("deliveryCount").getAsInt() : 0,
                text(obj, "visibleAfter"),
                text(obj, "expireAfter"),
                channelId);
    }

    private static String text(JsonObject obj, String field) {
        return obj.has(field) && !obj.get(field).isJsonNull() ? obj.get(field).getAsString() : null;
    }

    /** O receipt contém caracteres como "+", "/" e "=", que precisam ser codificados na URL. */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
