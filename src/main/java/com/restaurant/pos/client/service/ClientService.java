package com.restaurant.pos.client.service;

import com.restaurant.pos.client.domain.Client;
import com.restaurant.pos.client.repository.ClientRepository;
import com.restaurant.pos.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final com.restaurant.pos.common.context.TimezoneResolver timezoneResolver;

    public List<Client> getAllClients() {
        return clientRepository.findAll();
    }

    public Client getClientById(UUID id) {
        return clientRepository.findById(java.util.Objects.requireNonNull(id))
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + id));
    }

    public Client getClientByEmail(String email) {
        return clientRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with email: " + email));
    }

    @Transactional
    public Client updateClient(UUID id, Client clientDetails) {
        Client client = getClientById(id);
        client.setName(clientDetails.getName());
        client.setPhone(clientDetails.getPhone());
        client.setCountry(clientDetails.getCountry());
        client.setPosType(clientDetails.getPosType());
        
        // Multi-country / Registration fields
        client.setAddress(clientDetails.getAddress());
        client.setGstNumber(clientDetails.getGstNumber());
        client.setFssaiNumber(clientDetails.getFssaiNumber());
        client.setWebsite(clientDetails.getWebsite());
        client.setCurrency(clientDetails.getCurrency());
        
        // Branding & Operational
        client.setLogoUrl(clientDetails.getLogoUrl());
        client.setBrandColor(clientDetails.getBrandColor());
        client.setTimezone(clientDetails.getTimezone());
        client.setPrimaryLanguage(clientDetails.getPrimaryLanguage());

        // Social & Engagement
        client.setInstagramUrl(clientDetails.getInstagramUrl());
        client.setFacebookUrl(clientDetails.getFacebookUrl());
        client.setWhatsappNumber(clientDetails.getWhatsappNumber());

        // Location & Finance
        client.setGoogleMapsUrl(clientDetails.getGoogleMapsUrl());
        client.setPinCode(clientDetails.getPinCode());
        client.setBankName(clientDetails.getBankName());
        client.setAccountNumber(clientDetails.getAccountNumber());
        client.setIfscCode(clientDetails.getIfscCode());
        
        Client saved = clientRepository.save(client);
        timezoneResolver.evictCache(saved.getId(), null);
        return saved;
    }

    @Transactional
    public Client createClient(Client client) {
        // You might generate a specific Client ID here or let it fall back to standard behavior
        return clientRepository.save(java.util.Objects.requireNonNull(client));
    }
}
