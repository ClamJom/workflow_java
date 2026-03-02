package com.example.demoworkflow.utils.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

public class Token {
    // 密钥
    private static final String secret = "workflowDemo";
    // 过期时间
    private static final long expiration = 24 * 60 * 60 * 1000L;

    public static String generateToken(){
        Date now = new Date();
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        return Jwts.builder()
                .signWith(key)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .id(UUID.randomUUID().toString())
                .subject("WorkflowDemo")
                .compact();
    }

    public static boolean checkToken(String token){
        if(null == token || token.isEmpty()) return false;
        Date now = new Date();
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        if(!claims.getSubject().equals("WorkflowDemo")) return false;
        return !claims.getExpiration().before(now);
    }

    public static String getTokenId(String token){
        assert null != token && !token.isEmpty();
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return claims.getId();
    }
}