package com.restaurant.pos.table.service;

import com.restaurant.pos.auth.service.EmailService;
import com.restaurant.pos.common.exception.ResourceNotFoundException;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.common.util.SecurityUtils;
import com.restaurant.pos.table.domain.RestaurantTable;
import com.restaurant.pos.table.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestaurantTableService {

    private final RestaurantTableRepository tableRepository;
    private final EmailService emailService;

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Transactional(readOnly = true)
    public List<RestaurantTable> getAllTables() {
        UUID clientId = TenantContext.getCurrentTenant();
        if (SecurityUtils.isSuperAdmin()) {
            return tableRepository.findByClientIdOrderByDisplayOrderAscTableNumberAsc(clientId);
        }
        return tableRepository.findByClientIdAndOrgIdOrderByDisplayOrderAscTableNumberAsc(clientId, TenantContext.getCurrentOrg());
    }

    @Transactional(readOnly = true)
    public List<RestaurantTable> getActiveTables() {
        UUID clientId = TenantContext.getCurrentTenant();
        if (SecurityUtils.isSuperAdmin()) {
            return tableRepository.findByClientIdAndIsactiveOrderByDisplayOrderAscTableNumberAsc(clientId, "Y");
        }
        return tableRepository.findByClientIdAndOrgIdAndIsactiveOrderByDisplayOrderAscTableNumberAsc(clientId, TenantContext.getCurrentOrg(), "Y");
    }

    @Transactional(readOnly = true)
    public RestaurantTable getTable(UUID id) {
        UUID clientId = TenantContext.getCurrentTenant();
        if (SecurityUtils.isSuperAdmin()) {
            return tableRepository.findByIdAndClientId(id, clientId)
                    .orElseThrow(() -> new ResourceNotFoundException("Table not found"));
        }
        return tableRepository.findByIdAndClientIdAndOrgId(id, clientId, TenantContext.getCurrentOrg())
                .orElseThrow(() -> new ResourceNotFoundException("Table not found"));
    }

    @Transactional(readOnly = true)
    public List<RestaurantTable> getTablesChangedSince(Instant since) {
        if (since == null) {
            return getAllTables();
        }
        UUID clientId = TenantContext.getCurrentTenant();
        LocalDateTime updatedAfter = LocalDateTime.ofInstant(since, ZoneOffset.UTC);
        if (SecurityUtils.isSuperAdmin()) {
            return tableRepository.findByClientIdAndUpdatedAtAfterOrderByDisplayOrderAscTableNumberAsc(clientId, updatedAfter);
        }
        return tableRepository.findByClientIdAndOrgIdAndUpdatedAtAfterOrderByDisplayOrderAscTableNumberAsc(
                clientId,
                TenantContext.getCurrentOrg(),
                updatedAfter
        );
    }

    @Transactional
    public RestaurantTable saveTable(RestaurantTable table) {
        boolean isNew = table.getId() == null;
        if (table.getClientId() == null) {
            table.setClientId(TenantContext.getCurrentTenant());
        }
        if (table.getOrgId() == null) {
            table.setOrgId(TenantContext.getCurrentOrg());
        }
        RestaurantTable saved = tableRepository.save(table);
        
        // On creation, automatically send QR mail to owner
        if (isNew) {
            String qrLink = String.format("%s/menu/%s/%s/%s", frontendUrl, saved.getClientId(), saved.getOrgId(), saved.getId());
            sendQRCode(saved.getId(), null, qrLink);
        }
        
        return saved;
    }

    @Transactional
    public RestaurantTable updateTableStatus(UUID id, String status) {
        RestaurantTable table = getTable(id);
        table.setStatus(status);
        return tableRepository.save(table);
    }

    @Transactional
    public void deleteTable(UUID id) {
        RestaurantTable table = getTable(id);
        
        // Validation: Ensure table is not "Used"
        if ("OCCUPIED".equalsIgnoreCase(table.getStatus())
                || "BILLED".equalsIgnoreCase(table.getStatus())
                || "RESERVED".equalsIgnoreCase(table.getStatus())) {
            throw new IllegalStateException("Cannot delete Table " + table.getTableNumber() + " because it is currently " + table.getStatus());
        }

        // Soft Delete
        table.setIsactive("N");
        tableRepository.save(table);
    }

    public void sendQRCode(UUID id, String targetEmail, String qrLink) {
        RestaurantTable table = getTable(id);
        String email = (targetEmail != null && !targetEmail.isEmpty()) 
                       ? targetEmail 
                       : SecurityUtils.getCurrentUserEmail();
        
        if (email != null && !email.isEmpty()) {
            emailService.sendTableQREmail(email, table.getTableNumber(), qrLink);
        }
    }
}
