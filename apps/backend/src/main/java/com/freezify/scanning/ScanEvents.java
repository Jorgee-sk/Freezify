package com.freezify.scanning;

import java.util.UUID;

/** Published after scanned food reaches the inventory, for modules that react to it (statistics). */
public final class ScanEvents {

    private ScanEvents() {}

    /** A member reviewed a scanned receipt and put its products in the inventory. */
    public record ReceiptScanned(UUID householdId, UUID userId, int items) {}
}
