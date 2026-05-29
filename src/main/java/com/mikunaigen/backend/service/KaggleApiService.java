package com.mikunaigen.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class KaggleApiService {

    private static final String API_BASE = "https://www.kaggle.com/api/v1";

    @Value("${app.kaggle.api.token:}")
    private String apiToken;

    @Value("${app.kaggle.kernel.propietario:}")
    private String kernelPropietario;

    @Value("${app.kaggle.kernel.slug:}")
    private String kernelSlug;

    public void ejecutarKernelEntrenamiento() {
        if (apiToken == null || apiToken.isBlank()) {
            throw new IllegalStateException("Token de Kaggle no configurado.");
        }
        if (kernelPropietario == null || kernelPropietario.isBlank()
                || kernelSlug == null || kernelSlug.isBlank()) {
            throw new IllegalStateException(
                    "Configure KAGGLE_KERNEL_PROPIETARIO y KAGGLE_KERNEL_SLUG en el servidor.");
        }

        String usuario = kernelPropietario.trim();
        String url = API_BASE + "/kernels/" + usuario + "/" + kernelSlug.trim() + "/run";

        RestTemplate rest = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        headers.set("Authorization", "Bearer " + apiToken.trim());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of(), headers);
        rest.postForEntity(url, request, String.class);
    }
}