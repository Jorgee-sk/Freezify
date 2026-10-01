create table users (
    id            uuid         primary key,
    email         varchar(320) not null,
    password_hash varchar(255) not null,
    display_name  varchar(80)  not null,
    locale        varchar(10)  not null,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    constraint uq_users_email unique (email)
);

create table refresh_tokens (
    id         uuid        primary key,
    user_id    uuid        not null references users (id) on delete cascade,
    family_id  uuid        not null,
    token_hash char(64)    not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null,
    constraint uq_refresh_tokens_hash unique (token_hash)
);
create index ix_refresh_tokens_family on refresh_tokens (family_id);
create index ix_refresh_tokens_user on refresh_tokens (user_id);

create table households (
    id         uuid         primary key,
    name       varchar(80)  not null,
    created_at timestamptz  not null,
    updated_at timestamptz  not null
);

create table household_members (
    id           uuid        primary key,
    household_id uuid        not null references households (id) on delete cascade,
    user_id      uuid        not null references users (id) on delete cascade,
    role         varchar(16) not null,
    joined_at    timestamptz not null,
    constraint uq_household_members unique (household_id, user_id),
    constraint ck_household_members_role check (role in ('OWNER', 'MEMBER'))
);
create index ix_household_members_user on household_members (user_id);

create table household_invitations (
    id           uuid        primary key,
    household_id uuid        not null references households (id) on delete cascade,
    code         varchar(16) not null,
    created_by   uuid        not null references users (id) on delete cascade,
    expires_at   timestamptz not null,
    created_at   timestamptz not null,
    constraint uq_household_invitations_code unique (code)
);
create index ix_household_invitations_household on household_invitations (household_id);
