package com.datagami.edudron.student.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret:mySecretKey123456789012345678901234567890}")
    private String secret;

    private SecretKey getSigningKey() {
        // Ensure secret is at least 32 bytes (256 bits) for HS256
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 characters long. Current length: " + 
                (secret != null ? secret.length() : 0));
        }
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String extractTenantId(String token) {
        return extractClaim(token, claims -> claims.get("tenant", String.class));
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .setSigningKey(getSigningKey())
                .parseClaimsJws(token)
                .getBody();
    }

    public Boolean validateToken(String token) {
        try {
            Jwts.parser()
                .setSigningKey(getSigningKey())
                .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            // Log expiration details
            org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(JwtUtil.class);
            logger.warn("JWT token expired: expiredAt={}, tokenPrefix={}", 
                    e.getClaims().getExpiration(), 
                    token.length() > 30 ? token.substring(0, 30) + "..." : token);
            return false;
        } catch (MalformedJwtException e) {
            org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(JwtUtil.class);
            logger.warn("JWT token malformed: error={}, tokenPrefix={}", 
                    e.getMessage(), 
                    token.length() > 30 ? token.substring(0, 30) + "..." : token);
            return false;
        } catch (io.jsonwebtoken.security.SignatureException e) {
            org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(JwtUtil.class);
            logger.warn("JWT token signature invalid: error={}, tokenPrefix={}", 
                    e.getMessage(), 
                    token.length() > 30 ? token.substring(0, 30) + "..." : token);
            return false;
        } catch (IllegalArgumentException e) {
            org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(JwtUtil.class);
            logger.warn("JWT token validation failed - illegal argument: error={}, tokenPrefix={}", 
                    e.getMessage(), 
                    token != null && token.length() > 30 ? token.substring(0, 30) + "..." : token);
            return false;
        } catch (JwtException e) {
            org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(JwtUtil.class);
            logger.warn("JWT token validation failed: error={}, errorType={}, tokenPrefix={}", 
                    e.getMessage(), e.getClass().getSimpleName(),
                    token.length() > 30 ? token.substring(0, 30) + "..." : token);
            return false;
        } catch (Exception e) {
            org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(JwtUtil.class);
            logger.error("Unexpected error during JWT token validation: error={}, errorType={}", 
                    e.getMessage(), e.getClass().getSimpleName(), e);
            return false;
        }
    }
}

