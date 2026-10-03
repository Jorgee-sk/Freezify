package com.freezify.realtime.web;

import com.freezify.common.CurrentUser;
import com.freezify.households.HouseholdAccess;
import com.freezify.realtime.internal.HouseholdEventStreams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Tag(name = "Realtime")
class HouseholdEventsController {

    private final HouseholdAccess access;
    private final HouseholdEventStreams streams;

    HouseholdEventsController(HouseholdAccess access, HouseholdEventStreams streams) {
        this.access = access;
        this.streams = streams;
    }

    @Operation(
            summary = "Server-sent events of a household",
            description = "Sends 'inventory-changed' whenever a member changes the inventory. Events carry no data;"
                    + " fetch the inventory again when one arrives. The token goes in the Authorization header, so"
                    + " browsers need fetch() rather than EventSource.")
    @GetMapping(path = "/api/v1/households/{householdId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID householdId, HttpServletResponse response) {
        UUID userId = CurrentUser.id(jwt);
        access.requireMember(householdId, userId);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        // Tells nginx to pass events through as they are written instead of buffering them.
        response.setHeader("X-Accel-Buffering", "no");
        return streams.open(householdId, userId, jwt.getExpiresAt());
    }
}
