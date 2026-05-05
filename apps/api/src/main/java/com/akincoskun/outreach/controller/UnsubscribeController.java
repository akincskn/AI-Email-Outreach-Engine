package com.akincoskun.outreach.controller;

import com.akincoskun.outreach.service.email.HmacTokenService;
import com.akincoskun.outreach.service.email.SuppressionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/unsubscribe")
@RequiredArgsConstructor
@Slf4j
public class UnsubscribeController {

    private final HmacTokenService hmacTokenService;
    private final SuppressionService suppressionService;

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> unsubscribe(@RequestParam String token) {
        try {
            // Token format: hex(email.nonce.sig) — payload = email address
            byte[] bytes = java.util.HexFormat.of().parseHex(token);
            String decoded = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\.");
            if (parts.length != 3) {
                return ResponseEntity.badRequest().body(invalidPage());
            }
            String email = parts[0];

            if (!hmacTokenService.verifyToken(token, email)) {
                log.warn("Invalid unsubscribe token for email: {}", email);
                return ResponseEntity.badRequest().body(invalidPage());
            }

            suppressionService.suppress(email, "unsubscribe", null);
            log.info("Unsubscribed: {}", email);
            return ResponseEntity.ok(successPage(email));

        } catch (Exception e) {
            log.warn("Unsubscribe error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(invalidPage());
        }
    }

    private String successPage(String email) {
        return """
            <!DOCTYPE html>
            <html lang="tr">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Abonelikten Çıkıldı</title>
              <style>
                body { font-family: Arial, sans-serif; max-width: 600px; margin: 80px auto;
                       text-align: center; color: #333; }
                h1 { color: #2d7a2d; }
                p { color: #555; line-height: 1.6; }
              </style>
            </head>
            <body>
              <h1>✓ Abonelikten Çıkıldınız</h1>
              <p><strong>%s</strong> adresine artık e-posta göndermeyeceğiz.</p>
              <p>Bu işlem kalıcıdır ve anında geçerli olur.</p>
            </body>
            </html>
            """.formatted(email);
    }

    private String invalidPage() {
        return """
            <!DOCTYPE html>
            <html lang="tr">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Geçersiz Bağlantı</title>
              <style>
                body { font-family: Arial, sans-serif; max-width: 600px; margin: 80px auto;
                       text-align: center; color: #333; }
                h1 { color: #c0392b; }
              </style>
            </head>
            <body>
              <h1>Geçersiz veya Süresi Dolmuş Bağlantı</h1>
              <p>Bu abonelik bağlantısı geçerli değil.</p>
            </body>
            </html>
            """;
    }
}
