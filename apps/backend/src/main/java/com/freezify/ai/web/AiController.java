package com.freezify.ai.web;

import com.freezify.ai.AiService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "AI")
class AiController {

    private final AiService ai;

    AiController(AiService ai) {
        this.ai = ai;
    }

    /** Whether this installation has a language model, so that clients only offer what can work. */
    @GetMapping("/api/v1/ai")
    AiStatus status() {
        return new AiStatus(ai.enabled());
    }

    record AiStatus(boolean enabled) {}
}
