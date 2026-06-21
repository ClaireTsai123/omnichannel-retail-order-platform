package com.ordering.userservice.filter;

import com.ordering.userservice.util.JwtUtil;
import com.ordering.userservice.util.Role;
import com.ordering.userservice.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
@Component
public class JwtValidationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtTokenUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String header =request.getHeader("Authorization");
        if(header!=null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if(jwtTokenUtil.validateToken(token)) {
                String username = jwtTokenUtil.extractUsername(token);
                Long userId = jwtTokenUtil.extractUserId(token);
                String role = jwtTokenUtil.extractRole(token);
                User u = new User();
                u.setId(userId);
                u.setUsername(username);
                u.setRole(Role.valueOf(role));

                SecurityContext context = SecurityContextHolder.createEmptyContext();
                UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities());
                context.setAuthentication(usernamePasswordAuthenticationToken);
                SecurityContextHolder.setContext(context);
                filterChain.doFilter(request, response);
            } else {
                filterChain.doFilter(request, response);
                return;
            }
        } else {
            filterChain.doFilter(request, response);
            return;
        }
    }

}
