package com.rafael.pixapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// CORS: o navegador bloqueia JavaScript de um domínio (github.io) chamando outro (onrender.com),
// a menos que o servidor diga explicitamente quais origens confia.
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    // Lido de application.properties (pix.cors.origens) ou da variável de ambiente PIX_CORS_ORIGENS.
    private final String[] origens;

    public CorsConfig(@Value("${pix.cors.origens}") String[] origens) {
        this.origens = origens;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(origens) // "patterns" aceita curinga de porta: http://localhost:*
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type")
                .maxAge(3600); // navegador guarda o preflight por 1h
    }
}
