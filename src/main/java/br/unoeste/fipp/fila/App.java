package br.unoeste.fipp.fila;

import br.unoeste.fipp.fila.config.AppConfig;
import br.unoeste.fipp.fila.email.SimulatedEmailSender;
import br.unoeste.fipp.fila.oci.OciCredentials;
import br.unoeste.fipp.fila.oci.OciHttpClient;
import br.unoeste.fipp.fila.oci.OciRequestSigner;
import br.unoeste.fipp.fila.queue.QueueClient;
import br.unoeste.fipp.fila.service.ConsumerService;
import br.unoeste.fipp.fila.service.ProducerService;
import br.unoeste.fipp.fila.web.WebServer;

import java.nio.file.Path;

/**
 * Ponto de entrada: monta os objetos (configuração → assinatura → cliente da fila →
 * serviços → servidor web) e sobe a aplicação.
 *
 * <p>Uso: {@code java -jar fila-oci-java.jar [caminho/do/application.properties]}</p>
 */
public final class App {

    public static void main(String[] args) throws Exception {
        Path configFile = Path.of(args.length > 0 ? args[0] : "application.properties");
        AppConfig config = AppConfig.load(configFile);

        // Camada OCI: credenciais → assinador → cliente HTTP (equivalente ao ociHttpRequest.js)
        OciCredentials credentials = OciCredentials.fromConfigFile(config.ociConfigFile(), config.ociConfigProfile());
        OciHttpClient ociHttp = new OciHttpClient(new OciRequestSigner(credentials));
        QueueClient queue = new QueueClient(ociHttp, config.queueMessagesEndpoint(), config.queueOcid());

        // Regras de negócio
        ProducerService producer = new ProducerService(queue, config.queueChannelId());
        ConsumerService consumer = new ConsumerService(
                queue,
                new SimulatedEmailSender(config.emailFailureRate()),
                config.queueChannelId(),
                config.consumerMaxMessages(),
                config.consumerPollTimeoutSeconds(),
                config.consumerRetryVisibilitySeconds());

        WebServer web = new WebServer(config.serverPort(), producer, consumer);
        web.start();
        Runtime.getRuntime().addShutdownHook(new Thread(web::stop));

        System.out.println("Aplicação no ar:");
        System.out.println("  Produtor:   http://localhost:" + config.serverPort() + "/produtor");
        System.out.println("  Consumidor: http://localhost:" + config.serverPort() + "/consumidor");
    }
}
