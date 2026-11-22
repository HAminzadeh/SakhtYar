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

class Phase04PageFetcherMimeTest {
    @Test @SuppressWarnings("unchecked")
    void rejectsUnexpectedMimeWithoutRealNetworkRequest() throws Exception {
        HttpClient client=mock(HttpClient.class);
        HttpResponse<java.io.InputStream> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new ByteArrayInputStream("not a page".getBytes()));
        when(response.headers()).thenReturn(HttpHeaders.of(
             Map.of("content-type", List.of("application/x-msdownload")), (k,v)->true));
        doReturn(response).when(client).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        PageFetcher fetcher=new PageFetcher(client);
        assertThrows(SecurityException.class,()->fetcher.fetch("https://8.8.8.8/a",1000,"SakhtYarBot"));
        verify(client,times(1)).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }
}
