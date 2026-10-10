package edu.cmu.cs214.booking.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.booking.domain.Booking;
import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@link BookingStore} contract for {@link InMemoryBookingStore}: queries
 * key by room id and must not bleed bookings from one room into another. The
 * starter code returned every booking regardless of room, which broke booking
 * and cancellation in ways that the service-level tests did not catch.
 */
class InMemoryBookingStoreTest {

    private final Room roomA = new Room("A", "Alpha", 10);
    private final Room roomB = new Room("B", "Beta", 10);
    private final User alice = new User("alice", "Alice");

    private Booking seed(Room room, User who, int start, int end) {
        return new Booking("id-" + room.id() + "-" + start, room, who,
                new TimeInterval(start, end));
    }

    @Test
    void bookingsForRoomReturnsOnlyMatchingRoom() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        Booking aliceBooking = seed(roomA, alice, 600, 660);
        store.addBooking(aliceBooking);
        store.addBooking(seed(roomB, alice, 600, 660));

        List<Booking> roomAListings = store.bookingsForRoom(roomA);

        assertEquals(1, roomAListings.size(),
                "bookings for room A must not include room B's booking");
        assertEquals(aliceBooking.id(), roomAListings.get(0).id());
    }

    @Test
    void emptyRoomReturnsEmptyList() {
        InMemoryBookingStore store = new InMemoryBookingStore();

        assertTrue(store.bookingsForRoom(roomA).isEmpty());
    }

    @Test
    void bookingsForRoomIsConsistentWithAllBookingsAfterRemove() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        Booking keep = seed(roomA, alice, 600, 660);
        Booking drop = seed(roomA, alice, 800, 860);
        store.addBooking(keep);
        store.addBooking(drop);
        store.removeBooking(drop.id());

        List<Booking> listings = store.bookingsForRoom(roomA);
        assertEquals(1, listings.size());
        assertEquals(keep.id(), listings.get(0).id());
    }
}
