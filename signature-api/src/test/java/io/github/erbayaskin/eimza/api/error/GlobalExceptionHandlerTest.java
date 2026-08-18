package io.github.erbayaskin.eimza.api.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GlobalExceptionHandlerTest {

    @Test
    void returnsStableProblemDetailsForInvalidUuidInJsonBody() throws Exception {
        var mockMvc = MockMvcBuilders.standaloneSetup(new BodyController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"UNCONFIGURED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_BODY_INVALID"))
                .andExpect(jsonPath("$.detail").value(
                        "JSON alan türleri API sözleşmesiyle eşleşmiyor; özellikle UUID ve enum alanlarını kontrol edin."));
    }

    @RestController
    static class BodyController {
        @PostMapping("/body")
        void body(@RequestBody Body request) {}
    }

    record Body(UUID deviceId) {}
}
