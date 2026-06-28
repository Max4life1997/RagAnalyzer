package ru.max.raganalyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import ru.max.raganalyzer.config.FileStorageProperties;
import ru.max.raganalyzer.config.OllamaProperties;
import ru.max.raganalyzer.config.RagSearchProperties;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties({
        FileStorageProperties.class,
        OllamaProperties.class,
        RagSearchProperties.class
})
public class RagAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagAnalyzerApplication.class, args);
    }
}
