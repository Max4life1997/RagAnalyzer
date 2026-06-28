package ru.max.raganalyzer.dto;

import java.util.List;

public record StreamEvent(
        String type,      // "meta" | "token" | "done" | "error"
        String text,
        List<SourceDto> sources,
        List<ImageDto> images
) {
    public static StreamEvent token(String text) {
        return new StreamEvent("token", text, null, null);
    }

    public static StreamEvent meta(List<SourceDto> sources, List<ImageDto> images) {
        return new StreamEvent("meta", null, sources, images);
    }

    public static StreamEvent done() {
        return new StreamEvent("done", null, null, null);
    }

    public static StreamEvent error(String message) {
        return new StreamEvent("error", message, null, null);
    }
}
