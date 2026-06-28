package ru.max.raganalyzer.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.max.raganalyzer.dto.SearchResultDto;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VectorStoreService {

    private final JdbcTemplate jdbcTemplate;

    public VectorStoreService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void updateChunkEmbedding(UUID chunkId, List<Double> embedding) {
        String vectorValue = toPgVector(embedding);

        int updatedRows = jdbcTemplate.update(
                """
                UPDATE document_chunks
                SET embedding = ?::vector
                WHERE id = ?
                """,
                vectorValue,
                chunkId
        );

        if (updatedRows == 0) {
            throw new IllegalStateException("Не удалось обновить embedding для chunkId: " + chunkId);
        }
    }

    public List<SearchResultDto> findSimilarChunks(List<Double> questionEmbedding, int limit) {
        String vectorValue = toPgVector(questionEmbedding);

        return jdbcTemplate.query(
                """
                SELECT
                    dc.id AS chunk_id,
                    d.id AS document_id,
                    d.original_file_name AS file_name,
                    dc.chunk_index,
                    dc.page_number,
                    dc.content,
                    dc.embedding <=> ?::vector AS distance
                FROM document_chunks dc
                JOIN documents d ON d.id = dc.document_id
                WHERE dc.embedding IS NOT NULL
                  AND d.status = 'INDEXED'
                ORDER BY dc.embedding <=> ?::vector
                LIMIT ?
                """,
                (rs, rowNum) -> {
                    double distance = rs.getDouble("distance");

                    return new SearchResultDto(
                            rs.getObject("chunk_id", UUID.class),
                            rs.getObject("document_id", UUID.class),
                            rs.getString("file_name"),
                            rs.getInt("chunk_index"),
                            rs.getInt("page_number"),
                            rs.getString("content"),
                            distance,
                            1.0 - distance
                    );
                },
                vectorValue,
                vectorValue,
                limit
        );
    }
    private String toPgVector(List<Double> embedding) {
        return embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }

    public List<SearchResultDto> findSimilarChunksByDocuments(
            List<Double> questionEmbedding,
            List<UUID> documentIds,
            int limit
    ) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }

        String vectorValue = toPgVector(questionEmbedding);

        String placeholders = documentIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(", "));

        String sql = """
            SELECT
                dc.id AS chunk_id,
                d.id AS document_id,
                d.original_file_name AS file_name,
                dc.chunk_index,
                dc.page_number,
                dc.content,
                dc.embedding <=> ?::vector AS distance
            FROM document_chunks dc
            JOIN documents d ON d.id = dc.document_id
            WHERE dc.embedding IS NOT NULL
              AND d.status = 'INDEXED'
              AND d.id IN (%s)
            ORDER BY dc.embedding <=> ?::vector
            LIMIT ?
            """.formatted(placeholders);

        List<Object> params = new java.util.ArrayList<>();
        params.add(vectorValue);
        params.addAll(documentIds);
        params.add(vectorValue);
        params.add(limit);

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    double distance = rs.getDouble("distance");

                    return new SearchResultDto(
                            rs.getObject("chunk_id", UUID.class),
                            rs.getObject("document_id", UUID.class),
                            rs.getString("file_name"),
                            rs.getInt("chunk_index"),
                            rs.getInt("page_number"),
                            rs.getString("content"),
                            distance,
                            1.0 - distance
                    );
                },
                params.toArray()
        );
    }
}