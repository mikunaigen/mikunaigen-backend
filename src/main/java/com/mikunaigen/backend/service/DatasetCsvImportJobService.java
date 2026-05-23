package com.mikunaigen.backend.service;

import com.mikunaigen.backend.repository.sql.AlimentoDatasetRepository;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DatasetCsvImportJobService {

    private final AdminAlimentoDatasetService datasetService;
    private final AlimentoDatasetRepository alimentoRepo;
    private final ConcurrentHashMap<String, JobState> jobs = new ConcurrentHashMap<>();

    public DatasetCsvImportJobService(
            AdminAlimentoDatasetService datasetService,
            AlimentoDatasetRepository alimentoRepo
    ) {
        this.datasetService = datasetService;
        this.alimentoRepo = alimentoRepo;
    }

    public Map<String, Object> iniciarJob(int totalFilas) {
        String jobId = UUID.randomUUID().toString();
        long conteoInicialBd = alimentoRepo.count();
        jobs.put(jobId, new JobState(totalFilas, conteoInicialBd));
        return Map.of(
                "jobId", jobId,
                "total", totalFilas,
                "conteoInicialBd", conteoInicialBd
        );
    }

    @Async
    public void ejecutarImportacion(String jobId, java.util.List<String> lineas, UUID adminId) {
        JobState job = jobs.get(jobId);
        if (job == null) {
            return;
        }
        try {
            Map<String, Object> resultado = datasetService.importarLineasConProgreso(lineas, adminId, null);
            job.estado = "completado";
            job.mensaje = String.valueOf(resultado.get("message"));
            job.totalBd = ((Number) resultado.get("total")).longValue();
            job.registros = ((Number) resultado.get("registrosProcesados")).intValue();
        } catch (Exception e) {
            job.estado = "error";
            job.mensaje = e.getMessage() != null ? e.getMessage() : "Error al importar el CSV.";
        }
    }

    public Map<String, Object> consultarProgreso(String jobId) {
        JobState job = jobs.get(jobId);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Importación no encontrada.");
        }
        long registrosEnBd = alimentoRepo.count();
        long subidos = Math.max(0, registrosEnBd - job.conteoInicialBd);
        int actual = (int) Math.min(job.totalFilas, subidos);
        if ("completado".equals(job.estado)) {
            actual = job.totalFilas;
        }
        return Map.of(
                "actual", actual,
                "total", job.totalFilas,
                "registrosEnBd", registrosEnBd,
                "conteoInicialBd", job.conteoInicialBd,
                "estado", job.estado,
                "mensaje", job.mensaje != null ? job.mensaje : "",
                "registrosProcesados", job.registros,
                "totalBd", job.totalBd > 0 ? job.totalBd : registrosEnBd
        );
    }

    public void eliminarJob(String jobId) {
        jobs.remove(jobId);
    }

    private static final class JobState {
        private final int totalFilas;
        private final long conteoInicialBd;
        private volatile String estado = "procesando";
        private volatile String mensaje;
        private volatile int registros;
        private volatile long totalBd;

        private JobState(int totalFilas, long conteoInicialBd) {
            this.totalFilas = totalFilas;
            this.conteoInicialBd = conteoInicialBd;
        }
    }
}
