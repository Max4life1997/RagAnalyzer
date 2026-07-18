package ru.max.raganalyzer.telegram;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class ByteArrayMultipartFile implements MultipartFile {

    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] content;

    public ByteArrayMultipartFile(String originalFilename, String contentType, byte[] content) {
        this.name             = "file";
        this.originalFilename = originalFilename;
        this.contentType      = contentType;
        this.content          = content;
    }

    @Override public String getName()             { return name; }
    @Override public String getOriginalFilename() { return originalFilename; }
    @Override public String getContentType()      { return contentType; }
    @Override public boolean isEmpty()            { return content.length == 0; }
    @Override public long getSize()               { return content.length; }
    @Override public byte[] getBytes()            { return content; }
    @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }

    @Override
    public void transferTo(java.io.File dest) throws java.io.IOException {
        try (var out = new java.io.FileOutputStream(dest)) {
            out.write(content);
        }
    }
}
