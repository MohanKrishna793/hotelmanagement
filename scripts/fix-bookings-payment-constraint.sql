-- Run once on PostgreSQL (e.g. Supabase SQL editor) if you see:
--   ERROR: new row for relation "bookings" violates check constraint "bookings_payment_method_check"
--   Detail: ... STRIPE, PENDING ...
--
-- Older app versions inserted STRIPE + PENDING before payment; the DB constraint may forbid that.
-- Current code only creates Stripe bookings after payment (STRIPE + PAID), so this constraint is safe to drop.

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_payment_method_check;
