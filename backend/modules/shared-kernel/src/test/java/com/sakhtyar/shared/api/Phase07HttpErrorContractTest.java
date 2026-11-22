package com.sakhtyar.shared.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REAL MockMvc servlet-dispatch integration tests, with a synthetic controller.
 * These do not start PostgreSQL, Spring Boot, external HTTP, or REAL/TEST fixtures.
 */
class Phase07HttpErrorContractTest {

    private MockMvc mvc() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void malformedJsonReturnsStable400WithoutRawParserDetails() throws Exception {
        mvc().perform(post("/phase07/probe/payload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{private: broken json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"))
                .andExpect(jsonPath("$.message").value("Malformed JSON request."))
                .andExpect(jsonPath("$.fieldErrors").isMap());
    }

    @Test
    void invalidParameterTypeReturnsStable400() throws Exception {
        mvc().perform(get("/phase07/probe/number").param("number", "not-an-integer"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void missingRequiredParameterReturnsStable400() throws Exception {
        mvc().perform(get("/phase07/probe/number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void dtoValidationRetainsFieldErrors() throws Exception {
        mvc().perform(post("/phase07/probe/payload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @RestController
    @RequestMapping("/phase07/probe")
    public static class ProbeController {
        @PostMapping("/payload")
        public Map<String, String> payload(@Valid @RequestBody Payload payload) {
            return Map.of("title", payload.title());
        }

        @GetMapping("/number")
        public Map<String, Integer> number(@RequestParam int number) {
            return Map.of("number", number);
        }
    }

    public record Payload(@NotBlank String title) { }
}
