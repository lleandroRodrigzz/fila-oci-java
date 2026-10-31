package br.unoeste.fipp.fila.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configurações da aplicação, lidas do arquivo {@code application.properties}.
 *
 * <p>Melhoria em relação ao exemplo original: lá o caminho do config e o perfil
 * ficavam fixos no código. Aqui tudo que muda entre ambientes (OCID da fila,
 * endpoint, tempos) fica fora do código-fonte.</p>
 *
 * <p>Qualquer chave pode ser sobrescrita por variável de ambiente, trocando
 * pontos por "_" e usando maiúsculas. Ex.: {@code queue.ocid} → {@code QUEUE_OCID}.</p>
 */
public final class AppConfig {

    private final Properties props;

    private AppConfig(Properties props) {
        this.props = props;
    }

    /** Carrega as configurações a partir do arquivo indicado. */
    public static AppConfig load(Path file) throws IOException {
        if (!Files.exists(file)) {
            throw new IOException("Arquivo de configuração não encontrado: " + file.toAbsolutePath()
                    + "\nCopie application.properties.example para application.properties e preencha os dados.");
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        }
        return new AppConfig(props);
    }

    // ---------- Acesso tipado às chaves usadas pela aplicação ----------

    public String ociConfigFile()          { return get("oci.config.file", ".oci/config"); }
    public String ociConfigProfile()       { return get("oci.config.profile", "DEFAULT"); }
    public String queueMessagesEndpoint()  { return require("queue.messages.endpoint"); }
    public String queueOcid()              { return require("queue.ocid"); }
    public String queueChannelId()         { return get("queue.channel.id", "canal-email"); }
    public int consumerMaxMessages()       { return getInt("consumer.max.messages", 20); }
    public int consumerPollTimeoutSeconds(){ return getInt("consumer.poll.timeout.seconds", 2); }
    public int consumerRetryVisibilitySeconds() { return getInt("consumer.retry.visibility.seconds", 10); }
    public double emailFailureRate()       { return Double.parseDouble(get("email.simulated.failure.rate", "0.0")); }
    public int serverPort()                { return getInt("server.port", 8080); }

    // ---------- Auxiliares ----------

    private String get(String key, String defaultValue) {
        String envKey = key.replace('.', '_').toUpperCase();
        String fromEnv = System.getenv(envKey);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String value = props.getProperty(key);
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    private String require(String key) {
        String value = get(key, null);
        if (value == null) {
            throw new IllegalStateException("Configuração obrigatória ausente: " + key);
        }
        return value;
    }

    private int getInt(String key, int defaultValue) {
        return Integer.parseInt(get(key, String.valueOf(defaultValue)));
    }
}
