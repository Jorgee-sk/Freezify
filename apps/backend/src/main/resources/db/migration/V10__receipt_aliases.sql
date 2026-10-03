-- How receipts name catalog foods. Rows without a household are shared hints written by hand below; rows of a
-- household are learned from what its members confirmed when reviewing a scanned receipt, and win over the rest.
-- The text is the receipt's product words, lower case, without accents, sizes or prices ("pech pollo").
create table receipt_aliases (
    id           uuid         primary key default gen_random_uuid(),
    household_id uuid         references households (id) on delete cascade,
    text         varchar(120) not null,
    food_id      uuid         not null references foods (id),
    updated_at   timestamptz  not null default now()
);
create unique index uq_receipt_aliases_household_text on receipt_aliases (household_id, text)
    where household_id is not null;
create unique index uq_receipt_aliases_shared_text on receipt_aliases (text)
    where household_id is null;

-- Abbreviations and other names that Spanish receipts use and that the catalog names do not contain (plurals
-- and words such as "de" are already taken care of when comparing). They only suggest a food: every scanned
-- line is reviewed by a person before it reaches the inventory.
insert into receipt_aliases (text, food_id)
select alias.text, foods.id
from (values
    ('lech',         'milk'),
    ('yog',          'yogurt'),
    ('pech pollo',   'chicken-breast'),
    ('filete pollo', 'chicken-breast'),
    ('contramuslo',  'chicken-thigh'),
    ('picada',       'minced-beef'),
    ('burger meat',  'minced-beef'),
    ('lomo',         'pork-loin'),
    ('serrano',      'serrano-ham'),
    ('jamon york',   'cooked-ham'),
    ('york',         'cooked-ham'),
    ('atun',         'canned-tuna'),
    ('langostino',   'prawns'),
    ('mantq',        'butter'),
    ('nata',         'cream'),
    ('barra',        'bread'),
    ('baguette',     'bread'),
    ('molde',        'sliced-bread'),
    ('spaghetti',    'pasta'),
    ('espagueti',    'pasta'),
    ('macarron',     'pasta'),
    ('tallarin',     'pasta'),
    ('aove',         'olive-oil'),
    ('tomate trit',  'crushed-tomato'),
    ('papas',        'potato'),
    ('guisantes',    'frozen-peas'),
    ('cerv',         'beer'),
    ('zumo nar',     'orange-juice')
) as alias (text, slug)
join foods on foods.slug = alias.slug;
