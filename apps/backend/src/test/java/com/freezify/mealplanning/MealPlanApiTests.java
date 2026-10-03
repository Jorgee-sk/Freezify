package com.freezify.mealplanning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.freezify.testsupport.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class MealPlanApiTests extends ApiTestSupport {

    private static final String PASTA = "Pasta con calabacín y tomate";
    private static final String ZUCCHINI_SOUP = "Crema de calabacín";
    private static final String SCRAMBLED_EGGS = "Revuelto de champiñones";
    private static final String CHICKEN = "Pollo a la plancha con brócoli";
    private static final String TORRIJAS = "Torrijas";

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Madrid"));
    /** A whole week ahead of today, whatever day the tests run on. */
    private static final LocalDate NEXT_MONDAY = TODAY.with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @Autowired
    private JdbcTemplate jdbc;

    private TestUser jorge;
    private String householdId;

    @BeforeEach
    void householdWithOwner() throws Exception {
        jorge = register("Jorge");
        householdId = createHousehold(jorge, "Casa");
    }

    @Test
    void aWeekRunsFromMondayToSundayAndStartsEmpty() throws Exception {
        week(jorge, NEXT_MONDAY.plusDays(2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value(NEXT_MONDAY.toString()))
                .andExpect(jsonPath("$.weekEnd").value(NEXT_MONDAY.plusDays(6).toString()))
                .andExpect(jsonPath("$.today").value(TODAY.toString()))
                .andExpect(jsonPath("$.meals", hasSize(0)))
                .andExpect(jsonPath("$.unusedExpiring", hasSize(0)));
        // Without a date, the week of today.
        mvc.perform(as(jorge, get(plan())))
                .andExpect(jsonPath("$.weekStart")
                        .value(TODAY.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                .toString()));
    }

    @Test
    void aMemberChoosesTheRecipeOfAMeal() throws Exception {
        choose(jorge, NEXT_MONDAY, "DINNER", recipeId(PASTA)).andExpect(status().isNoContent());

        week(jorge, NEXT_MONDAY)
                .andExpect(jsonPath("$.meals", hasSize(1)))
                .andExpect(jsonPath("$.meals[0].date").value(NEXT_MONDAY.toString()))
                .andExpect(jsonPath("$.meals[0].slot").value("DINNER"))
                .andExpect(jsonPath("$.meals[0].origin").value("MANUAL"))
                .andExpect(jsonPath("$.meals[0].recipe.id").value(recipeId(PASTA)))
                .andExpect(jsonPath("$.meals[0].recipe.name").value(PASTA))
                .andExpect(jsonPath("$.meals[0].recipe.servings").value(2))
                .andExpect(jsonPath("$.meals[0].recipe.totalMinutes").value(25))
                .andExpect(jsonPath("$.meals[0].recipe.difficulty").value("EASY"))
                .andExpect(jsonPath("$.meals[0].recipe.contains", contains("DAIRY", "EGG", "GLUTEN")))
                .andExpect(jsonPath("$.meals[0].ingredients", hasSize(7)));
        mvc.perform(as(jorge, get(plan()).param("week", NEXT_MONDAY.toString()).param("lang", "en")))
                .andExpect(jsonPath("$.meals[0].recipe.name").value("Pasta with zucchini and tomato"))
                .andExpect(jsonPath("$.meals[0].ingredients[1].name").value("Zucchini"));
    }

    @Test
    void choosingAgainReplacesWhatWasPlanned() throws Exception {
        choose(jorge, NEXT_MONDAY, "LUNCH", recipeId(PASTA)).andExpect(status().isNoContent());
        choose(jorge, NEXT_MONDAY, "LUNCH", recipeId(CHICKEN)).andExpect(status().isNoContent());

        week(jorge, NEXT_MONDAY)
                .andExpect(jsonPath("$.meals", hasSize(1)))
                .andExpect(jsonPath("$.meals[0].recipe.name").value(CHICKEN));
    }

    @Test
    void mealsComeInTheOrderTheyAreEaten() throws Exception {
        choose(jorge, NEXT_MONDAY.plusDays(1), "LUNCH", recipeId(CHICKEN)).andExpect(status().isNoContent());
        choose(jorge, NEXT_MONDAY, "DINNER", recipeId(PASTA)).andExpect(status().isNoContent());
        choose(jorge, NEXT_MONDAY, "LUNCH", recipeId(ZUCCHINI_SOUP)).andExpect(status().isNoContent());

        week(jorge, NEXT_MONDAY)
                .andExpect(jsonPath("$.meals[*].recipe.name", contains(ZUCCHINI_SOUP, PASTA, CHICKEN)))
                .andExpect(jsonPath("$.meals[*].slot", contains("LUNCH", "DINNER", "LUNCH")));
    }

    @Test
    void aMealSaysWhatTheHouseholdWouldHaveThatDay() throws Exception {
        String zucchiniDate = NEXT_MONDAY.plusDays(2).toString();
        add("Pasta", 500, "GRAM", "OTHER", null);
        add("Calabacín", 1, "UNIT", "REFRIGERATOR", zucchiniDate);
        choose(jorge, NEXT_MONDAY, "LUNCH", recipeId(PASTA)).andExpect(status().isNoContent());
        choose(jorge, NEXT_MONDAY.plusDays(1), "LUNCH", recipeId(ZUCCHINI_SOUP)).andExpect(status().isNoContent());

        String plan = week(jorge, NEXT_MONDAY).andReturn().getResponse().getContentAsString();

        assertThat(ingredient(plan, 0, "Pasta")).containsEntry("availability", "ENOUGH");
        assertThat(ingredient(plan, 0, "Pasta").get("expirationDate")).isNull();
        assertThat(ingredient(plan, 0, "Calabacín"))
                .containsEntry("availability", "ENOUGH")
                .containsEntry("expirationDate", zucchiniDate)
                .containsEntry("estimated", false);
        assertThat(ingredient(plan, 0, "Tomate")).containsEntry("availability", "MISSING");
        assertThat(ingredient(plan, 0, "Sal")).containsEntry("availability", "ASSUMED");
        // The pasta of Monday took the only zucchini.
        assertThat(ingredient(plan, 1, "Calabacín")).containsEntry("availability", "MISSING");
    }

    @Test
    void anEstimatedDateIsSaidToBeAnEstimate() throws Exception {
        // Without a date and in the fridge: the application estimates one from its shelf-life rules.
        add("Champiñones", 300, "GRAM", "REFRIGERATOR", null);
        choose(jorge, TODAY, "DINNER", recipeId(SCRAMBLED_EGGS)).andExpect(status().isNoContent());

        String plan = week(jorge, TODAY).andReturn().getResponse().getContentAsString();

        assertThat(ingredient(plan, 0, "Champiñones"))
                .containsEntry("availability", "ENOUGH")
                .containsEntry("estimated", true);
        assertThat(ingredient(plan, 0, "Champiñones").get("expirationDate")).isNotNull();
    }

    @Test
    void aMealPlannedAfterTheDateOfAFoodDoesNotCountOnIt() throws Exception {
        add("Calabacín", 1, "UNIT", "REFRIGERATOR", NEXT_MONDAY.toString());
        choose(jorge, NEXT_MONDAY.plusDays(1), "LUNCH", recipeId(PASTA)).andExpect(status().isNoContent());

        String plan = week(jorge, NEXT_MONDAY).andReturn().getResponse().getContentAsString();

        assertThat(ingredient(plan, 0, "Calabacín")).containsEntry("availability", "MISSING");
        assertThat(ingredient(plan, 0, "Calabacín").get("expirationDate")).isNull();
    }

    @Test
    void foodThatExpiresInTheWeekAndIsInNoMealIsPointedOut() throws Exception {
        add("Calabacín", 1, "UNIT", "REFRIGERATOR", NEXT_MONDAY.plusDays(2).toString());
        add("Yogur", 4, "UNIT", "REFRIGERATOR", NEXT_MONDAY.plusDays(1).toString());
        add("Arroz", 1, "KILOGRAM", "OTHER", null);
        mvc.perform(as(jorge, json(post(inventory()), """
                        {"name": "Tarta de la abuela", "quantity": {"amount": 1, "unit": "UNIT"},
                         "storageLocation": "OTHER", "expirationDate": "%s"}
                        """.formatted(NEXT_MONDAY.plusDays(3)))))
                .andExpect(status().isCreated());

        week(jorge, NEXT_MONDAY)
                .andExpect(jsonPath("$.unusedExpiring[*].name", contains("Yogur", "Calabacín", "Tarta de la abuela")))
                .andExpect(jsonPath("$.unusedExpiring[0].expirationDate")
                        .value(NEXT_MONDAY.plusDays(1).toString()))
                .andExpect(jsonPath("$.unusedExpiring[0].estimated").value(false))
                .andExpect(jsonPath("$.unusedExpiring[0].amount").value(4))
                .andExpect(jsonPath("$.unusedExpiring[0].unit").value("UNIT"));

        // Planned in time, the zucchini is no longer on the list; what no recipe uses stays.
        choose(jorge, NEXT_MONDAY.plusDays(2), "LUNCH", recipeId(PASTA)).andExpect(status().isNoContent());
        week(jorge, NEXT_MONDAY)
                .andExpect(jsonPath("$.unusedExpiring[*].name", contains("Yogur", "Tarta de la abuela")));

        // Planned for after its date, it is on the list again.
        move(jorge, NEXT_MONDAY.plusDays(2), "LUNCH", NEXT_MONDAY.plusDays(3), "LUNCH")
                .andExpect(status().isNoContent());
        week(jorge, NEXT_MONDAY)
                .andExpect(jsonPath("$.unusedExpiring[*].name", contains("Yogur", "Calabacín", "Tarta de la abuela")));

        // Food that expires after the week is over is not this week's concern.
        week(jorge, TODAY).andExpect(jsonPath("$.unusedExpiring[*].name", not(hasItem("Tarta de la abuela"))));
    }

    @Test
    void foodThatThePlanOnlyUsesInPartBeforeItsDateIsPointedOutWithWhatIsLeft() throws Exception {
        add("Pechuga de pollo", 0.5, "KILOGRAM", "REFRIGERATOR", NEXT_MONDAY.plusDays(1).toString());
        add("Brócoli", 500, "GRAM", "REFRIGERATOR", NEXT_MONDAY.plusDays(1).toString());
        choose(jorge, NEXT_MONDAY, "LUNCH", recipeId(CHICKEN)).andExpect(status().isNoContent());

        // The recipe takes 300 g of chicken. It asks for one broccoli and the household has it by weight: how
        // much of it is used is not known, so nothing is said about it.
        week(jorge, NEXT_MONDAY)
                .andExpect(jsonPath("$.unusedExpiring", hasSize(1)))
                .andExpect(jsonPath("$.unusedExpiring[0].name").value("Pechuga de pollo"))
                .andExpect(jsonPath("$.unusedExpiring[0].amount").value(0.2))
                .andExpect(jsonPath("$.unusedExpiring[0].unit").value("KILOGRAM"));

        // A second meal before the date uses the rest.
        choose(jorge, NEXT_MONDAY.plusDays(1), "LUNCH", recipeId("Arroz con pollo y verduras"))
                .andExpect(status().isNoContent());
        week(jorge, NEXT_MONDAY).andExpect(jsonPath("$.unusedExpiring", hasSize(0)));
    }

    @Test
    void ofTwoPacksOfTheSameFoodTheOneNoMealReachesIsPointedOut() throws Exception {
        add("Pechuga de pollo", 300, "GRAM", "REFRIGERATOR", NEXT_MONDAY.plusDays(1).toString());
        add("Pechuga de pollo", 300, "GRAM", "REFRIGERATOR", NEXT_MONDAY.plusDays(2).toString());
        choose(jorge, NEXT_MONDAY, "LUNCH", recipeId(CHICKEN)).andExpect(status().isNoContent());

        // The meal takes the pack that expires first; the other one is still whole on its date.
        week(jorge, NEXT_MONDAY)
                .andExpect(jsonPath("$.unusedExpiring", hasSize(1)))
                .andExpect(jsonPath("$.unusedExpiring[0].amount").value(300))
                .andExpect(jsonPath("$.unusedExpiring[0].expirationDate")
                        .value(NEXT_MONDAY.plusDays(2).toString()));
    }

    @Test
    void aMealSaysWhetherTheHouseholdCookedIt() throws Exception {
        choose(jorge, TODAY, "LUNCH", recipeId(PASTA)).andExpect(status().isNoContent());
        choose(jorge, TODAY, "DINNER", recipeId(CHICKEN)).andExpect(status().isNoContent());
        week(jorge, TODAY).andExpect(jsonPath("$.meals[*].cooked", contains(false, false)));

        mvc.perform(as(jorge, post("/api/v1/households/" + householdId + "/recipes/" + recipeId(PASTA) + "/cooked")))
                .andExpect(status().isNoContent());

        week(jorge, TODAY)
                .andExpect(jsonPath("$.meals[0].recipe.name").value(PASTA))
                .andExpect(jsonPath("$.meals[0].cooked").value(true))
                .andExpect(jsonPath("$.meals[1].cooked").value(false));
        // Cooking it today says nothing about the same recipe planned for another day.
        choose(jorge, NEXT_MONDAY, "LUNCH", recipeId(PASTA)).andExpect(status().isNoContent());
        week(jorge, NEXT_MONDAY).andExpect(jsonPath("$.meals[0].cooked").value(false));
    }

    @Test
    void membersChoosingTheSameEmptyMealAtOnceNeverCollide() throws Exception {
        TestUser partner = register("Lucía");
        join(partner, invite(jorge, householdId)).andExpect(status().isOk());
        String pasta = recipeId(PASTA);
        String chicken = recipeId(CHICKEN);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            for (int day = 0; day < 7; day++) {
                LocalDate date = NEXT_MONDAY.plusDays(day);
                java.util.concurrent.CyclicBarrier together = new java.util.concurrent.CyclicBarrier(2);
                java.util.concurrent.Future<Integer> one = pool.submit(() -> {
                    together.await();
                    return choose(jorge, date, "LUNCH", pasta).andReturn().getResponse().getStatus();
                });
                java.util.concurrent.Future<Integer> other = pool.submit(() -> {
                    together.await();
                    return choose(partner, date, "LUNCH", chicken).andReturn().getResponse().getStatus();
                });
                assertThat(one.get()).as("first member, day " + day).isEqualTo(204);
                assertThat(other.get()).as("second member, day " + day).isEqualTo(204);
            }
        } finally {
            pool.shutdownNow();
        }

        // One meal per day, holding whichever choice came last.
        week(jorge, NEXT_MONDAY)
                .andExpect(jsonPath("$.meals", hasSize(7)))
                .andExpect(jsonPath("$.meals[*].origin", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("MANUAL"))));
    }

    @Test
    void aMealCanBeRemoved() throws Exception {
        choose(jorge, NEXT_MONDAY, "LUNCH", recipeId(PASTA)).andExpect(status().isNoContent());

        mvc.perform(as(jorge, delete(meal(NEXT_MONDAY, "LUNCH")))).andExpect(status().isNoContent());

        week(jorge, NEXT_MONDAY).andExpect(jsonPath("$.meals", hasSize(0)));
        mvc.perform(as(jorge, delete(meal(NEXT_MONDAY, "LUNCH"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEAL_NOT_FOUND"));
    }

    @Test
    void aMealCanBeMovedToAnEmptySlot() throws Exception {
        choose(jorge, NEXT_MONDAY, "LUNCH", recipeId(PASTA)).andExpect(status().isNoContent());

        move(jorge, NEXT_MONDAY, "LUNCH", NEXT_MONDAY.plusDays(3), "DINNER").andExpect(status().isNoContent());

        week(jorge, NEXT_MONDAY)
                .andExpect(jsonPath("$.meals", hasSize(1)))
                .andExpect(jsonPath("$.meals[0].date").value(NEXT_MONDAY.plusDays(3).toString()))
                .andExpect(jsonPath("$.meals[0].slot").value("DINNER"))
                .andExpect(jsonPath("$.meals[0].recipe.name").value(PASTA));
        // Nothing is left to move from where it was.
        move(jorge, NEXT_MONDAY, "LUNCH", NEXT_MONDAY.plusDays(1), "LUNCH")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEAL_NOT_FOUND"));
    }

    @Test
    void movingAMealOntoAnotherSwapsThem() throws Exception {
        choose(jorge, NEXT_MONDAY, "LUNCH", recipeId(PASTA)).andExpect(status().isNoContent());
        generate(jorge, NEXT_MONDAY, false).andExpect(status().isOk());
        String before = week(jorge, NEXT_MONDAY).andReturn().getResponse().getContentAsString();
        String tuesdayDinner = JsonPath.read(before, "$.meals[3].recipe.name");

        move(jorge, NEXT_MONDAY, "LUNCH", NEXT_MONDAY.plusDays(1), "DINNER").andExpect(status().isNoContent());

        // Each meal keeps who chose it.
        week(jorge, NEXT_MONDAY)
                .andExpect(jsonPath("$.meals", hasSize(14)))
                .andExpect(jsonPath("$.meals[0].recipe.name").value(tuesdayDinner))
                .andExpect(jsonPath("$.meals[0].origin").value("GENERATED"))
                .andExpect(jsonPath("$.meals[3].recipe.name").value(PASTA))
                .andExpect(jsonPath("$.meals[3].origin").value("MANUAL"));
    }

    @Test
    void generatingFillsTheWeekWithDifferentMainDishes() throws Exception {
        generate(jorge, NEXT_MONDAY.plusDays(4), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filled").value(14))
                .andExpect(jsonPath("$.unfilled").value(0));

        String plan = week(jorge, NEXT_MONDAY).andReturn().getResponse().getContentAsString();
        List<String> names = JsonPath.read(plan, "$.meals[*].recipe.name");
        List<String> origins = JsonPath.read(plan, "$.meals[*].origin");
        List<String> dates = JsonPath.read(plan, "$.meals[*].date");
        assertThat(names).hasSize(14).doesNotHaveDuplicates();
        assertThat(origins).containsOnly("GENERATED");
        assertThat(dates.get(0)).isEqualTo(NEXT_MONDAY.toString());
        assertThat(dates.get(13)).isEqualTo(NEXT_MONDAY.plusDays(6).toString());
        // Lunch and dinner are main dishes.
        assertThat(names).doesNotContainAnyElementsOf(recipeNames("BREAKFAST")).doesNotContainAnyElementsOf(recipeNames("DESSERT"));
        // Nothing was planned outside the week asked for.
        week(jorge, NEXT_MONDAY.plusDays(7)).andExpect(jsonPath("$.meals", hasSize(0)));
        assertThat(productEvents("meal_plan_created")).isEqualTo(1);
    }

    @Test
    void generatingUsesFirstWhatExpiresFirst() throws Exception {
        add("Pechuga de pollo", 300, "GRAM", "REFRIGERATOR", NEXT_MONDAY.plusDays(1).toString());
        add("Brócoli", 1, "UNIT", "REFRIGERATOR", NEXT_MONDAY.plusDays(1).toString());
        add("Limón", 2, "UNIT", "OTHER", null);
        add("Salmón", 300, "GRAM", "REFRIGERATOR", NEXT_MONDAY.plusDays(3).toString());

        generate(jorge, NEXT_MONDAY, false).andExpect(jsonPath("$.filled").value(14));

        String plan = week(jorge, NEXT_MONDAY).andReturn().getResponse().getContentAsString();
        List<String> names = JsonPath.read(plan, "$.meals[*].recipe.name");
        // The chicken has one day left on Monday: it is the first thing cooked. The salmon follows before its
        // date, and neither is left to expire.
        assertThat(names.get(0)).isEqualTo(CHICKEN);
        assertThat(ingredient(plan, 0, "Pechuga de pollo")).containsEntry("availability", "ENOUGH");
        assertThat(names.indexOf("Salmón al horno con patatas")).isBetween(1, 7);
        assertThat(JsonPath.<List<String>>read(plan, "$.unusedExpiring[*].name")).isEmpty();
    }

    @Test
    void generatingKeepsWhatMembersChose() throws Exception {
        choose(jorge, NEXT_MONDAY, "LUNCH", recipeId(TORRIJAS)).andExpect(status().isNoContent());
        choose(jorge, NEXT_MONDAY.plusDays(6), "DINNER", recipeId(PASTA)).andExpect(status().isNoContent());

        generate(jorge, NEXT_MONDAY, false)
                .andExpect(jsonPath("$.filled").value(12))
                .andExpect(jsonPath("$.unfilled").value(0));

        String plan = week(jorge, NEXT_MONDAY).andReturn().getResponse().getContentAsString();
        List<String> names = JsonPath.read(plan, "$.meals[*].recipe.name");
        assertThat(names).hasSize(14).doesNotHaveDuplicates();
        assertThat(names.get(0)).isEqualTo(TORRIJAS);
        assertThat(names.get(13)).isEqualTo(PASTA);
        assertThat(JsonPath.<String>read(plan, "$.meals[0].origin")).isEqualTo("MANUAL");
        assertThat(JsonPath.<String>read(plan, "$.meals[1].origin")).isEqualTo("GENERATED");
        // The week already had something planned: generating did not create the plan.
        assertThat(productEvents("meal_plan_created")).isEqualTo(1);
    }

    @Test
    void generatingAgainOnlyReplacesWhatWasGeneratedAndOnlyWhenAsked() throws Exception {
        choose(jorge, NEXT_MONDAY, "LUNCH", recipeId(TORRIJAS)).andExpect(status().isNoContent());
        generate(jorge, NEXT_MONDAY, false).andExpect(jsonPath("$.filled").value(13));
        mvc.perform(as(jorge, delete(meal(NEXT_MONDAY.plusDays(2), "DINNER")))).andExpect(status().isNoContent());
        String before = week(jorge, NEXT_MONDAY).andReturn().getResponse().getContentAsString();

        // Without replacing, only the gap is filled.
        generate(jorge, NEXT_MONDAY, false)
                .andExpect(jsonPath("$.filled").value(1))
                .andExpect(jsonPath("$.unfilled").value(0));
        List<String> ids = JsonPath.read(before, "$.meals[*].id");
        String filled = week(jorge, NEXT_MONDAY).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(filled, "$.meals[*].id")).hasSize(14).containsAll(ids);

        // With food to use up at home, replacing plans the week again around it; the member's choice stays.
        add("Salmón", 300, "GRAM", "REFRIGERATOR", NEXT_MONDAY.toString());
        generate(jorge, NEXT_MONDAY, true)
                .andExpect(jsonPath("$.filled").value(13))
                .andExpect(jsonPath("$.unfilled").value(0));
        week(jorge, NEXT_MONDAY)
                .andExpect(jsonPath("$.meals", hasSize(14)))
                .andExpect(jsonPath("$.meals[0].recipe.name").value(TORRIJAS))
                .andExpect(jsonPath("$.meals[0].origin").value("MANUAL"))
                .andExpect(jsonPath("$.meals[1].recipe.name").value("Salmón al horno con patatas"));
    }

    @Test
    void generatingNeverPlansWhatTheHouseholdDoesNotEat() throws Exception {
        mvc.perform(as(jorge, json(put("/api/v1/households/" + householdId + "/diet"), """
                        {"type": "VEGAN", "avoided": []}
                        """)))
                .andExpect(status().isOk());

        String generated =
                generate(jorge, NEXT_MONDAY, false).andReturn().getResponse().getContentAsString();

        int filled = JsonPath.read(generated, "$.filled");
        int unfilled = JsonPath.read(generated, "$.unfilled");
        assertThat(filled).isPositive();
        // There are not fourteen such recipes: meals are left empty rather than repeated without measure.
        assertThat(unfilled).isPositive();
        assertThat(filled + unfilled).isEqualTo(14);
        week(jorge, NEXT_MONDAY)
                .andExpect(jsonPath("$.meals", hasSize(filled)))
                .andExpect(jsonPath("$.meals[*].recipe.contains[*]", not(hasItem("MEAT"))))
                .andExpect(jsonPath("$.meals[*].recipe.contains[*]", not(hasItem("FISH"))))
                .andExpect(jsonPath("$.meals[*].recipe.contains[*]", not(hasItem("SHELLFISH"))))
                .andExpect(jsonPath("$.meals[*].recipe.contains[*]", not(hasItem("DAIRY"))))
                .andExpect(jsonPath("$.meals[*].recipe.contains[*]", not(hasItem("EGG"))));
    }

    @Test
    void generatingThisWeekOnlyPlansFromTodayOn() throws Exception {
        String generated = generate(jorge, TODAY, false).andReturn().getResponse().getContentAsString();

        int daysLeft = 8 - TODAY.getDayOfWeek().getValue();
        assertThat(JsonPath.<Integer>read(generated, "$.filled")).isEqualTo(2 * daysLeft);
        week(jorge, TODAY).andExpect(jsonPath("$.meals[*].date", not(hasItem(TODAY.minusDays(1).toString()))));
    }

    @Test
    void aWeekThatIsOverCannotBeGeneratedAndItsMealsAreHistory() throws Exception {
        generate(jorge, TODAY.minusDays(7), false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WEEK_IN_THE_PAST"));

        // What was eaten can still be written down, but nothing is said about ingredients.
        add("Pasta", 500, "GRAM", "OTHER", null);
        choose(jorge, TODAY.minusDays(7), "LUNCH", recipeId(PASTA)).andExpect(status().isNoContent());
        week(jorge, TODAY.minusDays(7))
                .andExpect(jsonPath("$.meals", hasSize(1)))
                .andExpect(jsonPath("$.meals[0].ingredients", hasSize(0)))
                .andExpect(jsonPath("$.unusedExpiring", hasSize(0)));
    }

    @Test
    void anyMemberSeesAndChangesThePlan() throws Exception {
        TestUser partner = register("Lucía");
        join(partner, invite(jorge, householdId)).andExpect(status().isOk());
        choose(jorge, NEXT_MONDAY, "LUNCH", recipeId(PASTA)).andExpect(status().isNoContent());

        week(partner, NEXT_MONDAY).andExpect(jsonPath("$.meals[0].recipe.name").value(PASTA));
        choose(partner, NEXT_MONDAY, "LUNCH", recipeId(CHICKEN)).andExpect(status().isNoContent());
        week(jorge, NEXT_MONDAY).andExpect(jsonPath("$.meals[0].recipe.name").value(CHICKEN));
    }

    @Test
    void thePlanOfAHouseholdIsOutOfReachForEveryoneElse() throws Exception {
        TestUser stranger = register("Desconocido");
        choose(jorge, NEXT_MONDAY, "LUNCH", recipeId(PASTA)).andExpect(status().isNoContent());

        week(stranger, NEXT_MONDAY).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("HOUSEHOLD_NOT_FOUND"));
        choose(stranger, NEXT_MONDAY, "DINNER", recipeId(CHICKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOUSEHOLD_NOT_FOUND"));
        mvc.perform(as(stranger, delete(meal(NEXT_MONDAY, "LUNCH"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOUSEHOLD_NOT_FOUND"));
        move(stranger, NEXT_MONDAY, "LUNCH", NEXT_MONDAY, "DINNER")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOUSEHOLD_NOT_FOUND"));
        generate(stranger, NEXT_MONDAY, true)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOUSEHOLD_NOT_FOUND"));
        mvc.perform(get(plan())).andExpect(status().isUnauthorized());

        // None of it left a trace.
        week(jorge, NEXT_MONDAY)
                .andExpect(jsonPath("$.meals", hasSize(1)))
                .andExpect(jsonPath("$.meals[0].recipe.name").value(PASTA));
    }

    @Test
    void whatIsAskedForMustMakeSense() throws Exception {
        choose(jorge, NEXT_MONDAY, "LUNCH", "00000000-0000-0000-0000-000000000000")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECIPE_NOT_FOUND"));
        choose(jorge, NEXT_MONDAY, "BRUNCH", recipeId(PASTA)).andExpect(status().isBadRequest());
        choose(jorge, TODAY.plusYears(3), "LUNCH", recipeId(PASTA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(as(jorge, json(put(meal(NEXT_MONDAY, "LUNCH")), "{}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(as(jorge, get(plan()).param("week", "next week"))).andExpect(status().isBadRequest());
        mvc.perform(as(jorge, get(plan()).param("lang", "fr")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(as(jorge, json(post(plan() + "/generate"), "{}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        week(jorge, NEXT_MONDAY).andExpect(jsonPath("$.meals", hasSize(0)));
    }

    @Test
    void everyChangeToThePlanReachesTheOtherMembers() throws Exception {
        TestUser partner = register("Lucía");
        join(partner, invite(jorge, householdId)).andExpect(status().isOk());
        MvcResult stream = mvc.perform(as(partner, get("/api/v1/households/" + householdId + "/events")))
                .andExpect(request().asyncStarted())
                .andReturn();

        choose(jorge, NEXT_MONDAY, "LUNCH", recipeId(PASTA)).andExpect(status().isNoContent());
        assertThat(changes(stream)).isEqualTo(1);
        move(jorge, NEXT_MONDAY, "LUNCH", NEXT_MONDAY, "DINNER").andExpect(status().isNoContent());
        assertThat(changes(stream)).isEqualTo(2);
        mvc.perform(as(jorge, delete(meal(NEXT_MONDAY, "DINNER")))).andExpect(status().isNoContent());
        assertThat(changes(stream)).isEqualTo(3);
        generate(jorge, NEXT_MONDAY, false).andExpect(status().isOk());
        assertThat(changes(stream)).isEqualTo(4);
        // Nothing left to fill: nothing changed, nothing is announced.
        generate(jorge, NEXT_MONDAY, false).andExpect(jsonPath("$.filled").value(0));
        assertThat(changes(stream)).isEqualTo(4);
        // The plan says which meals were cooked.
        mvc.perform(as(jorge, post("/api/v1/households/" + householdId + "/recipes/" + recipeId(PASTA) + "/cooked")))
                .andExpect(status().isNoContent());
        assertThat(changes(stream)).isEqualTo(5);
    }

    @Test
    void theSameHouseholdFoodAndDayGiveTheSamePlan() throws Exception {
        add("Pechuga de pollo", 300, "GRAM", "REFRIGERATOR", NEXT_MONDAY.plusDays(1).toString());
        generate(jorge, NEXT_MONDAY, false).andExpect(status().isOk());
        String first = week(jorge, NEXT_MONDAY).andReturn().getResponse().getContentAsString();

        // The same household, food and day give the same plan.
        generate(jorge, NEXT_MONDAY, true).andExpect(jsonPath("$.filled").value(14));
        String second = week(jorge, NEXT_MONDAY).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(second, "$.meals[*].recipe.name"))
                .isEqualTo(JsonPath.<List<String>>read(first, "$.meals[*].recipe.name"));
        assertThat(new HashSet<>(JsonPath.<List<String>>read(second, "$.meals[*].id")))
                .doesNotContainAnyElementsOf(JsonPath.<List<String>>read(first, "$.meals[*].id"));
    }

    private ResultActions week(TestUser user, LocalDate day) throws Exception {
        return mvc.perform(as(user, get(plan()).param("week", day.toString())));
    }

    private ResultActions choose(TestUser user, LocalDate date, String slot, String recipeId) throws Exception {
        return mvc.perform(as(user, json(put(meal(date, slot)), """
                {"recipeId": "%s"}
                """.formatted(recipeId))));
    }

    private ResultActions move(TestUser user, LocalDate date, String slot, LocalDate toDate, String toSlot)
            throws Exception {
        return mvc.perform(as(user, json(post(meal(date, slot) + "/move"), """
                {"date": "%s", "slot": "%s"}
                """.formatted(toDate, toSlot))));
    }

    private ResultActions generate(TestUser user, LocalDate week, boolean replaceGenerated) throws Exception {
        return mvc.perform(as(user, json(post(plan() + "/generate"), """
                {"week": "%s", "replaceGenerated": %s}
                """.formatted(week, replaceGenerated))));
    }

    /** An ingredient of a planned meal, by the position of the meal in the week. */
    private static java.util.Map<String, Object> ingredient(String plan, int meal, String name) {
        List<java.util.Map<String, Object>> found =
                JsonPath.read(plan, "$.meals[" + meal + "].ingredients[?(@.name == '" + name + "')]");
        assertThat(found).as("ingredient " + name + " of meal " + meal).hasSize(1);
        return found.get(0);
    }

    private static int changes(MvcResult stream) throws Exception {
        return stream.getResponse().getContentAsString().split("event:meal-plan-changed", -1).length - 1;
    }

    private long productEvents(String name) {
        return jdbc.queryForObject(
                "select count(*) from product_events where name = ? and household_id = ?::uuid",
                Long.class,
                name,
                householdId);
    }

    private String recipeId(String name) throws Exception {
        String list = mvc.perform(as(jorge, get("/api/v1/recipes").param("size", "100")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> ids = JsonPath.read(list, "$.items[?(@.name == '" + name + "')].id");
        assertThat(ids).as("recipe " + name).hasSize(1);
        return ids.get(0);
    }

    private List<String> recipeNames(String course) throws Exception {
        String list = mvc.perform(as(jorge, get("/api/v1/recipes").param("size", "100").param("course", course)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(list, "$.items[*].name");
    }

    /**
     * @param expirationDate {@code null} for food without a date; kept in the fridge, the application then
     *     estimates one
     */
    private void add(String food, double amount, String unit, String storage, String expirationDate) throws Exception {
        String foods = mvc.perform(as(jorge, get("/api/v1/foods").param("q", food)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> ids = JsonPath.read(foods, "$[?(@.name == '" + food + "')].id");
        assertThat(ids).as("food " + food).hasSize(1);
        mvc.perform(as(jorge, json(post(inventory()), """
                        {"foodId": "%s", "name": "%s", "quantity": {"amount": %s, "unit": "%s"},
                         "storageLocation": "%s", "expirationDate": %s}
                        """.formatted(
                                ids.get(0),
                                food,
                                amount,
                                unit,
                                storage,
                                expirationDate == null ? "null" : "\"" + expirationDate + "\""))))
                .andExpect(status().isCreated());
    }

    private String plan() {
        return "/api/v1/households/" + householdId + "/meal-plan";
    }

    private String meal(LocalDate date, String slot) {
        return plan() + "/" + date + "/" + slot;
    }

    private String inventory() {
        return "/api/v1/households/" + householdId + "/inventory";
    }
}
