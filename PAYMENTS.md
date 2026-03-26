# Payments (Stripe Test Mode – No Real Money)

This project uses **Stripe in Test Mode** for the “Pay now” option. **No real money is charged**; it is ideal for semester/demo use. **No PAN or KYC required** – sign up with just your email.

## Quick start (Test Mode)

1. **Sign up** at [Stripe](https://dashboard.stripe.com/register) (email only; no business details or PAN needed).
2. Open the **Dashboard** and ensure you’re in **Test mode** (toggle in the top right).
3. Go to **Developers → API keys** and copy the **Publishable key** and **Secret key** (use the “Reveal” link for the secret).
4. In your project, open `src/main/resources/application.properties` and set:
   ```properties
   app.stripe.secret-key=sk_test_xxxxxxxxxxxx
   app.stripe.publishable-key=pk_test_xxxxxxxxxxxx
   app.stripe.enabled=true
   app.stripe.webhook-secret=whsec_xxxxxxxxxxxx
   app.base-url=http://localhost:8080
   ```
   Or use environment variables so you don’t commit secrets:
   - `STRIPE_SECRET_KEY=sk_test_...`
   - `STRIPE_PUBLISHABLE_KEY=pk_test_...`
   - `STRIPE_ENABLED=true`
   - `STRIPE_WEBHOOK_SECRET=whsec_...`
   - `APP_BASE_URL=http://localhost:8080`
5. Restart the application. “Pay now” will then redirect to Stripe Checkout in test mode.

## Test cards (Stripe)

- **Card number:** `4242 4242 4242 4242`
- **CVV:** any 3 digits (e.g. `123`)
- **Expiry:** any future date (e.g. `12/30`)

No real payment is processed; the gateway simulates success.

## Flow

- **Pay at hotel:** No gateway; booking is confirmed and payment status is set to “Pay at hotel”.
- **Pay now:** The backend **does not insert a booking or lock a room row** before Stripe. It only creates a Checkout Session (amount + room/dates/guest are stored in **Stripe session metadata**). The browser redirects to Stripe immediately. After payment, the booking row is created when:
  - the user returns to `/index.html` with `session_id` and the app calls **verify**, or
  - the **webhook** `checkout.session.completed` runs (signature-verified).
  The server recomputes the price and checks `amount_total` vs metadata to prevent tampering; `guest_email` in metadata must match the logged-in user on verify.
- **Webhook backup:** Same materialization logic as verify (idempotent by Stripe session id).

## Webhook (recommended)

Run Stripe CLI locally:

```bash
stripe listen --forward-to http://localhost:8080/api/payments/stripe/webhook
```

Copy the shown signing secret (`whsec_...`) into `app.stripe.webhook-secret`.

## Production

For real payments, use Stripe Live keys and complete Stripe account verification. Set `app.base-url` to your production URL.

## Troubleshooting

### `bookings_payment_method_check` / STRIPE + PENDING

If the database still has a check constraint from an older version of the app, you may see an integrity error mentioning `STRIPE` and `PENDING`. Current code **does not** insert that combination (bookings are created after Stripe payment as `STRIPE` + `PAID`).

1. **Redeploy** the latest backend JAR so no old code path runs.
2. Run `scripts/fix-bookings-payment-constraint.sql` once on your PostgreSQL database (e.g. Supabase → SQL editor) to drop the obsolete constraint.

### Slow booking / “switching to Razorpay”

Slowness is usually **network + database** (e.g. remote Supabase round-trips, row locks), not Stripe vs Razorpay. Razorpay would still call your API and hit the same DB. The deferred Stripe flow avoids holding locks **before** payment; keep the DB close to the app or use connection pooling for best UX.

Configuration is now via **environment variables** — see `env.example`. Do not commit API keys or DB passwords to git.
