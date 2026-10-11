package com.sakhtyar.notification.api;

import static com.sakhtyar.notification.api.NotificationDtos.*;
import com.sakhtyar.notification.application.NotificationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service){this.service=service;}

    @PostMapping("/outbox")
    public NotificationResponse enqueue(@Valid @RequestBody EnqueueRequest request){return service.enqueue(request);}
    @GetMapping("/outbox")
    public List<NotificationResponse> pending(@RequestParam(defaultValue="100") int limit){return service.pending(limit);}
    @PostMapping("/outbox/{id}/sent")
    public NotificationResponse sent(@PathVariable UUID id){return service.markSent(id);}
    @PostMapping("/outbox/{id}/failed")
    public NotificationResponse failed(@PathVariable UUID id,@RequestBody(required=false) Map<String,String> body){
        return service.markFailed(id,body==null?null:body.get("error"));
    }
}
