package edu.cmu.cs214.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.booking.domain.Booking;
import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.repo.InMemoryBookingStore;
import java.util.List;
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
    private final User carol = new User("u3", "Carol");
    private final User dave = new User("u4", "Dave");

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

    private void assertHolders(User... expected) {
        List<Booking> actual = svc.listBookings(roomA);
        assertEquals(expected.length, actual.size(), "confirmed bookings: " + actual);
        for (User user : expected) {
            assertTrue(actual.stream().anyMatch(b -> b.user().equals(user)),
                    user.name() + " should hold a booking, got " + actual);
        }
    }

    private void assertWaiting(User... expected) {
        List<User> actual = store.waitlistForRoom(roomA).stream()
                .map(w -> w.user())
                .toList();
        assertEquals(List.of(expected), actual, "waitlist order should be arrival order");
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

    @Test
    void cancelPromotesTheEarliestWaiter() {
        String id = confirm(alice, 600, 660);
        waitlist(bob, 600, 660);

        svc.cancelBooking(id);

        assertHolders(bob);
        assertEquals(new TimeInterval(600, 660), svc.listBookings(roomA).get(0).interval());
        assertWaiting();
    }

    @Test
    void cancelPromotesAtMostOneWaiter() {
        String id = confirm(alice, 600, 660);
        waitlist(bob, 600, 660);
        waitlist(carol, 600, 660);

        svc.cancelBooking(id);

        assertHolders(bob);
        assertWaiting(carol);
    }

    @Test
    void cancelSkipsWaiterWhoseIntervalStillConflicts() {
        String id = confirm(alice, 600, 660);
        confirm(dave, 660, 720);
        waitlist(bob, 660, 720);
        waitlist(carol, 600, 660);

        svc.cancelBooking(id);

        assertHolders(carol, dave);
        assertWaiting(bob);
    }

    @Test
    void cancelPromotesNoOneWhenNoWaiterFits() {
        String id = confirm(alice, 600, 660);
        confirm(dave, 660, 720);
        waitlist(bob, 630, 690);

        svc.cancelBooking(id);

        assertHolders(dave);
        assertWaiting(bob);
    }

    @Test
    void unknownIdDoesNotPromoteWaiters() {
        confirm(alice, 600, 660);
        waitlist(bob, 600, 660);

        svc.cancelBooking("no-such-booking");

        assertHolders(alice);
        assertWaiting(bob);
    }

    @Test
    void promotedBookingGetsAFreshId() {
        String cancelled = confirm(alice, 600, 660);
        waitlist(bob, 600, 660);

        svc.cancelBooking(cancelled);

        Booking promoted = svc.listBookings(roomA).get(0);
        assertNotEquals(cancelled, promoted.id(),
                "promotion creates a new booking rather than reviving the old id");
    }

    @Test
    void successiveCancellationsPromoteOneWaiterEach() {
        String first = confirm(alice, 600, 660);
        String second = confirm(dave, 660, 720);
        waitlist(bob, 600, 660);
        waitlist(carol, 660, 720);

        svc.cancelBooking(first);

        assertHolders(bob, dave);
        assertWaiting(carol);

        svc.cancelBooking(second);

        assertHolders(bob, carol);
        assertWaiting();
    }
}
