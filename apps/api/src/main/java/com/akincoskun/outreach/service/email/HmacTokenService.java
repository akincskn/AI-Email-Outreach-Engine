package com.akincoskun.outreach.service.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class HmacTokenService {

    private final byte[] secretKey;

    public HmacTokenService(@Value("${app.security.hmac-secret}") String secret) {
        this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String generateToken(String payload) {
        try {
            String nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String data = payload + "|" + nonce;
            String sig = hmacSha256(data);
            return HexFormat.of().formatHex((data + "|" + sig).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Token generation failed", e);
        }
    }

    public boolean verifyToken(String token, String expectedPayload) {
        try {
            byte[] bytes = HexFormat.of().parseHex(token);
            String decoded = new String(bytes, StandardCharsets.UTF_8);
            // format: payload|nonce|sig — payload may contain '|' chars are safe
            int lastPipe = decoded.lastIndexOf('|');
            if (lastPipe < 0) return false;
            int secondLastPipe = decoded.lastIndexOf('|', lastPipe - 1);
            if (secondLastPipe < 0) return false;

            String payload = decoded.substring(0, secondLastPipe);
            String data    = decoded.substring(0, lastPipe);
            String sig     = decoded.substring(lastPipe + 1);
            String expectedSig = hmacSha256(data);

            return expectedSig.equals(sig) && payload.equals(expectedPayload);
        } catch (Exception e) {
            return false;
        }
    }

    // Used by UnsubscribeController to extract the email from a token without prior knowledge
    public String extractPayload(String token) {
        try {
            byte[] bytes = HexFormat.of().parseHex(token);
            String decoded = new String(bytes, StandardCharsets.UTF_8);
            int lastPipe = decoded.lastIndexOf('|');
            if (lastPipe < 0) return null;
            int secondLastPipe = decoded.lastIndexOf('|', lastPipe - 1);
            if (secondLastPipe < 0) return null;
            return decoded.substring(0, secondLastPipe);
        } catch (Exception e) {
            return null;
        }
    }

    private String hmacSha256(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
        byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(result);
    }
}
