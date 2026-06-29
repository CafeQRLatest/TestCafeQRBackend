package com.restaurant.pos.subscription.service;

import com.restaurant.pos.client.repository.ClientRepository;
import com.restaurant.pos.common.context.TimezoneResolver;
import com.restaurant.pos.payment.service.RazorpayService;
import com.restaurant.pos.subscription.dto.SubscriptionPaymentRequest;
import com.restaurant.pos.subscription.repository.ClientSubscriptionModuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class SubscriptionTimezoneTest {

    private SubscriptionService subscriptionService;
    private TimezoneResolver timezoneResolver;
    private ClientSubscriptionModuleRepository clientSubscriptionModuleRepository;

    @BeforeEach
    public void setUp() {
        ClientRepository clientRepository = Mockito.mock(ClientRepository.class);
        RazorpayService razorpayService = Mockito.mock(RazorpayService.class);
        timezoneResolver = Mockito.mock(TimezoneResolver.class);
        clientSubscriptionModuleRepository = Mockito.mock(ClientSubscriptionModuleRepository.class);

        subscriptionService = new SubscriptionService(
                clientRepository,
                razorpayService,
                timezoneResolver,
                clientSubscriptionModuleRepository
        );
    }

    @Test
    public void testExpiryDateCalculationAlignsToEndOfDayInClientTimezone() {
        UUID clientId = UUID.randomUUID();
        // Mock timezoneResolver to return Asia/Kolkata (+05:30)
        ZoneId kolkataZone = ZoneId.of("Asia/Kolkata");
        when(timezoneResolver.resolveTimezone(any(UUID.class), any())).thenReturn(kolkataZone);

        // Run calculation
        LocalDateTime expiry = subscriptionService.calculateExpiryDate(null, clientId, null, 1);

        // Convert calculated expiry back to Kolkata timezone
        ZonedDateTime expiryInKolkata = expiry.atZone(ZoneId.systemDefault()).withZoneSameInstant(kolkataZone);

        // Check if hour, minute, second match exactly 23:59:59
        assertEquals(23, expiryInKolkata.getHour(), "Hour should be 23 in Kolkata time");
        assertEquals(59, expiryInKolkata.getMinute(), "Minute should be 59 in Kolkata time");
        assertEquals(59, expiryInKolkata.getSecond(), "Second should be 59 in Kolkata time");
    }

    @Test
    public void testExpiryDateCalculationWithActiveExistingExpiry() {
        UUID clientId = UUID.randomUUID();
        ZoneId kolkataZone = ZoneId.of("Asia/Kolkata");
        when(timezoneResolver.resolveTimezone(any(UUID.class), any())).thenReturn(kolkataZone);

        // Set an active future expiry date in the database: 10 days from now in the local zone
        ZonedDateTime futureExpiryInKolkata = ZonedDateTime.now(kolkataZone).plusDays(10).withHour(23).withMinute(59).withSecond(59).withNano(999000000);
        LocalDateTime systemFutureExpiry = futureExpiryInKolkata.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();

        LocalDateTime newExpiry = subscriptionService.calculateExpiryDate(systemFutureExpiry, clientId, null, 1);

        ZonedDateTime newExpiryInKolkata = newExpiry.atZone(ZoneId.systemDefault()).withZoneSameInstant(kolkataZone);

        // The new expiry should be exactly 1 month after the existing future expiry
        ZonedDateTime expectedExpiryInKolkata = futureExpiryInKolkata.plusMonths(1);
        
        assertEquals(expectedExpiryInKolkata.getYear(), newExpiryInKolkata.getYear());
        assertEquals(expectedExpiryInKolkata.getMonth(), newExpiryInKolkata.getMonth());
        assertEquals(expectedExpiryInKolkata.getDayOfMonth(), newExpiryInKolkata.getDayOfMonth());
        assertEquals(23, newExpiryInKolkata.getHour());
        assertEquals(59, newExpiryInKolkata.getMinute());
        assertEquals(59, newExpiryInKolkata.getSecond());
    }

    @Test
    public void testCalculateTotalAmountMonthlyWithKotAndSetup() {
        com.restaurant.pos.client.domain.Client client = new com.restaurant.pos.client.domain.Client();
        
        SubscriptionPaymentRequest request = SubscriptionPaymentRequest.builder()
                .billingCycle("MONTHLY")
                .modules(java.util.List.of(
                        com.restaurant.pos.subscription.domain.ModuleName.KOT,
                        com.restaurant.pos.subscription.domain.ModuleName.CRM
                ))
                .setupOption("WHITE_GLOVE")
                .build();
        
        // Base monthly (9900) + KOT monthly (5000) + CRM monthly (9900) + Setup White Glove (149900) = 174700 paise (₹1747)
        long total = subscriptionService.calculateTotalAmount(client, request);
        assertEquals(174700, total, "Total should be 174700 paise (₹1747)");
    }

    @Test
    public void testCalculateTotalAmountAnnualWithKotAndFreeModules() {
        com.restaurant.pos.client.domain.Client client = new com.restaurant.pos.client.domain.Client();
        
        SubscriptionPaymentRequest request = SubscriptionPaymentRequest.builder()
                .billingCycle("ANNUAL")
                .modules(java.util.List.of(
                        com.restaurant.pos.subscription.domain.ModuleName.KOT,
                        com.restaurant.pos.subscription.domain.ModuleName.TABLE_QR,
                        com.restaurant.pos.subscription.domain.ModuleName.ONLINE_DELIVERY
                ))
                .setupOption("DIY")
                .build();
        
        // Base annual (99900) + KOT annual (50000) + Table QR (0) + Online Delivery (0) = 149900 paise (₹1499)
        long total = subscriptionService.calculateTotalAmount(client, request);
        assertEquals(149900, total, "Total should be 149900 paise (₹1499)");
    }

    @Test
    public void testCalculateProratedAmountForActiveSubscription() {
        UUID clientId = UUID.randomUUID();
        com.restaurant.pos.client.domain.Client client = new com.restaurant.pos.client.domain.Client();
        client.setId(clientId);
        client.setSubscriptionStatus("ACTIVE");
        // Expiry in exactly 182 days (approx 6 months)
        client.setSubscriptionExpiryDate(LocalDateTime.now().plusDays(182));

        // Mock empty active modules in repository
        when(clientSubscriptionModuleRepository.findByClientId(clientId)).thenReturn(new java.util.ArrayList<>());

        SubscriptionPaymentRequest request = SubscriptionPaymentRequest.builder()
                .billingCycle("ANNUAL")
                .modules(java.util.List.of(com.restaurant.pos.subscription.domain.ModuleName.INVENTORY))
                .setupOption("DIY")
                .build();

        // Inventory Annual price = 199200 paise
        // Daily rate = 199200 / 365 = 545.7534 paise
        // 182 days cost = 545.7534 * 182 = 99327 paise (approx ₹993)
        long total = subscriptionService.calculateTotalAmount(client, request);
        
        // Base plan (₹999/yr) is NOT charged again, White-Glove Setup is NOT charged.
        // Total should be exactly the prorated amount of Inventory (~99327 paise)
        assertEquals(99327, total, "Total should be exactly the prorated Inventory price");
    }
}
