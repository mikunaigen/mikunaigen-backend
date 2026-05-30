package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.sql.*;
import com.mikunaigen.backend.repository.sql.*;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class FormulacionExportacionService {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final String DESCARGO =
            "La receta presentada es de naturaleza teórico-simulada y no reemplaza la validación "
                    + "en laboratorio ni constituye algún asesoramiento médico. Favor de verificar la información.";

    private final InferenciaRecetaRepository inferenciaRepo;
    private final ComposicionRecetaRepository composicionRepo;
    private final AlimentoDatasetRepository alimentoRepo;
    private final UserRepository userRepo;
    private final AuditoriaExportacionRepository auditoriaRepo;
    private final FormulacionCuotaService cuotaService;

    public FormulacionExportacionService(
            InferenciaRecetaRepository inferenciaRepo,
            ComposicionRecetaRepository composicionRepo,
            AlimentoDatasetRepository alimentoRepo,
            UserRepository userRepo,
            AuditoriaExportacionRepository auditoriaRepo,
            FormulacionCuotaService cuotaService
    ) {
        this.inferenciaRepo = inferenciaRepo;
        this.composicionRepo = composicionRepo;
        this.alimentoRepo = alimentoRepo;
        this.userRepo = userRepo;
        this.auditoriaRepo = auditoriaRepo;
        this.cuotaService = cuotaService;
    }

    @Transactional
    public byte[] exportarFicha(UUID usuarioId, UUID inferenciaId, String formato, User user) {
        String rol = cuotaService.normalizarRol(user);
        if ("estudiante".equals(rol)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Exportación disponible en Plan Emprendedor y Nutricionista.");
        }
        if ("pdf".equalsIgnoreCase(formato) && !"nutricionista".equals(rol)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Exportación PDF disponible solo para Plan Nutricionista.");
        }

        InferenciaReceta inf = inferenciaRepo.findById(inferenciaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Receta no encontrada."));
        if (!inf.getUsuarioId().equals(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado.");
        }

        byte[] bytes = "pdf".equalsIgnoreCase(formato)
                ? generarPdf(user, inf)
                : generarExcel(user, inf);

        AuditoriaExportacion audit = new AuditoriaExportacion();
        audit.setInferenciaId(inferenciaId);
        audit.setUsuarioId(usuarioId);
        audit.setRolUsuarioMomento(rol);
        audit.setFormatoExportacion(formato.toLowerCase(Locale.ROOT));
        auditoriaRepo.save(audit);
        return bytes;
    }

    public String nombreArchivo(String formato) {
        String ts = LocalDateTime.now(ZONA_LIMA).format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String ext = "pdf".equalsIgnoreCase(formato) ? ".pdf" : ".xlsx";
        return "receta_superalimento_" + ts + ext;
    }

    private byte[] generarExcel(User user, InferenciaReceta inf) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Ficha técnica");
            int row = 0;
            row = fila(sheet, row, "Usuario", user.getNombres() + " " + user.getApellidos());
            row = fila(sheet, row, "Fecha", String.valueOf(inf.getFechaGeneracion()));
            row = fila(sheet, row, "Modo optimización", inf.getModoOptimizacion());
            row = fila(sheet, row, "Costo estimado S/kg", str(inf.getCostoEstimadoKg()));
            row = fila(sheet, row, "MAE", str(inf.getMargenErrorMae()));
            row++;
            fila(sheet, row++, "Ingrediente", "Porcentaje (%)");
            for (ComposicionReceta c : composicionRepo.findByInferenciaIdOrderByPorcentajeDesc(inf.getId())) {
                String nombre = alimentoRepo.findById(c.getAlimentoId()).map(AlimentoDataset::getNombre).orElse("—");
                fila(sheet, row++, nombre, str(c.getPorcentaje()));
            }
            row++;
            fila(sheet, row++, "Descargo", DESCARGO);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el Excel.");
        }
    }

    private byte[] generarPdf(User user, InferenciaReceta inf) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph("Ficha técnica - Receta de superalimento", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
            doc.add(new Paragraph("Usuario: " + user.getNombres() + " " + user.getApellidos()));
            doc.add(new Paragraph("Fecha: " + inf.getFechaGeneracion()));
            doc.add(new Paragraph("Modo: " + inf.getModoOptimizacion()));
            doc.add(new Paragraph("Costo S/kg: " + str(inf.getCostoEstimadoKg())));
            doc.add(new Paragraph("MAE: " + str(inf.getMargenErrorMae())));
            doc.add(new Paragraph(" "));
            PdfPTable table = new PdfPTable(2);
            table.addCell(celda("Ingrediente"));
            table.addCell(celda("Porcentaje (%)"));
            for (ComposicionReceta c : composicionRepo.findByInferenciaIdOrderByPorcentajeDesc(inf.getId())) {
                String nombre = alimentoRepo.findById(c.getAlimentoId()).map(AlimentoDataset::getNombre).orElse("—");
                table.addCell(celda(nombre));
                table.addCell(celda(str(c.getPorcentaje())));
            }
            doc.add(table);
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(DESCARGO, FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9)));
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el PDF.");
        }
    }

    private static int fila(Sheet sheet, int rowIdx, String... valores) {
        Row row = sheet.createRow(rowIdx);
        for (int i = 0; i < valores.length; i++) {
            row.createCell(i).setCellValue(valores[i]);
        }
        return rowIdx + 1;
    }

    private static PdfPCell celda(String texto) {
        return new PdfPCell(new Phrase(texto != null ? texto : ""));
    }

    private static String str(Object o) {
        return o != null ? String.valueOf(o) : "—";
    }
}
