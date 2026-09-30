package no.weatheragent.web;

import no.weatheragent.account.AuthUserRemover;
import no.weatheragent.account.UserDataEraser;
import no.weatheragent.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@Import(SecurityConfig.class)
class AccountControllerSecurityTest {

    private static final String SUB = "33333333-3333-3333-3333-333333333333";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserDataEraser eraser;

    @MockitoBean
    private AuthUserRemover authUserRemover;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void deletingAnAccountRequiresLogin() throws Exception {
        mvc.perform(delete("/api/konto")).andExpect(status().isUnauthorized());

        verifyNoInteractions(eraser, authUserRemover);
    }

    @Test
    void deletesOnlyTheCallersOwnAccountAndReportsWhatHappened() throws Exception {
        UUID user = UUID.fromString(SUB);
        when(eraser.eraseAll(user)).thenReturn(42);
        when(authUserRemover.remove(user)).thenReturn(true);

        mvc.perform(delete("/api/konto").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedRows").value(42))
                .andExpect(jsonPath("$.loginRemoved").value(true));

        verify(eraser).eraseAll(user);
        verify(authUserRemover).remove(user);
    }

    @Test
    void tellsTheUserWhenTheLoginCouldNotBeRemoved() throws Exception {
        UUID user = UUID.fromString(SUB);
        when(eraser.eraseAll(user)).thenReturn(3);
        when(authUserRemover.remove(user)).thenReturn(false);

        mvc.perform(delete("/api/konto").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginRemoved").value(false));
    }

    @Test
    void thereIsNoWayToDeleteAccountsWithGet() throws Exception {
        mvc.perform(get("/api/konto").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(eraser, authUserRemover);
    }
}
