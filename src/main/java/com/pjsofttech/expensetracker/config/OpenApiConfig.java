package com.pjsofttech.expensetracker.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "PJ SoftTech Expense Tracker API",
                version = "1.0",
                description = """
                        REST API for managing personal finances.

                        Features:
                        - User registration and authentication
                        - Bank account management
                        - Category management
                        - Expense management
                        - Asset management
                        - Liability management

                        JWT authentication is required for protected APIs.
                        """,
                contact = @Contact(
                        name = "PJ SoftTech",
                        email = "support@pjsofttech.com"
                )
        ),
        servers = {
                @Server(
                        url = "/",
                        description = "Local Server"
                )
        }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Enter your JWT token. Example: eyJhbGciOiJIUzI1NiJ9..."
)
public class OpenApiConfig {
}