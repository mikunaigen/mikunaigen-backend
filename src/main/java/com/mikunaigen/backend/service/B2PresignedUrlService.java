package com.mikunaigen.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

@Service
public class B2PresignedUrlService {

    private final S3Presigner presigner;

    @Value("${app.backup.b2-bucket}")
    private String bucket;

    public B2PresignedUrlService(S3Presigner presigner) {
        this.presigner = presigner;
    }

    public String presignedGetUrl(String objectKey, Duration ttl) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Key requerida.");
        }
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl != null ? ttl : Duration.ofHours(24))
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey.trim())
                        .build())
                .build();
        return presigner.presignGetObject(presignRequest).url().toExternalForm();
    }
}
