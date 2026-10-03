package com.freezify.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.freezify.notifications.internal.PushSender.PushMessage;
import com.freezify.testsupport.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.ResultActions;

@Import(PushApiTests.RecordingSenderConfig.class)
class PushApiTests extends ApiTestSupport {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final String DEVICES = "/api/v1/notifications/devices";

    /** Stands in for the push service: remembers what it was asked to send and answers as told. */
    static class RecordingSender implements PushSender {

        record Sent(String token, PushMessage message) {}

        final List<Sent> sent = new CopyOnWriteArrayList<>();
        final Set<String> gone = ConcurrentHashMap.newKeySet();
        final Set<String> failing = ConcurrentHashMap.newKeySet();

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public Result send(String deviceToken, PushMessage message) {
            sent.add(new Sent(deviceToken, message));
            if (gone.contains(deviceToken)) {
                return Result.UNREGISTERED;
            }
            return failing.contains(deviceToken) ? Result.FAILED : Result.SENT;
        }

        List<Sent> to(String token) {
            return sent.stream().filter(push -> push.token().equals(token)).toList();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RecordingSenderConfig {
        @Bean
        @Primary
        RecordingSender recordingSender() {
            return new RecordingSender();
        }
    }

    @Autowired
    private ExpirationAlerts alerts;

    @Autowired
    private RecordingSender sender;

    private TestUser jorge;
    private String householdId;
    private LocalDate firstDay;
    /** Tokens are unique in the whole database, which other tests share. */
    private String phone;

    @BeforeEach
    void householdWithOwnerEarlyInTheMorning() throws Exception {
        firstDay = LocalDate.now(MADRID).plusDays(1);
        moveTo(0, 7);
        jorge = register("Jorge");
        householdId = createHousehold(jorge, "Casa");
        phone = "phone-" + UUID.randomUUID();
    }

    @Test
    void aNewNotificationIsPushedToThePhoneOfItsUser() throws Exception {
        registerDevice(jorge, phone).andExpect(status().isNoContent());
        addItem("Leche", 1);

        moveTo(0, 9);
        alerts.run();

        String notificationId = JsonPath.read(notificationsOf(jorge), "$.items[0].id");
        assertThat(sender.to(phone)).hasSize(1);
        PushMessage message = sender.to(phone).get(0).message();
        assertThat(message.title()).isEqualTo("Casa");
        assertThat(message.body()).isEqualTo("Leche: queda 1 día para su fecha de caducidad");
        assertThat(message.data())
                .containsEntry("type", "EXPIRATION")
                .containsEntry("householdId", householdId)
                .containsEntry("notificationId", notificationId);
    }

    @Test
    void thePushIsWordedInTheLanguageOfTheUserAndCountsSeveralFoods() throws Exception {
        mvc.perform(as(jorge, json(patch("/api/v1/users/me"), """
                        {"locale": "en"}
                        """)))
                .andExpect(status().isOk());
        registerDevice(jorge, phone).andExpect(status().isNoContent());
        addItem("Leche", 1);
        addItem("Jamón", 0);

        moveTo(0, 9);
        alerts.run();

        assertThat(sender.to(phone)).hasSize(1);
        assertThat(sender.to(phone).get(0).message().body()).isEqualTo("You have 2 foods you should eat soon");
    }

    @Test
    void everyPhoneOfTheUserGetsItAndNobodyElseDoes() throws Exception {
        String tablet = "tablet-" + UUID.randomUUID();
        TestUser stranger = register("Marta");
        String strangerPhone = "stranger-" + UUID.randomUUID();
        registerDevice(jorge, phone).andExpect(status().isNoContent());
        registerDevice(jorge, tablet).andExpect(status().isNoContent());
        // Registering twice, as the app does on every start, changes nothing.
        registerDevice(jorge, phone).andExpect(status().isNoContent());
        registerDevice(stranger, strangerPhone).andExpect(status().isNoContent());
        addItem("Leche", 1);

        moveTo(0, 9);
        alerts.run();

        assertThat(sender.to(phone)).hasSize(1);
        assertThat(sender.to(tablet)).hasSize(1);
        assertThat(sender.to(strangerPhone)).isEmpty();
    }

    @Test
    void aPhoneBelongsToWhoeverSignedInOnItLast() throws Exception {
        TestUser ana = register("Ana");
        registerDevice(jorge, phone).andExpect(status().isNoContent());
        // Ana signs in on the same phone: Jorge's food must not show up on it any more.
        registerDevice(ana, phone).andExpect(status().isNoContent());
        addItem("Leche", 1);

        moveTo(0, 9);
        alerts.run();

        notificationsOfExpect(jorge, 1);
        assertThat(sender.to(phone)).isEmpty();
    }

    @Test
    void signingOutStopsThePushesAndOnlyTheOwnerCanDoIt() throws Exception {
        TestUser stranger = register("Marta");
        registerDevice(jorge, phone).andExpect(status().isNoContent());
        addItem("Leche", 2);

        unregisterDevice(stranger, phone).andExpect(status().isNoContent());
        moveTo(0, 9);
        alerts.run();
        assertThat(sender.to(phone)).hasSize(1);

        unregisterDevice(jorge, phone).andExpect(status().isNoContent());
        addItem("Merluza", 2);
        moveTo(1, 9);
        alerts.run();

        notificationsOfExpect(jorge, 2);
        assertThat(sender.to(phone)).hasSize(1);
    }

    @Test
    void aPhoneThePushServiceReportsAsGoneIsForgotten() throws Exception {
        registerDevice(jorge, phone).andExpect(status().isNoContent());
        sender.gone.add(phone);
        addItem("Leche", 2);
        moveTo(0, 9);
        alerts.run();
        assertThat(sender.to(phone)).hasSize(1);

        addItem("Merluza", 2);
        moveTo(1, 9);
        alerts.run();

        assertThat(sender.to(phone)).hasSize(1);
    }

    @Test
    void aFailedPushLosesNeitherTheNotificationNorThePhone() throws Exception {
        registerDevice(jorge, phone).andExpect(status().isNoContent());
        sender.failing.add(phone);
        addItem("Leche", 2);
        moveTo(0, 9);
        alerts.run();
        notificationsOfExpect(jorge, 1);

        sender.failing.remove(phone);
        addItem("Merluza", 2);
        moveTo(1, 9);
        alerts.run();

        notificationsOfExpect(jorge, 2);
        assertThat(sender.to(phone)).hasSize(2);
    }

    @Test
    void someoneWithoutAPhoneIsOnlyToldInsideTheApp() throws Exception {
        int before = sender.sent.size();
        addItem("Leche", 1);

        moveTo(0, 9);
        alerts.run();

        notificationsOfExpect(jorge, 1);
        // Other users of the shared database may have phones; none of the pushes is about this household.
        assertThat(sender.sent.subList(before, sender.sent.size()))
                .noneMatch(push -> householdId.equals(push.message().data().get("householdId")));
    }

    @Test
    void invalidDevicesAreRejected() throws Exception {
        mvc.perform(as(jorge, json(put(DEVICES), """
                        {"token": " ", "platform": "ANDROID"}
                        """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(as(jorge, json(put(DEVICES), """
                        {"token": "abc"}
                        """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(as(jorge, json(put(DEVICES), """
                        {"token": "abc", "platform": "FRIDGE"}
                        """)))
                .andExpect(status().isBadRequest());
        mvc.perform(json(put(DEVICES), """
                        {"token": "abc", "platform": "ANDROID"}
                        """))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions registerDevice(TestUser user, String token) throws Exception {
        return mvc.perform(as(user, json(put(DEVICES), """
                {"token": "%s", "platform": "ANDROID"}
                """.formatted(token))));
    }

    private ResultActions unregisterDevice(TestUser user, String token) throws Exception {
        return mvc.perform(as(user, json(post(DEVICES + "/unregister"), """
                {"token": "%s"}
                """.formatted(token))));
    }

    private String notificationsOf(TestUser user) throws Exception {
        return mvc.perform(as(user, get("/api/v1/notifications")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private void notificationsOfExpect(TestUser user, int count) throws Exception {
        mvc.perform(as(user, get("/api/v1/notifications"))).andExpect(jsonPath("$.items", hasSize(count)));
    }

    /** Moves the clock to the given hour, {@code days} after the day the test started on. */
    private void moveTo(int days, int hour) {
        clock.advance(Duration.between(
                clock.instant(), firstDay.plusDays(days).atTime(hour, 0).atZone(MADRID).toInstant()));
    }

    /**
     * @param expiresInDays counted from the day the test started on
     */
    private void addItem(String name, int expiresInDays) throws Exception {
        mvc.perform(as(jorge, json(post("/api/v1/households/" + householdId + "/inventory"), """
                        {"name": "%s", "quantity": {"amount": 1, "unit": "UNIT"}, "storageLocation": "REFRIGERATOR",
                         "expirationDate": "%s"}
                        """.formatted(name, firstDay.plusDays(expiresInDays)))))
                .andExpect(status().isCreated());
    }
}
