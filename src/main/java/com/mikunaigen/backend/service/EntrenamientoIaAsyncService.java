package com.mikunaigen.backend.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EntrenamientoIaAsyncService {

    private final EntrenamientoDatasetGithubService datasetGithub;
    private final KaggleApiService kaggleApi;
    private final EntrenamientoIaService entrenamientoIaService;

    public EntrenamientoIaAsyncService(
            EntrenamientoDatasetGithubService datasetGithub,
            KaggleApiService kaggleApi,
            @Lazy EntrenamientoIaService entrenamientoIaService
    ) {
        this.datasetGithub = datasetGithub;
        this.kaggleApi = kaggleApi;
        this.entrenamientoIaService = entrenamientoIaService;
    }

    @Async
    public void despacharDataset(String jobId) {
        try {
            datasetGithub.despacharExportacion(jobId);
        } catch (Exception e) {
            entrenamientoIaService.marcarErrorPublico(jobId,
                    "No se pudo iniciar la exportación del dataset: " + e.getMessage());
        }
    }

    @Async
    public void invocarKaggle(String jobId) {
        try {
            kaggleApi.ejecutarKernelEntrenamiento();
            entrenamientoIaService.marcarInvocacionKaggleOk(jobId);
        } catch (Exception e) {
            entrenamientoIaService.marcarErrorPublico(jobId, "No se pudo invocar Kaggle: " + e.getMessage());
        }
    }
}
