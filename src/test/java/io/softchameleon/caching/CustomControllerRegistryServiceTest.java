package io.softchameleon.caching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CustomControllerRegistryServiceTest {
    @Autowired
    CustomControllerRegistryService customControllerRegistryService;

    @Autowired
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        customControllerRegistryService.clearMappings();
    }

    @Test
    void testRegisteringNewEndpoint() throws Exception {
        CustomControllerRegistryService.MappingInfo mappingInfo = new CustomControllerRegistryService.MappingInfo();
        mappingInfo.pattern = "/api";
        mappingInfo.requestMethod = RequestMethod.GET;
        mappingInfo.consumer = (pathVariables, requestBody, info) -> {
//            irrelevant
        };

        customControllerRegistryService.mapEndpoint(mappingInfo);

        mockMvc.perform(get("/api").requestAttr("A", 1)).andExpect(status().isOk()).andExpect(content().json("{\"counter\":1,\"key\":null}"));
        mockMvc.perform(get("/api").requestAttr("A", 1)).andExpect(status().isOk()).andExpect(content().json("{\"counter\":2,\"key\":null}"));;
        mockMvc.perform(get("/api").requestAttr("A", 1)).andExpect(status().isOk()).andExpect(content().json("{\"counter\":3,\"key\":null}"));;
    }

    @Test
    void testCaching() throws Exception {
        CustomControllerRegistryService.MappingInfo mappingInfo = new CustomControllerRegistryService.MappingInfo();
        mappingInfo.pattern = "/api";
        mappingInfo.requestMethod = RequestMethod.GET;
        mappingInfo.consumer = (pathVariables, requestBody, info) -> {
//            irrelevant
        };

        mappingInfo.cachingInfo = new CustomControllerRegistryService.CachingInfo();
        mappingInfo.cachingInfo.cacheName = "test";
        mappingInfo.cachingInfo.keyName = "param1";

        customControllerRegistryService.mapEndpoint(mappingInfo);

        mockMvc.perform(get("/api?param1=test")).andExpect(status().isOk()).andExpect(content().json("{\"counter\":1,\"key\":\"test\"}"));
        mockMvc.perform(get("/api?param1=test")).andExpect(status().isOk()).andExpect(content().json("{\"counter\":1,\"key\":\"test\"}"));
        mockMvc.perform(get("/api?param1=test")).andExpect(status().isOk()).andExpect(content().json("{\"counter\":1,\"key\":\"test\"}"));
        mockMvc.perform(get("/api?param1=test")).andExpect(status().isOk()).andExpect(content().json("{\"counter\":1,\"key\":\"test\"}"));
    }
}