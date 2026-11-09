package com.tripbler.backend.place.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;
import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.common.exception.PlacesProviderException;

class GooglePlacesClientTest {

    private static final String BASE_URL =
        "https://places.googleapis.com";

    private static final String NEARBY_URL =
        BASE_URL + "/v1/places:searchNearby";

    private static final String API_KEY_INVALID_BODY =
        """
        {"error":{"code":400,
          "message":"API key not valid. Please pass a valid API key.",
          "status":"INVALID_ARGUMENT",
          "details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo",
            "reason":"API_KEY_INVALID","domain":"googleapis.com"}]}}
        """;

    private MockRestServiceServer mockServer;
    private GooglePlacesClient client;

    // 타임아웃 테스트에서만 쓰는 로컬 HTTP 서버
    private HttpServer slowServer;
    private ExecutorService slowServerExecutor;
    private final CountDownLatch releaseSlowResponse =
        new CountDownLatch(1);

    @BeforeEach
    void setUp() {
        RestClient.Builder builder =
            RestClient.builder()
                .baseUrl(BASE_URL);

        mockServer =
            MockRestServiceServer.bindTo(builder)
                .build();

        client =
            new GooglePlacesClient(
                builder.build(),
                "test-api-key"
            );
    }

    @Test
    void 최근접_장소가_없으면_null을_반환한다() {
        // Google은 결과가 없으면 places 필드를 생략한 빈 객체를 준다.
        mockServer.expect(requestTo(NEARBY_URL))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess("{}", MediaType.APPLICATION_JSON)
            );

        assertNull(
            client.findNearestPlace(37.5665, 126.9780, 40)
        );

        mockServer.verify();
    }

    @Test
    void 최근접_조회가_시간_초과되면_503_예외를_던진다() {
        // 시간 초과는 빈 결과(204)로 바뀌지 않고 제공자 장애가 되어야 한다.
        mockServer.expect(requestTo(NEARBY_URL))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withException(
                    new SocketTimeoutException("Read timed out")
                )
            );

        PlacesProviderException exception = assertThrows(
            PlacesProviderException.class,
            () -> client.findNearestPlace(37.5665, 126.9780, 40)
        );

        assertEquals(
            ErrorCode.PLACES_PROVIDER_UNAVAILABLE,
            exception.getErrorCode()
        );

        mockServer.verify();
    }

    @Test
    void 주변_검색에서_Google_5xx면_503_예외를_던진다() {
        mockServer.expect(requestTo(NEARBY_URL))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withServerError());

        assertThrows(
            PlacesProviderException.class,
            () -> client.searchNearby(
                37.5665,
                126.9780,
                "tourist_attraction",
                1500
            )
        );

        mockServer.verify();
    }

    @Test
    void 주변_검색에서_Google_키_오류면_503_예외를_던진다() {
        // 키 오류는 사용자 잘못이 아닌 서버 설정 문제다.
        mockServer.expect(requestTo(NEARBY_URL))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThrows(
            PlacesProviderException.class,
            () -> client.searchNearby(
                37.5665,
                126.9780,
                "tourist_attraction",
                1500
            )
        );

        mockServer.verify();
    }

    @Test
    void 상세_조회에서_Google_404면_404_예외를_던진다() {
        mockServer.expect(requestTo(BASE_URL + "/v1/places/unknown-id"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withResourceNotFound());

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> client.getPlaceDetails("unknown-id")
        );

        assertEquals(
            ErrorCode.RESOURCE_NOT_FOUND,
            exception.getErrorCode()
        );

        mockServer.verify();
    }

    @Test
    void 상세_조회에서_Google_400이면_400_예외를_던진다() {
        mockServer.expect(requestTo(BASE_URL + "/v1/places/bad-id"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> client.getPlaceDetails("bad-id")
        );

        assertEquals(
            ErrorCode.INVALID_REQUEST,
            exception.getErrorCode()
        );

        mockServer.verify();
    }

    @Test
    void 상세_조회가_시간_초과되면_503_예외를_던진다() {
        mockServer.expect(requestTo(BASE_URL + "/v1/places/some-id"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withException(
                    new SocketTimeoutException("Read timed out")
                )
            );

        assertThrows(
            PlacesProviderException.class,
            () -> client.getPlaceDetails("some-id")
        );

        mockServer.verify();
    }

    @Test
    void 상세_조회에서_Google_400이_API_키_오류면_503_예외를_던진다() {
        // Google은 잘못된 키도 400(INVALID_ARGUMENT)으로 응답한다.
        // 사용자 입력 오류가 아닌 서버 설정 문제이므로 503이어야 한다.
        mockServer.expect(requestTo(BASE_URL + "/v1/places/some-id"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withStatus(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(API_KEY_INVALID_BODY)
            );

        PlacesProviderException exception = assertThrows(
            PlacesProviderException.class,
            () -> client.getPlaceDetails("some-id")
        );

        assertEquals(
            ErrorCode.PLACES_PROVIDER_UNAVAILABLE,
            exception.getErrorCode()
        );

        mockServer.verify();
    }

    @Test
    void 상세_조회에서_Google_400이_키_오류가_아니면_400_예외를_던진다() {
        String body = """
            {"error":{"code":400,
              "message":"Not a valid Place ID: bad-id",
              "status":"INVALID_ARGUMENT",
              "details":[{"@type":"type.googleapis.com/google.rpc.BadRequest",
                "fieldViolations":[{"field":"name"}]}]}}
            """;

        mockServer.expect(requestTo(BASE_URL + "/v1/places/bad-id"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withStatus(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
            );

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> client.getPlaceDetails("bad-id")
        );

        assertEquals(
            ErrorCode.INVALID_REQUEST,
            exception.getErrorCode()
        );

        mockServer.verify();
    }

    @Test
    void 상세_조회에서_Google_400_본문이_JSON이_아니면_400_예외를_던진다() {
        // 본문 해석 실패가 다른 예외로 새지 않고 일반 400으로 처리되어야 한다.
        mockServer.expect(requestTo(BASE_URL + "/v1/places/bad-id"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withStatus(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("{not json")
            );

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> client.getPlaceDetails("bad-id")
        );

        assertEquals(
            ErrorCode.INVALID_REQUEST,
            exception.getErrorCode()
        );

        mockServer.verify();
    }

    @Test
    void 운영_타임아웃은_연결_2초_읽기_5초다() {
        // 앱의 장소 요청 제한 시간(8초)보다 먼저 끝나야 한다.
        assertEquals(Duration.ofSeconds(2), GooglePlacesClient.CONNECT_TIMEOUT);
        assertEquals(Duration.ofSeconds(5), GooglePlacesClient.READ_TIMEOUT);
    }

    @Test
    void 응답이_읽기_타임아웃보다_늦으면_503_예외를_던진다() throws IOException {
        // 실제 소켓으로 응답을 지연시켜 requestFactory의 타임아웃이 걸리는지 확인한다.
        // 운영 생성자와 같은 생성 경로를 쓰고 타임아웃 값만 짧게 준다.
        slowServerExecutor = Executors.newSingleThreadExecutor();
        slowServer = HttpServer.create(
            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
            0
        );
        slowServer.setExecutor(slowServerExecutor);
        slowServer.createContext("/", exchange -> {
            try {
                // 타임아웃(300ms)보다 길게 기다린 뒤 정상 응답한다.
                releaseSlowResponse.await(2, TimeUnit.SECONDS);
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders()
                    .add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(body);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // 클라이언트가 먼저 연결을 끊으면 쓰기가 실패할 수 있다.
            } finally {
                exchange.close();
            }
        });
        slowServer.start();

        String baseUrl =
            "http://127.0.0.1:" + slowServer.getAddress().getPort();

        GooglePlacesClient timeoutClient =
            new GooglePlacesClient(
                RestClient.builder(),
                baseUrl,
                "test-api-key",
                Duration.ofMillis(300),
                Duration.ofMillis(300)
            );

        PlacesProviderException exception = assertThrows(
            PlacesProviderException.class,
            () -> timeoutClient.searchNearby(
                37.5665,
                126.9780,
                "tourist_attraction",
                1500
            )
        );

        assertEquals(
            ErrorCode.PLACES_PROVIDER_UNAVAILABLE,
            exception.getErrorCode()
        );
    }

    @AfterEach
    void stopSlowServer() {
        releaseSlowResponse.countDown();

        if (slowServer != null) {
            slowServer.stop(0);
        }

        if (slowServerExecutor != null) {
            slowServerExecutor.shutdownNow();
        }
    }
}
