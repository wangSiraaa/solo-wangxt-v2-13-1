package com.acme.tm.controller;

import com.acme.tm.dto.Dtos.*;
import com.acme.tm.model.BatchCandidate;
import com.acme.tm.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/candidates")
public class ReviewController {
    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * Accept / reject / resolve a candidate. Requires the caller's expectedVersion; a stale
     * reviewer gets 409 (version conflict) instead of producing a double review result.
     */
    @PostMapping("/{id}/decisions")
    public CandidateView decide(@PathVariable Long id, @Valid @RequestBody DecisionRequest req) {
        BatchCandidate c = reviewService.decide(id, req.reviewer(), req.action(), req.termOverride(),
                req.correctedTarget(), req.reason(), req.expectedVersion());
        return CandidateView.of(c);
    }

    @GetMapping("/{id}/history")
    public List<DecisionView> history(@PathVariable Long id) {
        return reviewService.history(List.of(id)).stream().map(DecisionView::of).toList();
    }
}
