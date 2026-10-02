package com.freezify.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.freezify.testsupport.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class NotificationApiTests extends ApiTestSupport {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final String NOTIFICATIONS = "/api/v1/notifications";
    private static final String PREFERENCES = NOTIFICATIONS + "/preferences";

    @Autowired
    private ExpirationAlerts alerts;

    @Autowired
    private JdbcTemplate jdbc;

    private TestUser jorge;
    private String householdId;
    /** The day the test starts on, as the application sees it: tomorrow, so that the hour can be chosen freely. */
    private LocalDate firstDay;

    @BeforeEach
    void householdWithOwnerEarlyInTheMorning() throws Exception {
        firstDay = LocalDate.now(MADRID).plusDays(1);
        moveTo(0, 7);
        jorge = register("Jorge");
        householdId = createHousehold(jorge, "Casa");
    }

    @Test
    void nobodyIsToldBeforeTheHourTheyChose() throws Exception {
        addItem("Leche", 1);

        alerts.run();
        notificationsOf(jorge).andExpect(jsonPath("$.items", hasSize(0)));
        unreadOf(jorge).andExpect(jsonPath("$.count").value(0));

        moveTo(0, 9);
        alerts.run();

        notificationsOf(jorge)
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].type").value("EXPIRATION"))
                .andExpect(jsonPath("$.items[0].householdId").value(householdId))
                .andExpect(jsonPath("$.items[0].householdName").value("Casa"))
                .andExpect(jsonPath("$.items[0].day").value(day(0)))
                .andExpect(jsonPath("$.items[0].itemCount").value(1))
                .andExpect(jsonPath("$.items[0].items[0].name").value("Leche"))
                .andExpect(jsonPath("$.items[0].items[0].expirationDate").value(day(1)))
                .andExpect(jsonPath("$.items[0].items[0].daysUntilExpiration").value(1))
                .andExpect(jsonPath("$.items[0].items[0].estimated").value(false))
                .andExpect(jsonPath("$.items[0].read").value(false));
        unreadOf(jorge).andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void aHouseholdGetsOneNotificationADayWithEverythingInIt() throws Exception {
        addItem("Leche", 1);
        addItem("Jamón", 0);
        addItem("Yogur", -1);
        addItem("Queso", 8);
        addItemWithoutDate("Arroz", "OTHER");

        moveTo(0, 9);
        alerts.run();
        alerts.run();
        moveTo(0, 10);
        alerts.run();

        notificationsOf(jorge)
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].itemCount").value(3))
                .andExpect(jsonPath("$.items[0].items[*].name", contains("Yogur", "Jamón", "Leche")))
                .andExpect(jsonPath("$.items[0].items[0].daysUntilExpiration").value(-1));
    }

    @Test
    void onlyTheMostPressingItemsAreListedAndTheRestCounted() throws Exception {
        for (int i = 1; i <= ExpirationAlerts.LISTED_ITEMS + 2; i++) {
            addItem("Yogur " + i, 1);
        }
        addItem("Pescado", 0);

        moveTo(0, 9);
        alerts.run();

        notificationsOf(jorge)
                .andExpect(jsonPath("$.items[0].itemCount").value(ExpirationAlerts.LISTED_ITEMS + 3))
                .andExpect(jsonPath("$.items[0].items", hasSize(ExpirationAlerts.LISTED_ITEMS)))
                .andExpect(jsonPath("$.items[0].items[0].name").value("Pescado"));
    }

    @Test
    void foodIsBroughtUpAgainOnlyWhenItBecomesMorePressing() throws Exception {
        addItem("Leche", 2);

        // 2 days left: worth telling.
        moveTo(0, 9);
        alerts.run();
        notificationsOf(jorge).andExpect(jsonPath("$.items", hasSize(1)));

        // 1 day left is the same level; the user already knows.
        moveTo(1, 9);
        alerts.run();
        notificationsOf(jorge).andExpect(jsonPath("$.items", hasSize(1)));

        // Expires today.
        moveTo(2, 9);
        alerts.run();
        notificationsOf(jorge)
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].day").value(day(2)))
                .andExpect(jsonPath("$.items[0].items[0].daysUntilExpiration").value(0));

        // Expired.
        moveTo(3, 9);
        alerts.run();
        notificationsOf(jorge)
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].items[0].daysUntilExpiration").value(-1));

        // It just sits there expired: nothing new to say.
        moveTo(4, 9);
        alerts.run();
        moveTo(5, 9);
        alerts.run();
        notificationsOf(jorge).andExpect(jsonPath("$.items", hasSize(3)));
    }

    @Test
    void somethingNewBringsEverythingAlongInOneNotification() throws Exception {
        addItem("Leche", 2);
        moveTo(0, 9);
        alerts.run();

        addItem("Merluza", 2);
        moveTo(1, 9);
        alerts.run();

        notificationsOf(jorge)
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].itemCount").value(2))
                .andExpect(jsonPath("$.items[0].items[*].name", contains("Leche", "Merluza")));
    }

    @Test
    void foodThatWasEatenIsNotMentioned() throws Exception {
        String milk = addItem("Leche", 1);
        mvc.perform(as(jorge, post(inventory() + "/" + milk + "/consume"))).andExpect(status().isOk());

        moveTo(0, 9);
        alerts.run();

        notificationsOf(jorge).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void anEstimatedDateIsSaidToBeAnEstimate() throws Exception {
        // Fresh fish in the fridge has a rule: one day from the purchase.
        addItemWithoutDate("Merluza", "FISH");

        moveTo(0, 9);
        alerts.run();

        notificationsOf(jorge)
                .andExpect(jsonPath("$.items[0].items[0].name").value("Merluza"))
                .andExpect(jsonPath("$.items[0].items[0].expirationDate").value(day(1)))
                .andExpect(jsonPath("$.items[0].items[0].estimated").value(true));
    }

    @Test
    void everyMemberIsToldAndReadsOnTheirOwn() throws Exception {
        TestUser ana = register("Ana");
        join(ana, invite(jorge, householdId)).andExpect(status().isOk());
        addItem("Leche", 1);

        moveTo(0, 9);
        alerts.run();

        String id = firstNotificationId(jorge);
        mvc.perform(as(jorge, post(NOTIFICATIONS + "/" + id + "/read"))).andExpect(status().isNoContent());
        mvc.perform(as(jorge, post(NOTIFICATIONS + "/" + id + "/read"))).andExpect(status().isNoContent());

        notificationsOf(jorge).andExpect(jsonPath("$.items[0].read").value(true));
        unreadOf(jorge).andExpect(jsonPath("$.count").value(0));
        notificationsOf(ana)
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].read").value(false));
        unreadOf(ana).andExpect(jsonPath("$.count").value(1));
        // Opening it counts once, however many times it is opened.
        assertThat(jdbc.queryForObject(
                        "select count(*) from product_events where name = 'notification_opened' and user_id = ?::uuid",
                        Long.class,
                        jorge.id()))
                .isEqualTo(1);
    }

    @Test
    void everythingCanBeMarkedAsReadAtOnce() throws Exception {
        String otherHousehold = createHousehold(jorge, "Pueblo");
        addItem("Leche", 1);
        addItemTo(otherHousehold, "Pan", 0);
        moveTo(0, 9);
        alerts.run();
        unreadOf(jorge).andExpect(jsonPath("$.count").value(2));

        mvc.perform(as(jorge, post(NOTIFICATIONS + "/read-all"))).andExpect(status().isNoContent());

        unreadOf(jorge).andExpect(jsonPath("$.count").value(0));
        notificationsOf(jorge).andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void nobodyCanSeeOrTouchTheNotificationsOfSomeoneElse() throws Exception {
        TestUser stranger = register("Marta");
        addItem("Leche", 1);
        moveTo(0, 9);
        alerts.run();
        String id = firstNotificationId(jorge);

        notificationsOf(stranger).andExpect(jsonPath("$.items", hasSize(0)));
        unreadOf(stranger).andExpect(jsonPath("$.count").value(0));
        mvc.perform(as(stranger, post(NOTIFICATIONS + "/" + id + "/read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        mvc.perform(as(stranger, post(NOTIFICATIONS + "/read-all"))).andExpect(status().isNoContent());

        unreadOf(jorge).andExpect(jsonPath("$.count").value(1));
        mvc.perform(get(NOTIFICATIONS)).andExpect(status().isUnauthorized());
        mvc.perform(get(PREFERENCES)).andExpect(status().isUnauthorized());
    }

    @Test
    void leavingAHouseholdTakesItsNotificationsAway() throws Exception {
        TestUser ana = register("Ana");
        join(ana, invite(jorge, householdId)).andExpect(status().isOk());
        addItem("Leche", 1);
        moveTo(0, 9);
        alerts.run();
        notificationsOf(ana).andExpect(jsonPath("$.items", hasSize(1)));

        mvc.perform(as(ana, delete("/api/v1/households/" + householdId + "/members/" + ana.id())))
                .andExpect(status().isNoContent());

        notificationsOf(ana).andExpect(jsonPath("$.items", hasSize(0)));
        unreadOf(ana).andExpect(jsonPath("$.count").value(0));
        notificationsOf(jorge).andExpect(jsonPath("$.items", hasSize(1)));

        // And nothing more arrives about a household they no longer belong to.
        addItem("Merluza", 0);
        moveTo(1, 9);
        alerts.run();
        notificationsOf(ana).andExpect(jsonPath("$.items", hasSize(0)));
        notificationsOf(jorge).andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void deletingAHouseholdDeletesItsNotifications() throws Exception {
        addItem("Leche", 1);
        moveTo(0, 9);
        alerts.run();
        notificationsOf(jorge).andExpect(jsonPath("$.items", hasSize(1)));

        mvc.perform(as(jorge, delete("/api/v1/households/" + householdId))).andExpect(status().isNoContent());

        notificationsOf(jorge).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void preferencesStartWithFewAndRelevantNotifications() throws Exception {
        mvc.perform(as(jorge, get(PREFERENCES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expirationAlerts").value(true))
                .andExpect(jsonPath("$.deliveryHour").value(9))
                .andExpect(jsonPath("$.frequency").value("DAILY"))
                .andExpect(jsonPath("$.threshold").value("URGENT"))
                .andExpect(jsonPath("$.mutedCategories", hasSize(0)));
    }

    @Test
    void preferencesAreKeptPerUser() throws Exception {
        TestUser ana = register("Ana");

        prefer(jorge, false, 20, "WEEKLY", "SOON", "\"MEAT\", \"DAIRY\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryHour").value(20));
        prefer(jorge, true, 21, "EVERY_THREE_DAYS", "TODAY", "\"MEAT\", \"DAIRY\"").andExpect(status().isOk());

        mvc.perform(as(jorge, get(PREFERENCES)))
                .andExpect(jsonPath("$.expirationAlerts").value(true))
                .andExpect(jsonPath("$.deliveryHour").value(21))
                .andExpect(jsonPath("$.frequency").value("EVERY_THREE_DAYS"))
                .andExpect(jsonPath("$.threshold").value("TODAY"))
                .andExpect(jsonPath("$.mutedCategories", contains("MEAT", "DAIRY")));
        mvc.perform(as(ana, get(PREFERENCES))).andExpect(jsonPath("$.deliveryHour").value(9));
    }

    @Test
    void invalidPreferencesAreRejected() throws Exception {
        prefer(jorge, true, 24, "DAILY", "URGENT", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(as(jorge, json(put(PREFERENCES), """
                        {"expirationAlerts": true, "deliveryHour": 9}
                        """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        prefer(jorge, true, 9, "HOURLY", "URGENT", "").andExpect(status().isBadRequest());
        prefer(jorge, true, 9, "DAILY", "OK", "").andExpect(status().isBadRequest());
    }

    @Test
    void someoneWhoTurnedAlertsOffIsNotTold() throws Exception {
        prefer(jorge, false, 9, "DAILY", "URGENT", "").andExpect(status().isOk());
        addItem("Leche", 0);

        moveTo(0, 9);
        alerts.run();

        notificationsOf(jorge).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void theChosenHourIsRespectedAndALateRunStillTells() throws Exception {
        prefer(jorge, true, 20, "DAILY", "URGENT", "").andExpect(status().isOk());
        addItem("Leche", 0);

        moveTo(0, 19);
        alerts.run();
        notificationsOf(jorge).andExpect(jsonPath("$.items", hasSize(0)));

        // The application was down at 20:00.
        moveTo(0, 22);
        alerts.run();
        notificationsOf(jorge).andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void mutedCategoriesAreLeftOut() throws Exception {
        prefer(jorge, true, 9, "DAILY", "URGENT", "\"DAIRY\"").andExpect(status().isOk());
        addItem("Leche", 0, "DAIRY");

        moveTo(0, 9);
        alerts.run();
        notificationsOf(jorge).andExpect(jsonPath("$.items", hasSize(0)));

        addItem("Merluza", 1, "FISH");
        moveTo(1, 9);
        alerts.run();
        notificationsOf(jorge)
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].itemCount").value(1))
                .andExpect(jsonPath("$.items[0].items[*].name", contains("Merluza")));
    }

    @Test
    void theThresholdDecidesHowEarlyFoodIsMentioned() throws Exception {
        addItem("Queso", 4);

        moveTo(0, 9);
        alerts.run();
        notificationsOf(jorge).andExpect(jsonPath("$.items", hasSize(0)));

        prefer(jorge, true, 9, "DAILY", "SOON", "").andExpect(status().isOk());
        moveTo(1, 9);
        alerts.run();
        notificationsOf(jorge)
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].items[0].daysUntilExpiration").value(3));
    }

    @Test
    void aLowerFrequencyHoldsNewsBackWithoutLosingThem() throws Exception {
        prefer(jorge, true, 9, "EVERY_THREE_DAYS", "URGENT", "").andExpect(status().isOk());
        addItem("Leche", 0);
        moveTo(0, 9);
        alerts.run();
        notificationsOf(jorge).andExpect(jsonPath("$.items", hasSize(1)));

        addItem("Merluza", 3);
        moveTo(1, 9);
        alerts.run();
        moveTo(2, 9);
        alerts.run();
        notificationsOf(jorge).andExpect(jsonPath("$.items", hasSize(1)));

        moveTo(3, 9);
        alerts.run();
        notificationsOf(jorge)
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].itemCount").value(2))
                .andExpect(jsonPath("$.items[0].items[*].name", contains("Leche", "Merluza")));
    }

    /** Moves the clock to the given hour, {@code days} after the day the test started on. */
    private void moveTo(int days, int hour) {
        ZonedDateTime target = firstDay.plusDays(days).atTime(hour, 0).atZone(MADRID);
        clock.advance(Duration.between(clock.instant(), target.toInstant()));
    }

    private String day(int days) {
        return firstDay.plusDays(days).toString();
    }

    private ResultActions notificationsOf(TestUser user) throws Exception {
        return mvc.perform(as(user, get(NOTIFICATIONS))).andExpect(status().isOk());
    }

    private ResultActions unreadOf(TestUser user) throws Exception {
        return mvc.perform(as(user, get(NOTIFICATIONS + "/unread-count"))).andExpect(status().isOk());
    }

    private String firstNotificationId(TestUser user) throws Exception {
        return JsonPath.read(
                notificationsOf(user).andReturn().getResponse().getContentAsString(), "$.items[0].id");
    }

    private ResultActions prefer(
            TestUser user, boolean enabled, int hour, String frequency, String threshold, String mutedCategories)
            throws Exception {
        return mvc.perform(as(user, json(put(PREFERENCES), """
                {"expirationAlerts": %s, "deliveryHour": %d, "frequency": "%s", "threshold": "%s",
                 "mutedCategories": [%s]}
                """.formatted(enabled, hour, frequency, threshold, mutedCategories))));
    }

    /**
     * @param expiresInDays counted from the day the test started on
     */
    private String addItem(String name, int expiresInDays) throws Exception {
        return addItem(name, expiresInDays, "OTHER");
    }

    private String addItem(String name, int expiresInDays, String category) throws Exception {
        return add(householdId, name, category, "\"" + day(expiresInDays) + "\"");
    }

    private void addItemTo(String household, String name, int expiresInDays) throws Exception {
        add(household, name, "OTHER", "\"" + day(expiresInDays) + "\"");
    }

    private void addItemWithoutDate(String name, String category) throws Exception {
        add(householdId, name, category, "null");
    }

    private String add(String household, String name, String category, String expirationDate) throws Exception {
        String response = mvc.perform(as(jorge, json(post("/api/v1/households/" + household + "/inventory"), """
                        {"name": "%s", "category": "%s", "quantity": {"amount": 1, "unit": "UNIT"},
                         "storageLocation": "REFRIGERATOR", "expirationDate": %s}
                        """.formatted(name, category, expirationDate))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String inventory() {
        return "/api/v1/households/" + householdId + "/inventory";
    }
}
