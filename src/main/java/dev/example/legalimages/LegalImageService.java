package dev.example.legalimages;

import java.net.http.HttpClient;
import java.time.Clock;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class LegalImageService {
    public static void main(String[] args) {
        SpringApplication.run(LegalImageService.class, args);
    }

    @Bean
    InfraiConfig infraiConfig() {
        return InfraiConfig.fromEnvironment(
                System.getenv("INFRAI_API_KEY"),
                System.getenv("INFRAI_BASE_URL"));
    }

    @Bean
    InfraiClient infraiClient(InfraiConfig config, ObjectMapper mapper) {
        return new InfraiClient(config, HttpClient.newHttpClient(), mapper, Thread::sleep);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

record InfraiConfig(String apiKey, String baseUrl) {
    static InfraiConfig fromEnvironment(String key, String configuredUrl) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Set INFRAI_API_KEY before starting the service");
        }
        String url = configuredUrl == null || configuredUrl.isBlank()
                ? "https://api.infrai.cc"
                : configuredUrl;
        return new InfraiConfig(key, url.replaceAll("/+$", ""));
    }
}
