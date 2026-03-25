package com.phonepe.devx.arnold.client;


import com.phonepe.platform.http.v2.executor.HttpGetExecutor;
import com.phonepe.platform.http.v2.executor.factory.HttpExecutorBuilderFactory;
import com.phonepe.devx.arnold.client.impl.ArnoldHttpClient;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArnoldHttpClientTest {

    @Mock
    private HttpExecutorBuilderFactory executorBuilderFactory;

    private ArnoldHttpClient httpClient;


    @BeforeEach
    void init() {
        this.httpClient = new ArnoldHttpClient(executorBuilderFactory);
    }

    @Test
    void testPing() {
        final ArnoldResponse response = Mockito.mock(ArnoldResponse.class);
        HttpGetExecutor.HttpGetExecutorBuilder<String> postExecutorBuilder =
                Mockito.mock(
                        HttpGetExecutor.HttpGetExecutorBuilder.class,
                        Mockito.RETURNS_SELF);
        HttpGetExecutor<String> httpGetExecutor = Mockito.mock(HttpGetExecutor.class, Mockito.RETURNS_SELF);
        Mockito.doReturn(postExecutorBuilder)
                .when(executorBuilderFactory)
                .httpGetExecutorBuilder();
        Mockito.doReturn(httpGetExecutor).when(postExecutorBuilder).build();
        Mockito.doReturn(response).when(httpGetExecutor).executeTracked();

        Assertions.assertEquals(response,
                                this.httpClient.ping("key", "value"));
    }

}
