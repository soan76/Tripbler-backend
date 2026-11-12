package com.tripbler.backend.place.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
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
import java.util.List;
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
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;
import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.common.exception.PlacesProviderException;
import com.tripbler.backend.place.dto.GoogleNearbySearchResponse;
import com.tripbler.backend.place.dto.PlaceResponse;

class GooglePlacesClientTest {

    private static final String BASE_URL =
        "https://places.googleapis.com";

    private static final String NEARBY_URL =
        BASE_URL + "/v1/places:searchNearby";

    private static final String SEARCH_TEXT_URL =
        BASE_URL + "/v1/places:searchText";

    // 검색은 Pro SKU 필드만 요청해야 한다(rating, currentOpeningHours 제외).
    private static final String EXPECTED_SEARCH_FIELD_MASK =
        "places.id,places.displayName,places.location,"
            + "places.formattedAddress,places.primaryType";

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
    void 최근접_후보가_빈_배열이면_null을_반환한다() {
        mockServer.expect(requestTo(NEARBY_URL))
            .andRespond(
                withSuccess("{\"places\":[]}", MediaType.APPLICATION_JSON)
            );

        assertNull(
            client.findNearestPlace(37.5665, 126.9780, 40)
        );

        mockServer.verify();
    }

    @Test
    void 최근접_응답_본문이_없으면_503_예외를_던진다() {
        // 본문 누락은 빈 결과(204)가 아니라 제공자 장애다.
        mockServer.expect(requestTo(NEARBY_URL))
            .andRespond(withSuccess());

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
    void 최근접은_좌표가_없는_후보를_제외한다() {
        // 위치 없는 후보가 (0, 0)으로 계산되거나 선택되면 안 된다.
        String body = """
            {"places":[
              {"id":"no-location","displayName":{"text":"위치 없음"}},
              {"id":"far","displayName":{"text":"먼 곳"},
               "location":{"latitude":37.5700,"longitude":126.9800}},
              {"id":"near","displayName":{"text":"가까운 곳"},
               "location":{"latitude":37.5666,"longitude":126.9781}}]}
            """;

        mockServer.expect(requestTo(NEARBY_URL))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertEquals(
            "near",
            client.findNearestPlace(37.5665, 126.9780, 40).id()
        );

        mockServer.verify();
    }

    @Test
    void 빈_location_객체나_한쪽_좌표만_있으면_좌표를_null로_둔다() {
        // 원시 double로 받으면 빈 location이 (0, 0)이 되어 대서양 한가운데 마커가 생긴다.
        mockServer.expect(requestTo(SEARCH_TEXT_URL))
            .andRespond(withSuccess(
                """
                {"places":[
                  {"id":"empty","displayName":{"text":"빈 위치"},"location":{}},
                  {"id":"half","displayName":{"text":"반쪽 위치"},"location":{"latitude":37.5}}]}
                """,
                MediaType.APPLICATION_JSON
            ));

        var places = client.searchText("경복궁", null, null);

        assertEquals(2, places.size());
        for (var place : places) {
            assertNull(place.latitude());
            assertNull(place.longitude());
        }

        mockServer.verify();
    }

    @Test
    void 최근접은_빈_location_객체_후보를_제외한다() {
        mockServer.expect(requestTo(NEARBY_URL))
            .andRespond(withSuccess(
                """
                {"places":[
                  {"id":"empty","displayName":{"text":"빈 위치"},"location":{}},
                  {"id":"far","displayName":{"text":"먼 곳"},
                   "location":{"latitude":37.5700,"longitude":126.9800}}]}
                """,
                MediaType.APPLICATION_JSON
            ));

        assertEquals("far", client.findNearestPlace(37.5665, 126.9780, 1000).id());

        mockServer.verify();
    }

    @Test
    void 최근접_후보가_모두_좌표가_없으면_null을_반환한다() {
        String body = """
            {"places":[{"id":"no-location","displayName":{"text":"위치 없음"}}]}
            """;

        mockServer.expect(requestTo(NEARBY_URL))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertNull(
            client.findNearestPlace(37.5665, 126.9780, 40)
        );

        mockServer.verify();
    }

    @Test
    void 주변_검색_응답_본문이_없으면_503_예외를_던진다() {
        mockServer.expect(requestTo(NEARBY_URL))
            .andRespond(withSuccess());

        PlacesProviderException exception = assertThrows(
            PlacesProviderException.class,
            () -> client.searchNearby(
                37.5665, 126.9780, "tourist_attraction", 1000
            )
        );

        assertEquals(
            ErrorCode.PLACES_PROVIDER_UNAVAILABLE,
            exception.getErrorCode()
        );

        mockServer.verify();
    }

    @Test
    void 상세_조회는_한국어_결과를_요청한다() {
        // 검색과 같은 언어로 받아야 이름·주소가 영어로 바뀌지 않는다.
        mockServer.expect(requestTo(BASE_URL + "/v1/places/some-id?languageCode=ko"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                """
                {"id":"some-id","displayName":{"text":"경복궁","languageCode":"ko"}}
                """,
                MediaType.APPLICATION_JSON
            ));

        assertEquals("경복궁", client.getPlaceDetails("some-id").name());

        mockServer.verify();
    }

    @Test
    void 주변_검색은_한국어_결과를_요청한다() {
        mockServer.expect(requestTo(NEARBY_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.languageCode").value("ko"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.searchNearby(37.5665, 126.9780, "tourist_attraction", 1000);

        mockServer.verify();
    }

    @Test
    void 최근접_조회도_한국어_결과를_요청한다() {
        mockServer.expect(requestTo(NEARBY_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.languageCode").value("ko"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.findNearestPlace(37.5665, 126.9780, 40);

        mockServer.verify();
    }

    @Test
    void 주변_검색_결과가_없으면_빈_목록을_반환한다() {
        mockServer.expect(requestTo(NEARBY_URL))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertTrue(
            client.searchNearby(
                37.5665, 126.9780, "tourist_attraction", 1000
            ).isEmpty()
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
        mockServer.expect(requestTo(BASE_URL + "/v1/places/unknown-id?languageCode=ko"))
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
        mockServer.expect(requestTo(BASE_URL + "/v1/places/bad-id?languageCode=ko"))
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
        mockServer.expect(requestTo(BASE_URL + "/v1/places/some-id?languageCode=ko"))
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
        mockServer.expect(requestTo(BASE_URL + "/v1/places/some-id?languageCode=ko"))
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

        mockServer.expect(requestTo(BASE_URL + "/v1/places/bad-id?languageCode=ko"))
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
        mockServer.expect(requestTo(BASE_URL + "/v1/places/bad-id?languageCode=ko"))
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

    @Test
    void 좌표가_있는_검색은_locationBias를_담아_요청한다() {
        mockServer.expect(requestTo(SEARCH_TEXT_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Goog-Api-Key", "test-api-key"))
            .andExpect(header("X-Goog-FieldMask", EXPECTED_SEARCH_FIELD_MASK))
            .andExpect(
                content().json(
                    """
                    {"textQuery":"경복궁",
                     "languageCode":"ko",
                     "pageSize":20,
                     "locationBias":{"circle":{
                       "center":{"latitude":37.5665,"longitude":126.978},
                       "radius":5000.0}}}
                    """,
                    JsonCompareMode.STRICT
                )
            )
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.searchText("경복궁", 37.5665, 126.978);

        mockServer.verify();
    }

    @Test
    void 좌표가_없는_검색은_locationBias_필드를_보내지_않는다() {
        // STRICT 비교는 "locationBias": null이 섞여도 실패한다.
        mockServer.expect(requestTo(SEARCH_TEXT_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Goog-Api-Key", "test-api-key"))
            .andExpect(header("X-Goog-FieldMask", EXPECTED_SEARCH_FIELD_MASK))
            .andExpect(
                content().json(
                    """
                    {"textQuery":"경복궁","languageCode":"ko","pageSize":20}
                    """,
                    JsonCompareMode.STRICT
                )
            )
            .andExpect(jsonPath("$.locationBias").doesNotExist())
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.searchText("경복궁", null, null);

        mockServer.verify();
    }

    @Test
    void 검색_응답을_변환하고_평점과_영업_여부는_null이다() {
        String body = """
            {"places":[{"id":"place-1",
              "displayName":{"text":"경복궁","languageCode":"ko"},
              "location":{"latitude":37.5796,"longitude":126.977},
              "formattedAddress":"서울특별시 종로구 사직로 161",
              "primaryType":"tourist_attraction"}]}
            """;

        mockServer.expect(requestTo(SEARCH_TEXT_URL))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<PlaceResponse> places =
            client.searchText("경복궁", null, null);

        assertEquals(
            List.of(
                new PlaceResponse(
                    "place-1",
                    "경복궁",
                    37.5796,
                    126.977,
                    "서울특별시 종로구 사직로 161",
                    null,
                    "tourist_attraction",
                    null
                )
            ),
            places
        );

        mockServer.verify();
    }

    @Test
    void 검색_결과가_없으면_빈_목록을_반환한다() {
        mockServer.expect(requestTo(SEARCH_TEXT_URL))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertTrue(client.searchText("없는장소", null, null).isEmpty());

        mockServer.verify();
    }

    @Test
    void 검색_결과가_빈_배열이면_빈_목록을_반환한다() {
        mockServer.expect(requestTo(SEARCH_TEXT_URL))
            .andRespond(
                withSuccess("{\"places\":[]}", MediaType.APPLICATION_JSON)
            );

        assertTrue(client.searchText("없는장소", null, null).isEmpty());

        mockServer.verify();
    }

    @Test
    void 검색_응답_본문이_없으면_503_예외를_던진다() {
        // 본문과 Content-Type이 없는 200. RestClient는 이 경우 null을 돌려준다.
        mockServer.expect(requestTo(SEARCH_TEXT_URL))
            .andRespond(withSuccess());

        assertSearchFailsWithProviderError();
    }

    @Test
    void 검색_응답_본문이_JSON_형식의_빈_본문이어도_503_예외를_던진다() {
        mockServer.expect(requestTo(SEARCH_TEXT_URL))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertSearchFailsWithProviderError();
    }

    @Test
    void RestClient는_본문_없는_200_응답을_null로_돌려준다() {
        // 본문 누락 판정(response == null)이 실제로 동작하는 전제를 확인한다.
        RestClient.Builder builder =
            RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(SEARCH_TEXT_URL))
            .andRespond(withSuccess());

        assertNull(
            builder.build()
                .post()
                .uri("/v1/places:searchText")
                .retrieve()
                .body(GoogleNearbySearchResponse.class)
        );

        server.verify();
    }

    @Test
    void 위치가_없는_장소는_좌표를_null로_변환한다() {
        String body = """
            {"places":[{"id":"place-1",
              "displayName":{"text":"경복궁","languageCode":"ko"},
              "formattedAddress":"서울특별시 종로구 사직로 161",
              "primaryType":"tourist_attraction"}]}
            """;

        mockServer.expect(requestTo(SEARCH_TEXT_URL))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        PlaceResponse place =
            client.searchText("경복궁", null, null).get(0);

        assertEquals("place-1", place.id());
        assertNull(place.latitude());
        assertNull(place.longitude());

        mockServer.verify();
    }

    @Test
    void 검색에서_Google_5xx면_503_예외를_던진다() {
        mockServer.expect(requestTo(SEARCH_TEXT_URL))
            .andRespond(withServerError());

        assertSearchFailsWithProviderError();
    }

    @Test
    void 검색에서_Google_403이면_503_예외를_던진다() {
        mockServer.expect(requestTo(SEARCH_TEXT_URL))
            .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertSearchFailsWithProviderError();
    }

    @Test
    void 검색에서_Google_400_키_오류면_503_예외를_던진다() {
        mockServer.expect(requestTo(SEARCH_TEXT_URL))
            .andRespond(
                withStatus(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(API_KEY_INVALID_BODY)
            );

        assertSearchFailsWithProviderError();
    }

    @Test
    void 검색이_시간_초과되면_503_예외를_던진다() {
        mockServer.expect(requestTo(SEARCH_TEXT_URL))
            .andRespond(
                withException(
                    new SocketTimeoutException("Read timed out")
                )
            );

        assertSearchFailsWithProviderError();
    }

    private void assertSearchFailsWithProviderError() {
        PlacesProviderException exception = assertThrows(
            PlacesProviderException.class,
            () -> client.searchText("경복궁", 37.5665, 126.978)
        );

        assertEquals(
            ErrorCode.PLACES_PROVIDER_UNAVAILABLE,
            exception.getErrorCode()
        );

        mockServer.verify();
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
