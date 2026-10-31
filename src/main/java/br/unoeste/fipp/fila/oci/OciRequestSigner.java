package br.unoeste.fipp.fila.oci;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Assina requisições no padrão da OCI (RSA-SHA256).
 * Ref.: https://docs.oracle.com/iaas/Content/API/Concepts/signingrequests.htm
 */
public final class OciRequestSigner {

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);

    private final String keyId;
    private final PrivateKey privateKey;

    public OciRequestSigner(OciCredentials credentials) throws IOException {
        this.keyId = credentials.keyId();
        this.privateKey = loadPrivateKey(credentials);
    }

    /** Retorna os cabeçalhos de assinatura que devem ser enviados na requisição. */
    public Map<String, String> sign(String method, URI uri, String body) {
        String lowerMethod = method.toLowerCase(Locale.ROOT);
        boolean hasBody = lowerMethod.equals("post") || lowerMethod.equals("put");
        byte[] bodyBytes = (body == null ? "" : body).getBytes(StandardCharsets.UTF_8);

        // A ordem importa: é a mesma informada em "headers"
        Map<String, String> signed = new LinkedHashMap<>();
        signed.put("(request-target)", lowerMethod + " " + pathAndQuery(uri));
        signed.put("host", hostHeader(uri));
        signed.put("date", HTTP_DATE.format(ZonedDateTime.now(ZoneOffset.UTC)));
        if (hasBody) {
            signed.put("x-content-sha256", sha256Base64(bodyBytes));
            signed.put("content-type", "application/json");
            signed.put("content-length", String.valueOf(bodyBytes.length));
        }

        String signingString = buildSigningString(signed);
        String signature = rsaSign(signingString);

        String authorization = String.format(
                "Signature version=\"1\",keyId=\"%s\",algorithm=\"rsa-sha256\",headers=\"%s\",signature=\"%s\"",
                keyId, String.join(" ", signed.keySet()), signature);

        // host e content-length são definidos pelo HttpClient
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("date", signed.get("date"));
        if (hasBody) {
            headers.put("x-content-sha256", signed.get("x-content-sha256"));
            headers.put("content-type", signed.get("content-type"));
        }
        headers.put("authorization", authorization);
        return headers;
    }

    static String buildSigningString(Map<String, String> signedHeaders) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> h : signedHeaders.entrySet()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(h.getKey()).append(": ").append(h.getValue());
        }
        return sb.toString();
    }

    private String rsaSign(String data) {
        try {
            Signature rsa = Signature.getInstance("SHA256withRSA");
            rsa.initSign(privateKey);
            rsa.update(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rsa.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao assinar a requisição", e);
        }
    }

    private static String sha256Base64(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            return Base64.getEncoder().encodeToString(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    private static String pathAndQuery(URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }

    private static String hostHeader(URI uri) {
        return uri.getPort() == -1 ? uri.getHost() : uri.getHost() + ":" + uri.getPort();
    }

    /** Lê a chave privada .pem nos formatos PKCS#8 ou PKCS#1. */
    private static PrivateKey loadPrivateKey(OciCredentials credentials) throws IOException {
        String pem = Files.readString(credentials.keyFile());
        if (pem.contains("ENCRYPTED")) {
            throw new IOException("Chaves privadas protegidas por senha não são suportadas. "
                    + "Use uma chave sem pass_phrase.");
        }
        boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
        String base64 = pem.replaceAll("-----(BEGIN|END)[A-Z ]*-----", "").replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        if (pkcs1) {
            der = wrapPkcs1InPkcs8(der);
        }
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IOException("Não foi possível ler a chave privada em " + credentials.keyFile(), e);
        }
    }

    /** Converte PKCS#1 em PKCS#8, único formato lido pelo Java. */
    private static byte[] wrapPkcs1InPkcs8(byte[] pkcs1) {
        // OID do RSA (1.2.840.113549.1.1.1) + NULL
        byte[] rsaAlgorithmId = {0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
                (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
        byte[] version = {0x02, 0x01, 0x00};
        byte[] octetString = derElement((byte) 0x04, pkcs1);
        byte[] content = concat(version, rsaAlgorithmId, octetString);
        return derElement((byte) 0x30, content);
    }

    private static byte[] derElement(byte tag, byte[] content) {
        int len = content.length;
        byte[] lenBytes;
        if (len < 0x80) {
            lenBytes = new byte[]{(byte) len};
        } else if (len <= 0xff) {
            lenBytes = new byte[]{(byte) 0x81, (byte) len};
        } else if (len <= 0xffff) {
            lenBytes = new byte[]{(byte) 0x82, (byte) (len >> 8), (byte) len};
        } else {
            lenBytes = new byte[]{(byte) 0x83, (byte) (len >> 16), (byte) (len >> 8), (byte) len};
        }
        return concat(new byte[]{tag}, lenBytes, content);
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) total += p.length;
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }
}
