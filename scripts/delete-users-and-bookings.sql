-- Run this in PostgreSQL (Supabase SQL Editor, psql, or any client) to delete all users and all bookings.
-- Order matters: bookings first, then user_roles (join table), then users.
-- Roles are kept so you can still create an admin user (e.g. via app startup DataInitializer).

DELETE FROM bookings;
DELETE FROM user_roles;
DELETE FROM users;

-- Optional: delete guests (they are created when customers book; no FK from users to guests)
-- DELETE FROM guests;
