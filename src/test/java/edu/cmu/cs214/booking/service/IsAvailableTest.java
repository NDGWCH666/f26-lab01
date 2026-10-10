package edu.cmu.cs214.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.repo.InMemoryBookingStore;
import org.junit.jupiter.api.Test;

class IsAvailableTest {

    private final Room roomA = new Room("A", "Alpha", 10);
    private final User alice = new User("u1", "Alice");
    private final User bob = new User("u2", "Bob");

    private BookingService newService() {
        return new BookingService(new InMemoryBookingStore());
    }

    /**
     * The agent's isAvailable only checks whether an existing booking's <em>start</em>
     * falls inside the requested interval. When an existing booking starts earlier and
     * runs into the request (E=[570,630), Q=[600,660)), their overlap is missed.
     */
    @Test
    void notAvailableWhenAnEarlierBookingRunsIntoTheRequest() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(570, 630));

        boolean available = svc.isAvailable(roomA, new TimeInterval(600, 660));

        assertFalse(available,
                "E=[570,630) overlaps Q=[600,660); the room is not available");
    }

    /**
     * isAvailable must agree with what book actually does: when it says available, a
     * subsequent book should be Confirmed; when it says not available, book should
     * push the user to the waitlist.
     */
    @Test
    void isAvailableAgreesWithBook() {
        BookingService svc = newService();

        // Empty room -> available -> book confirms
        assertTrue(svc.isAvailable(roomA, new TimeInterval(600, 660)));
        assertInstanceOf(BookingResult.Confirmed.class,
                svc.book(roomA, alice, new TimeInterval(600, 660)));

        // Now the room is busy over [600,660).
        // A disjoint request [480,540) is still available.
        assertTrue(svc.isAvailable(roomA, new TimeInterval(480, 540)));
        // An overlapping request [630,700) is not.
        assertFalse(svc.isAvailable(roomA, new TimeInterval(630, 700)));
        BookingResult waitlisted = svc.book(roomA, bob, new TimeInterval(630, 700));
        assertInstanceOf(BookingResult.Waitlisted.class, waitlisted);
        assertEquals(1, ((BookingResult.Waitlisted) waitlisted).position());
    }
}
