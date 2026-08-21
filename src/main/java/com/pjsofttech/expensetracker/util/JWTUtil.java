package com.pjsofttech.expensetracker.util;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JWTUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private SecretKey getKey(){
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(String username){
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()+expiration))
                .signWith(getKey())
                .compact();
    }
    public String getUsername(String token){
        return getClaims(token)
                .getSubject();
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token, String username, UserDetails userDetails) {
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);

    }
    public boolean isTokenExpired(String token){
        return getClaims(token).getExpiration().before(new Date());
    }

}
