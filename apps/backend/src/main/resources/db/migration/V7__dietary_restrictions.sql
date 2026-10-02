-- What each catalog food contains that someone may not eat: the basis of the dietary filter on recipes.
-- Processed foods are marked with what they commonly contain, erring on the side of excluding too much.
-- These values are hand-written, not taken from product labels: the filter is a help, never a guarantee.
create table food_traits (
    food_id uuid        not null references foods (id) on delete cascade,
    trait   varchar(16) not null,
    primary key (food_id, trait),
    constraint ck_food_traits_trait check (trait in
        ('MEAT', 'PORK', 'FISH', 'SHELLFISH', 'DAIRY', 'EGG', 'GLUTEN', 'NUTS', 'SOY', 'SESAME', 'ALCOHOL'))
);

insert into food_traits (food_id, trait)
select f.id, v.trait
from (values
    ('chicken-breast', 'MEAT'),
    ('chicken-thigh', 'MEAT'),
    ('minced-beef', 'MEAT'), ('minced-beef', 'PORK'),
    ('beef-steak', 'MEAT'),
    ('pork-loin', 'MEAT'), ('pork-loin', 'PORK'),
    ('serrano-ham', 'MEAT'), ('serrano-ham', 'PORK'),
    ('cooked-ham', 'MEAT'), ('cooked-ham', 'PORK'), ('cooked-ham', 'DAIRY'), ('cooked-ham', 'GLUTEN'), ('cooked-ham', 'SOY'),
    ('chorizo', 'MEAT'), ('chorizo', 'PORK'), ('chorizo', 'DAIRY'), ('chorizo', 'GLUTEN'), ('chorizo', 'SOY'),
    ('bacon', 'MEAT'), ('bacon', 'PORK'),
    ('salmon', 'FISH'),
    ('hake', 'FISH'),
    ('canned-tuna', 'FISH'),
    ('prawns', 'SHELLFISH'),
    ('milk', 'DAIRY'),
    ('yogurt', 'DAIRY'),
    ('butter', 'DAIRY'),
    ('cheese', 'DAIRY'),
    ('mozzarella', 'DAIRY'),
    ('cream', 'DAIRY'),
    ('egg', 'EGG'),
    ('bread', 'GLUTEN'), ('bread', 'SESAME'),
    ('sliced-bread', 'GLUTEN'), ('sliced-bread', 'DAIRY'), ('sliced-bread', 'SOY'), ('sliced-bread', 'SESAME'),
    ('pasta', 'GLUTEN'), ('pasta', 'EGG'),
    ('flour', 'GLUTEN'),
    ('soy-sauce', 'SOY'), ('soy-sauce', 'GLUTEN'),
    ('breakfast-cereal', 'GLUTEN'), ('breakfast-cereal', 'NUTS'), ('breakfast-cereal', 'DAIRY'),
    ('biscuits', 'GLUTEN'), ('biscuits', 'DAIRY'), ('biscuits', 'EGG'), ('biscuits', 'NUTS'), ('biscuits', 'SOY'),
    ('chocolate', 'DAIRY'), ('chocolate', 'NUTS'), ('chocolate', 'SOY'),
    ('frozen-pizza', 'MEAT'), ('frozen-pizza', 'PORK'), ('frozen-pizza', 'DAIRY'), ('frozen-pizza', 'GLUTEN'), ('frozen-pizza', 'SOY'),
    ('ice-cream', 'DAIRY'), ('ice-cream', 'EGG'), ('ice-cream', 'NUTS'), ('ice-cream', 'SOY'),
    ('beer', 'GLUTEN'), ('beer', 'ALCOHOL'),
    ('wine', 'ALCOHOL'),
    ('hummus', 'SESAME'),
    ('gazpacho', 'GLUTEN'),
    ('spanish-omelette', 'EGG')
) as v (slug, trait)
join foods f on f.slug = v.slug;

-- A typo in a slug above would silently drop a row from the join; fail the migration instead.
do $$
begin
    if (select count(*) from food_traits) <> 69 then
        raise exception 'Expected 69 food traits, found %', (select count(*) from food_traits);
    end if;
end $$;

-- What is not cooked in a household. It belongs to the household, not to a person: members share a kitchen,
-- and every member can see and change it. Households without a row have no restrictions.
create table household_diets (
    household_id uuid         primary key references households (id) on delete cascade,
    diet         varchar(16)  not null,
    avoided      varchar(200) not null,
    updated_by   uuid         references users (id) on delete set null,
    updated_at   timestamptz  not null,
    constraint ck_household_diets_diet check (diet in ('NONE', 'VEGETARIAN', 'VEGAN'))
);
