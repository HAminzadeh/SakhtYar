package com.sakhtyar.crawler.infrastructure;

import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Proves redirect rejection using an injected fake HTTP client, without any network traffic. */
class PageFetcherPhase04FakeTransportTest {
    @Test @SuppressWarnings("unchecked")
    void fakeRedirectToMetadataOrLoopbackIsRejectedBeforeSecondRequest() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<java.io.InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(302);
        when(response.headers()).thenReturn(HttpHeaders.of(
            Map.of("location", List.of("http://169.254.169.254/latest/meta-data")), (k,v) -> true));
        when(response.body()).thenReturn(new ByteArrayInputStream(new byte[0]));
        doReturn(response).when(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        PageFetcher fetcher = new PageFetcher(client);
        assertThrows(SecurityException.class, () -> fetcher.fetch("http://8.8.8.8/start", 1000, "SakhtYar-Test"));
        verify(client, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }
}
