package com.freezify.households.web;

import com.freezify.common.CurrentUser;
import com.freezify.households.internal.HouseholdService;
import com.freezify.households.internal.HouseholdService.HouseholdView;
import com.freezify.households.internal.HouseholdService.InvitationView;
import com.freezify.households.internal.HouseholdService.MemberView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/households")
@Tag(name = "Households")
class HouseholdController {

    private final HouseholdService householdService;

    HouseholdController(HouseholdService householdService) {
        this.householdService = householdService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    HouseholdView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody HouseholdNameRequest request) {
        return householdService.create(CurrentUser.id(jwt), request.name());
    }

    @GetMapping
    List<HouseholdView> list(@AuthenticationPrincipal Jwt jwt) {
        return householdService.listFor(CurrentUser.id(jwt));
    }

    @PostMapping("/join")
    HouseholdView join(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody JoinRequest request) {
        return householdService.join(CurrentUser.id(jwt), request.code());
    }

    @GetMapping("/{householdId}")
    HouseholdView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID householdId) {
        return householdService.get(householdId, CurrentUser.id(jwt));
    }

    @PatchMapping("/{householdId}")
    HouseholdView rename(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID householdId,
            @Valid @RequestBody HouseholdNameRequest request) {
        return householdService.rename(householdId, CurrentUser.id(jwt), request.name());
    }

    @DeleteMapping("/{householdId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID householdId) {
        householdService.delete(householdId, CurrentUser.id(jwt));
    }

    @GetMapping("/{householdId}/members")
    List<MemberView> members(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID householdId) {
        return householdService.members(householdId, CurrentUser.id(jwt));
    }

    @DeleteMapping("/{householdId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID householdId, @PathVariable UUID userId) {
        householdService.removeMember(householdId, CurrentUser.id(jwt), userId);
    }

    @PostMapping("/{householdId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    InvitationView invite(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID householdId) {
        return householdService.invite(householdId, CurrentUser.id(jwt));
    }

    record HouseholdNameRequest(@NotBlank @Size(max = 80) String name) {}

    record JoinRequest(@NotBlank @Size(max = 32) String code) {}
}
