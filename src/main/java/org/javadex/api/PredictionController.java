package org.javadex.api;

import org.javadex.ml.CardPredictionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class PredictionController {

    private final CardPredictionService service;

    public PredictionController(CardPredictionService service) {
        this.service = service;
    }

    @PostMapping(value = "/api/predict", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public CardPredictionService.Prediction predict(@RequestPart("image") MultipartFile image) throws java.io.IOException {
        return service.predict(image.getBytes());
    }
}
