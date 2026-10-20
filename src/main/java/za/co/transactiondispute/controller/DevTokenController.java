package za.co.transactiondispute.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Stands in for the bank's identity provider so the API can be exercised locally. Never enable in production. */
@RestController
@RequestMapping("/dev/token")
@ConditionalOnProperty(name = "app.dev-token-endpoint", havingValue = "true")
public class DevTokenController {

    public record TokenRequest(@NotBlank String subject, List<String> roles) {
    }

    private final JwtEncoder encoder;
    private final String issuer;

    public DevTokenController(JwtEncoder encoder, @Value("${security.jwt.issuer}") String issuer) {
        this.encoder = encoder;
        this.issuer = issuer;
    }

    @PostMapping
    public Map<String, Object> issue(@Valid @RequestBody TokenRequest request) {
        Instant now = Instant.now();
        Duration ttl = Duration.ofMinutes(15);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(request.subject())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("roles", request.roles() == null ? List.of() : request.roles())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return Map.of("access_token", token, "token_type", "Bearer", "expires_in", ttl.toSeconds());
    }
}
