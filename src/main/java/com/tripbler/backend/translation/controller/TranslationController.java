package com.tripbler.backend.translation.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripbler.backend.translation.dto.TranslationRequest;
import com.tripbler.backend.translation.dto.TranslationResponse;
import com.tripbler.backend.translation.service.TranslationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/translation")
public class TranslationController {

    private final TranslationService translationService;

    public TranslationController(
        TranslationService translationService
    ) {
        this.translationService =
            translationService;
    }

    @PostMapping(
        produces = "application/json;charset=UTF-8"
    )
    public TranslationResponse translate(
        @Valid
        @RequestBody
        TranslationRequest request
    ) {
        return translationService.translate(
            request
        );
    }
}