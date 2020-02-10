package io.softchameleon.caching;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.TriConsumer;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@Controller
public class CustomControllerRegistryService {

    private static AtomicInteger counter;
    private final CacheManager cacheManager;
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    public CustomControllerRegistryService(CacheManager cacheManager, RequestMappingHandlerMapping requestMappingHandlerMapping) {
        counter = new AtomicInteger();

        this.cacheManager = cacheManager;
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
    }

    void mapEndpoint(MappingInfo mappingInfo) throws NoSuchMethodException {
        RequestMappingInfo requestMappingInfo = RequestMappingInfo
                .paths(mappingInfo.pattern)
                .methods(mappingInfo.requestMethod)
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .build();

        EndpointProxy endpointProxy = (pathVariables, requestBody, httpServletRequest) -> handleEndpointRequest(pathVariables, requestBody, httpServletRequest, mappingInfo);

        requestMappingHandlerMapping.registerMapping(
                requestMappingInfo,
                endpointProxy,
                endpointProxy
                        .getClass()
                        .getMethod("onRequest", Map.class, Object.class, HttpServletRequest.class)
        );
    }

    Object handleEndpointRequest(Map<String, String> pathVariables, Object requestBody, HttpServletRequest httpServletRequest, MappingInfo mappingInfo) {
        mappingInfo.consumer.accept(pathVariables, requestBody, mappingInfo);

        if (mappingInfo.cachingInfo != null) {
            ResponsePayload cachedResponsePayload = checkCache(mappingInfo.cachingInfo, httpServletRequest);
            if (cachedResponsePayload != null) {
                return cachedResponsePayload;
            }
        }

        ResponsePayload responsePayload = new ResponsePayload();
        responsePayload.setCounter(counter.incrementAndGet());

        if (mappingInfo.cachingInfo != null) {
            putCache(mappingInfo.cachingInfo, httpServletRequest, responsePayload);
        }

        return responsePayload;
    }

    private ResponsePayload checkCache(CachingInfo cachingInfo, HttpServletRequest httpServletRequest) {
        String cache = Objects.requireNonNull(cachingInfo.cacheName, "Cache name cannot be null");
        String keyName = Objects.requireNonNull(cachingInfo.keyName, "Cache key cannot be null");

        try {
            String key = httpServletRequest.getParameter(keyName);
            return cacheManager.getCache(cache).get(key, ResponsePayload.class);
        } catch (ClassCastException cce) {
            log.error(cce.getMessage(), cce);
            return null;
        }
    }

    private void putCache(CachingInfo cachingInfo, HttpServletRequest httpServletRequest, ResponsePayload responsePayload) {
        String cache = Objects.requireNonNull(cachingInfo.cacheName, "Cache name cannot be null");
        String keyName = Objects.requireNonNull(cachingInfo.keyName, "Cache key cannot be null");

        try {
            String key = httpServletRequest.getParameter(keyName);
            responsePayload.setKey(key);
            cacheManager.getCache(cache).putIfAbsent(key, responsePayload);
        } catch (ClassCastException cce) {
            log.error(cce.getMessage(), cce);
        }

    }

    public void clearMappings() {
        counter.set(0);
        new HashMap<>(requestMappingHandlerMapping.getHandlerMethods()).forEach((info, method) -> {
            requestMappingHandlerMapping.unregisterMapping(info);
        });
    }

    public interface EndpointProxy {
        @ResponseBody
        Object onRequest(@PathVariable Map<String, String> pathVariables, @RequestBody(required=false) Object requestBody, HttpServletRequest httpServletRequest);
    }

    @Data
    public static class MappingInfo {
        String pattern;
        RequestMethod requestMethod;
        CachingInfo cachingInfo;
        TriConsumer<Map<String, String>, Object, MappingInfo> consumer;
    }

    @Data
    public static class CachingInfo {
        String cacheName;
        String keyName;
    }

    @Data
    public static class ResponsePayload {
        int counter;
        String key;
    }
}
