# SmartHotel Management - Final Project Report

## 1) Project Overview

SmartHotel Management is a full-stack hotel booking platform with:

- Customer booking flow
- Admin operations dashboard
- JWT-based authentication and role-based access control
- Stripe payment integration (test mode supported)
- Email notifications (Brevo API + SMTP fallback)
- Progressive Web App (PWA) support
- Public cloud deployment on Railway with PostgreSQL

This report captures what has been implemented, fixed, and deployed so far.

---

## 2) Core Goals Achieved

- Built and deployed a working end-to-end hotel booking platform
- Added mobile-friendly install experience (PWA)
- Implemented customer and admin panels
- Implemented booking lifecycle (create, list, history, cancel)
- Added payment flow through Stripe Checkout + webhook verification
- Implemented transactional email notifications (registration, login, booking, cancellation)
- Removed Twilio/WhatsApp integration completely from backend, frontend, configs, and docs
- Fixed repeated production issues around wrong redirect domains (`localhost` vs Railway)
- Stabilized deployment with Railway environment variable based configuration
- Finalized branding/logo assets across web surfaces

---

## 3) Architecture Summary

## Backend

- Framework: Spring Boot `3.2.2`
- Java version: `17`
- Layers: Controller -> Service -> Repository
- Persistence: Spring Data JPA + Hibernate
- Database: PostgreSQL
- Auth: Spring Security + JWT (`jjwt`)
- Payments: Stripe (`stripe-java`)
- Emails: Brevo API + Spring Mail SMTP fallback
- Ops: Spring Boot Actuator (`health`, `info`, `metrics`)

## Frontend

- Static web app served from Spring Boot (`src/main/resources/static`)
- Pages: `index.html`, `admin.html`, `my-bookings.html`
- Styling: `styles.css`
- Logic: `app.js`, `admin.js`
- PWA: `manifest.webmanifest`, `sw.js`, `icon.svg`

## Hosting / Infra

- App hosting: Railway
- DB hosting: Railway PostgreSQL
- VCS: GitHub repository

---

## 4) Implemented Functional Modules

## Authentication & Authorization

- User registration and login
- JWT issuance and validation
- Session persistence hardened on frontend
- Role-aware flows (customer/admin/staff-manager routes)
- Case-insensitive email handling for reliability

## Hotel Discovery and Booking

- Search/filter hotels by state, city, dates, class/type, price
- Dynamic room listing and booking UI
- Booking creation with:
  - `PAY_AT_HOTEL`
  - `STRIPE` (if enabled)
- Idempotency key support for safer booking requests
- Booking history and cancellation support
- Cancellation policy endpoint and controls

## Payment Processing (Stripe)

- Checkout session creation with metadata
- Post-payment verification endpoint
- Webhook-based sync path for paid sessions
- Payment status mapping and persistence
- Success/cancel URL generation with dynamic base URL handling

## Notifications

- Welcome email (registration)
- Login notification email
- Booking confirmation email
- Booking cancellation email (restyled to premium format)
- Brevo HTTP API as primary sender when configured
- SMTP fallback if Brevo is unavailable/not configured

## Admin Operations

- Dashboard stats and reports
- Destination, hotel, room, guest management
- Staff/manager management
- Booking management and status updates
- Audit logs and reporting endpoints

## PWA / Mobile Experience

- Installable web app support
- Manifest and icon setup
- Service worker caching strategy
- Install button behavior and UX improvements

---

## 5) Branding and UI Assets Finalized

- New app icon:
  - `src/main/resources/static/icon.svg`
- New reusable wordmark:
  - `src/main/resources/static/logo-wordmark.svg`
- Header branding applied to:
  - `index.html`
  - `admin.html`
  - `my-bookings.html`
- Booking confirmation template updated to use wordmark:
  - `smart_hotel_booking_confirmation.html`
- Service worker cache bumped and updated to include wordmark:
  - `src/main/resources/static/sw.js`

---

## 6) Major Issues Resolved During Project

## Deployment and Infrastructure

- Railway domain provisioning failures (`Not Found` / unprovisioned domain)
- DB connection failures (`localhost:5432` in cloud)
- Environment mismatch across deployments

## URL / Redirect Reliability

- Email CTA links and Stripe redirects incorrectly pointing to `localhost`
- Fixed via request-origin-aware base URL resolution with fallback order:
  1. Request `Origin`
  2. `APP_BASE_URL`
  3. localhost (dev fallback only)

## Notification Reliability

- SMTP timeouts from cloud runtime
- Added Brevo API-based sending path
- Added robust fallback behavior

## Auth & UX

- Re-login required after refresh
- Fixed by hardening token persistence in frontend

## Twilio/WhatsApp (Removed)

- Persistent outbound/inbound integration issues
- Full removal completed:
  - Related services/controllers
  - Frontend floating button and JS/CSS
  - Config properties
  - Setup docs and env traces

## Test/Build Compatibility

- Updated test signatures after booking API changes
- Fixed `CustomerBookingControllerTest` parameter mismatches
- Build now passes `test-compile`

---

## 7) Technologies and Tools Used

## Languages

- Java
- JavaScript
- HTML
- CSS
- SQL

## Backend Libraries/Frameworks

- Spring Boot Starter Web
- Spring Boot Starter Security
- Spring Boot Starter Data JPA
- Spring Boot Starter Validation
- Spring Boot Starter Mail
- Spring Boot Starter Actuator
- PostgreSQL JDBC driver
- Stripe Java SDK (`28.4.0`)
- JJWT (`0.11.5`)

## Frontend / Web Platform

- Vanilla JS + static HTML/CSS
- PWA manifest
- Service workers

## Tooling / DevOps

- Maven wrapper (`mvnw`)
- Git / GitHub
- Railway deployment platform
- Railway managed PostgreSQL
- Stripe Dashboard / Webhook tooling
- Brevo transactional email API

---

## 8) API / Controller Coverage (Current)

Backend controllers present:

- `AuthController`
- `CustomerBookingController`
- `BookingController`
- `PublicHotelController`
- `AdminHotelController`
- `AdminDestinationController`
- `AdminStaffController`
- `GuestController`
- `RoomController`
- `PaymentController`
- `StripeWebhookController`
- `ReportingController`
- `CancellationPolicyController`
- `HelloController`

---

## 9) Configuration and Environment Variables

## Primary Runtime Variables

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `PORT`
- `APP_BASE_URL`
- `APP_MOBILE_PLAY_STORE_URL`
- `APP_MOBILE_APP_STORE_URL`
- `PEXELS_API_KEY`
- `SMTP_HOST`
- `SMTP_PORT`
- `SMTP_USERNAME`
- `SMTP_PASSWORD`
- `MAIL_FROM`
- `SUPPORT_EMAIL`
- `SUPPORT_PHONE`
- `STRIPE_ENABLED`
- `STRIPE_SECRET_KEY`
- `STRIPE_PUBLISHABLE_KEY`
- `STRIPE_WEBHOOK_SECRET`
- `BREVO_API_KEY`
- `BREVO_API_URL`

## Removed from Project Scope

- Any Twilio/WhatsApp-related environment keys and app properties

---

## 10) Database and Data Operations

- PostgreSQL as source of truth
- JPA entities/repositories for booking domain
- Cleanup SQL workflows used during testing:
  - delete bookings
  - delete non-admin users
  - reset related sequences
- Admin-preserving cleanup variants were used to maintain privileged accounts

---

## 11) Build, QA, and Verification Status

## Automated

- Maven test-compile success (`./mvnw -DskipTests test-compile`)
- Lint checks passed for modified files during recent updates

## Functional Validation Focus Areas

- Register -> welcome email
- Login -> login email
- Book room -> confirmation email
- Cancel booking -> cancellation email
- Email CTA links -> correct Railway domain
- Stripe flow -> successful redirect + verification

These are the critical final smoke tests for release confidence.

---

## 12) Deployment and Git Status

- Latest changes pushed to GitHub `main`
- Recent key commit examples:
  - `69480bc` - origin-based redirect fixes for Stripe/email
  - `09ad020` - unified branding assets + booking test alignment
- Current deployed domain used in recent work:
  - `https://hotelmanagement-production-b642.up.railway.app`

---

## 13) Project Completeness Assessment

The project is in a strong, near-production-ready state for demonstration and practical use.

What is complete:

- Core business flows (auth, search, booking, cancel, admin, notifications, payment)
- Deployment path and cloud runtime setup
- Branding and UI polish
- Removal of unstable WhatsApp/Twilio scope
- Major reliability fixes (redirects, email delivery fallback, test compilation)

Residual risk before calling it "perfect":

- Final end-to-end smoke run on live Railway after last deploy should be executed once more for all user journeys
- Stripe and email integrations should be validated with fresh runtime values after each infra/domain change

---

## 14) Final Conclusion

SmartHotel Management has been successfully delivered with a complete full-stack architecture, cloud deployment, transactional notifications, payment integration, admin tooling, and PWA support.  
The platform now reflects a stable and polished project outcome suitable for submission, demo, and further enhancement.

