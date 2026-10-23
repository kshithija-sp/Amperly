-- ============================================================================
--  Amperly — link seeded users to Supabase Auth accounts
-- ============================================================================
--  Run this AFTER schema.sql and pilot_seed.sql, and after creating the four
--  accounts in Dashboard -> Authentication -> Users.
--
--  You usually do NOT need this file any more. schema.sql installs the
--  on_auth_user_created trigger, which links a seeded profile to its Auth account
--  by email the moment the account is created, and its backfill does the same for
--  accounts that already existed. Run this file when the seeded users were added
--  to public.users *after* their Auth accounts already existed, or when you want
--  to confirm the link by hand.
--
--  The app identifies the signed-in user by matching auth.uid() to
--  users."authUserId". Until that column is filled in, a signed-in user is
--  anonymous as far as Row Level Security is concerned and sees nothing.
--
--  Safe to re-run.
-- ============================================================================

-- ---------------------------------------------------------------------------
--  0. Clear the way for a seeded row to take the link.
--     If the Auth account was created before pilot_seed.sql ran, the trigger
--     will already have given it a profile row of its own. That duplicate has to
--     go first, because "authUserId" is UNIQUE and the seeded row — the one that
--     owns the demo history — cannot hold the link while the duplicate does.
-- ---------------------------------------------------------------------------
delete from public.users u
 using auth.users a
 where u."authUserId" = a.id
   and u.id <> a.id::text
   and lower(u.email) = lower(a.email)
   and exists (select 1
                 from public.users seeded
                where seeded.id <> u.id
                  and seeded."authUserId" is null
                  and lower(seeded.email) = lower(a.email));

-- ---------------------------------------------------------------------------
--  1. Link every seeded row whose email has an Auth account.
-- ---------------------------------------------------------------------------
update public.users u
   set "authUserId" = a.id
  from auth.users a
 where lower(a.email) = lower(u.email)
   and u."authUserId" is distinct from a.id;

-- ---------------------------------------------------------------------------
--  2. Check the result. Every seeded user should now show linked = true, except
--     any you have not created an Auth account for yet.
-- ---------------------------------------------------------------------------
select u.id,
       u.email,
       u.role,
       u."authUserId" is not null as linked
from public.users u
order by u.id;

-- ---------------------------------------------------------------------------
--  3. Every Auth account must be linked. This is the check that matters: a row
--     here with a null app_user_id is an account the app cannot act as.
-- ---------------------------------------------------------------------------
select a.id                             as auth_user_id,
       a.email,
       a.email_confirmed_at is not null as email_confirmed,
       u.id                             as app_user_id,
       u.role                           as app_role
from auth.users a
left join public.users u on u."authUserId" = a.id
order by a.created_at;

-- ---------------------------------------------------------------------------
--  If you would rather link by hand, use the user's UUID from
--  Dashboard -> Authentication -> Users:
--
--    update public.users
--       set "authUserId" = '<paste-auth-user-uuid-here>'
--     where id = 'user_student_1';
-- ---------------------------------------------------------------------------
