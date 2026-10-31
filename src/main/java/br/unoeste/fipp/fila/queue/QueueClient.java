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
 * Cliente da API de Filas da OCI (20210201).
 * Ref.: https://docs.oracle.com/iaas/api/#/en/queue/20210201/
 */
public final class QueueClient {

    private static final String API_VERSION = "20210201";

    private final OciHttpClient http;
    private final String messagesBaseUrl;

    public QueueClient(OciHttpClient http, String messagesEndpoint, String queueOcid) {
        this.http = http;
        String base = messagesEndpoint.endsWith("/")
                ? messagesEndpoint.substring(0, messagesEndpoint.length() - 1)
                : messagesEndpoint;
        this.messagesBaseUrl = base + "/" + API_VERSION + "/queues/" + queueOcid + "/messages";
    }

    /** PutMessages. Retorna o id da mensagem criada. */
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

        JsonObject first = response.getAsJsonObject().getAsJsonArray("messages").get(0).getAsJsonObject();
        if (first.has("error") && !first.get("error").isJsonNull()) {
            throw new IOException("A OCI recusou a mensagem: " + first.get("errorMessage"));
        }
        return first.get("id").getAsLong();
    }

    /**
     * GetMessages.
     *
     * @param visibilityInSeconds {@code null} usa o padrão da fila
     * @param channelFilter       {@code null} consome de qualquer canal
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

    /** UpdateMessage. Devolve a mensagem à fila após {@code visibilityInSeconds}. */
    public void updateMessageVisibility(String receipt, int visibilityInSeconds)
            throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("visibilityInSeconds", visibilityInSeconds);
        http.request("PUT", URI.create(messagesBaseUrl + "/" + encode(receipt)), body);
    }

    /** DeleteMessage. */
    public void deleteMessage(String receipt) throws IOException, InterruptedException {
        http.request("DELETE", URI.create(messagesBaseUrl + "/" + encode(receipt)), null);
    }

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

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
