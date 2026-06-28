package ru.max.raganalyzer.controller;

import org.springframework.web.bind.annotation.*;
import ru.max.raganalyzer.dto.SearchRequest;
import ru.max.raganalyzer.dto.SearchResultDto;
import ru.max.raganalyzer.service.SearchService;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/test")
    public List<SearchResultDto> search(@RequestBody SearchRequest request) {
        return searchService.search(request);
    }
}