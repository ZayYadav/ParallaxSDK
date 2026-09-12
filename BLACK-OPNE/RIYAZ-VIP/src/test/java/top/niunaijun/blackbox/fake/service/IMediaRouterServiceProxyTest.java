package top.niunaijun.blackbox.fake.service;

import org.junit.Test;

import java.util.Arrays;

import top.niunaijun.blackbox.fake.hook.ProxyMethods;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class IMediaRouterServiceProxyTest {
    @Test
    public void hooksAndroid16MediaRouter2ReadMethods() {
        ProxyMethods annotation = IMediaRouterServiceProxy.mediaRouter2ReadCalls.class
                .getAnnotation(ProxyMethods.class);

        assertNotNull(annotation);
        assertTrue(Arrays.asList(annotation.value()).contains("getSystemRoutes"));
        assertTrue(Arrays.asList(annotation.value()).contains("getSystemSessionInfo"));
        assertTrue(Arrays.asList(annotation.value()).contains("getRemoteSessions"));
    }
}
