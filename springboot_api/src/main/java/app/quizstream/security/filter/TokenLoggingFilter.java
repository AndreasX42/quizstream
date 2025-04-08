package app.quizstream.security.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

@Component
public class TokenLoggingFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            System.out.println("JWT Token: " + token);

            String xAmznOidcIdentity = request.getHeader("x-amzn-oidc-identity");
            System.out.println("x-amzn-oidc-identity: " + xAmznOidcIdentity);

            String xAmznOidcData = request.getHeader("x-amzn-oidc-data");
            System.out.println("x-amzn-oidc-data: " + xAmznOidcData);

            String xAmznOidcAccessToken = request.getHeader("x-amzn-oidc-access-token");
            System.out.println("x-amzn-oidc-access-token: " + xAmznOidcAccessToken);
        }

        filterChain.doFilter(request, response);
    }
}
