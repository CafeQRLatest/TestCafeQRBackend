package com.restaurant.pos.client.dto;

import com.restaurant.pos.client.domain.Organization;

import java.util.UUID;

public record OrganizationDto(
        UUID id,
        UUID clientId,
        UUID orgId,
        String name,
        String orgCode,
        String branchCode,
        String address,
        String phone,
        String email,
        String gstin,
        String isactive,
        String logoUrl,
        String googleMapsUrl,
        String pinCode,
        Double latitude,
        Double longitude,
        Double deliveryRadiusKm
) {

    public static OrganizationDto from(Organization organization) {
        if (organization == null) {
            return null;
        }

        return new OrganizationDto(
                organization.getId(),
                organization.getClientId(),
                organization.getId(),
                organization.getName(),
                organization.getOrgCode(),
                organization.getBranchCode(),
                organization.getAddress(),
                organization.getPhone(),
                organization.getEmail(),
                organization.getGstin(),
                organization.getIsactive(),
                organization.getLogoUrl(),
                organization.getGoogleMapsUrl(),
                organization.getPinCode(),
                organization.getLatitude(),
                organization.getLongitude(),
                organization.getDeliveryRadiusKm()
        );
    }
}
