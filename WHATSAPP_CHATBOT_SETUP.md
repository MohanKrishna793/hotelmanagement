# Automated WhatsApp Chatbot – Setup Guide

Your Smart Hotel app already has **chatbot logic** that replies to user messages (menu 1–5, greetings, etc.). To make it reply **automatically** when someone messages your WhatsApp business number, you need to connect **Twilio** (or another WhatsApp Business API provider) and point its webhook to your backend.

---

## How It Works

1. User sends a WhatsApp message to your business number (e.g. +91 7671062848).
2. Twilio receives it and **POSTs** to your webhook: `https://your-server/api/public/whatsapp/webhook` with form fields `Body` and `From`.
3. Your backend uses `WhatsAppChatbotService` to get the reply text and sends it back via Twilio’s API.
4. The user sees the automated reply in WhatsApp.

---

## Step 1: Twilio Account & WhatsApp

1. Sign up at [twilio.com](https://www.twilio.com) and get:
   - **Account SID**
   - **Auth Token**  
   (Twilio Console → Account → API keys & tokens)

2. Enable WhatsApp:
   - **Twilio Console** → **Messaging** → **Try it out** → **Send a WhatsApp message**
   - Or: **Messaging** → **WhatsApp** → **Sandbox** (for testing) or connect your own WhatsApp Business number

3. Note your **WhatsApp “From” number** (e.g. `whatsapp:+14155238886` for sandbox, or your business number).

---

## Step 2: Configure Your App

In `application.properties` (or via environment variables), set:

```properties
app.twilio.account-sid=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
app.twilio.auth-token=your_auth_token
app.twilio.whatsapp-from=whatsapp:+14155238886
```

Use your real Account SID, Auth Token, and WhatsApp “From” number. For production, use env vars:

- `TWILIO_ACCOUNT_SID`
- `TWILIO_AUTH_TOKEN`
- `TWILIO_WHATSAPP_FROM`

---

## Step 3: Expose Your Backend to the Internet

Twilio must be able to POST to your server. Options:

- **Local development:** use a tunnel (e.g. [ngrok](https://ngrok.com)):
  ```bash
  ngrok http 8080
  ```
  Use the HTTPS URL (e.g. `https://abc123.ngrok.io`) as the base for the webhook.

- **Production:** deploy your Spring Boot app to a host with a public URL (e.g. Railway, Render, AWS). No tunnel needed.

---

## Step 4: Set the Webhook in Twilio

1. In Twilio: **Messaging** → **WhatsApp** → **Sandbox** (or your number) → **Settings**.
2. Under **“When a message comes in”**:
   - Set **Webhook URL** to:  
     `https://your-public-url/api/public/whatsapp/webhook`  
     (e.g. `https://abc123.ngrok.io/api/public/whatsapp/webhook` for ngrok).
   - Method: **POST**.
3. Save.

Twilio will send incoming WhatsApp messages to this URL; your app will reply using the chatbot logic.

---

## Step 5: Test

1. Start your Spring Boot app.
2. From your phone, send a WhatsApp message to the Twilio sandbox (or your business) number (e.g. “Hi” or “1”).
3. You should get an automated reply (greeting + menu, or the reply for option 1).

---

## Optional: Test Reply Logic Without WhatsApp

You can test the reply text without Twilio:

```bash
curl -X POST http://localhost:8080/api/public/whatsapp/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\": \"1\"}"
```

Response: `{"reply":"📌 To *book a room*: ..."}`.  
The same logic is used when Twilio calls `/webhook`.

---

## Summary

| Item | Value |
|------|--------|
| Webhook URL | `https://<your-domain>/api/public/whatsapp/webhook` |
| Method | POST (form-urlencoded) |
| Twilio sends | `Body`, `From` (and others) |
| App | Gets reply from `WhatsAppChatbotService`, sends via `NotificationService.sendWhatsAppMessage()` |

Once Twilio credentials and webhook URL are set, the chatbot will reply automatically to every incoming WhatsApp message to your business number.
