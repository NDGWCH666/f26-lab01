package edu.cmu.cs214.booking.service;

import edu.cmu.cs214.booking.domain.Booking;
import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.domain.WaitlistEntry;
import edu.cmu.cs214.booking.repo.BookingStore;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Coordinates bookings and the waitlist. Enforces the core invariant: a room
 * never holds two confirmed bookings whose intervals overlap. Persistence is
 * delegated to a {@link BookingStore}.
 */
public class BookingService {

    private final BookingStore store;
    private int nextBookingSeq = 1;
    private int nextWaitlistSeq = 1;

    public BookingService(BookingStore store) {
        this.store = store;
    }

    /**
     * Attempts to book {@code room} for {@code user} over {@code interval}. If the
     * room is free over that interval the booking is confirmed; otherwise the user
     * is placed on the room's waitlist.
     */
    public BookingResult book(Room room, User user, TimeInterval interval) {
        for (Booking existing : store.bookingsForRoom(room)) {
            if (existing.interval().overlaps(interval)) {
                int position = store.waitlistForRoom(room).size() + 1;
                int seq = nextWaitlistSeq++;
                store.addWaitlistEntry(new WaitlistEntry("w" + seq, room, user, interval, seq));
                return new BookingResult.Waitlisted(position);
            }
        }
        Booking booking = new Booking("b" + nextBookingSeq++, room, user, interval);
        store.addBooking(booking);
        return new BookingResult.Confirmed(booking);
    }

    /** Returns the confirmed bookings for {@code room}. */
    public List<Booking> listBookings(Room room) {
        return store.bookingsForRoom(room);
    }

    /**
     * Cancels the booking with {@code bookingId}, freeing its slot, then promotes at
     * most one waiter into it.
     *
     * <p>The id is looked up <em>before</em> anything is removed, so an unknown id is a
     * genuine no-op and can never trigger a promotion.
     *
     * <p>Promotion walks the room's waitlist in arrival order and takes the first
     * waiter whose interval fits the room's <em>remaining</em> bookings. The promoted
     * booking is written straight to the store rather than through {@link #book}: the
     * overlap check already happened here, and {@code book} would push the waiter back
     * onto the waitlist, duplicating the entry and disturbing the arrival order.
     */
    public void cancelBooking(String bookingId) {
        Optional<Booking> found = store.findBooking(bookingId);
        if (found.isEmpty()) {
            return;
        }
        Room room = found.get().room();
        store.removeBooking(bookingId);

        for (WaitlistEntry waiter : waitersInArrivalOrder(room)) {
            if (fits(store.bookingsForRoom(room), waiter.interval())) {
                store.removeWaitlistEntry(waiter.id());
                store.addBooking(new Booking(
                        "b" + nextBookingSeq++, room, waiter.user(), waiter.interval()));
                return;
            }
        }
    }

    /** {@code room}'s waitlist, earliest arrival first. */
    private List<WaitlistEntry> waitersInArrivalOrder(Room room) {
        return store.waitlistForRoom(room).stream()
                .sorted(Comparator.comparingInt(WaitlistEntry::seq))
                .toList();
    }

    /** Is {@code interval} disjoint from every interval in {@code existing}? */
    private static boolean fits(List<Booking> existing, TimeInterval interval) {
        return existing.stream().noneMatch(b -> b.interval().overlaps(interval));
    }
}
