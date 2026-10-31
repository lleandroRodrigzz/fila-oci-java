package br.unoeste.fipp.fila.oci;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Envia requisições assinadas para a API da OCI e devolve o JSON da resposta.
 */
public final class OciHttpClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(40);

    private final OciRequestSigner signer;
    private final HttpClient http;

    public OciHttpClient(OciRequestSigner signer) {
        this.signer = signer;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * @return JSON da resposta ({@link JsonNull} se vier vazia)
     * @throws OciApiException se a OCI responder 4xx/5xx
     */
    public JsonElement request(String method, URI uri, JsonElement body) throws IOException, InterruptedException {
        boolean sendsBody = (method.equalsIgnoreCase("POST") || method.equalsIgnoreCase("PUT"));
        String json = (sendsBody && body != null) ? body.toString() : (sendsBody ? "" : null);

        Map<String, String> signatureHeaders = signer.sign(method, uri, json);

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("accept", "application/json")
                .method(method.toUpperCase(),
                        json == null ? HttpRequest.BodyPublishers.noBody()
                                     : HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        signatureHeaders.forEach(builder::header);

        HttpResponse<String> response = http.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        JsonElement result = response.body() == null || response.body().isBlank()
                ? JsonNull.INSTANCE
                : JsonParser.parseString(response.body());

        if (response.statusCode() >= 400) {
            throw OciApiException.from(response.statusCode(), result);
        }
        return result;
    }

    /** Erro retornado pela API da OCI. */
    public static final class OciApiException extends IOException {
        private static final long serialVersionUID = 1L;
        private final int statusCode;

        private OciApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }

        static OciApiException from(int status, JsonElement body) {
            String detail = body.toString();
            if (body.isJsonObject()) {
                JsonObject obj = body.getAsJsonObject();
                String code = obj.has("code") ? obj.get("code").getAsString() : "";
                String msg = obj.has("message") ? obj.get("message").getAsString() : "";
                detail = code + ": " + msg;
            }
            return new OciApiException(status, "OCI respondeu HTTP " + status + " - " + detail);
        }
    }
}
