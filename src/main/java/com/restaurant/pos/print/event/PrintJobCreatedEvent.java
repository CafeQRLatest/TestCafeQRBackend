package com.restaurant.pos.print.event;

import java.util.UUID;

public record PrintJobCreatedEvent(
        UUID clientId,
        UUID orgId,
        UUID printJobId,
        String jobKind,
        String printerProfileId
) {}
