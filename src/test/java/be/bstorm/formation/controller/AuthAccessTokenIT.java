package be.bstorm.formation.controller;

import be.bstorm.formation.framework.crypto.CryptoException;
import be.bstorm.formation.framework.security.Role;
import be.bstorm.formation.support.SpiedAuthItSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static be.bstorm.formation.support.JwtTestTokens.b64url;
import static be.bstorm.formation.support.JwtTestTokens.header;
import static be.bstorm.formation.support.JwtTestTokens.payload;
import static be.bstorm.formation.support.JwtTestTokens.signature;
import static be.bstorm.formation.support.JwtTestTokens.signingInput;
import static be.bstorm.formation.support.JwtTestTokens.toJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

/** Feature 001 — jeton d'accès : RG-06, RG-07, RG-19, RG-20 (§ 5.8), AC-19 à AC-26, AC-40, AC-60, AC-SEC-04, AC-SD-04. */
@Tag("feature-001")
class AuthAccessTokenIT extends SpiedAuthItSupport {

    private static final UUID SUBJECT = UUID.fromString("0193a5f2-0000-7000-8000-0000000000a1");
    private static final UUID JTI = UUID.fromString("0193a5f2-0000-7000-8000-0000000000a2");
    private static final UUID SID = UUID.fromString("0193a5f2-0000-7000-8000-0000000000a3");

    @Test
    @DisplayName("[AC-19] En-tête ES256/JWT/kid courant, claims du § 5.8, signature P1363 vérifiable avec la clé publique")
    void ac19_accessTokenHeaderClaimsAndSignature() throws Exception {
        // Given
        UUID alice = createAlice();

        // When
        Session session = loginOk("alice", PASSWORD);

        // Then
        String token = session.accessToken();
        Map<String, Object> header = header(token);
        assertThat(header).containsEntry("alg", "ES256").containsEntry("typ", "JWT")
                .containsEntry("kid", jwtKeys.currentKeyId());
        Map<String, Object> claims = payload(token);
        assertThat(claims).containsEntry("iss", authProperties.jwt().issuer())
                .containsEntry("aud", authProperties.jwt().audience())
                .containsEntry("sub", alice.toString())
                .containsEntry("sid", familyOf(session.cookie()).toString())
                .containsEntry("depot_id", DEPOT.toString());
        assertThat(((Number) claims.get("iat")).longValue()).isEqualTo(T0.getEpochSecond());
        assertThat(((Number) claims.get("exp")).longValue()).isEqualTo(T0.getEpochSecond() + 300);
        assertThat(UUID.fromString((String) claims.get("jti"))).isNotNull();
        assertThat((List<Object>) claims.get("roles")).containsExactly((Object) "DRIVER", "FLEET_MANAGER");

        byte[] signatureBytes = signature(token);
        assertThat(signatureBytes).hasSize(64);
        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(jwtKeys.publicKey((String) header.get("kid")).orElseThrow());
        verifier.update(signingInput(token).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(signatureBytes)).isTrue();
    }

    @Test
    @DisplayName("[AC-19] Utilisateur sans dépôt : claim depot_id absente")
    void ac19_depotClaimIsAbsentWithoutDepot() {
        // Given
        createUser("bob", PASSWORD, Set.of(Role.DRIVER), null);

        // When
        Session session = loginOk("bob", PASSWORD);

        // Then
        assertThat(payload(session.accessToken())).doesNotContainKey("depot_id");
    }

    @Test
    @DisplayName("[AC-20] GET /me avec le jeton : id, rôles, dépôt de l'utilisateur courant")
    void ac20_meReturnsCurrentUserFromToken() {
        // Given
        UUID alice = createAlice();
        Session session = loginOk("alice", PASSWORD);

        // When
        MvcTestResult result = me(session.accessToken());

        // Then
        assertThat(result).hasStatusOk();
        assertThat(jsonString(result, "$.id")).isEqualTo(alice.toString());
        assertThat(jsonStrings(result, "$.roles")).containsExactly("DRIVER", "FLEET_MANAGER");
        assertThat(jsonString(result, "$.depotId")).isEqualTo(DEPOT.toString());
    }

    @Test
    @DisplayName("[AC-20] GET /me pour un utilisateur sans dépôt : depotId null")
    void ac20_meReturnsNullDepotWithoutDepot() {
        // Given
        createUser("bob", PASSWORD, Set.of(Role.DRIVER), null);
        Session session = loginOk("bob", PASSWORD);

        // When
        MvcTestResult result = me(session.accessToken());

        // Then
        assertThat(result).hasStatusOk();
        assertThat(jsonMap(result)).containsKey("depotId");
        assertThat(jsonMap(result).get("depotId")).isNull();
    }

    @Test
    @DisplayName("[AC-21] GET /me sans en-tête Authorization : 401")
    void ac21_meWithoutAuthorizationIsUnauthorized() {
        // When
        MvcTestResult result = mvc.get().uri(ME).with(ip(CLIENT_IP)).exchange();

        // Then
        assertProblem(result, 401, "Unauthorized", null);
    }

    @Test
    @DisplayName("[AC-22] Jeton accepté à T0 + 299 s, refusé dès T0 + 300 s (exp exclusif, aucune tolérance)")
    void ac22_tokenExpiresExactlyAtExp() {
        // Given
        createAlice();
        Session session = loginOk("alice", PASSWORD);

        // When
        clock.set(T0.plusSeconds(299));
        MvcTestResult beforeExp = me(session.accessToken());
        clock.set(T0.plusSeconds(300));
        MvcTestResult atExp = me(session.accessToken());

        // Then
        assertThat(beforeExp).hasStatusOk();
        assertProblem(atExp, 401, "Unauthorized", null);
    }

    @Test
    @DisplayName("[AC-23] Témoin : un jeton forgé conforme au § 5.8 et signé par la clé courante est accepté")
    void ac23_wellFormedForgedTokenIsAcceptedAsControl() {
        // When
        MvcTestResult result = me(es256(validClaims()));

        // Then
        assertThat(result).hasStatusOk();
    }

    @Test
    @DisplayName("[AC-23] (a) charge utile modifiée après signature : 401")
    void ac23_rejectsPayloadModifiedAfterSigning() {
        // Given
        String valid = es256(validClaims());
        Map<String, Object> elevated = validClaims();
        elevated.put("roles", List.of("ADMIN"));
        String[] parts = valid.split("\\.");
        String tampered = parts[0] + "." + b64url(toJson(elevated)) + "." + parts[2];

        // When / Then
        assertRejected(tampered);
    }

    @Test
    @DisplayName("[AC-23] (b) alg = none sans signature : 401")
    void ac23_rejectsAlgNone() {
        // Given
        String token = b64url(toJson(jwsHeader("none", jwtKeys.currentKeyId()))) + "." + b64url(toJson(validClaims())) + ".";

        // When / Then
        assertRejected(token);
    }

    @Test
    @DisplayName("[AC-23] (c) alg = HS256 signé avec des octets quelconques : 401")
    void ac23_rejectsHs256() throws Exception {
        // Given
        String input = b64url(toJson(jwsHeader("HS256", jwtKeys.currentKeyId()))) + "." + b64url(toJson(validClaims()));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("arbitrary-shared-secret-0123456789abcdef".getBytes(StandardCharsets.US_ASCII),
                "HmacSHA256"));
        String token = input + "." + b64url(mac.doFinal(input.getBytes(StandardCharsets.US_ASCII)));

        // When / Then
        assertRejected(token);
    }

    @Test
    @DisplayName("[AC-23] (d) kid inconnu : 401")
    void ac23_rejectsUnknownKid() {
        // Given
        String token = signWith(jwsHeader("ES256", "999"), validClaims(), jwtKeys.currentKeyId());

        // When / Then
        assertRejected(token);
    }

    @Test
    @DisplayName("[AC-23] (e) iss différent : 401")
    void ac23_rejectsOtherIssuer() {
        Map<String, Object> claims = validClaims();
        claims.put("iss", "https://other-issuer.example");
        assertRejected(es256(claims));
    }

    @Test
    @DisplayName("[AC-23] (f) aud différent : 401")
    void ac23_rejectsOtherAudience() {
        Map<String, Object> claims = validClaims();
        claims.put("aud", "other-audience");
        assertRejected(es256(claims));
    }

    @Test
    @DisplayName("[AC-23] (g) exp absent : 401")
    void ac23_rejectsMissingExp() {
        Map<String, Object> claims = validClaims();
        claims.remove("exp");
        assertRejected(es256(claims));
    }

    @Test
    @DisplayName("[AC-23] (h) sub qui n'est pas un UUID : 401")
    void ac23_rejectsNonUuidSubject() {
        Map<String, Object> claims = validClaims();
        claims.put("sub", "not-a-uuid");
        assertRejected(es256(claims));
    }

    @Test
    @DisplayName("[AC-23] (i) roles vide : 401")
    void ac23_rejectsEmptyRoles() {
        Map<String, Object> claims = validClaims();
        claims.put("roles", List.of());
        assertRejected(es256(claims));
    }

    @Test
    @DisplayName("[AC-23] (i) roles absent : 401")
    void ac23_rejectsMissingRoles() {
        Map<String, Object> claims = validClaims();
        claims.remove("roles");
        assertRejected(es256(claims));
    }

    @Test
    @DisplayName("[AC-23] (i) roles contenant SUPERUSER : 401")
    void ac23_rejectsUnknownRole() {
        Map<String, Object> claims = validClaims();
        claims.put("roles", List.of("DRIVER", "SUPERUSER"));
        assertRejected(es256(claims));
    }

    @Test
    @DisplayName("[AC-23] (j) signé ES256 par une autre clé P-256 avec le kid courant : 401")
    void ac23_rejectsSignatureFromAnotherKey() throws Exception {
        // Given
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair foreign = generator.generateKeyPair();
        String input = b64url(toJson(jwsHeader("ES256", jwtKeys.currentKeyId()))) + "." + b64url(toJson(validClaims()));
        Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
        signer.initSign(foreign.getPrivate());
        signer.update(input.getBytes(StandardCharsets.US_ASCII));
        String token = input + "." + b64url(signer.sign());

        // When / Then
        assertRejected(token);
    }

    @Test
    @DisplayName("[AC-23] (k) iat dans le futur : 401")
    void ac23_rejectsIssuedInTheFuture() {
        Map<String, Object> claims = validClaims();
        claims.put("iat", T0.getEpochSecond() + 60);
        assertRejected(es256(claims));
    }

    @Test
    @DisplayName("[AC-24] Jeton en paramètre d'URL (access_token) sans en-tête : 401")
    void ac24_tokenInQueryParameterIsIgnored() {
        // Given
        createAlice();
        Session session = loginOk("alice", PASSWORD);

        // When
        MvcTestResult result = mvc.get().uri(ME + "?access_token=" + session.accessToken())
                .with(ip(CLIENT_IP)).exchange();

        // Then
        assertThat(result).hasStatus(401);
    }

    @Test
    @DisplayName("[AC-25] Après rotation de la clé, l'ancien kid reste accepté et les nouveaux jetons portent le nouveau kid")
    void ac25_previousKeyVersionStillVerifiesAfterRotation() {
        // Given
        createAlice();
        Session before = loginOk("alice", PASSWORD);
        Object oldKid = header(before.accessToken()).get("kid");

        // When
        rotateJwtKey();

        // Then
        assertThat(me(before.accessToken())).hasStatusOk();
        Session after = loginOk("alice", PASSWORD);
        assertThat(header(after.accessToken()).get("kid"))
                .isEqualTo(jwtKeys.currentKeyId())
                .isNotEqualTo(oldKid);
    }

    @Test
    @DisplayName("[AC-26] Refresh avec un jeton d'accès expiré dans Authorization : 200 (en-tête ignoré)")
    void ac26_refreshIgnoresExpiredAccessToken() {
        // Given
        createAlice();
        Session session = loginOk("alice", PASSWORD);
        clock.set(T0.plusSeconds(300));

        // When
        MvcTestResult result = csrfPost(REFRESH, CLIENT_IP, session.cookie())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()).exchange();

        // Then
        assertThat(result).hasStatusOk();
    }

    @Test
    @DisplayName("[AC-26] Refresh avec un jeton d'accès altéré dans Authorization : 200 (en-tête ignoré)")
    void ac26_refreshIgnoresTamperedAccessToken() {
        // Given
        createAlice();
        Session session = loginOk("alice", PASSWORD);

        // When
        MvcTestResult result = csrfPost(REFRESH, CLIENT_IP, session.cookie())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken() + "tampered").exchange();

        // Then
        assertThat(result).hasStatusOk();
    }

    @Test
    @DisplayName("[AC-26] Login avec un Authorization invalide et des identifiants corrects : 200")
    void ac26_loginIgnoresInvalidAuthorizationHeader() {
        // Given
        createAlice();

        // When
        MvcTestResult result = mvc.post().uri(LOGIN).contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("alice", PASSWORD))
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.value")
                .with(ip(CLIENT_IP)).exchange();

        // Then
        assertThat(result).hasStatusOk();
    }

    @Test
    @DisplayName("[AC-40] GET sur /refresh, /logout, /login sans jeton : 401")
    void ac40_otherMethodsWithoutTokenAreUnauthorized() {
        for (String route : List.of(REFRESH, LOGOUT, LOGIN)) {
            assertThat(mvc.get().uri(route).with(ip(CLIENT_IP))).hasStatus(401);
        }
    }

    @Test
    @DisplayName("[AC-40] GET sur /refresh, /logout, /login avec un jeton valide : 405")
    void ac40_otherMethodsWithValidTokenAreNotAllowed() {
        // Given
        createAlice();
        Session session = loginOk("alice", PASSWORD);

        // When / Then
        for (String route : List.of(REFRESH, LOGOUT, LOGIN)) {
            assertThat(mvc.get().uri(route).header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                    .with(ip(CLIENT_IP))).hasStatus(405);
        }
    }

    @Test
    @DisplayName("[AC-60] Erreur du fournisseur de clés pendant la vérification : 401 ProblemDetail, jamais 500")
    void ac60_keyProviderFailureDuringVerificationIsUnauthorized() {
        // Given
        createAlice();
        Session session = loginOk("alice", PASSWORD);
        doThrow(new CryptoException("key provider unavailable")).when(jwtKeys).publicKey(anyString());

        // When
        MvcTestResult result = me(session.accessToken());

        // Then
        assertProblem(result, 401, "Unauthorized", null);
    }

    @Test
    @DisplayName("[AC-SEC-04] JWT valide présenté dans un cookie Authorization ou access_token : 401")
    void acsec04_tokenInCookieIsIgnored() {
        // Given
        createAlice();
        Session session = loginOk("alice", PASSWORD);

        // When
        MvcTestResult authorizationCookie = mvc.get().uri(ME)
                .cookie(new Cookie("Authorization", session.accessToken())).with(ip(CLIENT_IP)).exchange();
        MvcTestResult accessTokenCookie = mvc.get().uri(ME)
                .cookie(new Cookie("access_token", session.accessToken())).with(ip(CLIENT_IP)).exchange();

        // Then
        assertThat(authorizationCookie).hasStatus(401);
        assertThat(accessTokenCookie).hasStatus(401);
    }

    @Test
    @DisplayName("[AC-SD-04] Le JWT ne contient que les claims du § 5.8 et /me exactement id, roles, depotId")
    void acsd04_tokenAndCurrentUserCarryMinimalData() {
        // Given
        createAlice();
        Session session = loginOk("alice", PASSWORD);

        // When
        MvcTestResult meResult = me(session.accessToken());

        // Then
        assertThat(payload(session.accessToken()).keySet())
                .containsExactlyInAnyOrder("iss", "aud", "sub", "iat", "exp", "jti", "sid", "roles", "depot_id");
        assertThat(jsonMap(meResult).keySet()).containsExactlyInAnyOrder("id", "roles", "depotId");
    }

    // ------------------------------------------------------------------ fabrication de jetons

    private void assertRejected(String token) {
        MvcTestResult result = me(token);
        assertProblem(result, 401, "Unauthorized", null);
    }

    private Map<String, Object> validClaims() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", authProperties.jwt().issuer());
        claims.put("aud", authProperties.jwt().audience());
        claims.put("sub", SUBJECT.toString());
        claims.put("iat", T0.getEpochSecond());
        claims.put("exp", T0.getEpochSecond() + 300);
        claims.put("jti", JTI.toString());
        claims.put("sid", SID.toString());
        claims.put("roles", List.of("DRIVER"));
        return claims;
    }

    private static Map<String, Object> jwsHeader(String alg, String kid) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", alg);
        header.put("typ", "JWT");
        header.put("kid", kid);
        return header;
    }

    private String es256(Map<String, Object> claims) {
        String kid = jwtKeys.currentKeyId();
        return signWith(jwsHeader("ES256", kid), claims, kid);
    }

    private String signWith(Map<String, Object> header, Map<String, Object> claims, String signingKid) {
        String input = b64url(toJson(header)) + "." + b64url(toJson(claims));
        return input + "." + b64url(jwtKeys.sign(signingKid, input.getBytes(StandardCharsets.US_ASCII)));
    }
}
