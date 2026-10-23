package vn.fs.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import vn.fs.entity.User;
import vn.fs.service.implement.*;

class AccountSecurityTest {
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    private UserDetailsImpl disabled() {
        var user=new User();user.setUserId(7L);user.setEmail("disabled@example.invalid");
        user.setStatus(false);user.setRoles(Set.of());user.setPassword(new BCryptPasswordEncoder().encode("test-pass"));
        return UserDetailsImpl.build(user);
    }
    @Test void disabledAccountCannotSignInEvenWithCorrectPassword() {
        var provider=new DaoAuthenticationProvider();var user=disabled();
        provider.setPasswordEncoder(new BCryptPasswordEncoder());provider.setUserDetailsService(email->user);
        assertThatThrownBy(()->provider.authenticate(new UsernamePasswordAuthenticationToken(user.getEmail(),"test-pass"))).isInstanceOf(DisabledException.class);
    }
    @Test void existingJwtDoesNotAuthenticateAfterAccountIsDisabled() throws Exception {
        var filter=new AuthTokenFilter();var jwt=mock(JwtUtils.class);var users=mock(UserDetailsServiceImpl.class);
        when(jwt.validateJwtToken("old-token")).thenReturn(true);when(jwt.getEmailFromJwtToken("old-token")).thenReturn("disabled@example.invalid");
        when(users.loadUserByUsername(anyString())).thenReturn(disabled());
        ReflectionTestUtils.setField(filter,"jwtUtils",jwt);ReflectionTestUtils.setField(filter,"userDetailsService",users);
        var request=new MockHttpServletRequest();request.addHeader("Authorization","Bearer old-token");
        filter.doFilter(request,new MockHttpServletResponse(),new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
