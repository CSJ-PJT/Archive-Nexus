package com.archivenexus.backend.web;
import com.archivenexus.backend.audit.*;import org.springframework.web.bind.annotation.*;import java.util.List;
@RestController @RequestMapping("/api/audit") public class AuditController{private final AuditService audit;public AuditController(AuditService audit){this.audit=audit;}@GetMapping public List<AuditLogEntity> recent(){return audit.recent();}}
