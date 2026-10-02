package com.freezify.notifications.web;

import com.freezify.common.CurrentUser;
import com.freezify.common.PageResponse;
import com.freezify.food.FoodCategory;
import com.freezify.notifications.internal.NotificationService;
import com.freezify.notifications.internal.NotificationView;
import com.freezify.notifications.internal.Preferences;
import com.freezify.notifications.internal.Preferences.Frequency;
import com.freezify.notifications.internal.Preferences.Threshold;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The notifications of whoever is signed in. There is no user id in the path, so there is none to tamper with. */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
class NotificationController {

    private final NotificationService notifications;

    NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    PageResponse<NotificationView> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return notifications.list(CurrentUser.id(jwt), page, size);
    }

    @GetMapping("/unread-count")
    UnreadCount unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return new UnreadCount(notifications.unreadCount(CurrentUser.id(jwt)));
    }

    @PostMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID notificationId) {
        notifications.markRead(CurrentUser.id(jwt), notificationId);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markAllRead(@AuthenticationPrincipal Jwt jwt) {
        notifications.markAllRead(CurrentUser.id(jwt));
    }

    @GetMapping("/preferences")
    Preferences preferences(@AuthenticationPrincipal Jwt jwt) {
        return notifications.preferences(CurrentUser.id(jwt));
    }

    /** Replaces every preference. */
    @PutMapping("/preferences")
    Preferences updatePreferences(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PreferencesRequest request) {
        return notifications.updatePreferences(
                CurrentUser.id(jwt),
                new Preferences(
                        request.expirationAlerts(),
                        request.deliveryHour(),
                        request.frequency(),
                        request.threshold(),
                        request.mutedCategories()));
    }

    record UnreadCount(long count) {}

    record PreferencesRequest(
            @NotNull Boolean expirationAlerts,
            @NotNull @Min(0) @Max(23) Integer deliveryHour,
            @NotNull Frequency frequency,
            @NotNull Threshold threshold,
            @NotNull Set<@NotNull FoodCategory> mutedCategories) {}
}
