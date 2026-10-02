-- The date the user gave is kept apart from the date that applies, so that estimating never overwrites it.
alter table food_items add column user_expiration_date date;
update food_items set user_expiration_date = expiration_date where expiration_source = 'USER';

-- How long food keeps, by storage place. A rule is for one catalog food or for a whole category; the food's
-- own rule wins. Days are deliberately conservative: an estimate that is too short wastes a look in the
-- fridge, one that is too long can make someone ill.
--   unopened_days: from purchase. Null when only the date on the package can tell (packaged milk).
--   opened_days:   from opening. Null when opening changes nothing.
create table shelf_life_rules (
    id               uuid        primary key default gen_random_uuid(),
    food_id          uuid        references foods (id) on delete cascade,
    category         varchar(16),
    storage_location varchar(16) not null,
    unopened_days    integer,
    opened_days      integer,
    constraint ck_shelf_life_subject check ((food_id is null) <> (category is null)),
    constraint ck_shelf_life_days check (
        (unopened_days is not null or opened_days is not null)
        and (unopened_days is null or unopened_days > 0)
        and (opened_days is null or opened_days > 0)),
    constraint uq_shelf_life_food unique (food_id, storage_location),
    constraint uq_shelf_life_category unique (category, storage_location)
);

insert into shelf_life_rules (category, storage_location, unopened_days, opened_days) values
    ('VEGETABLES', 'REFRIGERATOR', 7,    null),
    ('FRUITS',     'REFRIGERATOR', 7,    null),
    ('MEAT',       'REFRIGERATOR', 2,    null),
    ('FISH',       'REFRIGERATOR', 1,    null),
    ('DAIRY',      'REFRIGERATOR', null, 5),
    ('EGGS',       'REFRIGERATOR', 21,   null),
    ('BAKERY',     'REFRIGERATOR', 5,    null),
    ('PREPARED',   'REFRIGERATOR', 3,    3),
    ('BEVERAGES',  'REFRIGERATOR', null, 5),
    ('VEGETABLES', 'FREEZER',      240,  null),
    ('FRUITS',     'FREEZER',      240,  null),
    ('MEAT',       'FREEZER',      90,   null),
    ('FISH',       'FREEZER',      90,   null),
    ('BAKERY',     'FREEZER',      90,   null),
    ('PREPARED',   'FREEZER',      90,   null),
    ('FROZEN',     'FREEZER',      180,  null),
    ('VEGETABLES', 'PANTRY',       5,    null),
    ('FRUITS',     'PANTRY',       5,    null),
    ('BAKERY',     'PANTRY',       3,    null),
    ('EGGS',       'PANTRY',       14,   null);

insert into shelf_life_rules (food_id, storage_location, unopened_days, opened_days)
select foods.id, rule.storage_location, rule.unopened_days, rule.opened_days
from (values
    ('minced-beef',    'REFRIGERATOR', 1,           null::integer),
    ('beef-steak',     'REFRIGERATOR', 3,           null),
    ('pork-loin',      'REFRIGERATOR', 3,           null),
    ('serrano-ham',    'REFRIGERATOR', null,        7),
    ('cooked-ham',     'REFRIGERATOR', null,        4),
    ('chorizo',        'REFRIGERATOR', null,        14),
    ('bacon',          'REFRIGERATOR', null,        5),
    ('canned-tuna',    'REFRIGERATOR', null,        2),
    ('milk',           'REFRIGERATOR', null,        3),
    ('butter',         'REFRIGERATOR', null,        30),
    ('cheese',         'REFRIGERATOR', null,        14),
    ('mozzarella',     'REFRIGERATOR', null,        3),
    ('cream',          'REFRIGERATOR', null,        3),
    ('lettuce',        'REFRIGERATOR', 5,           null),
    ('spinach',        'REFRIGERATOR', 4,           null),
    ('mushroom',       'REFRIGERATOR', 4,           null),
    ('broccoli',       'REFRIGERATOR', 5,           null),
    ('green-beans',    'REFRIGERATOR', 5,           null),
    ('carrot',         'REFRIGERATOR', 21,          null),
    ('leek',           'REFRIGERATOR', 10,          null),
    ('strawberry',     'REFRIGERATOR', 3,           null),
    ('peach',          'REFRIGERATOR', 5,           null),
    ('apple',          'REFRIGERATOR', 28,          null),
    ('orange',         'REFRIGERATOR', 21,          null),
    ('lemon',          'REFRIGERATOR', 21,          null),
    ('hummus',         'REFRIGERATOR', null,        4),
    ('gazpacho',       'REFRIGERATOR', null,        3),
    ('orange-juice',   'REFRIGERATOR', null,        4),
    ('fried-tomato',   'REFRIGERATOR', null,        5),
    ('crushed-tomato', 'REFRIGERATOR', null,        4),
    ('potato',         'PANTRY',       30,          null),
    ('onion',          'PANTRY',       30,          null),
    ('garlic',         'PANTRY',       60,          null),
    ('pumpkin',        'PANTRY',       30,          null),
    ('avocado',        'PANTRY',       4,           null),
    ('bread',          'PANTRY',       2,           null),
    ('sliced-bread',   'PANTRY',       7,           null)
) as rule (slug, storage_location, unopened_days, opened_days)
join foods on foods.slug = rule.slug;
