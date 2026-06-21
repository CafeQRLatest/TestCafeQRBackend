package com.restaurant.pos.client.service;

import com.restaurant.pos.client.domain.Terminal;
import com.restaurant.pos.client.repository.TerminalRepository;
import com.restaurant.pos.common.exception.ResourceNotFoundException;
import com.restaurant.pos.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.util.SecurityUtils;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class TerminalService {

    private final TerminalRepository repository;

    public List<Terminal> getMyTerminals() {
        UUID tenantId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        boolean isSuper = SecurityUtils.isSuperAdmin();

        if (tenantId == null) {
            log.warn("Terminal lookup skipped because tenant context is missing. user={}", SecurityUtils.getCurrentUserEmail());
            return List.of();
        }

        if (isSuper) {
            return repository.findAllByClientId(tenantId);
        }

        if (orgId == null) {
            log.warn("Terminal lookup skipped because org context is missing. user={} clientId={}",
                    SecurityUtils.getCurrentUserEmail(), tenantId);
            return List.of();
        }

        return repository.findAllByOrgIdAndClientId(orgId, tenantId);
    }

    public List<Terminal> getTerminalsByOrg(UUID orgId) {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || orgId == null) {
            log.warn("Terminal org lookup skipped. user={} clientId={} orgId={}",
                    SecurityUtils.getCurrentUserEmail(), tenantId, orgId);
            return List.of();
        }

        // If not super admin, they can only see terminals for their own org
        if (!SecurityUtils.isSuperAdmin() && !Objects.equals(orgId, TenantContext.getCurrentOrg())) {
            throw new com.restaurant.pos.common.exception.ResourceNotFoundException("Access denied to organization terminals");
        }
        
        return repository.findAllByOrgIdAndClientId(orgId, tenantId);
    }

    public Terminal getTerminalById(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new ResourceNotFoundException("Terminal not found or access denied");
        }
        Optional<Terminal> terminal;
        
        if (SecurityUtils.isSuperAdmin()) {
            terminal = repository.findByIdAndClientId(id, tenantId);
        } else {
            terminal = repository.findByIdAndClientIdAndOrgId(id, tenantId, TenantContext.getCurrentOrg());
        }
        
        return terminal.orElseThrow(() -> new ResourceNotFoundException("Terminal not found or access denied"));
    }

    @Transactional
    public Terminal saveTerminal(Terminal terminal) {
        terminal.setClientId(TenantContext.getCurrentTenant());
        if (!SecurityUtils.isSuperAdmin() || terminal.getOrgId() == null) {
            terminal.setOrgId(TenantContext.getCurrentOrg());
        }
        
        if (terminal.getIsactive() == null) {
            terminal.setIsactive("Y");
        }

        // Validate device uniqueness
        if (terminal.getDeviceId() != null) {
            Optional<Terminal> existing;
            if (terminal.getId() != null) {
                existing = repository.findByDeviceIdAndIsactiveAndClientIdAndIdNot(
                    terminal.getDeviceId(), "Y", TenantContext.getCurrentTenant(), terminal.getId()
                );
            } else {
                existing = repository.findByDeviceIdAndIsactiveAndClientId(
                    terminal.getDeviceId(), "Y", TenantContext.getCurrentTenant()
                );
            }
            if (existing.isPresent()) {
                throw new BusinessException(
                    "This hardware device is already assigned to another terminal (" + existing.get().getName() + ")"
                );
            }
        }

        return repository.save(terminal);
    }

    @Transactional
    public void deleteTerminal(UUID id) {
        // Soft Delete: Just set isactive to 'N'
        Terminal terminal = getTerminalById(id);
        terminal.setIsactive("N");
        repository.save(terminal);
    }
}
