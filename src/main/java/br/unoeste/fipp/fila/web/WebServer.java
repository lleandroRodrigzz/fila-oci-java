package br.unoeste.fipp.fila.web;

import br.unoeste.fipp.fila.email.EmailMessage;
import br.unoeste.fipp.fila.service.ConsumerService;
import br.unoeste.fipp.fila.service.ConsumerService.ProcessingResult;
import br.unoeste.fipp.fila.service.ProducerService;
import br.unoeste.fipp.fila.service.ProducerService.PublishResult;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servidor HTTP embutido no JDK com duas responsabilidades:
 *
 * <ul>
 *   <li><b>API REST (JSON)</b>, consumida pelo front-end com Axios:
 *     <ul>
 *       <li>{@code GET  /api/config}: canal configurado;</li>
 *       <li>{@code POST /api/produtor}: {@code {"email","texto","canal"}} → publica na fila;</li>
 *       <li>{@code POST /api/consumidor}: {@code {"canal"}} → consome um lote.</li>
 *     </ul>
 *   </li>
 *   <li><b>Arquivos estáticos</b> (HTML/CSS/JS puros) de {@code src/main/resources/static}:
 *     {@code /produtor} e {@code /consumidor}.</li>
 * </ul>
 *
 * <p>Esta classe só trata HTTP. As regras ficam nos serviços e a interface fica
 * inteiramente no front-end — separação de responsabilidades.</p>
 */
public final class WebServer {

    private static final Logger LOG = Logger.getLogger(WebServer.class.getName());
    private static final Gson GSON = new Gson();
    private static final String STATIC_ROOT = "/static";

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "svg", "image/svg+xml",
            "png", "image/png",
            "ico", "image/x-icon");

    private final HttpServer server;
    private final ProducerService producer;
    private final ConsumerService consumer;

    public WebServer(int port, ProducerService producer, ConsumerService consumer) throws IOException {
        this.producer = producer;
        this.consumer = consumer;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newFixedThreadPool(4));
        this.server.createContext("/api/config", this::handleConfig);
        this.server.createContext("/api/produtor", this::handleProducer);
        this.server.createContext("/api/consumidor", this::handleConsumer);
        this.server.createContext("/", this::handleStatic);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    // ---------- API ----------

    private void handleConfig(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "GET")) return;
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("channelId", producer.channelId());
        sendJson(ex, 200, json);
    }

    private void handleProducer(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "POST")) return;
        try {
            JsonObject body = readJson(ex);
            EmailMessage email = new EmailMessage(string(body, "email"), string(body, "texto"));
            boolean useChannel = body.has("canal") && body.get("canal").getAsBoolean();

            PublishResult result = producer.publish(email, useChannel);
            Map<String, Object> json = new LinkedHashMap<>();
            // IDs da OCI passam de 2^53: como texto, o JavaScript não perde precisão
            json.put("messageId", String.valueOf(result.messageId()));
            json.put("channelId", result.channelId());
            sendJson(ex, 201, json);
        } catch (IllegalArgumentException e) {
            sendError(ex, 400, e.getMessage());
        } catch (IOException | InterruptedException e) {
            LOG.log(Level.WARNING, "Falha ao publicar", e);
            sendError(ex, 502, "Erro ao publicar: " + e.getMessage());
            restoreInterrupt(e);
        }
    }

    private void handleConsumer(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "POST")) return;
        try {
            JsonObject body = readJson(ex);
            boolean useChannel = body.has("canal") && body.get("canal").getAsBoolean();

            List<Map<String, Object>> results = consumer.consume(useChannel).stream()
                    .map(WebServer::toJson)
                    .toList();
            sendJson(ex, 200, Map.of("resultados", results));
        } catch (IllegalArgumentException e) {
            sendError(ex, 400, e.getMessage());
        } catch (IOException | InterruptedException e) {
            LOG.log(Level.WARNING, "Falha ao consumir", e);
            sendError(ex, 502, "Erro ao consumir: " + e.getMessage());
            restoreInterrupt(e);
        }
    }

    private static Map<String, Object> toJson(ProcessingResult r) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", String.valueOf(r.message().id()));
        json.put("channelId", r.message().channelId());
        json.put("destinatario", r.destination());
        json.put("entregas", r.message().deliveryCount());
        json.put("status", r.status().name());
        json.put("detalhe", r.detail());
        return json;
    }

    // ---------- Arquivos estáticos ----------

    private void handleStatic(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "GET")) return;

        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) {
            redirect(ex, "/produtor");
            return;
        }
        if (path.equals("/produtor") || path.equals("/consumidor")) {
            path += ".html";
        }
        // Impede acesso fora da pasta static (path traversal)
        if (path.contains("..")) {
            sendError(ex, 400, "Caminho inválido.");
            return;
        }

        try (InputStream in = WebServer.class.getResourceAsStream(STATIC_ROOT + path)) {
            if (in == null) {
                sendError(ex, 404, "Recurso não encontrado: " + path);
                return;
            }
            String ext = path.substring(path.lastIndexOf('.') + 1);
            send(ex, 200, CONTENT_TYPES.getOrDefault(ext, "application/octet-stream"), in.readAllBytes());
        }
    }

    // ---------- Auxiliares HTTP ----------

    private static boolean requireMethod(HttpExchange ex, String method) throws IOException {
        if (ex.getRequestMethod().equals(method)) {
            return true;
        }
        ex.getResponseHeaders().set("Allow", method);
        sendError(ex, 405, "Método não permitido. Use " + method + ".");
        return false;
    }

    private static JsonObject readJson(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.isBlank()) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new IllegalArgumentException("Corpo da requisição não é um JSON válido.", e);
        }
    }

    private static String string(JsonObject obj, String name) {
        return obj.has(name) && !obj.get(name).isJsonNull() ? obj.get(name).getAsString() : null;
    }

    private static void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        send(ex, status, "application/json; charset=utf-8", GSON.toJson(body).getBytes(StandardCharsets.UTF_8));
    }

    private static void sendError(HttpExchange ex, int status, String message) throws IOException {
        sendJson(ex, status, Map.of("erro", message == null ? "Erro desconhecido" : message));
    }

    private static void send(HttpExchange ex, int status, String contentType, byte[] bytes) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().set("Location", location);
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }

    private static void restoreInterrupt(Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
