-- Calls to the language model each person caused per day, to keep costs and free quotas in hand. Only the last
-- week is kept.
create table ai_usage (
    user_id uuid    not null references users (id) on delete cascade,
    day     date    not null,
    calls   integer not null,
    primary key (user_id, day)
);
create index ix_ai_usage_day on ai_usage (day);
