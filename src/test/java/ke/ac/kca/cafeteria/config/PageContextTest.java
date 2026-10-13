package ke.ac.kca.cafeteria.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ke.ac.kca.cafeteria.identity.AppUserPrincipal;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class PageContextTest {

    @Test
    void welcomeNameFallsBackToTheUsername() {
        Principal plain = () -> "demo.student";
        assertEquals("DEMO.STUDENT", PageContext.welcomeName(plain));
    }

    @Test
    void welcomeNameUsesTheFirstNameOfTheDisplayName() {
        AppUserPrincipal user = new AppUserPrincipal("24/00123", "Jordan Mwangi", "x", true, true, false,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        Principal signedIn = UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
        assertEquals("JORDAN", PageContext.welcomeName(signedIn));
    }

    @Test
    void todayIsShownInCampusTime() {
        // 22:30 UTC on Thursday 8 October is already Friday 9 October in Nairobi (UTC+3)
        Clock clock = Clock.fixed(Instant.parse("2026-10-08T22:30:00Z"), ZoneOffset.UTC);
        assertEquals("FRI, OCT 9", PageContext.todayLabel("Africa/Nairobi", clock));
    }
}
