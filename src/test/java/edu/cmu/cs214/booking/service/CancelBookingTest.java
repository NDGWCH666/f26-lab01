package edu.cmu.cs214.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.repo.InMemoryBookingStore;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BookingService#cancelBooking(String)}. Every scenario uses a
 * single room, so the assertions do not depend on how bookings for other rooms are
 * stored.
 */
class CancelBookingTest {

    private final Room roomA = new Room("A", "Alpha", 10);
    private final User alice = new User("u1", "Alice");
    private final User bob = new User("u2", "Bob");

    private final InMemoryBookingStore store = new InMemoryBookingStore();
    private final BookingService svc = new BookingService(store);

    /** Books, asserts the outcome was Confirmed, and returns the booking id. */
    private String confirm(User user, int start, int end) {
        BookingResult result = svc.book(roomA, user, new TimeInterval(start, end));
        if (result instanceof BookingResult.Confirmed confirmed) {
            return confirmed.booking().id();
        }
        throw new AssertionError("expected Confirmed but was " + result);
    }

    /** Books, asserting the outcome was Waitlisted. */
    private void waitlist(User user, int start, int end) {
        BookingResult result = svc.book(roomA, user, new TimeInterval(start, end));
        assertInstanceOf(BookingResult.Waitlisted.class, result);
    }

    @Test
    void cancelRemovesTheBookingAndFreesTheSlot() {
        String id = confirm(alice, 600, 660);

        svc.cancelBooking(id);

        assertTrue(svc.listBookings(roomA).isEmpty(), "the cancelled booking is gone");
        assertInstanceOf(BookingResult.Confirmed.class,
                svc.book(roomA, bob, new TimeInterval(600, 660)),
                "the freed slot can be booked again");
    }

    @Test
    void cancelWithUnknownIdChangesNothing() {
        String id = confirm(alice, 600, 660);

        svc.cancelBooking("no-such-booking");

        assertEquals(1, svc.listBookings(roomA).size());
        assertEquals(id, svc.listBookings(roomA).get(0).id());
    }
}
