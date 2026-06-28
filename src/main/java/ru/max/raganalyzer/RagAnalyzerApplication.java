package ru.max.raganalyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.max.raganalyzer.config.FileStorageProperties;
import ru.max.raganalyzer.config.OllamaProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        FileStorageProperties.class,
        OllamaProperties.class
})
public class RagAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagAnalyzerApplication.class, args);
    }
}