# Filas OCI em Java — Produtor e Consumidor

Reimplementação em **Java 17+** do exemplo de uso de Filas da OCI (`appdemo3fila` + `ociHttpRequest.js`)
da disciplina Tópicos em Tecnologia da Computação III — FIPP/Unoeste.

A lógica central é a mesma do original:

- **Produtor** (`/produtor`): formulário com e-mail e texto; botões **PUBLICAR** e **PUBLICAR - CANAL** → `PutMessages`.
- **Consumidor** (`/consumidor`): botões **CONSUMIR** e **CONSUMIR - CANAL** → `GetMessages`; para cada mensagem tenta
  enviar o e-mail. Sucesso → `DeleteMessage`. Falha → `UpdateMessage` com `visibilityInSeconds` (volta para a fila;
  depois do "Máximo de tentativas de entrega", a OCI move para a DLQ).
- Todas as chamadas usam a **API REST** da OCI com requisições **assinadas** (como no exemplo), não o SDK.

## Estrutura

```
src/main/java/br/unoeste/fipp/fila/
├── App.java                     # ponto de entrada: monta os objetos e sobe o servidor
├── config/AppConfig.java        # lê application.properties (com override por variável de ambiente)
├── oci/
│   ├── OciCredentials.java      # lê ~/.oci/config  (≈ ConfigFileAuthenticationDetailsProvider)
│   ├── OciRequestSigner.java    # assinatura RSA-SHA256 das requisições (≈ DefaultRequestSigner)
│   └── OciHttpClient.java       # assina, envia e devolve o JSON (≈ ociHttpRequest.js)
├── queue/
│   ├── QueueClient.java         # PutMessages, GetMessages, UpdateMessage, DeleteMessage
│   └── QueueMessage.java        # mensagem retornada pelo GetMessages
├── email/
│   ├── EmailMessage.java        # payload {"email","texto"} com validação
│   ├── EmailSender.java         # interface de envio
│   └── SimulatedEmailSender.java# envio simulado com falhas aleatórias
├── service/
│   ├── ProducerService.java     # regra do produtor
│   └── ConsumerService.java     # regra do consumidor (envia / devolve / exclui)
└── web/
    └── WebServer.java           # API REST (JSON) + arquivos estáticos (servidor embutido no JDK)

src/main/resources/static/       # front-end em HTML/CSS/JS puros (consome a API com Axios)
├── produtor.html
├── consumidor.html
├── css/style.css                # tema Oracle Redwood + efeito "liquid glass"
└── js/
    ├── api.js                   # instância do Axios, toasts e utilitários
    ├── produtor.js
    └── consumidor.js
```

## API REST

| Método | Rota | Corpo | Resposta |
|---|---|---|---|
| `GET`  | `/api/config`     | — | `{"channelId"}` |
| `POST` | `/api/produtor`   | `{"email","texto","canal":bool}` | `201 {"messageId","channelId"}` · `400/502 {"erro"}` |
| `POST` | `/api/consumidor` | `{"canal":bool}` | `200 {"resultados":[{"id","channelId","destinatario","entregas","status","detalhe"}]}` · `502 {"erro"}` |

## Melhorias em relação ao exemplo original

| Original (Node.js) | Esta versão (Java) |
|---|---|
| Caminho do config e perfil fixos no código | Tudo em `application.properties` ou variáveis de ambiente |
| Em caso de erro: `console.log` e `return null` | `OciApiException` com status HTTP e mensagem da OCI |
| Corpo serializado duas vezes (`JSON.stringify`) | Serializado uma vez; o mesmo texto é assinado e enviado |
| Lógica de fila, HTTP e e-mail misturadas | Camadas separadas (oci → queue → service → web) |
| Envio de e-mail acoplado | Interface `EmailSender`: troca simulado por SMTP real sem mexer no consumidor |
| — | Validação do e-mail antes de publicar; escape HTML ao exibir dados |
| — | Erro em uma mensagem não interrompe o resto do lote |
| — | Aceita chave PEM nos formatos PKCS#8 e PKCS#1 |

## Pré-requisitos

1. **JDK 17 ou superior** e **Maven**.
2. **Chave de API** da OCI: Console → Perfil → *Chaves de API* → *Adicionar chave de API*. Baixe a chave privada e
   copie o trecho de configuração mostrado para `~/.oci/config` (ou `.oci/config` na pasta do projeto):

   ```ini
   [DEFAULT]
   user=ocid1.user.oc1..xxxx
   fingerprint=12:34:56:...
   tenancy=ocid1.tenancy.oc1..xxxx
   region=sa-saopaulo-1
   key_file=~/.oci/oci_api_key.pem
   ```
3. **Uma fila criada** (Serviços ao Desenvolvedor → Filas → Criar fila). Na página da fila anote o **OCID** e o
   **Endpoint de mensagens**.

## Como executar

```bash
cp application.properties.example application.properties
# edite: queue.ocid, queue.messages.endpoint e (se preciso) oci.config.file

mvn compile exec:java
```

Ou gere o jar executável:

```bash
mvn package
java -jar target/fila-oci-java-1.0.0.jar
```

Acesse <http://localhost:8080/produtor> e <http://localhost:8080/consumidor>.

## Roteiro para testar

1. No produtor, publique algumas mensagens com **PUBLICAR** e outras com **PUBLICAR - CANAL**.
2. No console da OCI (aba *Mensagens* → *Sondagem para mensagens*) confira que elas chegaram.
3. No consumidor, clique em **CONSUMIR - CANAL**: só as mensagens do canal aparecem.
4. Clique em **CONSUMIR**. Com `email.simulated.failure.rate=0.3`, parte dos envios falha e fica como **DEVOLVIDO**;
   depois de `consumer.retry.visibility.seconds` elas podem ser consumidas de novo (a coluna *Entregas* aumenta).
5. Para ver a DLQ, use `email.simulated.failure.rate=1.0`: depois do máximo de tentativas configurado na fila, a
   mensagem aparece na aba *Mensagens na fila de mensagens não processadas (DLQ)*.

## Erros comuns

- **401 NotAuthenticated**: fingerprint, OCIDs ou chave não batem, ou o relógio do computador está atrasado.
- **404 NotAuthorizedOrNotFound**: OCID da fila errado, endpoint de outra região ou usuário sem política de acesso à fila.
- **Arquivo de configuração não encontrado**: crie o `application.properties` a partir do `.example`.
