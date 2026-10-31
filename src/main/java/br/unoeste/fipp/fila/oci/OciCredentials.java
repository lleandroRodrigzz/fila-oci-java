package br.unoeste.fipp.fila.oci;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Credenciais de API da OCI lidas do arquivo de configuração padrão ({@code ~/.oci/config}).
 *
 * <p>Equivale ao {@code ConfigFileAuthenticationDetailsProvider} usado no exemplo em Node.js.
 * O arquivo tem formato INI, por exemplo:</p>
 * <pre>
 * [DEFAULT]
 * user=ocid1.user.oc1..xxxx
 * fingerprint=12:34:56:...
 * tenancy=ocid1.tenancy.oc1..xxxx
 * region=sa-saopaulo-1
 * key_file=~/.oci/oci_api_key.pem
 * </pre>
 *
 * @param tenancyOcid OCID da tenancy
 * @param userOcid    OCID do usuário dono da chave de API
 * @param fingerprint impressão digital da chave pública cadastrada no console
 * @param keyFile     caminho da chave privada (.pem)
 * @param region      região padrão do perfil
 */
public record OciCredentials(String tenancyOcid, String userOcid, String fingerprint, Path keyFile, String region) {

    /** Identificador da chave no formato exigido pela OCI: tenancy/usuário/fingerprint. */
    public String keyId() {
        return tenancyOcid + "/" + userOcid + "/" + fingerprint;
    }

    /**
     * Lê um perfil (ex.: DEFAULT) do arquivo de configuração.
     *
     * @param configFile caminho do arquivo; "~" é expandido para a pasta do usuário
     * @param profile    nome do perfil entre colchetes no arquivo
     */
    public static OciCredentials fromConfigFile(String configFile, String profile) throws IOException {
        Path configPath = expandHome(configFile);
        if (!Files.exists(configPath)) {
            throw new IOException("Arquivo de configuração OCI não encontrado: " + configPath.toAbsolutePath());
        }

        Map<String, String> values = readProfile(Files.readAllLines(configPath), profile);
        if (values.isEmpty()) {
            throw new IOException("Perfil [" + profile + "] não encontrado em " + configPath);
        }

        Path keyFile = expandHome(required(values, "key_file"));
        // Caminho relativo da chave é resolvido a partir da pasta do arquivo config
        if (!keyFile.isAbsolute() && configPath.getParent() != null && !Files.exists(keyFile)) {
            keyFile = configPath.getParent().resolve(keyFile);
        }

        return new OciCredentials(
                required(values, "tenancy"),
                required(values, "user"),
                required(values, "fingerprint"),
                keyFile,
                values.getOrDefault("region", ""));
    }

    /** Percorre as linhas do INI e devolve as chaves do perfil pedido. */
    private static Map<String, String> readProfile(List<String> lines, String profile) {
        Map<String, String> values = new HashMap<>();
        boolean insideProfile = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                insideProfile = line.substring(1, line.length() - 1).trim().equals(profile);
                continue;
            }
            int eq = line.indexOf('=');
            if (insideProfile && eq > 0) {
                values.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        }
        return values;
    }

    private static String required(Map<String, String> values, String key) throws IOException {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IOException("Chave obrigatória ausente no config OCI: " + key);
        }
        return value;
    }

    private static Path expandHome(String path) {
        if (path.startsWith("~")) {
            return Path.of(System.getProperty("user.home") + path.substring(1));
        }
        return Path.of(path);
    }
}
