package com.smarthotel.hotelmanagement.controller;

import com.smarthotel.hotelmanagement.service.NotificationService;
import com.smarthotel.hotelmanagement.service.WhatsAppChatbotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * WhatsApp chatbot API for automated replies.
 * - POST /chat: JSON API (message → reply); useful for testing or custom clients.
 * - POST /webhook: Twilio incoming webhook; receives form params Body, From and sends the reply via Twilio.
 * Public endpoints (no auth) so Twilio can POST to the webhook.
 */
@RestController
@RequestMapping("/api/public/whatsapp")
public class WhatsAppChatbotController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppChatbotController.class);

    private final WhatsAppChatbotService chatbotService;
    private final NotificationService notificationService;

    public WhatsAppChatbotController(WhatsAppChatbotService chatbotService,
                                    NotificationService notificationService) {
        this.chatbotService = chatbotService;
        this.notificationService = notificationService;
    }

    /**
     * Get automated reply for an incoming chat message (JSON).
     * Body: { "message": "user's message" }
     * Response: { "reply": "automated reply text" }
     */
    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody Map<String, String> body) {
        String message = body != null ? body.get("message") : null;
        String reply = chatbotService.getReply(message);
        return Map.of("reply", reply);
    }

    /**
     * Twilio WhatsApp webhook: when a user sends a message to your business number,
     * Twilio POSTs here with form params "Body" and "From". We compute the reply and
     * send it back via Twilio API, then return empty TwiML so Twilio does not retry.
     * Configure this URL in Twilio Console: Messaging → WhatsApp Sandbox (or your number) → "When a message comes in".
     * Example: https://your-domain.com/api/public/whatsapp/webhook
     */
    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> webhook(
            @RequestParam(value = "Body", required = false) String body,
            @RequestParam(value = "From", required = false) String from) {
        log.info("WhatsApp webhook hit. from='{}', body='{}'", from, body);
        if (StringUtils.hasText(from) && body != null) {
            String reply = chatbotService.getReply(body, from);
            log.info("WhatsApp chatbot reply computed: '{}'", reply);
            notificationService.sendWhatsAppMessage(from, reply);
        } else {
            log.info("WhatsApp webhook missing required params. fromText={}, bodyPresent={}",
                    StringUtils.hasText(from), body != null);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body("<Response></Response>");
    }
}
