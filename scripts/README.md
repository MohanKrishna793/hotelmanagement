# Database scripts

## Deleting hotel data and re-seeding with India-wide data

1. **Delete existing hotel-related data** (keeps users/roles so you can still log in):
   - Open your PostgreSQL client (Supabase SQL Editor, psql, DBeaver, etc.) and connect to your database.
   - Run the script: `delete-hotel-data.sql`
   - Or run these commands in order:
     ```sql
     TRUNCATE TABLE hotel_tourist_places RESTART IDENTITY CASCADE;
     TRUNCATE TABLE bookings RESTART IDENTITY CASCADE;
     TRUNCATE TABLE rooms RESTART IDENTITY CASCADE;
     TRUNCATE TABLE hotels RESTART IDENTITY CASCADE;
     TRUNCATE TABLE tourist_places RESTART IDENTITY CASCADE;
     TRUNCATE TABLE destinations RESTART IDENTITY CASCADE;
     ```

2. **Restart the application.**  
   On startup, `IndiaHotelDataSeeder` runs only when there are no hotels. It will create:
   - 20–40 hotels per state/UT (all Indian states + Delhi, J&K, Ladakh, Puducherry, Chandigarh)
   - Each hotel has amenities and 2–4 room types (DELUXE, SUITE, PREMIUM, LUXURY, AC, BUDGET)
   - Nearby tourist places per state, linked to hotels (shown as **Nearby tourist places** on the customer page)

No need to run any Java command; just restart the app after truncating the tables.
