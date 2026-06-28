package com.restaurant.pos.print.controller;

import com.restaurant.pos.print.domain.PrintStation;
import com.restaurant.pos.print.service.PrintStationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@RestController
@RequestMapping("/api/v1/public/print-stations")
@RequiredArgsConstructor
public class PrintSseController {

    private final PrintStationService stationService;
    private static final Map<UUID, List<SseEmitter>> emittersByClient = new ConcurrentHashMap<>();

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestParam String token) {
        PrintStation station = stationService.authenticate(token);
        UUID clientId = station.getClientId();

        SseEmitter emitter = new SseEmitter(86400000L); // 24 hours timeout
        emittersByClient.computeIfAbsent(clientId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(clientId, emitter));
        emitter.onTimeout(() -> removeEmitter(clientId, emitter));
        emitter.onError((e) -> removeEmitter(clientId, emitter));

        try {
            emitter.send(SseEmitter.event().name("init").data("connected"));
            log.info("Print station paired client connected to SSE stream: {}", station.getId());
        } catch (IOException e) {
            removeEmitter(clientId, emitter);
        }

        return emitter;
    }

    public static void publish(UUID clientId, UUID jobId) {
        if (clientId == null) return;
        List<SseEmitter> list = emittersByClient.get(clientId);
        if (list != null && !list.isEmpty()) {
            List<SseEmitter> deadEmitters = new ArrayList<>();
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().name("new-job").data(jobId.toString()));
                } catch (Exception e) {
                    deadEmitters.add(emitter);
                }
            }
            list.removeAll(deadEmitters);
        }
    }

    private static void removeEmitter(UUID clientId, SseEmitter emitter) {
        List<SseEmitter> list = emittersByClient.get(clientId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emittersByClient.remove(clientId);
            }
        }
    }
}
