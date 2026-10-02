-- What a household plans to eat: one recipe per day and meal. A "meal plan" is a week of these rows; the week
-- itself holds no data of its own, so it has no table.
create table meal_plan_entries (
    id           uuid        primary key,
    household_id uuid        not null references households (id) on delete cascade,
    planned_on   date        not null,
    slot         varchar(16) not null,
    recipe_id    uuid        not null references recipes (id),
    -- Who chose the recipe: a member (MANUAL) or the generator (GENERATED). Generating again may replace what
    -- the generator chose, never what a member chose.
    origin       varchar(16) not null,
    created_by   uuid        references users (id) on delete set null,
    created_at   timestamptz not null,
    updated_at   timestamptz not null,
    version      bigint      not null,
    constraint uq_meal_plan_entries_slot unique (household_id, planned_on, slot),
    constraint ck_meal_plan_entries_slot check (slot in ('LUNCH', 'DINNER')),
    constraint ck_meal_plan_entries_origin check (origin in ('MANUAL', 'GENERATED'))
);
