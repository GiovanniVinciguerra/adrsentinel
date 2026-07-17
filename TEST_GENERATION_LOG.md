# TEST GENERATION LOG — AdrSentinel SDET Suite

---

## Sessione del 2026-07-15T11:41+02:00

**Classe Analizzata:** ShipmentService.java
**File Generato:** ShipmentServiceTests.java
**Righe Generate:** 1792
**Test Totali:** 44
**Stack:** JUnit 5 Jupiter · Mockito · AssertJ · Java Reflection (per lifecycle hook privati)
**Isolamento:** Puro — nessun contesto Spring, nessun H2, nessun ORM avviato.

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
**Righe Generate:** 1164
**Test Totali:** 51
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

## [2026-07-16T01:53:46+02:00] — AdrClassService.java

**Classe Analizzata:** AdrClassService
**File Generato:** AdrClassServiceTests.java
**Righe Generate:** 1117
**Test Totali:** 23
**Stack:** JUnit 5 Jupiter · Mockito · AssertJ · Java Reflection (per metodo privato `syncCacheAfterInsert`)
**Isolamento:** Puro — nessun contesto Spring, nessun H2, nessun ORM avviato.

---

### Metodi Coperti

- **`getByClassCode(String)`** — 6 test (Happy Path, Failure Path, Edge Case x2, TDD-RED x2)
- **`getAllAdrClasses()`** — 3 test (Happy Path, Edge Case vuoto, Edge Case singolo record)
- **`save(AdrClass)`** — 4 test (Happy Path, Failure Path duplicato, TDD-RED x2)
- **`syncCacheAfterInsert(AdrClass)` (privato, via Reflection)** — 4 test (Happy Path x2, Edge Case Cache Miss, Edge Case Upsert)
- **`mapToEntity(AdrClassRequestDTO)`** — 6 test (Happy Path x2, Isolation Path, TDD-RED x3)

---

### Vulnerabilita' / Bug Rilevati (Fase RED TDD)

I test seguenti sono scritti appositamente per FALLIRE con il codice attuale e devono essere portati in "verde" dallo sviluppatore implementando le correzioni indicate.

1. **[CRITICA] Assenza di null/blank guard in `getByClassCode(String)`.**
   - *Falla:* Il metodo non valida l'input prima di delegare al repository. `getByClassCode(null)` causa `NullPointerException` non gestita (HTTP 500). `getByClassCode("   ")` invia una stringa blank al DB producendo query non semantica.
   - *Fix:* Aggiungere come prima istruzione: `if (classCode == null || classCode.isBlank()) throw new IllegalArgumentException("classCode cannot be null or blank");`

2. **[CRITICA] Assenza di null guard in `save(AdrClass)` su entita' null e su `classCode` null nell'entita'.**
   - *Falla 1:* `save(null)` accede a `newAdrClass.getClassCode()` per il log (riga 105) senza guard preventivo, causando `NullPointerException` (HTTP 500) anziche' `IllegalArgumentException`.
   - *Falla 2:* `save(entityConClassCodeNull)` bypassa il service senza eccezione semantica e arriva al DB, dove viola il constraint `nullable = false` con eccezione JPA non contestualizzata.
   - *Fix:* Aggiungere all'inizio di `save`: `if (newAdrClass == null) throw new IllegalArgumentException("entity cannot be null"); if (newAdrClass.getClassCode() == null || newAdrClass.getClassCode().isBlank()) throw new IllegalArgumentException(...);`

3. **[ALTA] Assenza di null guard in `mapToEntity(AdrClassRequestDTO)` su DTO null, `classCode` null e `description` null.**
   - *Falla:* Il metodo accede direttamente a `dto.classCode()` e `dto.description()` (righe 186-187) senza validazione preventiva. Qualsiasi campo null produce `NullPointerException` o un'entita' con stato invalido che viola i constraint `nullable = false` del DB al momento del persist.
   - *Fix:* Aggiungere guard clause all'inizio del metodo per `dto`, `dto.classCode()` e `dto.description()` lanciando `IllegalArgumentException` con messaggi contestuali.

4. **[MEDIA] Nessuna protezione contro la corruzione del contesto transazionale in `save()` quando invocato fuori da una transazione attiva.**
   - *Falla:* Il metodo chiama `TransactionSynchronizationManager.registerSynchronization()` senza verificare preventivamente che una transazione sia attiva. Se invocato in un contesto non transazionale (es. da un job schedulato o da un test senza setup), lancia `IllegalStateException: Transaction synchronization is not active` non gestita.
   - *Fix:* Aggiungere controllo `if (TransactionSynchronizationManager.isSynchronizationActive())` prima di registrare il synchronization, oppure demandare la chiamata a un metodo separato annotato con `@Transactional`.

---

## [2026-07-16T13:50:00+02:00] — AdrClass.java (Entity)

**Classe Analizzata:** AdrClass
**File Generato:** AdrClassTest.java
**Righe Generate:** 1510
**Test Totali:** 50
**Stack:** JUnit 5 Jupiter · AssertJ · Java Reflection (per metodo privato `normalize()`)
**Isolamento:** Puro — nessun contesto Spring, nessun H2, nessun ORM avviato. Zero mock (entità POJO pura).

---

### Metodi Coperti

- **`getId()` / `setId(Long)`** — 3 test (Transient state null, Happy Path, Accept-null)
- **`getClassCode()` / `setClassCode(String)`** — 2 test (Happy Path, Accept-null)
- **`getDescription()` / `setDescription(String)`** — 2 test (Happy Path, Accept-null)
- **`normalize()` (private @PrePersist/@PreUpdate, via Reflection)** — 12 test (Trim+Uppercase, Idempotency, Null classCode, Null description, Newline replacement, CRLF replacement, Tab replacement, Multiple-space collapse, Description trim, Full pipeline, Boundary 4-char, RED blank classCode, RED blank description)
- **`equals(Object)`** — 7 test (Reflexivity, Symmetry, Different classCode, Null argument, Different type, One-null classCode, HashSet integration, RED dual-null classCode)
- **`hashCode()`** — 4 test (Consistency, Different hash, Null-safe, Stability)
- **`compareTo(AdrClass)`** — 7 test (Negative result, Positive result, Zero result, Consistency with equals, TreeSet ordering, Collections.sort ordering, RED null this.classCode, RED null classB.classCode)
- **`toString()`** — 4 test (All fields present, All-null entity, Exact format match, Max-length classCode)
- **Scenari Composti** — 5 test (Post-normalize equals+hash, List.remove after normalize, Boundary 1-char classCode, Boundary 3-char description, RED TreeSet+null NPE)

---

### Vulnerabilita' / Bug Rilevati (Fase RED TDD)

I test seguenti sono scritti **appositamente per FALLIRE** con il codice attuale e devono essere portati in "verde" dallo sviluppatore implementando le correzioni indicate.

1. **[CRITICA] `compareTo()` causa NullPointerException non gestita se `this.classCode` o `classB.classCode` sono null.**
   - *Falla:* Il metodo esegue direttamente `classCode.compareTo(classB.classCode)` senza null-check su entrambi gli operandi. Un'entita' in stato Transient (pre-persist) puo' avere `classCode = null`. L'inserimento in un `TreeSet` o qualunque operazione che invochi `compareTo()` causa NPE non gestita.
   - *Fix:* Aggiungere null-guard all'inizio di `compareTo()`: `if (this.classCode == null) throw new IllegalStateException("classCode must not be null for comparison");` Oppure adottare `Comparator.nullsFirst(Comparator.naturalOrder())`.
   - *Test RED:* `shouldNotThrowNPEWhenThisClassCodeIsNull_RED`, `shouldNotThrowNPEWhenOtherClassCodeIsNull_RED`, `shouldNotCauseNPEWhenInsertingNullClassCodeInTreeSet_RED`.

2. **[ALTA] `normalize()` non valida il `classCode` post-trim: stringa vuota diventa "" senza eccezione.**
   - *Falla:* Se il `classCode` e' composto da soli spazi (es. "   "), dopo `trim().toUpperCase()` il campo diventa "". Nessuna eccezione viene lanciata. Una stringa vuota viola il vincolo semantico (non e' una Classe ADR valida) ma non quello sintattico, producendo potenzialmente record corrotti.
   - *Fix:* In `normalize()`, aggiungere post-trim: `if (classCode.isBlank()) throw new IllegalStateException("classCode cannot be blank after normalization");`.
   - *Test RED:* `shouldThrowWhenClassCodeIsBlankOnlyAfterTrim_RED`.

3. **[ALTA] `normalize()` non valida la `description` post-trim: stringa vuota diventa "" senza eccezione.**
   - *Falla:* Analoga alla falla precedente. Una description di soli spazi viene ridotta a "" senza errore applicativo, violando le future regole di presentazione e ricerca.
   - *Fix:* In `normalize()`, post-trim: `if (description.isBlank()) throw new IllegalStateException("description cannot be blank after normalization");`.
   - *Test RED:* `shouldThrowWhenDescriptionIsBlankOnlyAfterTrim_RED`.

4. **[MEDIA] `equals()` considera uguali due entita' con `classCode = null`: comportamento indesiderato.**
   - *Falla:* `Objects.equals(null, null)` restituisce `true`. Due entita' senza Business Key vengono considerate identiche da `equals()`, causando sovrascrizioni inattese in cache e false-positive nella deduplicazione di `AbstractGenericService.storeInCache()`.
   - *Fix:* Aggiungere early return `false` in `equals()` se `this.classCode == null`: `if (this.classCode == null) return false;`.
   - *Test RED:* `shouldReturnFalseWhenBothClassCodesAreNull_RED`.

---

---

## Sessione del 2026-07-16T16:36+02:00

**Classe Analizzata:** CompatibilityRuleService.java
**File Generato:** CompatibilityRuleServiceTest.java
**Righe Generate:** 1158
**Test Totali:** 18 (10 GREEN + 8 RED-TDD)
**Destinazione:** `/home/giovanni/Documenti/AntigravityTester/CompatibilityRuleServiceTest.java`

### Metodi Coperti

- `CompatibilityRuleService(CompatibilityRuleRepository, AdrClassService, CacheManager)` — Costruttore (3 test: 1 HP + 2 RED-TDD)
- `getByAdrClassA(String adrClassCodeA)` — Recupero regole per classe ADR sorgente (4 test: 2 HP + 2 RED-TDD)
- `save(CompatibilityRule newCompatibilityRule)` — Persistenza + Write-Through cache (7 test: 2 HP + 3 RED-TDD + 1 EDGE)
- `mapToEntity(CompatibilityRuleRequestDTO dto)` — Mapping DTO → Entity con lookup ADR (7 test: 3 HP + 2 FP + 2 RED-TDD)
- `syncCacheAfterInsert(CompatibilityRule)` — Testato indirettamente tramite `ArgumentCaptor` sul callback `afterCommit()`

### Vulnerabilita'/Bug Rilevati (8 Test RED-TDD)

1. **[CRITICA] Mancanza di null-check in `getByAdrClassA()`:** Il metodo non valida `adrClassCodeA` prima di passarlo al repository JPA. Un input `null` o blank viene propagato silenziosamente, potenzialmente producendo query malformate o risultati inattesi. **Fix:** Aggiungere `if (adrClassCodeA == null || adrClassCodeA.isBlank()) throw new IllegalArgumentException(...)` come prima istruzione del metodo.

2. **[CRITICA] Mancanza di null-check in `save()`:** Il metodo invoca `newCompatibilityRule.getAdrClassA().getClassCode()` (riga 110) senza verificare che l'argomento o le sue relazioni siano non null. Passare `null`, o una regola con `adrClassA = null` o `adrClassB = null`, causa `NullPointerException` non gestita con risposta HTTP 500. **Fix:** Aggiungere Guard Clause all'inizio del metodo che verifichi null sull'argomento e sulle due classi ADR, lanciando `IllegalArgumentException`.

3. **[MEDIA] Mancanza di null-check in `mapToEntity()`:** Il metodo non valida il DTO in ingresso prima di accedere a `dto.classCodeA()` (riga 187). Un DTO `null` causa `NullPointerException` con HTTP 500. Analogamente, un classCode blank nel DTO bypassa la validazione del service e viene delegato silenziosamente ad `adrClassService`. **Fix:** Aggiungere `if (dto == null) throw new IllegalArgumentException(...)` e validazione dei singoli campi nel mapper.

4. **[BASSA] Mancanza di null-check nel costruttore:** Le dipendenze `compatibilityRuleRepository` e `adrClassService` vengono assegnate ai campi `final` senza Guard Clause. Un'iniezione errata produce errori differiti (NPE al primo utilizzo) anziché immediati (Fail-Fast). **Fix:** Aggiungere `Objects.requireNonNull(compatibilityRuleRepository, ...)` e `Objects.requireNonNull(adrClassService, ...)` nel costruttore.

### Aree che Richiedono Ulteriori Validazioni

- Il metodo `getByAdrClassA()` manca di test di verifica per i casi in cui il repository lanci un'eccezione (es. `DataAccessException`): si consiglia di aggiungere un test che verifichi la propagazione di eccezioni infrastrutturali.
- Il metodo `save()` non verifica che la `CompatibilityRule` passata non abbia un ID gia' valorizzato (che indicherebbe una risorsa esistente, non una nuova): considerare l'aggiunta di un controllo `if (rule.getId() != null) throw new IllegalArgumentException(...)` per forzare la semantica di creazione.


---

## Sessione del 2026-07-17T00:04+02:00

**Classe Analizzata:** CompatibilityRule.java
**File Generato:** CompatibilityRuleTests.java
**Righe Generate:** 1349
**Test Totali:** 47 (di cui 4 in FASE RED intenzionale)
**Stack:** JUnit 5 Jupiter · AssertJ · Java Reflection (per lifecycle hook privati `@PrePersist`/`@PreUpdate`)
**Isolamento:** Puro — nessun contesto Spring, nessun H2, nessun ORM avviato. Istanze di `AdrClass` costruite direttamente (no mock) per rispettare il comportamento reale di `compareTo`/`equals`.

---

### Metodi Coperti

- `getId()` / `setId(Long)` — 2 test (Happy, transiente)
- `getAdrClassA()` / `setAdrClassA(AdrClass)` — 1 test (Happy)
- `getAdrClassB()` / `setAdrClassB(AdrClass)` — 1 test (Happy)
- `isCompatible()` / `setCompatible(boolean)` — 3 test (default difensivo, true, reset a false)
- `getWarningNote()` / `setWarningNote(String)` — 2 test (default, Happy)
- `normalize()` (privato, via reflection) — 10 test (Happy, lowercase→uppercase, newline/tab, spazi multipli, trim, null, empty, blank, PDF paste, boundary 255 char, RED 256 char)
- `safeOrderForUniqueConstraint()` (privato, via reflection) — 9 test (A<B, A>B swap, codici decimali, classi identiche, adiacenti, A=null, B=null, entrambe null, RED null parziale x2)
- `onBeforeSaveOrUpdate()` (privato, orchestratore JPA) — 4 test (sequenza ordering+normalize, abort pre-normalizzazione, valori default, idempotenza)
- `equals(Object)` — 5 test (same pair ID diversi, coppie diverse, riflessivita, null, tipo diverso, RED ordine invertito pre-persist)
- `hashCode()` — 3 test (stesso hash per pair uguale, hash diversi, stabilita con null)
- `toString()` — 4 test (campi essenziali, id=null transiente, stato default, warningNote=null)

---

### Vulnerabilita e Bug Rilevati

#### VULNERABILITA 1 — Assenza di validazione MAX LENGTH in `normalize()` [TEST RED]
- **Falla:** Il metodo `normalize()` non verifica che `warningNote` risultante rispetti il vincolo `@Column(length = 255)`. Una stringa di 256+ caratteri supera la normalizzazione senza eccezioni applicative, causando un DataException opaco a livello JDBC/MariaDB.
- **Test RED:** `redShouldThrowExceptionWhenNoteExceedsMaxLength()` — FALLIRA fino a correzione.
- **Azione Correttiva:** Aggiungere alla fine di `normalize()`, dopo `toUpperCase()`: `if (warningNote.length() > 255) throw new IllegalArgumentException("warningNote exceeds max length of 255 characters");`

#### VULNERABILITA 2 — Assenza di Fail-Fast per classi ADR null parziali in `safeOrderForUniqueConstraint()` [TEST RED x2]
- **Falla:** Il guard `if(adrClassA != null && adrClassB != null)` cortocircuita silenziosamente se solo una classe e null, lasciando propagare un'entita inconsistente fino al flush JPA. Il messaggio di errore risultante e opaco e non diagnosticabile a livello di dominio.
- **Test RED:** `redShouldThrowExceptionWhenOnlyClassAIsNull()` e `redShouldThrowExceptionWhenOnlyClassBIsNull()` — FALLIRANNO fino a correzione.
- **Azione Correttiva:** Aggiungere come prima istruzione di `safeOrderForUniqueConstraint()`: `if (adrClassA == null || adrClassB == null) throw new BadRequestException("Both ADR classes must be non-null to create a compatibility rule");`

#### VULNERABILITA 3 — `equals()` non rispetta l'invarianza di direzionalita per entita transiente [TEST RED]
- **Falla:** Il metodo `equals()` confronta direttamente `(adrClassA, adrClassB)` senza applicare preventivamente l'ordinamento canonico. Due istanze [3,8] e [8,3] risultano non uguali prima del passaggio per `@PrePersist`. Controlli di deduplicazione in-memory nel service layer possono produrre falsi negativi, consentendo l'inserimento di regole speculari duplicate.
- **Test RED:** `redShouldReturnTrueForInvertedClassPairBeforePersist()` — FALLIRA fino a correzione.
- **Azione Correttiva (opzione A):** Refactoring di `equals()` per confrontare i classCode nel loro ordine canonico. **Opzione B (documentazione):** Inserire un @apiNote esplicito in Javadoc che dichiari che `equals()` garantisce la simmeria SOLO post-`@PrePersist`.

---

### Aree che Richiedono Ulteriori Validazioni

- La costante `WARNING_NOTE_GENERAL = "NOTHING TO SAY"` è `private static final` e non esposta pubblicamente: se il valore cambia in futuro, i test di fallback si romperanno silenziosamente. Si consiglia di renderla `package-private` o esporla tramite metodo statico per i test.
- Il metodo `normalize()` applica `toUpperCase()` senza specificare un Locale. Per stringhe con caratteri locali (es. tedesco: 'ss' -> 'SS'), il comportamento dipende dalla JVM di sistema. Si consiglia di usare esplicitamente `toUpperCase(Locale.ROOT)` per garantire determinismo cross-platform.

---
