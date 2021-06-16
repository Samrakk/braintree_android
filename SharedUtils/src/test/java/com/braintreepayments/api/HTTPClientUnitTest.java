package com.braintreepayments.api;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class HTTPClientUnitTest {

    private SynchronousHTTPClient syncHTTPClient;

    private HTTPRequest httpRequest;
    private MockThreadScheduler threadScheduler;

    @Before
    public void beforeEach() {
        syncHTTPClient = mock(SynchronousHTTPClient.class);
        threadScheduler = spy(new MockThreadScheduler());

        httpRequest = new HTTPRequest().path("https://example.com");
    }

    @Test
    public void sendRequest_sendsRequestOnBackgroundThread() throws Exception {
        HTTPClient sut = new HTTPClient(syncHTTPClient, threadScheduler);

        HTTPResponseCallback callback = mock(HTTPResponseCallback.class);
        sut.sendRequest(httpRequest, callback);

        verifyZeroInteractions(syncHTTPClient);
        threadScheduler.flushBackgroundThread();

        verify(syncHTTPClient).request(httpRequest);
    }

    @Test
    public void sendRequest_whenBaseHTTPClientThrowsException_notifiesErrorViaCallbackOnMainThread() throws Exception {
        HTTPClient sut = new HTTPClient(syncHTTPClient, threadScheduler);

        Exception exception = new Exception("error");
        when(syncHTTPClient.request(httpRequest)).thenThrow(exception);

        HTTPResponseCallback callback = mock(HTTPResponseCallback.class);
        sut.sendRequest(httpRequest, callback);

        threadScheduler.flushBackgroundThread();
        verify(callback, never()).onResult(null, exception);

        threadScheduler.flushMainThread();
        verify(callback).onResult(null, exception);
    }

    @Test
    public void sendRequest_onBaseHTTPClientRequestSuccess_notifiesSuccessViaCallbackOnMainThread() throws Exception {
        HTTPClient sut = new HTTPClient(syncHTTPClient, threadScheduler);

        when(syncHTTPClient.request(httpRequest)).thenReturn("response body");

        HTTPResponseCallback callback = mock(HTTPResponseCallback.class);
        sut.sendRequest(httpRequest, callback);

        threadScheduler.flushBackgroundThread();
        verify(callback, never()).onResult("response body", null);

        threadScheduler.flushMainThread();
        verify(callback).onResult("response body", null);
    }

    @Test
    public void sendRequest_whenCallbackIsNull_doesNotNotifySuccess() throws Exception {
        HTTPClient sut = new HTTPClient(syncHTTPClient, threadScheduler);

        when(syncHTTPClient.request(httpRequest)).thenReturn("response body");
        sut.sendRequest(httpRequest, null);

        threadScheduler.flushBackgroundThread();
        verify(threadScheduler, never()).runOnMain(any(Runnable.class));
    }

    @Test
    public void sendRequest_whenCallbackIsNull_doesNotNotifyError() throws Exception {
        HTTPClient sut = new HTTPClient(syncHTTPClient, threadScheduler);

        Exception exception = new Exception("error");
        when(syncHTTPClient.request(httpRequest)).thenThrow(exception);

        sut.sendRequest(httpRequest, null);

        threadScheduler.flushBackgroundThread();
        verify(threadScheduler, never()).runOnMain(any(Runnable.class));
    }

    @Test
    public void sendRequest_whenRetryMax3TimesEnabled_retriesRequest3Times() throws Exception {
        HTTPClient sut = new HTTPClient(syncHTTPClient, threadScheduler);

        Exception exception = new Exception("error");
        when(syncHTTPClient.request(httpRequest)).thenThrow(exception);

        HTTPResponseCallback callback = mock(HTTPResponseCallback.class);
        sut.sendRequest(httpRequest, HTTPClient.RETRY_MAX_3_TIMES, callback);

        threadScheduler.flushBackgroundThread();
        verify(syncHTTPClient, times(3)).request(httpRequest);
    }

    @Test
    public void sendRequest_whenRetryMax3TimesEnabled_notifiesMaxRetriesLimitExceededOnForegroundThread() throws Exception {
        HTTPClient sut = new HTTPClient(syncHTTPClient, threadScheduler);

        Exception exception = new Exception("error");
        when(syncHTTPClient.request(httpRequest)).thenThrow(exception);

        HTTPResponseCallback callback = mock(HTTPResponseCallback.class);
        sut.sendRequest(httpRequest, HTTPClient.RETRY_MAX_3_TIMES, callback);

        threadScheduler.flushBackgroundThread();
        verify(callback, never()).onResult((String) isNull(), any(Exception.class));

        threadScheduler.flushMainThread();

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(callback).onResult((String) isNull(), captor.capture());

        HttpClientException httpClientException = (HttpClientException) captor.getValue();
        String expectedMessage = "Retry limit has been exceeded. Try again later.";
        assertEquals(expectedMessage, httpClientException.getMessage());
    }

    @Test
    public void sendRequest_whenRetryMax3TimesEnabled_futureRequestsAreAllowed() throws Exception {
        HTTPClient sut = new HTTPClient(syncHTTPClient, threadScheduler);

        Exception exception = new Exception("error");
        when(syncHTTPClient.request(httpRequest)).thenThrow(exception);

        HTTPResponseCallback callback = mock(HTTPResponseCallback.class);
        sut.sendRequest(httpRequest, HTTPClient.RETRY_MAX_3_TIMES, callback);

        threadScheduler.flushBackgroundThread();

        reset(syncHTTPClient);
        when(syncHTTPClient.request(httpRequest))
                .thenThrow(exception)
                .thenReturn("response body");
        sut.sendRequest(httpRequest, HTTPClient.RETRY_MAX_3_TIMES, callback);

        threadScheduler.flushBackgroundThread();
        threadScheduler.flushMainThread();

        verify(callback).onResult("response body", null);
    }

    @Test
    public void sendRequestSynchronous_sendsHTTPRequest() throws Exception {
        HTTPClient sut = new HTTPClient(syncHTTPClient, threadScheduler);

        when(syncHTTPClient.request(httpRequest)).thenReturn("response body");

        String result = sut.sendRequest(httpRequest);
        assertEquals("response body", result);
    }
}
