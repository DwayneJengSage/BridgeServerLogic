package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;

public class UrlShortenerServiceTest {

    private static final String WEBSERVICES_URL = BridgeConfigFactory.getConfig().get("webservices.url");
    private static final String LONG_URL = "https://org-sagebridge-usersigned-consents-bridgepf-alxdark.s3.amazonaws.com/copy-test%3A1481654941786%3Abf3bbf23-050c-421d-9859-df0e37009474?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Date=20180501T232536Z&X-Amz-SignedHeaders=host&X-Amz-Expires=7199&X-Amz-Credential=AKIAJLKGQW6FMKXQYUAA%2F20180501%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Signature=1577975258dd8b0b23496f0ce3c30f4d214ad127e2705d3c2e4006ad9288f080";
    private static final String ANOTHER_LONG_URL = "https://org-sagebridge-usersigned-consents-bridgepf-alxdark.s3.amazonaws.com/d8b0b23496f0ce3c30f4d214ad127e2705d3c2e4006ad9288f080";
    private static final int EXPIRES_IN = 30*60; // 30 min
    private static final String TOKEN = "ABC";
    private static final String NEXT_TOKEN = "DEF";
    
    @Mock
    private CacheProvider cacheProvider;
    
    @Spy
    private UrlShortenerService service;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        service.setCacheProvider(cacheProvider);
        when(service.getToken()).thenReturn(TOKEN, NEXT_TOKEN);
    }
    
    @Test
    public void newUrl() {
        // nothing in the cache
        String shortenedUrl = service.shortenUrl(LONG_URL, EXPIRES_IN);
        assertEquals(shortenedUrl, WEBSERVICES_URL+"/r/"+TOKEN);

        verify(cacheProvider).setObject(CacheKey.shortenUrl(TOKEN), LONG_URL, EXPIRES_IN);
    }
    
    @Test
    public void conflictingToken() {
        CacheKey key = CacheKey.shortenUrl(TOKEN);
        when(cacheProvider.getObject(key, String.class)).thenReturn(ANOTHER_LONG_URL);
        
        String shortenedUrl = service.shortenUrl(LONG_URL, EXPIRES_IN);
        assertEquals(shortenedUrl, WEBSERVICES_URL+"/r/"+NEXT_TOKEN);
        
        verify(cacheProvider).setObject(CacheKey.shortenUrl(NEXT_TOKEN), LONG_URL, EXPIRES_IN);
    }
    
    @Test
    public void duplicateURL() {
        CacheKey key = CacheKey.shortenUrl(TOKEN);
        when(cacheProvider.getObject(key, String.class)).thenReturn(LONG_URL);
        
        String shortenedUrl = service.shortenUrl(LONG_URL, EXPIRES_IN);
        assertEquals(shortenedUrl, WEBSERVICES_URL+"/r/"+TOKEN);
        
        verify(cacheProvider, never()).setObject(any(), any(), anyInt());
    }
    
    @Test
    public void retrieveLongUrl() { 
        CacheKey key = CacheKey.shortenUrl(TOKEN);
        when(cacheProvider.getObject(key, String.class)).thenReturn(LONG_URL);
        
        String longUrl = service.retrieveUrl(TOKEN);
        assertEquals(longUrl, LONG_URL);
    }
    
    @Test
    public void retrieveLongUrlNoMatch() {
        assertNull(service.retrieveUrl(TOKEN));
    }
}
