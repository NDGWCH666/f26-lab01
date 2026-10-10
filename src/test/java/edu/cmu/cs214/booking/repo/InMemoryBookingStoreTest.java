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
 * Regression test for the milestone-1 root cause: {@link InMemoryBookingStore#bookingsForRoom}
 * used to return every booking regardless of room, violating the contract
 * "The confirmed bookings held for {@code room}".
 */
class InMemoryBookingStoreTest {

    private final Room roomA = new Room("A", "Alpha", 10);
    private final Room roomB = new Room("B", "Beta", 4);
    private final User alice = new User("u1", "Alice");
    private final User bob = new User("u2", "Bob");

    @Test
    void bookingsForRoomReturnsOnlyThatRoomsBookings() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        Booking a = new Booking("b1", roomA, alice, new TimeInterval(600, 660));
        Booking b = new Booking("b2", roomB, bob,   new TimeInterval(600, 660));
        store.addBooking(a);
        store.addBooking(b);

        List<Booking> forA = store.bookingsForRoom(roomA);
        List<Booking> forB = store.bookingsForRoom(roomB);

        assertEquals(1, forA.size(), "roomA should hold exactly its own booking");
        assertEquals("b1", forA.get(0).id());
        assertEquals(1, forB.size(), "roomB should hold exactly its own booking");
        assertEquals("b2", forB.get(0).id());
    }

    @Test
    void bookingsForRoomIsEmptyWhenRoomHasNoBookings() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        store.addBooking(new Booking("b1", roomA, alice, new TimeInterval(600, 660)));

        assertTrue(store.bookingsForRoom(roomB).isEmpty(),
                "a room with no bookings must not see another room's bookings");
    }
}
