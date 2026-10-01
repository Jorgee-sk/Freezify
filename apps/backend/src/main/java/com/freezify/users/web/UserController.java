package com.freezify.users.web;

import com.freezify.common.CurrentUser;
import com.freezify.users.UserAccount;
import com.freezify.users.internal.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
class UserController {

    private final UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    UserAccount me(@AuthenticationPrincipal Jwt jwt) {
        return userService.get(CurrentUser.id(jwt));
    }

    @PatchMapping("/me")
    UserAccount updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(CurrentUser.id(jwt), request.displayName(), request.locale());
    }

    /** Absent fields are left unchanged. */
    record UpdateProfileRequest(
            @Nullable @Pattern(regexp = ".*\\S.*", message = "must not be blank") @Size(max = 80) String displayName,
            @Nullable @Pattern(regexp = "es|en", message = "must be 'es' or 'en'") String locale) {}
}
