package ru.max.raganalyzer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.search")
public class RagSearchProperties {

    private int topK = 5;
    private int wikiTopK = 12; // Wiki-режим ищет по всей библиотеке — берём чуть больше чанков
    private double maxDistance = 0.75;

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getWikiTopK() {
        return wikiTopK;
    }

    public void setWikiTopK(int wikiTopK) {
        this.wikiTopK = wikiTopK;
    }

    public double getMaxDistance() {
        return maxDistance;
    }

    public void setMaxDistance(double maxDistance) {
        this.maxDistance = maxDistance;
    }
}