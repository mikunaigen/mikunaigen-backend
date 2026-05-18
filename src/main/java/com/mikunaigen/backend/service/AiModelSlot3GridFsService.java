package com.mikunaigen.backend.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

@Service
public class AiModelSlot3GridFsService {

    private static final Logger log = LoggerFactory.getLogger(AiModelSlot3GridFsService.class);
    private static final String META_SLOT = "slot";
    private static final int SLOT_3 = 3;

    private final GridFsTemplate gridFsTemplate;

    public AiModelSlot3GridFsService(GridFsTemplate gridFsTemplate) {
        this.gridFsTemplate = gridFsTemplate;
    }

    public String storeBytes(byte[] data, String filename) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Contenido vacío para GridFS.");
        }
        String name = filename == null || filename.isBlank() ? "slot3.bin" : filename.trim();
        Document meta = new Document(META_SLOT, SLOT_3).append("filename", name);
        ObjectId id = gridFsTemplate.store(new ByteArrayInputStream(data), name, meta);
        log.info("[SLOT3-GRIDFS] almacenado filename={} id={} bytes={}", name, id.toHexString(), data.length);
        return id.toHexString();
    }

    public void deleteIfPresent(String gridFsIdHex) {
        if (isBlank(gridFsIdHex)) {
            return;
        }
        try {
            ObjectId id = new ObjectId(gridFsIdHex.trim());
            gridFsTemplate.delete(Query.query(Criteria.where("_id").is(id)));
            log.debug("[SLOT3-GRIDFS] eliminado id={}", gridFsIdHex);
        } catch (Exception e) {
            log.warn("[SLOT3-GRIDFS] no se pudo eliminar id={}: {}", gridFsIdHex, e.getMessage());
        }
    }

    public byte[] readBytes(String gridFsIdHex) {
        if (isBlank(gridFsIdHex)) {
            return null;
        }
        ObjectId id;
        try {
            id = new ObjectId(gridFsIdHex.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        GridFSFile file = gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(id)));
        if (file == null) {
            return null;
        }
        GridFsResource res = gridFsTemplate.getResource(file);
        try {
            if (!res.exists()) {
                return null;
            }
            try (InputStream in = res.getInputStream()) {
                return in.readAllBytes();
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer archivo GridFS: " + e.getMessage());
        }
    }

    public String readAsDataUrlBase64(String gridFsIdHex) {
        byte[] b = readBytes(gridFsIdHex);
        if (b == null || b.length == 0) {
            return null;
        }
        return "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(b);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
