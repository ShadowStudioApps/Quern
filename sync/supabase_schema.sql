-- Quern Multi-Screen Cloud Sync Schema (Supabase / PostgreSQL)
-- Run this once in your Supabase SQL Editor to enable free multi-TV & phone account sync.

create table if not exists public.quern_sync (
  user_id uuid primary key references auth.users(id) on delete cascade,
  payload text not null,
  updated_at timestamptz not null default now()
);

alter table public.quern_sync enable row level security;

create policy "Users can read own sync state"
  on public.quern_sync for select
  using (auth.uid() = user_id);

create policy "Users can upsert own sync state"
  on public.quern_sync for insert
  with check (auth.uid() = user_id);

create policy "Users can update own sync state"
  on public.quern_sync for update
  using (auth.uid() = user_id);
