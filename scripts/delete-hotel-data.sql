-- Run this in your PostgreSQL client (psql, Supabase SQL Editor, or any client connected to your DB)
-- to delete all hotel-related data so you can re-seed with India-wide data.
-- Keeps users and roles intact so you can still log in as admin.

-- Order matters for foreign keys. Using CASCADE so dependent rows are removed.
TRUNCATE TABLE hotel_tourist_places RESTART IDENTITY CASCADE;
TRUNCATE TABLE bookings RESTART IDENTITY CASCADE;
TRUNCATE TABLE rooms RESTART IDENTITY CASCADE;
TRUNCATE TABLE hotels RESTART IDENTITY CASCADE;
TRUNCATE TABLE tourist_places RESTART IDENTITY CASCADE;
TRUNCATE TABLE destinations RESTART IDENTITY CASCADE;

-- Optional: clear guests if you want a clean slate (bookings are already deleted)
-- TRUNCATE TABLE guests RESTART IDENTITY CASCADE;
