import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
public class CrptApi implements Runnable {
    private final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private ObjectMapper objectMapper = getObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Semaphore requestSemaphore;
    private final long requestIntervalMillis;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestSemaphore = new Semaphore(requestLimit, true);
        this.requestIntervalMillis = timeUnit.toMillis(1) / requestLimit;
    }

    @Override
    public void run() {
        createDocument(new Document(), "exampleSignature");
    }

    public void createDocument(Document document, String signature) {
        try {
            requestSemaphore.acquire();

            String jsonRequestBody = serializeToJson(new RequestBody(document, signature));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonRequestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.printf("Статус код ответа: %s \n Ответ: %s%n", response.statusCode(), response.body());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            new Thread(() -> {
                try {
                    Thread.sleep(requestIntervalMillis);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    requestSemaphore.release();
                }
            }).start();
        }
    }

    private String serializeToJson(Object object) throws Exception {
        return objectMapper.writeValueAsString(object);
    }

    private ObjectMapper getObjectMapper() {
        objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        return objectMapper;
    }

    private static class RequestBody {
        private final Document description;
        private final String signature;

        public RequestBody(Document description, String signature) {
            this.description = description;
            this.signature = signature;
        }
    }

    private static class Description {
        private String participantInn;
    }

    private static class Product {
        private String certificateDocument;
        private String certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;
    }

    private static class Document {
        private Description description;
        private String docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String participantInn;
        private String producerInn;
        private String productionDate;
        private String productionType;
        private ArrayList<Product> products;
        private String regDate;
        private String regNumber;
    }

    public static void main(String[] args) {
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 5);
        for (int i = 0; i < 20; i++) {
            Thread thread = new Thread(crptApi);
            thread.start();
        }
    }
}
