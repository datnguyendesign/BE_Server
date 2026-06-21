package com.example.dacn2_beserver.controller;

import com.example.dacn2_beserver.dto.ai.AiFoodPredictRequest;
import com.example.dacn2_beserver.dto.ai.AiFoodPredictResponse;
import com.example.dacn2_beserver.service.ai.AiFoodClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiFoodClient aiFoodClient;

    @PostMapping(value = "/food-predict", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AiFoodPredictResponse test(@RequestBody AiFoodPredictRequest req) {
        return aiFoodClient.predictFoodByUrl(req.getImageUrl());
    }
}