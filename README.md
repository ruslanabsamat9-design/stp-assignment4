# Assignment 4 – Facade over a three-stage chain (Flight Reservation)

Individual assignment, domain **#8 Flight Reservation**. One logical operation, *book a flight*, drives three independent subsystems:

| | Subsystem | What it does | Package |
|---|---|---|---|
| **A** | Seat reservation | `SeatMap` validates flight/seat, seat is reserved and confirmed | `reservation` |
| **B** | E-ticket + baggage | `BaggageRulesEngine` checks the baggage, the e-ticket is issued | `ticket` |
| **C** | Loyalty + meal | `MealPreferenceCatalog` checks the meal, preference is saved and points are awarded | `loyalty` |

Everything is persisted to JSON files (Jackson), so state survives a restart. A, B and C never import each other; they only meet inside the two facades.

## Run it (single commands)

Requires JDK 17+ and Maven.

```bash
mvn -q compile exec:java          # start the console demo (data folder: ./demo-data)
mvn verify                        # compile, run all tests, JaCoCo report + 80 % check
```

Coverage report: `target/site/jacoco/index.html`.

**Reset to a clean state before a fresh run:** either menu option **8 "Reset store"**, or simply delete the `demo-data/` folder (it is re-created automatically).

## Demo menu (what to show the examiner)

| Option | What happens |
|---|---|
| 1 | Normal booking with real input: seat → ticket → loyalty. Chain ends `COMPLETED`. |
| 2 | **B fails after A committed.** Baggage over 23 kg is rejected by subsystem B. Facade compensates **A only**. |
| 3 | **C fails after A and B committed.** Meal "Pizza" is not offered by subsystem C. Facade compensates **B, then A**. |
| 4 | **Crash after B.** The "process dies" with seat + ticket committed and the chain `PENDING_C` on disk. |
| 5 | Recover interrupted chains from disk (this also runs automatically on every application start). |
| 6 | Inspect everything that is persisted (reservations, tickets, loyalty, chain log with compensation order). |
| 7 | Admin facade: manual compensation of stuck chains. |
| 8 | Reset store. |

Both failures are *real* domain rules (no fake "failure flag" in the facade): any input that breaks the baggage rule or asks for a meal the airline does not serve triggers them, so the examiner can also trigger them with his own values (e.g. baggage `40`, meal `Sushi`). A seat that is already taken or does not exist fails at step A before anything is committed (nothing to undo).


### Durable chain state and recovery

After every step the facade writes a `WorkflowProgress` record to `workflow.json`:
`PENDING_B` (A committed) → `PENDING_C` (A and B committed) → `COMPLETED`; or `COMPENSATING` → `COMPENSATED`.

Recovery never trusts memory. `WorkflowCompensator` looks at the records that are really on disk: it finds the ticket **by reservation id in subsystem B's own store** (so even a ticket whose id never made it into the chain log is found), cancels it if present, then cancels the seat. Every step is idempotent, so a rollback that itself was interrupted (`COMPENSATING`) is simply finished on the next start. The order in which compensation ran is stored in the chain's `history` (e.g. `TICKET_CANCELLED…`, `SEAT_CANCELLED…`) and asserted by the tests. Recovery policy: interrupted chains are rolled back (the booking data needed to roll forward is not persisted on purpose).

### Exception handling (beyond the two compensation depths)

Own types: `InvalidBookingRequestException` (malformed request, rejected before anything is touched), `BookingFailedException` (carries `stage` A/B/C, the chain id and whether compensation happened), `SeatUnavailableException`, `InvalidSeatSelectionException`, `BaggageLimitExceededException`, `MealUnavailableException`, plus `*NotFoundException`s. A failure at step A is reported with `isCompensated() == false` because nothing had been committed.

## Principle of Least Knowledge – before / after

**Before (no facade)**: the client has to know four collaborators, the order of the steps, what each throws, and the compensation rules:

```java
SeatReservationService seats = new SeatReservationService(dir);
TicketService tickets = new TicketService(dir);
LoyaltyService loyalty = new LoyaltyService(dir);
WorkflowGateway log = new WorkflowGateway(dir);

Reservation r = seats.reserveSeat(name, flight, seat);                       // friend 1
log.save(new WorkflowProgress(id, name, r.getId(), null, WorkflowStatus.PENDING_B)); // friend 4
try {
    ETicket t = tickets.issueTicket(r.getId(), name, kg);                    // friend 2
    log.save(...PENDING_C...);
    loyalty.savePreference(name, meal);                                      // friend 3
    log.save(...COMPLETED...);
} catch (BaggageLimitExceededException e) {
    seats.cancelReservation(r.getId());                    // client knows: undo A only
} catch (MealUnavailableException e) {
    tickets.cancelTicket(t.getId());                       // client knows: undo B ...
    seats.cancelReservation(r.getId());                    // ... then A
}
// plus: client must recover half-done chains after a restart by itself
```

**After (through the facade)**: one immediate friend, no chains of calls into subsystems:

```java
FlightReservationFacade facade = new FlightReservationFacade(dir);
try {
    BookingReceipt receipt = facade.book(new BookingRequest(name, flight, seat, kg, meal));
} catch (BookingFailedException e) {
    // e.getStage(), e.isCompensated(): already rolled back by the facade
}
```

## Facade vs. Adapter (reflection)

An Adapter exists to make *one* existing interface look like a *different* interface the client already expects, it is about **incompatibility**: translating method names or data formats between two parties. Nothing in my problem is incompatible. `SeatReservationService`, `TicketService` and `LoyaltyService` all have clean, usable interfaces; the client does not need them to look like something else.

What the client actually suffers from is **complexity**: to "book a flight" it would have to know three subsystems, the order of their steps, the failure modes of each, and above all the compensation rules (B fails → undo A; C fails → undo B then A) and the persisted chain state needed to recover after a restart. The intent of the Facade is to hide exactly that: it offers one simplified, higher-level operation (`book`) and keeps the sequencing, decision logic and rollback knowledge in one place, while the subsystems stay unchanged and still usable on their own. An Adapter would translate one call to one other call; here one client call fans out into a coordinated sequence over three unrelated subsystems with state. That coordination is not translation, so the pattern is Facade, not Adapter (and the second, admin facade shows the same idea: a different, narrower simplified view of the same trio).

## Tests

`mvn verify` runs 55 JUnit 5 tests (about 99 % line coverage of the subsystems, the facades, the workflow log and the storage layer; JaCoCo fails the build below 80 %).

* **F.I.R.S.T.** Fast (all in a second or two), Independent/Repeatable (every test gets its own `@TempDir`, no shared state, no ordering), Self-validating (all assertions programmatic), Timely (see commit history).
* **Domain-specific testing language:** `FlightDesk` reads like a sentence, e.g.
  `desk.passenger("Selma").onFlight("FZ-104").inSeat("30A").wantingMeal("Pizza").tryToBook();`
  `desk.assertBookingFailedAtStage("C");`
  `desk.assertCompensationRanInOrder("TICKET_CANCELLED", "SEAT_CANCELLED");`
* **Both compensation depths:** `overweightBaggageFailsAtB_andOnlyTheSeatIsCompensated` and `unavailableMealFailsAtC_andTicketThenSeatAreCompensatedInThatOrder` assert the persisted end state.
* **Persistence reload mid-chain:** `PersistenceRecoveryTest` kills the "process" at `PENDING_B` / `PENDING_C`, restarts with brand-new objects over the same files and asserts that recovery uses the persisted status. It also covers an interrupted rollback and a ticket whose id was never recorded.
* **Coverage tool:** JaCoCo. `App` (the Scanner/println shell) is excluded from the 80 % rule on purpose; it contains no domain logic, everything it calls is covered through the facades.

## Project layout

```
src/main/java/com/flightreservation/
  App.java                         console demo
  facade/                          FlightReservationFacade, ReservationAdminFacade, WorkflowCompensator, DTOs, exceptions
  reservation/  ticket/  loyalty/  subsystems A, B, C
  workflow/                        durable chain log (status + history)
  storage/                         JsonFileStore (shared utility, not a subsystem)
src/test/java/com/flightreservation/   tests + FlightDesk DSL
docs/                              UML (puml, png, svg)
```
