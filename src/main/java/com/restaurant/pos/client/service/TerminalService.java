package com.restaurant.pos.client.service;

import com.restaurant.pos.client.domain.Terminal;
import com.restaurant.pos.client.repository.TerminalRepository;
import com.restaurant.pos.common.exception.ResourceNotFoundException;
import com.restaurant.pos.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.util.SecurityUtils;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TerminalService {

    private final TerminalRepository repository;

    public List<Terminal> getMyTerminals() {
        UUID tenantId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        boolean isSuper = SecurityUtils.isSuperAdmin();
        Object authorities = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        
        System.out.println("===> [DEBUG] TerminalService.getMyTerminals: tenantId=" + tenantId + ", orgId=" + orgId + ", isSuperAdmin=" + isSuper + ", Authorities=" + authorities);
        
        List<Terminal> terminals;
        if (isSuper) {
            terminals = repository.findAllByClientId(tenantId);
        } else {
            terminals = repository.findAllByOrgIdAndClientId(orgId, tenantId);
        }
        
        System.out.println("===> [DEBUG] TerminalService.getMyTerminals: Found total=" + terminals.size());
        return terminals;
    }

    public List<Terminal> getTerminalsByOrg(UUID orgId) {
        UUID tenantId = TenantContext.getCurrentTenant();
        // If not super admin, they can only see terminals for their own org
        if (!SecurityUtils.isSuperAdmin() && !orgId.equals(TenantContext.getCurrentOrg())) {
            throw new com.restaurant.pos.common.exception.ResourceNotFoundException("Access denied to organization terminals");
        }
        
        return repository.findAllByOrgIdAndClientId(orgId, tenantId);
    }

    public Terminal getTerminalById(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenant();
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
