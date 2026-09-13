package com.pjsofttech.expensetracker.config;

import com.pjsofttech.expensetracker.model.User;
import com.pjsofttech.expensetracker.repository.UserRepository;
import com.pjsofttech.expensetracker.util.JWTUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2SuccessHandler
        extends SimpleUrlAuthenticationSuccessHandler {

    private final JWTUtil jwtService;
    private final UserRepository userRepository;

    public OAuth2SuccessHandler(
            JWTUtil jwtService,
            UserRepository userRepository) {

        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication)
            throws IOException {

        OAuth2User oauthUser =
                (OAuth2User) authentication.getPrincipal();

        String email = oauthUser.getAttribute("email");
        String name = oauthUser.getAttribute("name");

        // Find existing user
        // or create a new user
        User user = userRepository
                .findByEmail(email)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(email);
                    newUser.setName(name);
                    return userRepository.save(newUser);
                });

        // Generate your existing JWT
        String token = jwtService.generateToken(user.getUsername());

        response.setContentType("application/json");

        response.getWriter().write(
                """
                {
                    "token": "%s"
                }
                """.formatted(token)
        );
    }
}

