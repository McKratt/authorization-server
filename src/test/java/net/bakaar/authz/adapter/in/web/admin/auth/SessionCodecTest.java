package net.bakaar.authz.adapter.in.web.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SessionCodecTest {

    private static final Instant IN_ONE_HOUR = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

    private static final String ID_TOKEN = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhbGljZSJ9.c2lnbmF0dXJl";

    private final SessionCodec codec = new SessionCodec(randomKey());

    @Test
    void roundTrips() {
        AdminSession session = new AdminSession("alice", Set.of(AdminRole.ADMIN, AdminRole.READER), "csrf-1", ID_TOKEN, IN_ONE_HOUR);
        LoginState login = new LoginState("state-1", "nonce-1", "verifier-1", "/admin/applications", IN_ONE_HOUR);

        assertThat(codec.decode(codec.encode(session))).contains(session);
        assertThat(codec.decodeLoginState(codec.encode(login))).contains(login);
    }

    @Test
    void sessionKeepsTheIdTokenButNeverPrintsIt() {
        AdminSession session = new AdminSession("alice", Set.of(AdminRole.ADMIN), "csrf-1", ID_TOKEN, IN_ONE_HOUR);

        assertThat(codec.decode(codec.encode(session))).get()
                .extracting(AdminSession::idToken).isEqualTo(ID_TOKEN);
        assertThat(session.toString()).doesNotContain(ID_TOKEN).contains("alice");
    }

    @Test
    void roundTripsASessionWithoutRoles() {
        AdminSession session = new AdminSession("alice", Set.of(), "csrf-1", ID_TOKEN, IN_ONE_HOUR);

        assertThat(codec.decode(codec.encode(session))).contains(session);
    }

    @Test
    void tamperedOrExpiredSessionIsIgnored() {
        String encoded = codec.encode(new AdminSession("alice", Set.of(AdminRole.ADMIN), "csrf-1", ID_TOKEN, IN_ONE_HOUR));
        char[] chars = encoded.toCharArray();
        int middle = encoded.lastIndexOf('.') - 5;
        chars[middle] = chars[middle] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);
        String expired = codec.encode(new AdminSession("alice", Set.of(AdminRole.ADMIN), "csrf-1", ID_TOKEN,
                Instant.now().minusSeconds(1)));

        assertThat(codec.decode(tampered)).isEmpty();
        assertThat(new SessionCodec(randomKey()).decode(encoded)).isEmpty();
        assertThat(codec.decode(expired)).isEmpty();
        assertThat(codec.decode("not-a-jwe")).isEmpty();
        assertThat(codec.decode("")).isEmpty();
    }

    @Test
    void loginStateIsNeverAcceptedAsSessionAndViceVersa() {
        String login = codec.encode(new LoginState("state-1", "nonce-1", "verifier-1", "/admin", IN_ONE_HOUR));
        String session = codec.encode(new AdminSession("alice", Set.of(AdminRole.ADMIN), "csrf-1", ID_TOKEN, IN_ONE_HOUR));

        assertThat(codec.decode(login)).isEmpty();
        assertThat(codec.decodeLoginState(session)).isEmpty();
    }

    @Test
    void expiredLoginStateIsIgnored() {
        String expired = codec.encode(new LoginState("s", "n", "v", "/admin", Instant.now().minusSeconds(1)));

        assertThat(codec.decodeLoginState(expired)).isEmpty();
    }

    @Test
    void rejectsKeysThatAreNot32Bytes() {
        assertThatThrownBy(() -> new SessionCodec(new byte[16])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void csrfTokenMustBeBase64Url() {
        for (String token : new String[] {"", "a\"b", "a b", "a+b/c=", "<x>"}) {
            assertThatThrownBy(() -> new AdminSession("a", Set.of(AdminRole.ADMIN), token, ID_TOKEN, IN_ONE_HOUR))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void adminRoleMayWriteAndReaderMayNot() {
        assertThat(new AdminSession("a", Set.of(AdminRole.ADMIN), "c", ID_TOKEN, IN_ONE_HOUR).canWrite()).isTrue();
        assertThat(new AdminSession("a", Set.of(AdminRole.READER), "c", ID_TOKEN, IN_ONE_HOUR).canWrite()).isFalse();
    }

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }
}
