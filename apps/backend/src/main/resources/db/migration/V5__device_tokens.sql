-- Where push notifications for a user go: one row per app installation that registered itself.
create table device_tokens (
    id           uuid          primary key,
    user_id      uuid          not null references users (id) on delete cascade,
    -- Issued by the push service; long, opaque, and replaced from time to time.
    token        varchar(4096) not null,
    platform     varchar(16)   not null,
    created_at   timestamptz   not null,
    last_seen_at timestamptz   not null,
    -- An installation belongs to whoever signed in on it last.
    constraint uq_device_tokens_token unique (token),
    constraint ck_device_tokens_platform check (platform in ('ANDROID', 'IOS', 'WEB'))
);
create index ix_device_tokens_user on device_tokens (user_id);
