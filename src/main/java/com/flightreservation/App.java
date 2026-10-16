package com.flightreservation;

import com.flightreservation.facade.BookingFailedException;
import com.flightreservation.facade.BookingReceipt;
import com.flightreservation.facade.BookingRequest;
import com.flightreservation.facade.FlightReservationFacade;
import com.flightreservation.facade.InvalidBookingRequestException;
import com.flightreservation.facade.ReservationAdminFacade;
import com.flightreservation.facade.SimulatedCrash;
import com.flightreservation.loyalty.LoyaltyProfile;
import com.flightreservation.reservation.Reservation;
import com.flightreservation.ticket.ETicket;
import com.flightreservation.workflow.WorkflowProgress;
import com.flightreservation.workflow.WorkflowStatus;

import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

/**
 * Console demo for the examiner. All state lives in JSON files (default folder: demo-data),
 * so quitting and starting again keeps everything.
 */
public class App {
    private final Path dataDir;
    private final Scanner in;
    private final FlightReservationFacade facade;
    private final ReservationAdminFacade admin;

    App(Path dataDir, Scanner in) {
        this.dataDir = dataDir;
        this.in = in;
        this.facade = new FlightReservationFacade(dataDir);
        this.admin = new ReservationAdminFacade(dataDir);
    }

    public static void main(String[] args) {
        Path dir = Path.of(args.length > 0 ? args[0] : "demo-data");
        new App(dir, new Scanner(System.in)).run();
    }

    void run() {
        System.out.println("Data folder: " + dataDir.toAbsolutePath());
        recoverOnStartup();

        while (true) {
            printMenu();
            if (!in.hasNextLine()) {
                return;
            }
            switch (in.nextLine().trim()) {
                case "1" -> book(null, null);
                case "2" -> {
                    System.out.println("Scenario: B fails after A. Baggage is forced to 35 kg (limit 23 kg).");
                    book(35.0, null);
                }
                case "3" -> {
                    System.out.println("Scenario: C fails after A and B. Meal is forced to 'Pizza' (not offered).");
                    book(null, "Pizza");
                }
                case "4" -> bookAndCrashAfterB();
                case "5" -> recover();
                case "6" -> inspectState();
                case "7" -> manualCompensation();
                case "8" -> {
                    admin.resetStore();
                    System.out.println("Store cleared.");
                }
                case "0" -> {
                    System.out.println("Bye.");
                    return;
                }
                default -> System.out.println("Please choose a valid menu option.");
            }
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println("=== Flight Reservation Demo ===");
        System.out.println("1. Book a flight (full chain A seat -> B e-ticket -> C loyalty/meal)");
        System.out.println("2. Demo: B fails after A  (overweight baggage)      -> A is compensated");
        System.out.println("3. Demo: C fails after A+B (meal not available)     -> B, then A are compensated");
        System.out.println("4. Demo: crash right after B (chain left PENDING_C on disk)");
        System.out.println("5. Recover interrupted chains (what happens on every start)");
        System.out.println("6. Inspect persisted state");
        System.out.println("7. Admin: manual compensation of stuck chains");
        System.out.println("8. Reset store");
        System.out.println("0. Exit");
        System.out.print("Choose an option: ");
    }

    private void book(Double forcedBaggage, String forcedMeal) {
        BookingRequest request = askRequest(forcedBaggage, forcedMeal);
        try {
            BookingReceipt receipt = facade.book(request);
            System.out.println("Booked! reservation=" + receipt.reservationId() + " ticket=" + receipt.ticketId()
                    + " chain=" + receipt.status());
        } catch (InvalidBookingRequestException e) {
            System.out.println("Rejected before anything was saved: " + e.getMessage());
        } catch (BookingFailedException e) {
            System.out.println("FAILED at step " + e.getStage() + ": " + e.getCause().getMessage());
            if (e.isCompensated()) {
                System.out.println("Compensation done. Chain log: " + admin.latestWorkflow()
                        .map(w -> w.getStatus() + " " + w.getHistory()).orElse("?"));
            } else {
                System.out.println("Nothing had been committed, so nothing needed to be undone.");
            }
        }
    }

    private void bookAndCrashAfterB() {
        BookingRequest request = askRequest(null, null);
        FlightReservationFacade doomed = new FlightReservationFacade(dataDir, progress -> {
            if (progress.getStatus() == WorkflowStatus.PENDING_C) {
                throw new SimulatedCrash("process killed after step B was committed");
            }
        });
        try {
            doomed.book(request);
            System.out.println("Booking completed without a crash (this should not happen).");
        } catch (InvalidBookingRequestException | BookingFailedException e) {
            System.out.println("Booking did not reach the crash point: " + e.getMessage());
        } catch (SimulatedCrash crash) {
            System.out.println("*** CRASH: " + crash.getMessage());
            System.out.println("On disk now: seat CONFIRMED, ticket ISSUED, chain PENDING_C. Use option 6 to look,");
            System.out.println("then option 5 (or restart the application) to let the facade recover from disk.");
        }
    }

    private void recoverOnStartup() {
        List<WorkflowProgress> recovered = facade.recoverInterruptedWorkflows();
        if (!recovered.isEmpty()) {
            System.out.println("Startup recovery rolled back " + recovered.size() + " interrupted chain(s):");
            recovered.forEach(w -> System.out.println(" - " + w.getWorkflowId() + " " + w.getStatus() + " " + w.getHistory()));
        }
    }

    private void recover() {
        List<WorkflowProgress> recovered = facade.recoverInterruptedWorkflows();
        if (recovered.isEmpty()) {
            System.out.println("Nothing to recover: no interrupted chains on disk.");
            return;
        }
        recovered.forEach(w -> System.out.println("Recovered " + w.getWorkflowId() + " -> " + w.getStatus() + " " + w.getHistory()));
    }

    private void manualCompensation() {
        List<WorkflowProgress> stuck = admin.findStuckWorkflows();
        if (stuck.isEmpty()) {
            System.out.println("No stuck chains (everything is COMPLETED or COMPENSATED).");
            return;
        }
        System.out.println("Stuck chains:");
        stuck.forEach(w -> System.out.println(" - " + w.getWorkflowId() + " | " + w.getPassengerName() + " | " + w.getStatus()));
        admin.compensateAllStuck().forEach(w ->
                System.out.println("Compensated " + w.getWorkflowId() + " -> " + w.getStatus() + " " + w.getHistory()));
    }

    private void inspectState() {
        System.out.println("Reservations:");
        for (Reservation r : admin.inspectReservations()) {
            System.out.println(" - " + r.getId() + " | " + r.getPassengerName() + " | " + r.getFlightNumber()
                    + " " + r.getSeatNumber() + " | " + r.getStatus());
        }
        System.out.println("Tickets:");
        for (ETicket t : admin.inspectTickets()) {
            System.out.println(" - " + t.getId() + " | reservation " + t.getReservationId() + " | "
                    + t.getBaggageKg() + " kg | " + t.getStatus());
        }
        System.out.println("Loyalty profiles:");
        for (LoyaltyProfile p : admin.inspectProfiles()) {
            System.out.println(" - " + p.getPassengerName() + " | meal " + p.getMealPreference() + " | "
                    + p.getPoints() + " pts | " + p.getStatus());
        }
        System.out.println("Chains:");
        for (WorkflowProgress w : admin.inspectWorkflows()) {
            System.out.println(" - " + w.getWorkflowId() + " | " + w.getPassengerName() + " | " + w.getStatus()
                    + (w.getFailureReason() == null ? "" : " | " + w.getFailureReason())
                    + (w.getHistory().isEmpty() ? "" : " | " + w.getHistory()));
        }
    }

    private BookingRequest askRequest(Double forcedBaggage, String forcedMeal) {
        String passenger = ask("Passenger name: ");
        String flight = ask("Flight number (e.g. FZ-101): ");
        String seat = ask("Seat (e.g. 12A, rows 1-45, A-F): ");
        double baggage = forcedBaggage != null ? forcedBaggage : askDouble("Baggage kg: ");
        String meal = forcedMeal != null ? forcedMeal : ask("Meal (Standard, Vegetarian, Vegan, Kosher, Halal, Gluten-Free): ");
        return new BookingRequest(passenger, flight, seat, baggage, meal);
    }

    private String ask(String prompt) {
        System.out.print(prompt);
        return in.hasNextLine() ? in.nextLine() : "";
    }

    private double askDouble(String prompt) {
        while (true) {
            String text = ask(prompt).trim().replace(',', '.');
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a number, e.g. 18.5");
            }
        }
    }
}
