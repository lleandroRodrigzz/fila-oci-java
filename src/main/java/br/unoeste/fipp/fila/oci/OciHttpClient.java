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
 * Equivalente Java da função {@code ociHttpRequest(uri, method, body)} do arquivo
 * {@code ociHttpRequest.js}: recebe o endpoint, o método e o corpo, assina a
 * requisição, envia para a API da OCI e devolve o JSON da resposta.
 *
 * <p>Melhorias em relação ao original:</p>
 * <ul>
 *   <li>Em vez de imprimir o erro e retornar {@code null}, lança {@link OciApiException}
 *       com o código HTTP e a mensagem da OCI, para que quem chamou decida o que fazer;</li>
 *   <li>O corpo é serializado uma única vez (no original era feito duas vezes);</li>
 *   <li>Um único {@link HttpClient} é reutilizado (reaproveita conexões) e há timeouts.</li>
 * </ul>
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
     * Faz uma requisição assinada à API da OCI.
     *
     * @param method GET, POST, PUT ou DELETE
     * @param uri    endpoint completo
     * @param body   corpo JSON (usado apenas em POST/PUT); pode ser {@code null}
     * @return JSON retornado pela API ({@link JsonNull} quando a resposta vem vazia)
     * @throws OciApiException se a API responder com status de erro (4xx/5xx)
     */
    public JsonElement request(String method, URI uri, JsonElement body) throws IOException, InterruptedException {
        // 1. Serializa o corpo (só faz sentido para POST/PUT)
        boolean sendsBody = (method.equalsIgnoreCase("POST") || method.equalsIgnoreCase("PUT"));
        String json = (sendsBody && body != null) ? body.toString() : (sendsBody ? "" : null);

        // 2. Assina: gera date, authorization e, se houver corpo, x-content-sha256/content-type
        Map<String, String> signatureHeaders = signer.sign(method, uri, json);

        // 3. Monta a requisição com os cabeçalhos da assinatura
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("accept", "application/json")
                .method(method.toUpperCase(),
                        json == null ? HttpRequest.BodyPublishers.noBody()
                                     : HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        signatureHeaders.forEach(builder::header);

        // 4. Envia e aguarda a resposta
        HttpResponse<String> response = http.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // 5. Converte a resposta em JSON (pode vir vazia, ex.: DeleteMessage)
        JsonElement result = response.body() == null || response.body().isBlank()
                ? JsonNull.INSTANCE
                : JsonParser.parseString(response.body());

        // 6. Erros da API viram exceção com a mensagem retornada pela OCI
        if (response.statusCode() >= 400) {
            throw OciApiException.from(response.statusCode(), result);
        }
        return result;
    }

    /** Erro devolvido pela API da OCI (corpo no formato {"code": "...", "message": "..."}). */
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
