# TEST GENERATION LOG — AdrSentinel SDET Suite

---

## Sessione del 2026-07-15T11:41+02:00

**Classe Analizzata:** ShipmentService.java

**File Generato:** ShipmentServiceTests.java

**Righe Generate:** 1773

**Copertura Target:** >85% branch & line coverage

---

### Metodi Coperti

- `getByTrackingNumber(String)` — 3 test (Happy, Failure, TDD-RED)
- `getByShipmentDate(LocalDate)` — 3 test (Happy, Edge, TDD-RED)
- `getByShipmentStatus(ShipmentStatus, Pageable)` — 2 test (Happy, Edge)
- `getAllShipment(Pageable)` — 1 test (Happy)
- `save(Shipment)` — 2 test (Happy, TDD-RED)
- `updateDetailsByTrackingNumber(String, ShipmentUpdateDTO)` — 4 test (Happy, Failure x3)
- `updateDateByTrackingNumber(String, ShipmentUpdateDateDTO)` — 5 test (Happy, Failure x2, Edge, Failure)
- `updateShipmentReasonByTrackingNumber(String, ShipmentUpdateReasonDTO)` — 3 test (Happy, Failure, TDD-RED)
- `updateTunnelRestrictionByTrackingNumber(TunnelRestriction, String)` — 4 test (Happy, Failure x2, TDD-RED)
- `updateStatusByTrackingNumber(String, ShipmentUpdateStatusDTO)` — 12 test (Nodi pozzo x2, Transizioni illegali x4, Happy x4, TDD-RED x3)
- `mapToEntity(ShipmentRequestDTO)` — 3 test (Happy, TDD-RED, Failure)
- `syncCacheAfterInsert` / `syncCacheAfterUpdate` — metodi privati, copertura indiretta via test pubblici

**Totale test generati:** 42

---

### Vulnerabilita'/Bug Rilevati (FASE RED TDD)

Questi test sono stati scritti appositamente per **FALLIRE** finche' lo sviluppatore non implementa le correzioni indicate. Sono marcati con `[TDD-RED]` nel Javadoc del file di test.

1. **[CRITICA] Assenza di null guard su `getByTrackingNumber(null)` e `getByShipmentDate(null)`**
   - *Falla:* Entrambi i metodi non verificano l'input null. La chiamata inolta la query al DB/invoca metodi su null con `NullPointerException` non gestita → HTTP 500.
   - *Fix:* Aggiungere `if (param == null) throw new BadRequestException(...)` come prima istruzione di entrambi i metodi.

2. **[CRITICA] `Enum.valueOf()` non protetto da try-catch in `updateShipmentReasonByTrackingNumber` e `updateStatusByTrackingNumber`**
   - *Falla:* Le chiamate `Enum.valueOf(ShipmentReason.class, ...)` e `Enum.valueOf(ShipmentStatus.class, ...)` lanciano `IllegalArgumentException` raw se il valore e' non valido (es. bypass del validatore upstream). Il service non intercetta questa eccezione → HTTP 500 invece di HTTP 400.
   - *Fix:* Avvolgere in try-catch per `IllegalArgumentException` e rilanciarla come `BadRequestException` con messaggio chiaro.

3. **[ALTA] Assenza di pre-validazione su null in `save(null)` e `updateTunnelRestrictionByTrackingNumber(null, ...)`**
   - *Falla:* `save()` accede a `newShipment.getTrackingNumber()` per il log prima di qualunque null check → `NullPointerException`. `updateTunnelRestrictionByTrackingNumber()` assegna `null` all'entita' via `setTunnelRestriction(null)`, bypassando il default dell'hook JPA → potenziale violazione di constraint DB.
   - *Fix:* Guard espliciti `if (param == null) throw new IllegalArgumentException(...)` all'inizio di entrambi i metodi.

4. **[ALTA] `DriverSnapshot.fromDrivers()` e `CustomerSnapshot.fromCustomers()` non protetti in `updateStatusByTrackingNumber` (PLANNED -> TRANSIT)**
   - *Falla:* Se la `Shipment` in stato PLANNED non ha autisti o clienti associati al momento della transizione, i factory method statici degli snapshot lanciano `IllegalArgumentException` non gestita → HTTP 500. La validazione del dominio ("una Shipment in transito deve avere autisti e clienti") dovrebbe produrre `IllegalShipmentStateException`.
   - *Fix:* Prima di chiamare `DriverSnapshot.fromDrivers()` verificare `shipment.getDrivers().isEmpty()` e lanciare `IllegalShipmentStateException`. Analogamente per `CustomerSnapshot.fromCustomers()` verificare `shipment.getCustomerAsMap().isEmpty()`.

5. **[MEDIA] `mapToEntity()` - Assenza di guard per SENDER/CARRIER mancanti nel DTO**
   - *Falla:* Il metodo assume `customers.get(CustomerRole.SENDER)` sia sempre non-null. Se il validatore upstream non blocca un DTO senza SENDER/CARRIER, la chiamata `.get(0)` su null lancia `NullPointerException` → HTTP 500.
   - *Fix:* Aggiungere `if (!customers.containsKey(CustomerRole.SENDER)) throw new BadRequestException("Missing SENDER in payload")` prima dell'estrazione.
# TEST GENERATION LOG — AdrSentinel SDET Suite

---

## [2026-07-15T15:58:00+02:00] — Shipment.java (Entity)

**Classe Analizzata:** Shipment
**File Generato:** ShipmentTests.java
**Righe Generate:** 1182
**Test Totali:** 43
**Stack:** JUnit 5 Jupiter · Mockito · AssertJ · Java Reflection (per lifecycle hook privati)
**Isolamento:** Puro — nessun contesto Spring, nessun H2, nessun ORM avviato.

---

### Metodi / Comportamenti Coperti

- **`getTrackingNumber()`** — Generazione UUID v4 non-null, unicità per-istanza, assenza setter pubblico.
- **`normalize()` (private @PrePersist/@PreUpdate)** — Sanificazione originAddress (CR/LF/TAB/spazi multipli/CRLF Windows), rimozione null e sanificazione destinationAddresses, fallback tunnelRestriction a B se null, non-sovrascrittura se già valorizzata, trim a stringa vuota.
- **`setId() / getId()`** — Chiave primaria surrogata.
- **`setShipmentDate() / getShipmentDate()`** — Data di spedizione, default non-null all'istanziazione.
- **`setShipmentStatus() / getShipmentStatus()`** — Tutti e 4 i valori enum (PLANNED, TRANSIT, DELIVERED, CANCELLED).
- **`setOriginAddress() / getOriginAddress()`** — Persistenza corretta del campo.
- **`setDestinationAddresses() / getDestinationAddresses()`** — Ordine tappe preservato, lista vuota di default.
- **`setShipmentReason() / getShipmentReason()`** — Tutti e 6 i valori enum (SALE, WASTE_DISPOSAL, UNCLEANED_EMPTY_RETURN, NON_COMPLIANT_RETURN, INTERNAL_TRANSFER, OUTSOURCED_PROCESSING).
- **`setVehicle() / getVehicle()`** — Riferimento mock, accettazione di null (campo nullable JPA).
- **`setSender() / getSender()`** — Riferimento al mittente.
- **`setCarrier() / getCarrier()`** — Riferimento al vettore.
- **`setDrivers(null)`** — Fallback difensivo a HashSet vuoto; set valido; default non-null.
- **`setReceivers(null)`** — Fallback difensivo a ArrayList vuota; lista valida; default non-null.
- **`setTunnelRestriction(null)`** — Fallback a TunnelRestriction.B; tutti valori enum validi; preservazione di NONE.
- **`getCustomerAsMap()`** — Happy Path (tutti i ruoli valorizzati), omissione SENDER/CARRIER/RECEIVER se null/vuoto, mappa vuota, istanza EnumMap, immutabilità lista SENDER (List.of()), immutabilità lista CARRIER (List.of()), lista RECEIVER con piu' destinatari.
- **`equals()`** — Riflessività, non-null, ineguaglianza per trackingNumber diversi, uguaglianza per trackingNumber identico (via reflection), type-mismatch.
- **`hashCode()`** — Hash code diversi per istanze diverse; contratto equals-hashCode verificato.
- **`toString()`** — Presenza campi chiave; inclusione valore effettivo trackingNumber.

---

### Vulnerabilita' / Bug Rilevati (Fase RED TDD)

I test seguenti sono scritti appositamente per FALLIRE con il codice attuale e devono essere portati in "verde" dallo sviluppatore implementando le correzioni indicate.

1. **[CRITICA] Hook `ensurePlannedShipmentIsNotInThePast()` dichiarato nel Javadoc ma non implementato.**
   Il Javadoc della classe cita esplicitamente questo metodo come guardia temporale per spedizioni PLANNED. Il codice sorgente ne e' privo. E' possibile persistere una spedizione PLANNED con shipmentDate nel passato senza alcuna eccezione, causando dati inconsistenti nel pianificatore e nell'integrazione HeiGIT.
   **Fix:** Implementare `@PrePersist @PreUpdate private void ensurePlannedShipmentIsNotInThePast()` che lanci `IllegalStateException` se `status == PLANNED && shipmentDate.isBefore(LocalDateTime.now())`.

2. **[ALTA] `normalize()` non valida `originAddress` vuoto post-sanificazione.**
   Un indirizzo di soli whitespace viene ridotto a stringa vuota "" senza errori. Un originAddress vuoto blocca silenziosamente il geocoding HeiGIT e la generazione del DDT a valle.
   **Fix:** In `normalize()` dopo trim: `if (originAddress != null && originAddress.isBlank()) throw new IllegalArgumentException("originAddress non puo' essere vuoto")`.

3. **[ALTA] `normalize()` non valida la lista `destinationAddresses` vuota post-pulizia.**
   Possibile persistere una spedizione con lista tappe vuota. Un viaggio senza destinazioni genera NullPointerException o errore applicativo nel Service Layer (instradamento HeiGIT, generazione DDT).
   **Fix:** In `normalize()` dopo `replaceAll(...)`: `if (destinationAddresses.isEmpty()) throw new IllegalStateException("destinationAddresses non puo' essere vuota")`.

4. **[MEDIA] `getDrivers()` espone il riferimento diretto al HashSet interno (violazione encapsulation).**
   Un chiamante esterno puo' modificare il set degli autisti senza passare per setDrivers(), aggirando qualsiasi futura validazione. Viola il principio del Rich Domain Model.
   **Fix:** `getDrivers()` deve restituire `Collections.unmodifiableSet(drivers)` o una copia difensiva `new HashSet<>(drivers)`.

---
