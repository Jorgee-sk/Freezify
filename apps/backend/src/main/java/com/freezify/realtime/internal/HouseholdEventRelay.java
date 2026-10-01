package com.freezify.realtime.internal;

import com.freezify.households.HouseholdEvents.HouseholdDeleted;
import com.freezify.households.HouseholdEvents.MemberRemoved;
import com.freezify.inventory.InventoryEvents.InventoryChanged;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Turns what the other modules commit into notifications for the connected members. */
@Component
class HouseholdEventRelay {

    static final String INVENTORY_CHANGED = "inventory-changed";

    private final HouseholdEventStreams streams;

    HouseholdEventRelay(HouseholdEventStreams streams) {
        this.streams = streams;
    }

    @TransactionalEventListener
    void on(InventoryChanged event) {
        streams.publish(event.householdId(), INVENTORY_CHANGED);
    }

    /** Someone who is no longer a member must stop hearing about the household at once. */
    @TransactionalEventListener
    void on(MemberRemoved event) {
        streams.close(event.householdId(), userId -> userId.equals(event.userId()));
    }

    @TransactionalEventListener
    void on(HouseholdDeleted event) {
        streams.close(event.householdId(), userId -> true);
    }
}
