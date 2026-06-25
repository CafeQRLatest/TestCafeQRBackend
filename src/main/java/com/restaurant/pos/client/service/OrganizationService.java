package com.restaurant.pos.client.service;

import com.restaurant.pos.client.domain.Organization;
import com.restaurant.pos.client.domain.Client;
import com.restaurant.pos.client.repository.OrganizationRepository;
import com.restaurant.pos.client.repository.ClientRepository;
import com.restaurant.pos.common.exception.ResourceNotFoundException;
import com.restaurant.pos.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationService {

    private final OrganizationRepository repository;
    private final ClientRepository clientRepository;
    private final com.restaurant.pos.common.context.TimezoneResolver timezoneResolver;

    public List<Organization> getMyOrganizations() {
        UUID tenantId = TenantContext.getCurrentTenant();
        log.info("Fetching organizations for Client ID: {}", tenantId);
        List<Organization> orgs = repository.findAllByClientId(tenantId);
        log.info("Found {} organizations for Client ID: {}", orgs.size(), tenantId);
        return orgs;
    }

    public Organization getOrganizationById(UUID id) {
        return repository.findByIdAndClientId(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
    }

    @Transactional
    public Organization createOrganization(Organization organization) {
        log.info("Creating new organization: {}", organization);
        UUID clientId = TenantContext.getCurrentTenant();
        organization.setClientId(clientId);
        organization.setIsactive("Y");

        // Set default orgCode if not provided
        if (organization.getOrgCode() == null || organization.getOrgCode().isBlank()) {
            String slug = organization.getName().toLowerCase().replaceAll("[^a-z0-9]", "-");
            String code = slug + "-" + UUID.randomUUID().toString().substring(0, 4);
            organization.setOrgCode(code);
            log.info("Generated default orgCode: {}", code);
        }

        Client client = clientRepository.findById(java.util.Objects.requireNonNull(clientId))
                .orElseThrow(() -> new ResourceNotFoundException("Client not found for ID: " + clientId));
        organization.setClient(client);

        return repository.save(organization);
    }

    @Transactional
    public Organization updateOrganization(UUID id, Organization details) {
        Organization organization = getOrganizationById(id);
        organization.setName(details.getName());
        organization.setOrgCode(details.getOrgCode());
        organization.setAddress(details.getAddress());
        organization.setPhone(details.getPhone());
        organization.setEmail(details.getEmail());
        organization.setGstin(details.getGstin());
        organization.setLogoUrl(details.getLogoUrl());
        organization.setGoogleMapsUrl(details.getGoogleMapsUrl());
        organization.setPinCode(details.getPinCode());
        organization.setLatitude(details.getLatitude());
        organization.setLongitude(details.getLongitude());
        organization.setDeliveryRadiusKm(details.getDeliveryRadiusKm());
        organization.setBranchCode(details.getBranchCode());
        organization.setTimezone(details.getTimezone());
        
        if (details.getIsactive() != null) {
            organization.setIsactive(details.getIsactive());
        }

        Organization saved = repository.save(organization);
        timezoneResolver.evictCache(saved.getClientId(), saved.getId());
        return saved;
    }

    @Transactional
    public void deleteOrganization(UUID id) {
        // Soft Delete
        Organization organization = repository.findByIdAndClientId(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        organization.setIsactive("N");
        repository.save(organization);
    }
}
