package app.quizstream.security.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;

@Component
@Slf4j
public class CognitoHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String userId = request.getHeader("x-amzn-oidc-identity");
        String claimsJson = request.getHeader("x-amzn-oidc-data");

        if (userId != null) {
            List<GrantedAuthority> authorities = new ArrayList<>();

            if (claimsJson != null) {
                try {
                    Map<String, Object> claims = new ObjectMapper().readValue(claimsJson, new TypeReference<>() {
                    });
                    Object groupsObject = claims.getOrDefault("cognito:groups", List.of());
                    if (groupsObject instanceof List groupsList) {
                        for (Object group : groupsList) {
                            if (group instanceof String groupString) {
                                authorities.add(new SimpleGrantedAuthority(groupString));
                            } else {
                                log.warn("Non-string element found in cognito:groups: {}", group);
                            }
                        }
                    } else {
                        log.warn("cognito:groups claim is not a List: {}", groupsObject);
                    }
                } catch (Exception e) {
                    log.warn("Could not parse Cognito claims", e);
                }
            }

            var principal = new User(userId, "", authorities);
            var auth = new UsernamePasswordAuthenticationToken(principal, null,
                    authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
