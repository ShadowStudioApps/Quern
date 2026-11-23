-- Quern account sync schema (Supabase / PostgreSQL)
-- Run once in the Supabase SQL Editor of the Quern project.
-- Safe to run again: every statement is idempotent.
--
-- Data model: one row per (user, section). Sections are written independently so two TVs
-- editing different things (e.g. addons on one, watch progress on another) never overwrite
-- each other. Sections holding secrets (Debrid keys, server passwords, tokens) are encrypted
-- on the device with a key derived from the user's password before upload: the server only
-- ever stores ciphertext for them.

create table if not exists public.quern_sync_sections (
  user_id     uuid        not null references auth.users(id) on delete cascade,
  section     text        not null check (section ~ '^[a-z_]{1,32}$'),
  payload     text        not null check (length(payload) <= 2000000),  -- ~2 MB per section
  encrypted   boolean     not null default false,
  revision    bigint      not null default 1,
  updated_by  text        not null default '',                          -- random device id, not personal
  updated_at  timestamptz not null default now(),
  primary key (user_id, section)
);

alter table public.quern_sync_sections enable row level security;

-- Expose the table to the Data API for signed-in users only (projects created with
-- "Grant API access to new tables automatically" turned off need this explicitly).
revoke all on public.quern_sync_sections from anon;
grant select, insert, update, delete on public.quern_sync_sections to authenticated;

drop policy if exists "own sections: select" on public.quern_sync_sections;
drop policy if exists "own sections: insert" on public.quern_sync_sections;
drop policy if exists "own sections: update" on public.quern_sync_sections;
drop policy if exists "own sections: delete" on public.quern_sync_sections;

create policy "own sections: select" on public.quern_sync_sections
  for select using (auth.uid() = user_id);
create policy "own sections: insert" on public.quern_sync_sections
  for insert with check (auth.uid() = user_id);
create policy "own sections: update" on public.quern_sync_sections
  for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own sections: delete" on public.quern_sync_sections
  for delete using (auth.uid() = user_id);

-- Server-side revision counter and timestamp, so devices can't fake them.
create or replace function public.quern_touch_section()
returns trigger language plpgsql as $$
begin
  new.updated_at := now();
  if tg_op = 'UPDATE' then
    new.revision := old.revision + 1;
  else
    new.revision := 1;
  end if;
  return new;
end $$;

drop trigger if exists quern_touch_section on public.quern_sync_sections;
create trigger quern_touch_section
  before insert or update on public.quern_sync_sections
  for each row execute function public.quern_touch_section();

-- Lets a signed-in user delete their own account and all synced data from inside the app.
create or replace function public.quern_delete_my_account()
returns void language plpgsql security definer set search_path = public as $$
begin
  delete from auth.users where id = auth.uid();
end $$;

revoke all on function public.quern_delete_my_account() from public, anon;
grant execute on function public.quern_delete_my_account() to authenticated;

-- Legacy table from the first sync version (one opaque payload per user). Kept so older
-- app versions keep working; new versions migrate from it once and then ignore it.
create table if not exists public.quern_sync (
  user_id    uuid primary key references auth.users(id) on delete cascade,
  payload    text not null,
  updated_at timestamptz not null default now()
);
alter table public.quern_sync enable row level security;
revoke all on public.quern_sync from anon;
grant select, insert, update on public.quern_sync to authenticated;
drop policy if exists "Users can read own sync state" on public.quern_sync;
drop policy if exists "Users can upsert own sync state" on public.quern_sync;
drop policy if exists "Users can update own sync state" on public.quern_sync;
create policy "Users can read own sync state" on public.quern_sync
  for select using (auth.uid() = user_id);
create policy "Users can upsert own sync state" on public.quern_sync
  for insert with check (auth.uid() = user_id);
create policy "Users can update own sync state" on public.quern_sync
  for update using (auth.uid() = user_id);
