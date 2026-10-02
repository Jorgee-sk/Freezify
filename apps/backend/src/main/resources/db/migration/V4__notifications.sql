-- How each user wants to be told about food that is about to expire. Users without a row get the defaults.
create table notification_preferences (
    user_id           uuid         primary key references users (id) on delete cascade,
    expiration_alerts boolean      not null,
    delivery_hour     integer      not null,
    frequency         varchar(16)  not null,
    threshold         varchar(16)  not null,
    muted_categories  varchar(400) not null,
    updated_at        timestamptz  not null,
    constraint ck_notification_preferences_hour check (delivery_hour between 0 and 23),
    constraint ck_notification_preferences_frequency check (frequency in ('DAILY', 'EVERY_THREE_DAYS', 'WEEKLY')),
    constraint ck_notification_preferences_threshold check (threshold in ('TODAY', 'URGENT', 'SOON'))
);

create table notifications (
    id           uuid        primary key,
    user_id      uuid        not null references users (id) on delete cascade,
    household_id uuid        not null references households (id) on delete cascade,
    type         varchar(32) not null,
    -- The calendar day the notification is about.
    day          date        not null,
    -- Every item that needed attention that day; notification_items keeps only the most pressing ones.
    item_count   integer     not null,
    created_at   timestamptz not null,
    read_at      timestamptz,
    -- At most one notification of each type per household and day, whatever else goes wrong.
    constraint uq_notifications_per_day unique (user_id, household_id, type, day)
);
create index ix_notifications_user on notifications (user_id, created_at desc);

-- Name and date are copied so that a notification still reads the same after the item is edited or deleted.
create table notification_items (
    notification_id uuid         not null references notifications (id) on delete cascade,
    position        integer      not null,
    name            varchar(120) not null,
    expiration_date date         not null,
    -- Estimated dates must be shown as estimates, never as the date printed on the package.
    estimated       boolean      not null,
    primary key (notification_id, position)
);

-- The most pressing level each user has already been told about for each item, so that nobody is told twice.
create table notification_item_alerts (
    id           uuid        primary key,
    user_id      uuid        not null references users (id) on delete cascade,
    household_id uuid        not null references households (id) on delete cascade,
    item_id      uuid        not null references food_items (id) on delete cascade,
    priority     varchar(16) not null,
    constraint uq_notification_item_alerts unique (user_id, item_id)
);
create index ix_notification_item_alerts_household on notification_item_alerts (household_id);
create index ix_notification_item_alerts_item on notification_item_alerts (item_id);

-- The last day each user's food was checked, so that it happens once a day.
create table notification_checks (
    user_id    uuid primary key references users (id) on delete cascade,
    checked_on date not null
);
