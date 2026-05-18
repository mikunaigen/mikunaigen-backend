package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.service.EmailDispatchWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
public class EmailDispatchWebhookController {

    private final EmailDispatchWebhookService emailDispatchWebhookService;

    public EmailDispatchWebhookController(EmailDispatchWebhookService emailDispatchWebhookService) {
        this.emailDispatchWebhookService = emailDispatchWebhookService;
    }

    @PostMapping("/email-dispatch")
    public ResponseEntity<Map<String, Object>> emailDispatch(
            @RequestHeader(value = "X-Email-Dispatch-Signature", required = false) String signature,
            @RequestBody(required = false) Map<String, Object> body) {
        if (!emailDispatchWebhookService.verifySignature(signature)) {
            return ResponseEntity.status(401).body(Map.of("ok", false));
        }
        if (body != null) {
            emailDispatchWebhookService.handleCallback(body);
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
