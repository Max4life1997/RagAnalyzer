package ru.max.raganalyzer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public class FileStorageProperties {

    private String documentsDir;

    public String getDocumentsDir() {
        return documentsDir;
    }

    public void setDocumentsDir(String documentsDir) {
        this.documentsDir = documentsDir;
    }
}