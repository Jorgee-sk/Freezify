-- What a household has to buy: one shared list per household, so the list itself holds no data and has no table.
-- A line is either a catalog food (its name is read from the catalog, in each member's language) or free text.
create table shopping_list_items (
    id           uuid          primary key,
    household_id uuid          not null references households (id) on delete cascade,
    food_id      uuid          references foods (id),
    name         varchar(120),
    category     varchar(16)   not null,
    -- Both or neither: "bread" can go on the list without saying how much.
    amount       numeric(12,3),
    unit         varchar(16),
    -- PLAN lines are what the meal plan lacks and are recomputed from it; MANUAL lines are only changed by people.
    origin       varchar(16)   not null,
    -- For PLAN lines, the day of the first planned meal that needs it.
    needed_on    date,
    checked      boolean       not null,
    checked_by   uuid          references users (id) on delete set null,
    checked_at   timestamptz,
    created_by   uuid          references users (id) on delete set null,
    created_at   timestamptz   not null,
    updated_at   timestamptz   not null,
    version      bigint        not null,
    constraint ck_shopping_list_items_what check (food_id is not null or name is not null),
    constraint ck_shopping_list_items_quantity check ((amount is null) = (unit is null)),
    constraint ck_shopping_list_items_amount check (amount is null or amount > 0),
    constraint ck_shopping_list_items_origin check (origin in ('MANUAL', 'PLAN'))
);
create index ix_shopping_list_items_household on shopping_list_items (household_id);
