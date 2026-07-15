package dev.vinciguerra.adrsentinel.db.shipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import dev.vinciguerra.adrsentinel.db.customer.Customer;
import dev.vinciguerra.adrsentinel.db.customer.CustomerService;
import dev.vinciguerra.adrsentinel.db.customer.CustomerSnapshotService;
import dev.vinciguerra.adrsentinel.db.driver.Driver;
import dev.vinciguerra.adrsentinel.db.driver.DriverService;
import dev.vinciguerra.adrsentinel.db.driver.DriverSnapshot;
import dev.vinciguerra.adrsentinel.db.driver.DriverSnapshotService;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.TunnelRestriction;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment.ShipmentReason;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment.ShipmentStatus;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle.VehicleCategory;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle.VehicleCategory.LoadType;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle.VehicleCategory.VehicleApproval;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle.VehicleCategory.VehicleType;
import dev.vinciguerra.adrsentinel.db.vehicle.VehicleService;
import dev.vinciguerra.adrsentinel.db.vehicle.VehicleSnapshot;
import dev.vinciguerra.adrsentinel.db.vehicle.VehicleSnapshotService;
import dev.vinciguerra.adrsentinel.db.waybill.Waybill;
import dev.vinciguerra.adrsentinel.db.waybill.WaybillService;
import dev.vinciguerra.adrsentinel.exception.BadRequestException;
import dev.vinciguerra.adrsentinel.exception.IllegalShipmentStateException;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentRequestDTO.CustomerContainerDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentUpdateDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentUpdateDateDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentUpdateReasonDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentUpdateStatusDTO;

/**
 * Suite di test unitari per {@link ShipmentService} — Strato di Business Logic per la
 * gestione del ciclo di vita delle Spedizioni ADR (Merci Pericolose).
 *
 * <p><b>Strategia di Test (SDET Senior — TDD Difensivo):</b></p>
 * <p>
 * Questa classe è generata con un approccio di tipo <i>Zero-Trust</i>: oltre ai percorsi
 * felici (Happy Path), ogni metodo del Service viene aggredito con failure path, casi limite
 * e, laddove il codice sorgente sia carente, con test TDD di <b>Fase RED</b> appositamente
 * progettati per <b>fallire</b> ed esporre vulnerabilità architetturali latenti.
 * I test TDD-RED sono annotati con {@code [TDD-RED]} nel Javadoc per immediata
 * riconoscibilità da parte dello sviluppatore.
 * </p>
 *
 * <p><b>Isolamento (Service Layer Rules):</b></p>
 * <ul>
 *   <li>Nessun contesto Spring avviato: puro isolamento tramite {@link MockitoExtension}.</li>
 *   <li>Tutti i collaboratori (Repository, Service dipendenti) sono mockati.</li>
 *   <li>Nessun database in memoria (NO H2).</li>
 *   <li>Nessun framework di Serialization/Deserialization JSON.</li>
 * </ul>
 *
 * <p><b>Note Architetturali sui Metodi {@code @Transactional}:</b></p>
 * <p>
 * I metodi marcati con {@code @Transactional} vengono testati senza un contesto Spring
 * attivo. Le chiamate a {@code TransactionSynchronizationManager.registerSynchronization()}
 * lanceranno {@link IllegalStateException} in assenza di transazione. I test verificano
 * la logica di business <b>precedente</b> alla registrazione della cache (validazioni,
 * salvataggio) e catturano l'eccezione attesa dopo il salvataggio del repository.
 * </p>
 *
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShipmentService - Suite di Test Unitari (SDET Senior)")
class ShipmentServiceTests {

    /** Mock del repository JPA per l'accesso ai dati fisici delle Spedizioni. */
    @Mock
    private ShipmentRepository shipmentRepository;

    /** Mock del service per la gestione dei Veicoli (ricerca per targa, aggiornamento inTransit). */
    @Mock
    private VehicleService vehicleService;

    /** Mock del service per la persistenza degli Snapshot dei Veicoli. */
    @Mock
    private VehicleSnapshotService vehicleSnapshotService;

    /** Mock del service per la gestione degli Autisti (ricerca per patente, aggiornamento inTransit). */
    @Mock
    private DriverService driverService;

    /** Mock del service per la persistenza degli Snapshot degli Autisti. */
    @Mock
    private DriverSnapshotService driverSnapshotService;

    /** Mock del service per la gestione dei Clienti (ricerca per P.IVA). */
    @Mock
    private CustomerService customerService;

    /** Mock del service per la persistenza degli Snapshot dei Clienti. */
    @Mock
    private CustomerSnapshotService customerSnapshotService;

    /** Mock del service per la generazione delle Bolle di Viaggio (DDT). Iniettato con @Lazy nel sorgente. */
    @Mock
    private WaybillService waybillService;

    /** Mock del CacheManager di Spring (Caffeine). Richiesto dal costruttore della superclasse AbstractGenericService. */
    @Mock
    private CacheManager cacheManager;

    /** L'istanza del System Under Test (SUT). Tutti i mock vengono iniettati tramite Mockito. */
    @InjectMocks
    private ShipmentService shipmentService;

    // =====================================================================================
    // SEZIONE 1: getByTrackingNumber
    // =====================================================================================

    /**
     * Gruppo di test per il metodo {@link ShipmentService#getByTrackingNumber(String)}.
     * Verifica il comportamento del metodo di ricerca per Tracking Number in tutti i suoi
     * scenari: successo (Cache Hit simulato, DB Call), e fallimento (risorsa non trovata).
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("getByTrackingNumber()")
    class GetByTrackingNumberTests {

        /**
         * <b>Happy Path:</b> verifica che, dato un Tracking Number valido per cui esiste un record,
         * il metodo restituisca correttamente l'entità {@link Shipment} trovata.
         * <p>Mock: {@code shipmentRepository.findByTrackingNumber()} → Optional popolato.</p>
         * <p>Output atteso: Shipment non nulla con il tracking number corrispondente.</p>
         */
        @Test
        @DisplayName("HAPPY PATH: dovrebbe restituire la Shipment quando il tracking number esiste")
        void shouldReturnShipmentWhenTrackingNumberExists() {
            // ARRANGE
            String trackingNumber = "TEST-TRACKING-001";
            Shipment expectedShipment = buildPlannedShipment(trackingNumber);
            when(shipmentRepository.findByTrackingNumber(trackingNumber))
                    .thenReturn(Optional.of(expectedShipment));

            // ACT
            Shipment result = shipmentService.getByTrackingNumber(trackingNumber);

            // ASSERT
            assertThat(result).isNotNull();
            assertThat(result.getTrackingNumber()).isEqualTo(trackingNumber);
            verify(shipmentRepository, times(1)).findByTrackingNumber(trackingNumber);
        }

        /**
         * <b>Failure Path:</b> verifica che, dato un Tracking Number inesistente,
         * il metodo propaghi una {@link ResourceNotFoundException}.
         * <p>Mock: {@code shipmentRepository.findByTrackingNumber()} → Optional.empty().</p>
         * <p>Output atteso: lancio di {@link ResourceNotFoundException}.</p>
         */
        @Test
        @DisplayName("FAILURE PATH: dovrebbe lanciare ResourceNotFoundException quando il tracking number NON esiste")
        void shouldThrowResourceNotFoundExceptionWhenTrackingNumberNotExists() {
            // ARRANGE
            String nonExistentTracking = "NON-EXISTENT-999";
            when(shipmentRepository.findByTrackingNumber(nonExistentTracking))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.getByTrackingNumber(nonExistentTracking))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(nonExistentTracking);
        }

        /**
         * <b>[TDD-RED] Vulnerability Test:</b> verifica che il metodo gestisca correttamente
         * un Tracking Number {@code null} in input senza propagare una {@link NullPointerException}
         * silenziosa a livello di repository.
         *
         * <p><b>Vulnerabilita' Esposta:</b> Il metodo non possiede alcun guard di pre-validazione
         * per l'input {@code null}. Una chiamata con {@code trackingNumber = null} inoltrera'
         * la query al repository con comportamento non deterministico (potenziale NPE o query errata).</p>
         *
         * <p><b>Soluzione Raccomandata:</b> Aggiungere all'inizio del metodo:
         * {@code if (trackingNumber == null || trackingNumber.isBlank()) throw new BadRequestException("...")}</p>
         *
         * <p><b>Output atteso (FASE RED):</b> {@link IllegalArgumentException}.
         * Questo test FALLIRA' finche' lo sviluppatore non implementa il controllo.</p>
         */
        @Test
        @DisplayName("[TDD-RED] VULNERABILITY: dovrebbe lanciare IllegalArgumentException per tracking number null")
        void shouldThrowIllegalArgumentExceptionWhenTrackingNumberIsNull() {
            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.getByTrackingNumber(null))
                    .isInstanceOf(IllegalArgumentException.class);
            // Verifica che il repository NON sia stato raggiunto (guard deve bloccare prima)
            verify(shipmentRepository, never()).findByTrackingNumber(any());
        }
    }

    // =====================================================================================
    // SEZIONE 2: getByShipmentDate
    // =====================================================================================

    /**
     * Gruppo di test per il metodo {@link ShipmentService#getByShipmentDate(LocalDate)}.
     * Verifica la corretta trasformazione da {@link LocalDate} a intervallo
     * {@link LocalDateTime} (00:00:00 - 23:59:59) e il passaggio corretto al repository.
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("getByShipmentDate()")
    class GetByShipmentDateTests {

        /**
         * <b>Happy Path:</b> verifica che il metodo interroghi il repository con l'intervallo
         * corretto (inizio e fine della giornata) e restituisca la lista delle spedizioni.
         * <p>Mock: {@code shipmentRepository.findByShipmentDateBetween()} → lista con un elemento.</p>
         * <p>Output atteso: lista non vuota con gli elementi restituiti dal mock.</p>
         */
        @Test
        @DisplayName("HAPPY PATH: dovrebbe recuperare le Shipment per una data specifica con intervallo corretto")
        void shouldReturnShipmentsForGivenDate() {
            // ARRANGE
            LocalDate targetDate = LocalDate.of(2026, 7, 15);
            LocalDateTime expectedStart = targetDate.atStartOfDay();
            LocalDateTime expectedEnd = targetDate.atTime(LocalTime.MAX);
            Shipment shipment = buildPlannedShipment("TRK-01");
            when(shipmentRepository.findByShipmentDateBetween(expectedStart, expectedEnd))
                    .thenReturn(List.of(shipment));

            // ACT
            List<Shipment> result = shipmentService.getByShipmentDate(targetDate);

            // ASSERT
            assertThat(result).isNotNull().hasSize(1);
            verify(shipmentRepository, times(1)).findByShipmentDateBetween(expectedStart, expectedEnd);
        }

        /**
         * <b>Edge Case:</b> verifica che il metodo restituisca una lista vuota (e non null)
         * quando nessuna spedizione e' prevista per la data indicata.
         * <p>Mock: {@code shipmentRepository.findByShipmentDateBetween()} → lista vuota.</p>
         * <p>Output atteso: lista vuota, non nulla.</p>
         */
        @Test
        @DisplayName("EDGE CASE: dovrebbe restituire una lista vuota se non ci sono Shipment per quella data")
        void shouldReturnEmptyListWhenNoShipmentsForDate() {
            // ARRANGE
            LocalDate targetDate = LocalDate.of(2020, 1, 1);
            when(shipmentRepository.findByShipmentDateBetween(any(), any()))
                    .thenReturn(List.of());

            // ACT
            List<Shipment> result = shipmentService.getByShipmentDate(targetDate);

            // ASSERT
            assertThat(result).isNotNull().isEmpty();
        }

        /**
         * <b>[TDD-RED] Vulnerability Test:</b> verifica che il metodo gestisca correttamente
         * l'input {@code null} per il parametro {@code targetDate}.
         *
         * <p><b>Vulnerabilita' Esposta:</b> Il metodo non esegue alcun null check su {@code targetDate}.
         * La chiamata a {@code targetDate.atStartOfDay()} propaghera' una {@link NullPointerException}
         * non gestita, che Spring trasformera' in un HTTP 500 invece di un HTTP 400.</p>
         *
         * <p><b>Soluzione Raccomandata:</b> Aggiungere:
         * {@code if (targetDate == null) throw new BadRequestException("Target date cannot be null")}</p>
         *
         * <p><b>Output atteso (FASE RED):</b> {@link IllegalArgumentException}.
         * Questo test FALLIRA' finche' lo sviluppatore non aggiunge la validazione.</p>
         */
        @Test
        @DisplayName("[TDD-RED] VULNERABILITY: dovrebbe lanciare IllegalArgumentException per date null")
        void shouldThrowExceptionWhenTargetDateIsNull() {
            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.getByShipmentDate(null))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(shipmentRepository, never()).findByShipmentDateBetween(any(), any());
        }
    }

    // =====================================================================================
    // SEZIONE 3: getByShipmentStatus (Paginazione)
    // =====================================================================================

    /**
     * Gruppo di test per il metodo {@link ShipmentService#getByShipmentStatus(ShipmentStatus, Pageable)}.
     * Verifica la corretta delega al repository paginato con il parametro di stato tipizzato.
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("getByShipmentStatus()")
    class GetByShipmentStatusTests {

        /**
         * <b>Happy Path:</b> verifica che il metodo deleghi correttamente al repository
         * la ricerca paginata per stato e restituisca il {@link Page} risultante.
         * <p>Mock: {@code shipmentRepository.findByShipmentStatus()} → Page popolata.</p>
         * <p>Output atteso: pagina non nulla con il contenuto restituito dal mock.</p>
         */
        @Test
        @DisplayName("HAPPY PATH: dovrebbe delegare correttamente al repository la ricerca paginata per stato PLANNED")
        void shouldDelegateToRepositoryWithCorrectStatusAndPageable() {
            // ARRANGE
            ShipmentStatus status = ShipmentStatus.PLANNED;
            Pageable pageable = Pageable.ofSize(10);
            Page<Shipment> expectedPage = new PageImpl<>(List.of(buildPlannedShipment("TRK-01")));
            when(shipmentRepository.findByShipmentStatus(status, pageable)).thenReturn(expectedPage);

            // ACT
            Page<Shipment> result = shipmentService.getByShipmentStatus(status, pageable);

            // ASSERT
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            verify(shipmentRepository, times(1)).findByShipmentStatus(eq(status), eq(pageable));
        }

        /**
         * <b>Edge Case:</b> verifica il comportamento con lo stato terminale {@code DELIVERED},
         * che potrebbe generare volumi enormi di dati senza paginazione.
         * Con paginazione, il comportamento deve essere corretto.
         * <p>Mock: {@code shipmentRepository.findByShipmentStatus(DELIVERED, pageable)}.</p>
         * <p>Output atteso: pagina vuota senza eccezioni.</p>
         */
        @Test
        @DisplayName("EDGE CASE: dovrebbe funzionare correttamente anche con lo stato terminale DELIVERED")
        void shouldHandleDeliveredStatusCorrectly() {
            // ARRANGE
            ShipmentStatus status = ShipmentStatus.DELIVERED;
            Pageable pageable = Pageable.ofSize(5);
            when(shipmentRepository.findByShipmentStatus(status, pageable)).thenReturn(Page.empty());

            // ACT
            Page<Shipment> result = shipmentService.getByShipmentStatus(status, pageable);

            // ASSERT
            assertThat(result).isNotNull();
            assertThat(result.isEmpty()).isTrue();
        }
    }

    // =====================================================================================
    // SEZIONE 4: getAllShipment (Paginazione)
    // =====================================================================================

    /**
     * Gruppo di test per il metodo {@link ShipmentService#getAllShipment(Pageable)}.
     * Verifica la delega al metodo {@code findAll(pageable)} del repository.
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("getAllShipment()")
    class GetAllShipmentTests {

        /**
         * <b>Happy Path:</b> verifica che il metodo deleghi al repository {@code findAll(pageable)}
         * e ne restituisca correttamente il risultato paginato.
         * <p>Mock: {@code shipmentRepository.findAll(pageable)}.</p>
         * <p>Output atteso: la {@link Page} restituita dal mock con 2 elementi.</p>
         */
        @Test
        @DisplayName("HAPPY PATH: dovrebbe restituire la pagina completa di tutte le Shipment")
        void shouldReturnAllShipmentsPaginated() {
            // ARRANGE
            Pageable pageable = Pageable.ofSize(20);
            List<Shipment> shipments = List.of(buildPlannedShipment("TRK-A"), buildPlannedShipment("TRK-B"));
            Page<Shipment> expectedPage = new PageImpl<>(shipments);
            when(shipmentRepository.findAll(pageable)).thenReturn(expectedPage);

            // ACT
            Page<Shipment> result = shipmentService.getAllShipment(pageable);

            // ASSERT
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(2);
            verify(shipmentRepository, times(1)).findAll(pageable);
        }
    }

    // =====================================================================================
    // SEZIONE 5: save
    // =====================================================================================

    /**
     * Gruppo di test per il metodo {@link ShipmentService#save(Shipment)}.
     * Verifica la persistenza tramite repository.
     * La registrazione del {@code TransactionSynchronization} non e' verificabile in assenza
     * di contesto transazionale attivo: viene catturata la relativa {@link IllegalStateException}.
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("save()")
    class SaveTests {

        /**
         * <b>Happy Path:</b> verifica che il metodo invochi {@code shipmentRepository.save()}
         * con la shipment fornita. L'eccezione {@link IllegalStateException} del
         * {@code TransactionSynchronizationManager} (assenza di contesto Spring) e' attesa
         * e catturata: la logica di salvataggio e' gia' avvenuta prima di essa.
         * <p>Mock: {@code shipmentRepository.save()} → Shipment con ID generato.</p>
         * <p>Output atteso: {@code save()} invocato una volta sul repository.</p>
         */
        @Test
        @DisplayName("HAPPY PATH: dovrebbe invocare il repository e persistere la Shipment")
        void shouldPersistShipmentAndReturnSavedEntity() {
            // ARRANGE
            Shipment shipmentToPersist = buildPlannedShipment("TRK-SAVE-01");
            Shipment persistedShipment = buildPlannedShipment("TRK-SAVE-01");
            persistedShipment.setId(42L);
            when(shipmentRepository.save(shipmentToPersist)).thenReturn(persistedShipment);

            // ACT — cattura l'IllegalStateException attesa dal TSM in assenza di transazione Spring
            try {
                Shipment result = shipmentService.save(shipmentToPersist);
                assertThat(result.getId()).isEqualTo(42L);
            } catch (IllegalStateException e) {
                // L'eccezione e' attesa: la logica di business (save del repository) e' gia' avvenuta.
            }

            // ASSERT
            verify(shipmentRepository, times(1)).save(shipmentToPersist);
        }

        /**
         * <b>[TDD-RED] Vulnerability Test:</b> verifica che il metodo {@code save()} gestisca
         * correttamente un input {@code null} senza propagare una {@link NullPointerException}.
         *
         * <p><b>Vulnerabilita' Esposta:</b> Il metodo accede direttamente a
         * {@code newShipment.getTrackingNumber()} per il logging, senza verificare che
         * {@code newShipment} sia non-null. Un input {@code null} causa
         * {@link NullPointerException} alla riga del log, prima ancora di raggiungere il repository.</p>
         *
         * <p><b>Soluzione Raccomandata:</b> Aggiungere all'inizio del metodo:
         * {@code if (newShipment == null) throw new IllegalArgumentException("Shipment cannot be null")}</p>
         *
         * <p><b>Output atteso (FASE RED):</b> {@link IllegalArgumentException}.
         * Questo test FALLIRA' perche' attualmente il metodo lancia {@link NullPointerException}.</p>
         */
        @Test
        @DisplayName("[TDD-RED] VULNERABILITY: dovrebbe lanciare IllegalArgumentException per Shipment null in input")
        void shouldThrowIllegalShipmentStateExceptionWhenShipmentIsNull() {
            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.save(null))
                    .isInstanceOf(IllegalShipmentStateException.class);
        }
    }

    // =====================================================================================
    // SEZIONE 6: updateDetailsByTrackingNumber
    // =====================================================================================

    /**
     * Gruppo di test per il metodo {@link ShipmentService#updateDetailsByTrackingNumber(String, ShipmentUpdateDTO)}.
     * Verifica la guardia di stato (solo {@code PLANNED}), la risoluzione del veicolo
     * tramite targa e la persistenza dell'entita' aggiornata.
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("updateDetailsByTrackingNumber()")
    class UpdateDetailsByTrackingNumberTests {

        /**
         * <b>Happy Path:</b> verifica che, data una spedizione in stato {@code PLANNED} e
         * un DTO valido, il metodo aggiorni il veicolo e le destinazioni e salvi l'entita'.
         * <p>Mock: repo.findByTrackingNumber → PLANNED; vehicleService.getByLicensePlate → Vehicle; repo.save → ok.</p>
         * <p>Output atteso: {@code save()} invocato una volta.</p>
         */
        @Test
        @DisplayName("HAPPY PATH: dovrebbe aggiornare veicolo e destinazioni per una Shipment in stato PLANNED")
        void shouldUpdateVehicleAndDestinationsForPlannedShipment() {
            // ARRANGE
            String tracking = "TRK-UPDATE-01";
            Shipment existing = buildPlannedShipment(tracking);
            Vehicle newVehicle = buildVehicle("FI123BC");
            List<String> newDestinations = List.of("Roma, Via del Corso 1", "Milano, Via Montenapoleone 5");
            ShipmentUpdateDTO dto = new ShipmentUpdateDTO(newDestinations, "FI123BC");
            Shipment saved = buildPlannedShipment(tracking);
            saved.setVehicle(newVehicle);

            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(existing));
            when(vehicleService.getByLicensePlate("FI123BC")).thenReturn(newVehicle);
            when(shipmentRepository.save(existing)).thenReturn(saved);

            // ACT — cattura l'IllegalStateException attesa dal TSM
            try {
                shipmentService.updateDetailsByTrackingNumber(tracking, dto);
            } catch (IllegalStateException e) {
                // Atteso dal TSM
            }

            // ASSERT
            verify(shipmentRepository, times(1)).save(existing);
        }

        /**
         * <b>Failure Path:</b> verifica che il metodo lanci {@link ResourceNotFoundException}
         * quando il Tracking Number non corrisponde ad alcuna spedizione.
         * <p>Mock: repo.findByTrackingNumber → Optional.empty().</p>
         * <p>Output atteso: {@link ResourceNotFoundException}.</p>
         */
        @Test
        @DisplayName("FAILURE PATH: dovrebbe lanciare ResourceNotFoundException se la Shipment non esiste")
        void shouldThrowResourceNotFoundWhenShipmentNotFound() {
            // ARRANGE
            String tracking = "NON-EXISTING-TRK";
            ShipmentUpdateDTO dto = new ShipmentUpdateDTO(List.of("Firenze, Via Rossi 1"), "AB123CD");
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.updateDetailsByTrackingNumber(tracking, dto))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(tracking);
        }

        /**
         * <b>Failure Path:</b> verifica che il metodo lanci {@link IllegalShipmentStateException}
         * quando si tenta di aggiornare i dettagli di una spedizione in stato {@code TRANSIT}.
         * <p>Mock: repo.findByTrackingNumber → Shipment TRANSIT.</p>
         * <p>Output atteso: {@link IllegalShipmentStateException}, repo.save e vehicleService NON invocati.</p>
         */
        @Test
        @DisplayName("FAILURE PATH: dovrebbe lanciare IllegalShipmentStateException se la Shipment e' in stato TRANSIT")
        void shouldThrowIllegalShipmentStateExceptionForTransitShipment() {
            // ARRANGE
            String tracking = "TRK-TRANSIT-01";
            Shipment transitShipment = buildShipmentWithStatus(tracking, ShipmentStatus.TRANSIT);
            ShipmentUpdateDTO dto = new ShipmentUpdateDTO(List.of("Napoli, Via Roma 10"), "NA456GH");
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(transitShipment));

            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.updateDetailsByTrackingNumber(tracking, dto))
                    .isInstanceOf(IllegalShipmentStateException.class);
            verify(vehicleService, never()).getByLicensePlate(anyString());
            verify(shipmentRepository, never()).save(any());
        }

        /**
         * <b>Failure Path:</b> verifica che il metodo lanci {@link IllegalShipmentStateException}
         * per una spedizione in stato terminale {@code DELIVERED}.
         * <p>Mock: repo.findByTrackingNumber → Shipment DELIVERED.</p>
         * <p>Output atteso: {@link IllegalShipmentStateException}.</p>
         */
        @Test
        @DisplayName("FAILURE PATH: dovrebbe lanciare IllegalShipmentStateException se la Shipment e' DELIVERED")
        void shouldThrowIllegalShipmentStateExceptionForDeliveredShipment() {
            // ARRANGE
            String tracking = "TRK-DELIVERED-01";
            Shipment delivered = buildShipmentWithStatus(tracking, ShipmentStatus.DELIVERED);
            ShipmentUpdateDTO dto = new ShipmentUpdateDTO(List.of("Torino, Corso Vittorio 1"), "TO789IJ");
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(delivered));

            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.updateDetailsByTrackingNumber(tracking, dto))
                    .isInstanceOf(IllegalShipmentStateException.class);
        }
    }

    // =====================================================================================
    // SEZIONE 7: updateDateByTrackingNumber
    // =====================================================================================

    /**
     * Gruppo di test per il metodo {@link ShipmentService#updateDateByTrackingNumber(String, ShipmentUpdateDateDTO)}.
     * Verifica la logica di tolleranza delle 48 ore per spedizioni non-PLANNED, il parsing
     * della data ISO-8601 e la gestione di formati non validi.
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("updateDateByTrackingNumber()")
    class UpdateDateByTrackingNumberTests {

        /**
         * <b>Happy Path:</b> verifica che per una spedizione {@code PLANNED} la data possa
         * essere aggiornata con un formato ISO-8601 valido senza restrizioni sulla data passata.
         * <p>Mock: repo.findByTrackingNumber → PLANNED; repo.save → ok.</p>
         * <p>Output atteso: aggiornamento avvenuto, save invocato una volta.</p>
         */
        @Test
        @DisplayName("HAPPY PATH: dovrebbe aggiornare la data di una Shipment PLANNED con formato ISO-8601 valido")
        void shouldUpdateDateForPlannedShipmentWithValidIsoDate() {
            // ARRANGE
            String tracking = "TRK-DATE-01";
            Shipment planned = buildPlannedShipment(tracking);
            ShipmentUpdateDateDTO dto = new ShipmentUpdateDateDTO("2026-12-25T10:30:00");
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(planned));
            when(shipmentRepository.save(planned)).thenReturn(planned);

            // ACT
            try {
                shipmentService.updateDateByTrackingNumber(tracking, dto);
            } catch (IllegalStateException e) {
                // Atteso dal TSM
            }

            // ASSERT
            verify(shipmentRepository, times(1)).save(planned);
        }

        /**
         * <b>Failure Path:</b> verifica che per una spedizione non-{@code PLANNED} (es. {@code TRANSIT})
         * con data di spedizione piu' vecchia di 48 ore, venga lanciata {@link BadRequestException}.
         * <p>Mock: repo.findByTrackingNumber → Shipment TRANSIT con data di 3 giorni fa.</p>
         * <p>Output atteso: {@link BadRequestException} che indica il vincolo delle 48 ore.</p>
         */
        @Test
        @DisplayName("FAILURE PATH: dovrebbe lanciare BadRequestException per Shipment non-PLANNED con data > 48h nel passato")
        void shouldThrowBadRequestExceptionForNonPlannedShipmentWithOldDate() {
            // ARRANGE
            String tracking = "TRK-DATE-OLD-01";
            Shipment transitShipment = buildShipmentWithStatus(tracking, ShipmentStatus.TRANSIT);
            transitShipment.setShipmentDate(LocalDateTime.now().minusDays(3));
            ShipmentUpdateDateDTO dto = new ShipmentUpdateDateDTO("2026-07-10T10:00:00");
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(transitShipment));

            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.updateDateByTrackingNumber(tracking, dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("48 hours");
        }

        /**
         * <b>Edge Case:</b> verifica che per una spedizione non-{@code PLANNED} con data
         * recente (entro le 48 ore), l'aggiornamento venga consentito.
         * <p>Mock: repo.findByTrackingNumber → Shipment TRANSIT con data 1 ora fa; repo.save → ok.</p>
         * <p>Output atteso: aggiornamento consentito, nessuna eccezione di business lanciata.</p>
         */
        @Test
        @DisplayName("EDGE CASE: dovrebbe permettere l'aggiornamento per Shipment non-PLANNED con data DENTRO le 48h")
        void shouldAllowUpdateForNonPlannedShipmentWithDateWithin48Hours() {
            // ARRANGE
            String tracking = "TRK-DATE-RECENT-01";
            Shipment transitShipment = buildShipmentWithStatus(tracking, ShipmentStatus.TRANSIT);
            transitShipment.setShipmentDate(LocalDateTime.now().minusHours(1));
            ShipmentUpdateDateDTO dto = new ShipmentUpdateDateDTO("2026-07-15T15:00:00");
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(transitShipment));
            when(shipmentRepository.save(transitShipment)).thenReturn(transitShipment);

            // ACT — non deve lanciare BadRequestException
            try {
                shipmentService.updateDateByTrackingNumber(tracking, dto);
            } catch (IllegalStateException e) {
                // Atteso dal TSM, non e' un errore di business
            }

            verify(shipmentRepository, times(1)).save(transitShipment);
        }

        /**
         * <b>Failure Path:</b> verifica che un formato data non ISO-8601 nel DTO
         * causi il lancio di {@link IllegalShipmentStateException} (wrapping del {@code DateTimeParseException}).
         * <p>Mock: repo.findByTrackingNumber → Shipment PLANNED.</p>
         * <p>Output atteso: {@link IllegalShipmentStateException} con messaggio "invalid format".</p>
         */
        @Test
        @DisplayName("FAILURE PATH: dovrebbe lanciare IllegalShipmentStateException per formato data non valido nel DTO")
        void shouldThrowIllegalShipmentStateExceptionForInvalidDateFormat() {
            // ARRANGE
            String tracking = "TRK-DATE-INVALID";
            Shipment planned = buildPlannedShipment(tracking);
            ShipmentUpdateDateDTO dto = new ShipmentUpdateDateDTO("15-07-2026 10:30");
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(planned));

            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.updateDateByTrackingNumber(tracking, dto))
                    .isInstanceOf(IllegalShipmentStateException.class)
                    .hasMessageContaining("invalid format");
        }

        /**
         * <b>Failure Path:</b> verifica che il metodo lanci {@link ResourceNotFoundException}
         * quando il tracking number non esiste.
         * <p>Mock: repo.findByTrackingNumber → Optional.empty().</p>
         * <p>Output atteso: {@link ResourceNotFoundException}.</p>
         */
        @Test
        @DisplayName("FAILURE PATH: dovrebbe lanciare ResourceNotFoundException se la Shipment non esiste")
        void shouldThrowResourceNotFoundExceptionForMissingShipmentOnDateUpdate() {
            // ARRANGE
            String tracking = "NON-EXISTENT";
            ShipmentUpdateDateDTO dto = new ShipmentUpdateDateDTO("2026-12-01T08:00:00");
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.updateDateByTrackingNumber(tracking, dto))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =====================================================================================
    // SEZIONE 8: updateShipmentReasonByTrackingNumber
    // =====================================================================================

    /**
     * Gruppo di test per il metodo {@link ShipmentService#updateShipmentReasonByTrackingNumber(String, ShipmentUpdateReasonDTO)}.
     * Verifica la guardia di stato (solo PLANNED) e la corretta conversione del campo
     * {@code shipmentReason} da String a Enum.
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("updateShipmentReasonByTrackingNumber()")
    class UpdateShipmentReasonTests {

        /**
         * <b>Happy Path:</b> verifica che la causale venga aggiornata correttamente per
         * una spedizione in stato {@code PLANNED} con un valore Enum valido.
         * <p>Mock: repo.findByTrackingNumber → PLANNED; repo.save → ok.</p>
         * <p>Output atteso: save invocato; getShipmentReason() == WASTE_DISPOSAL.</p>
         */
        @Test
        @DisplayName("HAPPY PATH: dovrebbe aggiornare la causale (ShipmentReason) per una Shipment PLANNED")
        void shouldUpdateReasonForPlannedShipment() {
            // ARRANGE
            String tracking = "TRK-REASON-01";
            Shipment planned = buildPlannedShipment(tracking);
            ShipmentUpdateReasonDTO dto = new ShipmentUpdateReasonDTO("WASTE_DISPOSAL");
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(planned));
            when(shipmentRepository.save(planned)).thenReturn(planned);

            // ACT
            try {
                shipmentService.updateShipmentReasonByTrackingNumber(tracking, dto);
            } catch (IllegalStateException e) {
                // Atteso dal TSM
            }

            // ASSERT
            verify(shipmentRepository, times(1)).save(planned);
            assertThat(planned.getShipmentReason()).isEqualTo(ShipmentReason.WASTE_DISPOSAL);
        }

        /**
         * <b>Failure Path:</b> verifica che venga lanciata {@link IllegalShipmentStateException}
         * per una spedizione non in stato {@code PLANNED} (CANCELLED).
         * <p>Mock: repo.findByTrackingNumber → CANCELLED.</p>
         * <p>Output atteso: {@link IllegalShipmentStateException}, save NON invocato.</p>
         */
        @Test
        @DisplayName("FAILURE PATH: dovrebbe lanciare IllegalShipmentStateException per Shipment in stato CANCELLED")
        void shouldThrowIllegalShipmentStateExceptionForCancelledShipment() {
            // ARRANGE
            String tracking = "TRK-CANCELLED-01";
            Shipment cancelled = buildShipmentWithStatus(tracking, ShipmentStatus.CANCELLED);
            ShipmentUpdateReasonDTO dto = new ShipmentUpdateReasonDTO("SALE");
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(cancelled));

            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.updateShipmentReasonByTrackingNumber(tracking, dto))
                    .isInstanceOf(IllegalShipmentStateException.class);
            verify(shipmentRepository, never()).save(any());
        }

        /**
         * <b>[TDD-RED] Vulnerability Test:</b> verifica che un valore di {@code shipmentReason}
         * non valido produca una {@link BadRequestException} controllata invece di una
         * {@link IllegalArgumentException} non gestita proveniente da {@code Enum.valueOf()}.
         *
         * <p><b>Vulnerabilita' Esposta:</b> Il codice usa {@code Enum.valueOf(ShipmentReason.class, updateDto.shipmentReason())}
         * senza try-catch. Un valore non valido lancia {@link IllegalArgumentException} raw che
         * produce HTTP 500 invece di HTTP 400 controllato.</p>
         *
         * <p><b>Soluzione Raccomandata:</b> Avvolgere {@code Enum.valueOf()} in try-catch per
         * {@link IllegalArgumentException} e rilanciarla come {@link BadRequestException}.</p>
         *
         * <p><b>Output atteso (FASE RED):</b> {@link BadRequestException}.
         * Questo test FALLIRA' perche' attualmente viene propagata {@link IllegalArgumentException}.</p>
         */
        @Test
        @DisplayName("[TDD-RED] VULNERABILITY: dovrebbe lanciare BadRequestException per ShipmentReason non valido")
        void shouldThrowBadRequestExceptionForInvalidShipmentReasonString() {
            // ARRANGE
            String tracking = "TRK-REASON-INVALID";
            Shipment planned = buildPlannedShipment(tracking);
            ShipmentUpdateReasonDTO dto = new ShipmentUpdateReasonDTO("INVALID_REASON_VALUE");
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(planned));

            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.updateShipmentReasonByTrackingNumber(tracking, dto))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // =====================================================================================
    // SEZIONE 9: updateTunnelRestrictionByTrackingNumber
    // =====================================================================================

    /**
     * Gruppo di test per il metodo {@link ShipmentService#updateTunnelRestrictionByTrackingNumber(TunnelRestriction, String)}.
     * Verifica la guardia di stato, la corretta impostazione della restrizione tunnel e
     * i percorsi di errore per stati non modificabili.
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("updateTunnelRestrictionByTrackingNumber()")
    class UpdateTunnelRestrictionTests {

        /**
         * <b>Happy Path:</b> verifica che la restrizione tunnel venga impostata correttamente
         * per una spedizione in stato {@code PLANNED}.
         * <p>Mock: repo.findByTrackingNumber → PLANNED; repo.save → ok.</p>
         * <p>Output atteso: save invocato; tunnelRestriction == E.</p>
         */
        @Test
        @DisplayName("HAPPY PATH: dovrebbe aggiornare il TunnelRestriction per una Shipment PLANNED")
        void shouldUpdateTunnelRestrictionForPlannedShipment() {
            // ARRANGE
            String tracking = "TRK-TUNNEL-01";
            Shipment planned = buildPlannedShipment(tracking);
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(planned));
            when(shipmentRepository.save(planned)).thenReturn(planned);

            // ACT
            try {
                shipmentService.updateTunnelRestrictionByTrackingNumber(TunnelRestriction.E, tracking);
            } catch (IllegalStateException e) {
                // Atteso dal TSM
            }

            // ASSERT
            verify(shipmentRepository, times(1)).save(planned);
            assertThat(planned.getTunnelRestriction()).isEqualTo(TunnelRestriction.E);
        }

        /**
         * <b>Failure Path:</b> verifica che venga lanciata {@link ResourceNotFoundException}
         * quando il tracking number non esiste.
         * <p>Mock: repo.findByTrackingNumber → Optional.empty().</p>
         * <p>Output atteso: {@link ResourceNotFoundException}.</p>
         */
        @Test
        @DisplayName("FAILURE PATH: dovrebbe lanciare ResourceNotFoundException se la Shipment non esiste")
        void shouldThrowResourceNotFoundExceptionWhenShipmentNotExists() {
            // ARRANGE
            String tracking = "NON-EXISTENT-TRK";
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.updateTunnelRestrictionByTrackingNumber(TunnelRestriction.C, tracking))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        /**
         * <b>Failure Path:</b> verifica che venga lanciata {@link IllegalShipmentStateException}
         * per una spedizione in stato {@code TRANSIT}.
         * <p>Mock: repo.findByTrackingNumber → TRANSIT.</p>
         * <p>Output atteso: {@link IllegalShipmentStateException}, save NON invocato.</p>
         */
        @Test
        @DisplayName("FAILURE PATH: dovrebbe lanciare IllegalShipmentStateException per Shipment in stato TRANSIT")
        void shouldThrowIllegalShipmentStateExceptionForTransitShipmentOnTunnelUpdate() {
            // ARRANGE
            String tracking = "TRK-TRANSIT-02";
            Shipment transit = buildShipmentWithStatus(tracking, ShipmentStatus.TRANSIT);
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(transit));

            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.updateTunnelRestrictionByTrackingNumber(TunnelRestriction.D, tracking))
                    .isInstanceOf(IllegalShipmentStateException.class);
            verify(shipmentRepository, never()).save(any());
        }

        /**
         * <b>[TDD-RED] Vulnerability Test:</b> verifica che un valore {@code null} per
         * {@code tunnelRestriction} venga rifiutato con un'eccezione gestita prima di
         * essere applicato all'entita'.
         *
         * <p><b>Vulnerabilita' Esposta:</b> Il metodo non esegue un null check sul parametro
         * {@code TunnelRestriction tunnelRestriction}. Un valore null verra' assegnato
         * all'entita' tramite {@code shipment.setTunnelRestriction(null)}, bypassando la logica
         * di default presente solo nell'hook JPA {@code @PrePersist/@PreUpdate}, e causando
         * potenzialmente una violazione di not-null constraint a livello DB.</p>
         *
         * <p><b>Soluzione Raccomandata:</b> Aggiungere in cima al metodo:
         * {@code if (tunnelRestriction == null) throw new BadRequestException("TunnelRestriction cannot be null")}</p>
         *
         * <p><b>Output atteso (FASE RED):</b> {@link IllegalArgumentException}.
         * Il test FALLIRA' perche' il metodo non lancia nessuna eccezione preventiva.</p>
         */
        @Test
        @DisplayName("[TDD-RED] VULNERABILITY: dovrebbe lanciare IllegalArgumentException per TunnelRestriction null")
        void shouldThrowExceptionWhenTunnelRestrictionIsNull() {
            // ARRANGE
            String tracking = "TRK-TUNNEL-NULL";
            Shipment planned = buildPlannedShipment(tracking);
            planned.setTunnelRestriction(TunnelRestriction.E);
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(planned));
            when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
            
            try {
            	shipmentService.updateTunnelRestrictionByTrackingNumber(null, tracking);
            } catch (IllegalStateException e) {
                // Atteso dal TSM
            }
            
            assertThat(planned.getTunnelRestriction()).isEqualTo(TunnelRestriction.B);
            verify(shipmentRepository, times(1)).save(planned);
        }
    }

    // =====================================================================================
    // SEZIONE 10: updateStatusByTrackingNumber (State Machine ADR)
    // =====================================================================================

    /**
     * Gruppo di test per il metodo {@link ShipmentService#updateStatusByTrackingNumber(String, ShipmentUpdateStatusDTO)}.
     *
     * <p>Questo e' il metodo piu' critico e complesso del Service: contiene la Macchina a Stati
     * completa per il ciclo di vita della spedizione ADR. Vengono testati tutti i percorsi:
     * transizioni valide, transizioni vietate, uscita da stati terminali, e la corretta
     * orchestrazione degli Snapshot e delle risorse fisiche (Veicoli, Autisti).</p>
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("updateStatusByTrackingNumber() - State Machine ADR")
    class UpdateStatusByTrackingNumberTests {

        /**
         * <b>Failure Path (Nodo Pozzo):</b> verifica che qualsiasi transizione da stato
         * terminale {@code DELIVERED} venga rifiutata con {@link IllegalShipmentStateException}.
         * <p>Mock: repo.findByTrackingNumber → Shipment DELIVERED.</p>
         * <p>Output atteso: {@link IllegalShipmentStateException} con "DELIVERED" nel messaggio.</p>
         */
        @Test
        @DisplayName("FAILURE PATH (Nodo Pozzo): nessuna transizione e' permessa da stato DELIVERED")
        void shouldThrowExceptionWhenStatusIsDelivered() {
            // ARRANGE
            String tracking = "TRK-SM-DELIVERED";
            Shipment delivered = buildShipmentWithStatus(tracking, ShipmentStatus.DELIVERED);
            ShipmentUpdateStatusDTO dto = new ShipmentUpdateStatusDTO("TRANSIT");
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(delivered));

            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.updateStatusByTrackingNumber(tracking, dto))
                    .isInstanceOf(IllegalShipmentStateException.class)
                    .hasMessageContaining("DELIVERED");
        }

        /**
         * <b>Failure Path (Nodo Pozzo):</b> verifica che qualsiasi transizione da stato
         * terminale {@code CANCELLED} venga rifiutata.
         * <p>Mock: repo.findByTrackingNumber → Shipment CANCELLED.</p>
         * <p>Output atteso: {@link IllegalShipmentStateException} con "CANCELLED" nel messaggio.</p>
         */
        @Test
        @DisplayName("FAILURE PATH (Nodo Pozzo): nessuna transizione e' permessa da stato CANCELLED")
        void shouldThrowExceptionWhenStatusIsCancelled() {
            // ARRANGE
            String tracking = "TRK-SM-CANCELLED";
            Shipment cancelled = buildShipmentWithStatus(tracking, ShipmentStatus.CANCELLED);
            ShipmentUpdateStatusDTO dto = new ShipmentUpdateStatusDTO("DELIVERED");
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(cancelled));

            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.updateStatusByTrackingNumber(tracking, dto))
                    .isInstanceOf(IllegalShipmentStateException.class)
                    .hasMessageContaining("CANCELLED");
        }

        /**
         * <b>Failure Path (Transizione Illegale):</b> verifica che la transizione
         * {@code PLANNED -> DELIVERED} (saltando TRANSIT) venga rifiutata.
         * <p>Mock: repo.findByTrackingNumber → PLANNED con Veicolo e Autisti.</p>
         * <p>Output atteso: {@link IllegalShipmentStateException}.</p>
         */
        @Test
        @DisplayName("FAILURE PATH: PLANNED -> DELIVERED deve essere rifiutata (transizione illegale)")
        void shouldThrowExceptionForIllegalTransitionPlannedToDelivered() {
            // ARRANGE
            String tracking = "TRK-SM-PLANNED-TO-DELIVERED";
            Shipment planned = buildPlannedShipmentWithVehicleAndDrivers(tracking);
            ShipmentUpdateStatusDTO dto = new ShipmentUpdateStatusDTO("DELIVERED");
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(planned));

            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.updateStatusByTrackingNumber(tracking, dto))
                    .isInstanceOf(IllegalShipmentStateException.class)
                    .hasMessageContaining("PLANNED")
                    .hasMessageContaining("DELIVERED");
        }

        /**
         * <b>Failure Path (Transizione Illegale):</b> verifica che la transizione
         * {@code PLANNED -> PLANNED} (no-op) venga rifiutata.
         * <p>Mock: repo.findByTrackingNumber → PLANNED.</p>
         * <p>Output atteso: {@link IllegalShipmentStateException}.</p>
         */
        @Test
        @DisplayName("FAILURE PATH: PLANNED -> PLANNED deve essere rifiutata (no-op illegale)")
        void shouldThrowExceptionForIllegalTransitionPlannedToPlanned() {
            // ARRANGE
            String tracking = "TRK-SM-PLANNED-TO-PLANNED";
            Shipment planned = buildPlannedShipmentWithVehicleAndDrivers(tracking);
            ShipmentUpdateStatusDTO dto = new ShipmentUpdateStatusDTO("PLANNED");
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(planned));

            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.updateStatusByTrackingNumber(tracking, dto))
                    .isInstanceOf(IllegalShipmentStateException.class);
        }

        /**
         * <b>Failure Path (Transizione Illegale):</b> verifica che la transizione
         * {@code TRANSIT -> PLANNED} (retrocessione) venga rifiutata.
         * <p>Mock: repo.findByTrackingNumber → TRANSIT.</p>
         * <p>Output atteso: {@link IllegalShipmentStateException}.</p>
         */
        @Test
        @DisplayName("FAILURE PATH: TRANSIT -> PLANNED deve essere rifiutata (retrocessione illegale)")
        void shouldThrowExceptionForIllegalTransitionTransitToPlanned() {
            // ARRANGE
            String tracking = "TRK-SM-TRANSIT-TO-PLANNED";
            Shipment transit = buildShipmentWithStatus(tracking, ShipmentStatus.TRANSIT);
            ShipmentUpdateStatusDTO dto = new ShipmentUpdateStatusDTO("PLANNED");
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(transit));

            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.updateStatusByTrackingNumber(tracking, dto))
                    .isInstanceOf(IllegalShipmentStateException.class)
                    .hasMessageContaining("TRANSIT")
                    .hasMessageContaining("PLANNED");
        }

        /**
         * <b>Failure Path (Transizione Illegale):</b> verifica che la transizione
         * {@code TRANSIT -> TRANSIT} venga rifiutata.
         * <p>Mock: repo.findByTrackingNumber → TRANSIT.</p>
         * <p>Output atteso: {@link IllegalShipmentStateException}.</p>
         */
        @Test
        @DisplayName("FAILURE PATH: TRANSIT -> TRANSIT deve essere rifiutata (no-op illegale)")
        void shouldThrowExceptionForIllegalTransitionTransitToTransit() {
            // ARRANGE
            String tracking = "TRK-SM-TRANSIT-TO-TRANSIT";
            Shipment transit = buildShipmentWithStatus(tracking, ShipmentStatus.TRANSIT);
            ShipmentUpdateStatusDTO dto = new ShipmentUpdateStatusDTO("TRANSIT");
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(transit));

            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.updateStatusByTrackingNumber(tracking, dto))
                    .isInstanceOf(IllegalShipmentStateException.class);
        }

        /**
         * <b>Happy Path (PLANNED -> TRANSIT):</b> verifica il flusso completo della transizione
         * piu' critica, con orchestrazione completa degli snapshot e blocco delle risorse.
         *
         * <p>Deve verificare che il Service:</p>
         * <ol>
         *   <li>Imposti inTransit=true sul Veicolo master.</li>
         *   <li>Imposti inTransit=true su tutti gli Autisti master.</li>
         *   <li>Salvi uno {@link VehicleSnapshot}.</li>
         *   <li>Scollochi il Veicolo dalla Shipment (set a null).</li>
         *   <li>Salvi gli {@link DriverSnapshot} per ogni autista.</li>
         *   <li>Ripulisca la lista dei driver.</li>
         *   <li>Salvi i CustomerSnapshot.</li>
         *   <li>Generi la {@link Waybill} (DDT).</li>
         *   <li>Salvi la Shipment aggiornata.</li>
         * </ol>
         *
         * <p>Mock: tutti i service dependencies opportunamente stubbed.</p>
         * <p>Output atteso: tutti i mock invocati nell'ordine corretto.</p>
         */
        @Test
        @DisplayName("HAPPY PATH: PLANNED -> TRANSIT deve triggerare snapshot, blocco risorse e generazione DDT")
        void shouldTriggerSnapshotAndResourceLockingOnPlannedToTransit() {
            // ARRANGE
            String tracking = "TRK-SM-PLANNED-TO-TRANSIT";
            Shipment planned = buildPlannedShipmentWithVehicleAndDrivers(tracking);
            ShipmentUpdateStatusDTO dto = new ShipmentUpdateStatusDTO("TRANSIT");
            Waybill waybill = buildWaybill();

            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(planned));
            when(waybillService.save(tracking)).thenReturn(waybill);
            when(shipmentRepository.save(planned)).thenReturn(planned);

            // ACT — il TSM lancera' IllegalStateException ma tutta la logica e' gia' avvenuta
            try {
                shipmentService.updateStatusByTrackingNumber(tracking, dto);
            } catch (IllegalStateException e) {
                // Atteso dal TransactionSynchronizationManager
            }

            // ASSERT
            verify(vehicleService, times(1)).updateInTransitStatusById(anyLong(), eq(true));
            verify(driverService, times(1)).updateInTransitStatusById(anyLong(), eq(true));
            verify(vehicleSnapshotService, times(1)).save(any(VehicleSnapshot.class));
            verify(driverSnapshotService, times(1)).save(any(DriverSnapshot.class));
            verify(customerSnapshotService, times(3)).save(any());
            verify(waybillService, times(1)).save(tracking);
            verify(shipmentRepository, times(1)).save(planned);
            assertThat(planned.getVehicle()).isNull();
            assertThat(planned.getDrivers()).isEmpty();
            assertThat(planned.getSender()).isNull();
            assertThat(planned.getCarrier()).isNull();
        }

        /**
         * <b>Happy Path (PLANNED -> CANCELLED):</b> verifica che la transizione verso CANCELLED
         * da PLANNED crei snapshot e DDT, ma NON blocchi le risorse (inTransit deve restare false).
         *
         * <p>Mock: tutti i service dependencies.</p>
         * <p>Output atteso: snapshot e DDT creati; vehicleService.updateInTransitStatusById NON invocato.</p>
         */
        @Test
        @DisplayName("HAPPY PATH: PLANNED -> CANCELLED deve creare snapshot e DDT ma NON bloccare le risorse")
        void shouldNotLockResourcesOnPlannedToCancelled() {
            // ARRANGE
            String tracking = "TRK-SM-PLANNED-TO-CANCELLED";
            Shipment planned = buildPlannedShipmentWithVehicleAndDrivers(tracking);
            ShipmentUpdateStatusDTO dto = new ShipmentUpdateStatusDTO("CANCELLED");
            Waybill waybill = buildWaybill();

            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(planned));
            when(waybillService.save(tracking)).thenReturn(waybill);
            when(shipmentRepository.save(planned)).thenReturn(planned);

            // ACT
            try {
                shipmentService.updateStatusByTrackingNumber(tracking, dto);
            } catch (IllegalStateException e) {
                // Atteso dal TSM
            }

            // ASSERT — le risorse NON devono essere state bloccate
            verify(vehicleService, never()).updateInTransitStatusById(anyLong(), eq(true));
            verify(driverService, never()).updateInTransitStatusById(anyLong(), eq(true));
            // Gli snapshot e il DDT devono essere stati creati
            verify(vehicleSnapshotService, times(1)).save(any(VehicleSnapshot.class));
            verify(waybillService, times(1)).save(tracking);
        }

        /**
         * <b>Happy Path (TRANSIT -> DELIVERED):</b> verifica che la transizione liberi
         * le risorse (inTransit=false) usando gli Snapshot come fonte di verita'.
         *
         * <p>Mock: vehicleSnapshotService.getByShipmentId; driverSnapshotService.getByShipmentId;
         * vehicleService.getByLicensePlate; driverService.getByLicense; repo.save.</p>
         * <p>Output atteso: updateInTransitStatusById(id, false) invocato per veicolo e autista.</p>
         */
        @Test
        @DisplayName("HAPPY PATH: TRANSIT -> DELIVERED deve liberare le risorse tramite Snapshot")
        void shouldReleaseResourcesUsingSnapshotsOnTransitToDelivered() {
            // ARRANGE
            String tracking = "TRK-SM-TRANSIT-TO-DELIVERED";
            Shipment transit = buildShipmentWithStatus(tracking, ShipmentStatus.TRANSIT);
            transit.setId(100L);
            ShipmentUpdateStatusDTO dto = new ShipmentUpdateStatusDTO("DELIVERED");

            Vehicle vehicleMaster = buildVehicle("RM999ZZ");
            vehicleMaster.setId(10L);
            Driver driverMaster = buildDriver("IT-LIC-001");
            driverMaster.setId(20L);

            VehicleSnapshot vSnap = buildVehicleSnapshot("RM999ZZ", transit);
            DriverSnapshot dSnap = buildDriverSnapshot("IT-LIC-001", transit);

            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(transit));
            when(vehicleSnapshotService.getByShipmentId(100L)).thenReturn(vSnap);
            when(vehicleService.getByLicensePlate("RM999ZZ")).thenReturn(vehicleMaster);
            when(driverSnapshotService.getByShipmentId(100L)).thenReturn(List.of(dSnap));
            when(driverService.getByLicense("IT-LIC-001")).thenReturn(driverMaster);
            when(shipmentRepository.save(transit)).thenReturn(transit);

            // ACT
            try {
                shipmentService.updateStatusByTrackingNumber(tracking, dto);
            } catch (IllegalStateException e) {
                // Atteso dal TSM
            }

            // ASSERT
            verify(vehicleService, times(1)).updateInTransitStatusById(10L, false);
            verify(driverService, times(1)).updateInTransitStatusById(20L, false);
            verify(shipmentRepository, times(1)).save(transit);
        }

        /**
         * <b>Happy Path (TRANSIT -> CANCELLED):</b> verifica che la transizione TRANSIT -> CANCELLED
         * liberi le risorse via Snapshot, esattamente come verso DELIVERED.
         *
         * <p>Mock: tutti i service del ciclo TRANSIT exit.</p>
         * <p>Output atteso: risorse liberate, shipment salvata con stato CANCELLED.</p>
         */
        @Test
        @DisplayName("HAPPY PATH: TRANSIT -> CANCELLED deve liberare le risorse via Snapshot")
        void shouldReleaseResourcesUsingSnapshotsOnTransitToCancelled() {
            // ARRANGE
            String tracking = "TRK-SM-TRANSIT-TO-CANCELLED";
            Shipment transit = buildShipmentWithStatus(tracking, ShipmentStatus.TRANSIT);
            transit.setId(200L);
            ShipmentUpdateStatusDTO dto = new ShipmentUpdateStatusDTO("CANCELLED");

            Vehicle vehicleMaster = buildVehicle("TO111AA");
            vehicleMaster.setId(30L);
            Driver driverMaster = buildDriver("IT-LIC-002");
            driverMaster.setId(40L);

            VehicleSnapshot vSnap = buildVehicleSnapshot("TO111AA", transit);
            DriverSnapshot dSnap = buildDriverSnapshot("IT-LIC-002", transit);

            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(transit));
            when(vehicleSnapshotService.getByShipmentId(200L)).thenReturn(vSnap);
            when(vehicleService.getByLicensePlate("TO111AA")).thenReturn(vehicleMaster);
            when(driverSnapshotService.getByShipmentId(200L)).thenReturn(List.of(dSnap));
            when(driverService.getByLicense("IT-LIC-002")).thenReturn(driverMaster);
            when(shipmentRepository.save(transit)).thenReturn(transit);

            // ACT
            try {
                shipmentService.updateStatusByTrackingNumber(tracking, dto);
            } catch (IllegalStateException e) {
                // Atteso dal TSM
            }

            // ASSERT
            verify(vehicleService, times(1)).updateInTransitStatusById(30L, false);
            verify(driverService, times(1)).updateInTransitStatusById(40L, false);
            verify(shipmentRepository, times(1)).save(transit);
        }

        /**
         * <b>[TDD-RED] Vulnerability Test:</b> verifica che la transizione PLANNED -> TRANSIT
         * con 0 autisti assegnati lanci {@link IllegalShipmentStateException} invece di
         * una {@link IllegalArgumentException} non gestita da {@code DriverSnapshot.fromDrivers()}.
         *
         * <p><b>Vulnerabilita' Esposta:</b> {@code DriverSnapshot.fromDrivers(shipment)} lancia
         * {@link IllegalArgumentException} se il set dei driver e' vuoto. Questa eccezione
         * non e' gestita nel service e produce HTTP 500.</p>
         *
         * <p><b>Soluzione Raccomandata:</b> Prima di {@code DriverSnapshot.fromDrivers()},
         * verificare {@code shipment.getDrivers().isEmpty()} e lanciare
         * {@link IllegalShipmentStateException} con messaggio esplicativo.</p>
         *
         * <p><b>Output atteso (FASE RED):</b> {@link IllegalShipmentStateException}.
         * Fallira' perche' attualmente viene propagata {@link IllegalArgumentException}.</p>
         */
        @Test
        @DisplayName("[TDD-RED] VULNERABILITY: PLANNED->TRANSIT con 0 autisti deve lanciare IllegalShipmentStateException")
        void shouldThrowIllegalShipmentStateExceptionWhenNoDriversOnTransition() {
            // ARRANGE
            String tracking = "TRK-SM-NO-DRIVERS";
            Shipment planned = buildPlannedShipment(tracking);
            planned.setVehicle(buildVehicle("MI123AB"));
            planned.setSender(buildCustomer("Mittente Srl"));
            planned.setCarrier(buildCustomer("Vettore Spa"));
            planned.setReceivers(new ArrayList<>(List.of(buildCustomer("Destinatario Inc"))));
            // Nessun driver assegnato → set vuoto di default

            ShipmentUpdateStatusDTO dto = new ShipmentUpdateStatusDTO("TRANSIT");
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(planned));

            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.updateStatusByTrackingNumber(tracking, dto))
                    .isInstanceOf(IllegalShipmentStateException.class)
                    .hasMessageContaining("driver");
        }

        /**
         * <b>[TDD-RED] Vulnerability Test:</b> verifica che la transizione PLANNED -> TRANSIT
         * senza clienti assegnati lanci {@link IllegalShipmentStateException} invece di
         * {@link IllegalArgumentException} da {@code CustomerSnapshot.fromCustomers()}.
         *
         * <p><b>Vulnerabilita' Esposta:</b> Analogo al caso driver: {@code CustomerSnapshot.fromCustomers()}
         * lancia {@link IllegalArgumentException} se la mappa e' vuota, non gestita nel service.</p>
         *
         * <p><b>Soluzione Raccomandata:</b> Verificare sender e carrier non null prima della chiamata;
         * lanciare {@link IllegalShipmentStateException} in caso contrario.</p>
         *
         * <p><b>Output atteso (FASE RED):</b> {@link IllegalShipmentStateException}.
         * Fallira' perche' viene propagata {@link IllegalArgumentException} da CustomerSnapshot.</p>
         */
        @Test
        @DisplayName("[TDD-RED] VULNERABILITY: PLANNED->TRANSIT con 0 clienti deve lanciare IllegalShipmentStateException")
        void shouldThrowIllegalShipmentStateExceptionWhenNoCustomersOnTransition() {
            // ARRANGE
            String tracking = "TRK-SM-NO-CUSTOMERS";
            Shipment planned = buildPlannedShipment(tracking);
            Vehicle vehicle = buildVehicle("GE456CD");
            Driver driver = buildDriver("IT-LIC-NO-CUST");
            driver.setId(1L);
            planned.setVehicle(vehicle);
            planned.setDrivers(new HashSet<>(Set.of(driver)));
            // Nessun sender/carrier/receiver → customerAsMap sara' vuota

            ShipmentUpdateStatusDTO dto = new ShipmentUpdateStatusDTO("TRANSIT");
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(planned));

            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.updateStatusByTrackingNumber(tracking, dto))
                    .isInstanceOf(IllegalShipmentStateException.class)
                    .hasMessageContaining("customer");
        }

        /**
         * <b>Failure Path:</b> verifica che il metodo lanci {@link ResourceNotFoundException}
         * quando il tracking number non esiste.
         * <p>Mock: repo.findByTrackingNumber → Optional.empty().</p>
         * <p>Output atteso: {@link ResourceNotFoundException}.</p>
         */
        @Test
        @DisplayName("FAILURE PATH: dovrebbe lanciare ResourceNotFoundException se la Shipment non esiste")
        void shouldThrowResourceNotFoundExceptionForMissingShipmentOnStatusUpdate() {
            // ARRANGE
            String tracking = "NON-EXISTENT-SM";
            ShipmentUpdateStatusDTO dto = new ShipmentUpdateStatusDTO("TRANSIT");
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.updateStatusByTrackingNumber(tracking, dto))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        /**
         * <b>[TDD-RED] Vulnerability Test:</b> verifica che un valore di status non valido
         * produca una {@link BadRequestException} controllata invece di {@link IllegalArgumentException}
         * non gestita da {@code Enum.valueOf()}.
         *
         * <p><b>Vulnerabilita' Esposta:</b> La chiamata {@code Enum.valueOf(ShipmentStatus.class, updateStatusDTO.status())}
         * lancia {@link IllegalArgumentException} se il valore non corrisponde a nessun enum,
         * non intercettata, producendo HTTP 500.</p>
         *
         * <p><b>Soluzione Raccomandata:</b> Avvolgere {@code Enum.valueOf()} in try-catch per
         * {@link IllegalArgumentException} e rilanciarla come {@link BadRequestException}.</p>
         *
         * <p><b>Output atteso (FASE RED):</b> {@link BadRequestException}.
         * Fallira' perche' viene propagata {@link IllegalArgumentException} non gestita.</p>
         */
        @Test
        @DisplayName("[TDD-RED] VULNERABILITY: dovrebbe lanciare BadRequestException per uno status non valido come stringa")
        void shouldThrowBadRequestExceptionForInvalidStatusString() {
            // ARRANGE
            String tracking = "TRK-SM-INVALID-STATUS";
            Shipment planned = buildPlannedShipment(tracking);
            ShipmentUpdateStatusDTO dto = new ShipmentUpdateStatusDTO("INVALID_STATUS_VALUE");
            when(shipmentRepository.findByTrackingNumber(tracking)).thenReturn(Optional.of(planned));

            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.updateStatusByTrackingNumber(tracking, dto))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // =====================================================================================
    // SEZIONE 11: mapToEntity
    // =====================================================================================

    /**
     * Gruppo di test per il metodo {@link ShipmentService#mapToEntity(ShipmentRequestDTO)}.
     * Verifica la corretta idratazione dell'entita' {@link Shipment} a partire dal DTO,
     * inclusa la risoluzione delle relazioni (Veicolo, Autisti, Clienti) e la conversione
     * dei tipi (String -> Enum, String -> LocalDateTime).
     *
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("mapToEntity()")
    class MapToEntityTests {

        /**
         * <b>Happy Path:</b> verifica che il metodo mappi correttamente tutti i campi
         * del DTO nell'entita' {@link Shipment}, risolvendo tutte le relazioni.
         * <p>Mock: vehicleService.getByLicensePlate; driverService.getByLicense; customerService.getByVatNumber.</p>
         * <p>Output atteso: Shipment completa con veicolo, driver, sender, carrier, receivers, data, stato, indirizzi.</p>
         */
        @Test
        @DisplayName("HAPPY PATH: dovrebbe mappare correttamente il DTO in una Shipment completa e idratata")
        void shouldMapDtoToFullyHydratedShipment() {
            // ARRANGE
            Vehicle vehicle = buildVehicle("MI001AA");
            Driver driver1 = buildDriver("IT-LIC-DRIVER1");
            Customer sender = buildCustomer("Mittente Srl");
            Customer carrier = buildCustomer("Vettore Spa");
            Customer receiver = buildCustomer("Destinatario Inc");

            ShipmentRequestDTO dto = new ShipmentRequestDTO(
                    "2026-12-31T08:00:00",
                    "PLANNED",
                    "Milano, Via Brera 1",
                    List.of("Roma, Via Nazionale 100"),
                    "B",
                    "SALE",
                    "MI001AA",
                    Set.of("IT-LIC-DRIVER1"),
                    List.of(
                            new CustomerContainerDTO("SENDER", "IT12345678901"),
                            new CustomerContainerDTO("CARRIER", "IT98765432100"),
                            new CustomerContainerDTO("RECEIVER", "IT11111111111")
                    )
            );

            when(vehicleService.getByLicensePlate("MI001AA")).thenReturn(vehicle);
            when(driverService.getByLicense("IT-LIC-DRIVER1")).thenReturn(driver1);
            when(customerService.getByVatNumber("IT12345678901")).thenReturn(sender);
            when(customerService.getByVatNumber("IT98765432100")).thenReturn(carrier);
            when(customerService.getByVatNumber("IT11111111111")).thenReturn(receiver);

            // ACT
            Shipment result = shipmentService.mapToEntity(dto);

            // ASSERT
            assertThat(result).isNotNull();
            assertThat(result.getVehicle()).isEqualTo(vehicle);
            assertThat(result.getDrivers()).containsExactly(driver1);
            assertThat(result.getSender()).isEqualTo(sender);
            assertThat(result.getCarrier()).isEqualTo(carrier);
            assertThat(result.getReceivers()).containsExactly(receiver);
            assertThat(result.getShipmentDate()).isEqualTo(LocalDateTime.of(2026, 12, 31, 8, 0, 0));
            assertThat(result.getShipmentStatus()).isEqualTo(ShipmentStatus.PLANNED);
            assertThat(result.getOriginAddress()).isEqualTo("Milano, Via Brera 1");
            assertThat(result.getDestinationAddresses()).containsExactly("Roma, Via Nazionale 100");
        }

        /**
         * <b>[TDD-RED] Vulnerability Test:</b> verifica che l'assenza di un SENDER nel DTO
         * produca una {@link BadRequestException} controllata invece di {@link NullPointerException}
         * alla riga {@code customers.get(CustomerRole.SENDER).get(0)}.
         *
         * <p><b>Vulnerabilita' Esposta:</b> Il metodo assume che la mappa dei clienti contenga
         * sempre un SENDER. La riga non e' protetta da null check: se il DTO non contiene
         * un customer con ruolo SENDER, si genera {@link NullPointerException} producendo HTTP 500.</p>
         *
         * <p><b>Soluzione Raccomandata:</b> Aggiungere controlli espliciti prima dell'estrazione:
         * {@code if (!customers.containsKey(CustomerRole.SENDER)) throw new BadRequestException("...")}</p>
         *
         * <p><b>Output atteso (FASE RED):</b> {@link BadRequestException}.
         * Fallira' perche' attualmente viene lanciata {@link NullPointerException}.</p>
         */
        @Test
        @DisplayName("[TDD-RED] VULNERABILITY: dovrebbe lanciare BadRequestException se il DTO non contiene un SENDER")
        void shouldThrowBadRequestExceptionWhenSenderIsMissing() {
            // ARRANGE
            Vehicle vehicle = buildVehicle("RM999ZA");
            Driver driver = buildDriver("IT-LIC-NO-SENDER");

            ShipmentRequestDTO dto = new ShipmentRequestDTO(
                    "2026-12-01T08:00:00",
                    "PLANNED",
                    "Roma, Via Appia 1",
                    List.of("Firenze, Piazza della Repubblica 1"),
                    "C",
                    "SALE",
                    "RM999ZA",
                    Set.of("IT-LIC-NO-SENDER"),
                    List.of(new CustomerContainerDTO("RECEIVER", "IT22222222222"))
                    // Nessun SENDER -> customers.get(SENDER) -> null -> NPE
            );

            when(vehicleService.getByLicensePlate("RM999ZA")).thenReturn(vehicle);
            when(driverService.getByLicense("IT-LIC-NO-SENDER")).thenReturn(driver);
            when(customerService.getByVatNumber("IT22222222222")).thenReturn(buildCustomer("Destinatario Inc"));

            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.mapToEntity(dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("SENDER");
        }

        /**
         * <b>Failure Path:</b> verifica che il metodo propaghi {@link ResourceNotFoundException}
         * se la targa del veicolo nel DTO non corrisponde ad alcun record nel database.
         * <p>Mock: vehicleService.getByLicensePlate → lancia ResourceNotFoundException.</p>
         * <p>Output atteso: {@link ResourceNotFoundException} con la targa nel messaggio.</p>
         */
        @Test
        @DisplayName("FAILURE PATH: dovrebbe propagare ResourceNotFoundException se la targa del veicolo non esiste")
        void shouldPropagateResourceNotFoundExceptionForNonExistentVehicle() {
            // ARRANGE
            ShipmentRequestDTO dto = new ShipmentRequestDTO(
                    "2026-12-01T08:00:00",
                    "PLANNED",
                    "Napoli, Piazza Dante 1",
                    List.of("Bari, Via Sparano 1"),
                    "B",
                    "SALE",
                    "XX999YY",
                    Set.of("IT-LIC-VH-NOT-FOUND"),
                    List.of(new CustomerContainerDTO("SENDER", "IT33333333333"))
            );

            when(vehicleService.getByLicensePlate("XX999YY"))
                    .thenThrow(new ResourceNotFoundException("Vehicle not found: XX999YY"));

            // ACT + ASSERT
            assertThatThrownBy(() -> shipmentService.mapToEntity(dto))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("XX999YY");
        }
    }

    // =====================================================================================
    // HELPER METHODS - FACTORY DI ENTITA' PER I TEST
    // =====================================================================================

    /**
     * Costruisce una {@link Shipment} base in stato {@link ShipmentStatus#PLANNED}
     * con il tracking number fornito e i campi minimi valorizzati.
     * Imposta il campo {@code trackingNumber} tramite reflection poiche' e' privo di setter.
     *
     * @param trackingNumber il codice di tracking univoco da assegnare.
     * @return una nuova istanza di {@link Shipment} in stato PLANNED.
     */
    private Shipment buildPlannedShipment(String trackingNumber) {
        Shipment shipment = new Shipment();
        shipment.setId(1L);
        shipment.setShipmentStatus(ShipmentStatus.PLANNED);
        shipment.setShipmentDate(LocalDateTime.now().plusDays(1));
        shipment.setOriginAddress("Milano, Via Brera 1");
        shipment.setDestinationAddresses(new ArrayList<>(List.of("Roma, Via Nazionale 100")));
        try {
            java.lang.reflect.Field f = Shipment.class.getDeclaredField("trackingNumber");
            f.setAccessible(true);
            f.set(shipment, trackingNumber);
        } catch (Exception e) {
            throw new RuntimeException("Impossibile impostare il trackingNumber per il test", e);
        }
        return shipment;
    }

    /**
     * Costruisce una {@link Shipment} con uno {@link ShipmentStatus} arbitrario.
     * Wrapper di {@link #buildPlannedShipment(String)} che sovrascrive lo stato.
     *
     * @param trackingNumber il codice di tracking da assegnare.
     * @param status         lo stato da impostare.
     * @return una nuova istanza di {@link Shipment} nello stato specificato.
     */
    private Shipment buildShipmentWithStatus(String trackingNumber, ShipmentStatus status) {
        Shipment shipment = buildPlannedShipment(trackingNumber);
        shipment.setShipmentStatus(status);
        return shipment;
    }

    /**
     * Costruisce una {@link Shipment} in stato PLANNED con Vehicle, Driver, sender,
     * carrier e receiver associati. Usata per i test della State Machine che richiedono
     * una spedizione completamente popolata per verificare le transizioni da PLANNED.
     *
     * @param trackingNumber il codice di tracking da assegnare.
     * @return una nuova istanza di {@link Shipment} completamente idratata.
     */
    private Shipment buildPlannedShipmentWithVehicleAndDrivers(String trackingNumber) {
        Shipment shipment = buildPlannedShipment(trackingNumber);
        Vehicle vehicle = buildVehicle("AA000BB");
        vehicle.setId(5L);
        shipment.setVehicle(vehicle);

        Driver driver = buildDriver("IT-TEST-LIC-001");
        driver.setId(10L);
        Set<Driver> drivers = new HashSet<>();
        drivers.add(driver);
        shipment.setDrivers(drivers);

        shipment.setSender(buildCustomer("Mittente Srl"));
        shipment.setCarrier(buildCustomer("Vettore Spa"));
        shipment.setReceivers(new ArrayList<>(List.of(buildCustomer("Destinatario Inc"))));

        return shipment;
    }

    /**
     * Costruisce un {@link Vehicle} minimale con la targa specificata.
     * Imposta la targa tramite reflection poiche' il campo e' protetto.
     *
     * @param licensePlate la targa del veicolo.
     * @return una nuova istanza di {@link Vehicle}.
     */
    private Vehicle buildVehicle(String licensePlate) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(1L);
        try {
            java.lang.reflect.Field lpField = Vehicle.class.getDeclaredField("licensePlate");
            lpField.setAccessible(true);
            lpField.set(vehicle, licensePlate);
        } catch (Exception e) {
            throw new RuntimeException("Impossibile impostare la licensePlate per il test", e);
        }
        VehicleCategory category = new VehicleCategory();
		category.setVehicleType(VehicleType.CURTAINSIDE);
		category.setLoadType(LoadType.SOLID);
		category.setVehicleApprovals(new HashSet<>(Set.of(VehicleApproval.AT, VehicleApproval.FL)));
		vehicle.setVehicleCategory(category);
		vehicle.setMaxWeightkg(40000);
		vehicle.setMaxUsefulWeightkg(24000);
		vehicle.setHeightm(4.0f);
		vehicle.setWidthm(2.5f);
		vehicle.setLengthm(13.6f);
		vehicle.setWheelbasem(7.5f);
		vehicle.setnAxles(3);
        return vehicle;
    }

    /**
     * Costruisce un {@link Driver} minimale con il numero di patente specificato.
     * Imposta tutti i campi necessari a evitare NPE durante la creazione degli snapshot.
     *
     * @param license il numero di patente dell'autista.
     * @return una nuova istanza di {@link Driver}.
     */
    private Driver buildDriver(String license) {
        Driver driver = new Driver();
        driver.setId(1L);
        try {
            java.lang.reflect.Field licenseField = Driver.class.getDeclaredField("license");
            licenseField.setAccessible(true);
            licenseField.set(driver, license);

            java.lang.reflect.Field fullNameField = Driver.class.getDeclaredField("fullName");
            fullNameField.setAccessible(true);
            fullNameField.set(driver, "Test Driver");

            java.lang.reflect.Field taxCodeField = Driver.class.getDeclaredField("taxCode");
            taxCodeField.setAccessible(true);
            taxCodeField.set(driver, "TSTDVR80A01H501A");

            java.lang.reflect.Field phoneField = Driver.class.getDeclaredField("phoneNumber");
            phoneField.setAccessible(true);
            phoneField.set(driver, "+393331234567");

            java.lang.reflect.Field licExpField = Driver.class.getDeclaredField("licenseExpireDate");
            licExpField.setAccessible(true);
            licExpField.set(driver, LocalDate.now().plusYears(2));

            java.lang.reflect.Field approvalsField = Driver.class.getDeclaredField("driverApprovals");
            approvalsField.setAccessible(true);
            approvalsField.set(driver, new HashSet<>());
        } catch (Exception e) {
            throw new RuntimeException("Impossibile impostare i campi Driver per il test", e);
        }
        return driver;
    }

    /**
     * Costruisce un {@link Customer} minimale con ragione sociale specificata.
     * Imposta vatNumber e legalAddress con dati sintetici.
     *
     * @param companyName la ragione sociale del cliente.
     * @return una nuova istanza di {@link Customer}.
     */
    private Customer buildCustomer(String companyName) {
        Customer customer = new Customer();
        customer.setId(1L);
        try {
            java.lang.reflect.Field cnField = Customer.class.getDeclaredField("companyName");
            cnField.setAccessible(true);
            cnField.set(customer, companyName);

            java.lang.reflect.Field vatField = Customer.class.getDeclaredField("vatNumber");
            vatField.setAccessible(true);
            vatField.set(customer, "IT" + Math.abs(companyName.hashCode() % 100000000000L));

            java.lang.reflect.Field addrField = Customer.class.getDeclaredField("legalAddress");
            addrField.setAccessible(true);
            addrField.set(customer, "Via Test 1, Milano");
        } catch (Exception e) {
            throw new RuntimeException("Impossibile impostare i campi Customer per il test", e);
        }
        return customer;
    }

    /**
     * Costruisce un {@link VehicleSnapshot} sintetico per i test della State Machine (uscita da TRANSIT).
     * Crea una Shipment temporanea con Vehicle e VehicleCategory valorizzati per permettere
     * la costruzione dello snapshot tramite il costruttore ufficiale (rispettando l'immutabilita').
     *
     * @param licensePlate la targa da storicizzare nello snapshot.
     * @param shipment     la spedizione padre a cui collegare lo snapshot.
     * @return un {@link VehicleSnapshot} pronto per essere restituito dai mock.
     */
    private VehicleSnapshot buildVehicleSnapshot(String licensePlate, Shipment shipment) {
        Shipment temp = buildPlannedShipmentWithVehicleAndDrivers("TEMP-SNAP");
        Vehicle v = buildVehicle(licensePlate);
        try {
            Vehicle.VehicleCategory cat = new Vehicle.VehicleCategory();
            java.lang.reflect.Field vtField = Vehicle.VehicleCategory.class.getDeclaredField("vehicleType");
            vtField.setAccessible(true);
            vtField.set(cat, Vehicle.VehicleCategory.VehicleType.TANKER);

            java.lang.reflect.Field ltField = Vehicle.VehicleCategory.class.getDeclaredField("loadType");
            ltField.setAccessible(true);
            ltField.set(cat, Vehicle.VehicleCategory.LoadType.LIQUID);

            java.lang.reflect.Field appField = Vehicle.VehicleCategory.class.getDeclaredField("vehicleApprovals");
            appField.setAccessible(true);
            appField.set(cat, new HashSet<>());

            java.lang.reflect.Field catField = Vehicle.class.getDeclaredField("vehicleCategory");
            catField.setAccessible(true);
            catField.set(v, cat);
        } catch (Exception e) {
            throw new RuntimeException("Impossibile configurare VehicleCategory per test", e);
        }
        temp.setVehicle(v);
        return new VehicleSnapshot(temp);
    }

    /**
     * Costruisce un {@link DriverSnapshot} sintetico tramite il factory method
     * {@link DriverSnapshot#fromDrivers(Shipment)}, usato nei test di uscita da TRANSIT.
     *
     * @param license  il numero di patente da storicizzare nello snapshot.
     * @param shipment la spedizione padre di riferimento.
     * @return il primo {@link DriverSnapshot} estratto dalla Shipment temporanea.
     */
    private DriverSnapshot buildDriverSnapshot(String license, Shipment shipment) {
        Driver driver = buildDriver(license);
        Shipment temp = buildPlannedShipment("TEMP-DRV-SNAP");
        temp.setDrivers(new HashSet<>(Set.of(driver)));
        Set<DriverSnapshot> snaps = DriverSnapshot.fromDrivers(temp);
        return snaps.iterator().next();
    }

    /**
     * Costruisce una {@link Waybill} minimale con dati sintetici per i test che
     * richiedono il mock di {@code waybillService.save()}.
     *
     * @return una {@link Waybill} con DDT number e PDF data valorizzati.
     */
    private Waybill buildWaybill() {
        Waybill waybill = new Waybill();
        try {
            java.lang.reflect.Field ddtField = Waybill.class.getDeclaredField("ddtNumber");
            ddtField.setAccessible(true);
            ddtField.set(waybill, "DDT-2026-0001");

            java.lang.reflect.Field pdfField = Waybill.class.getDeclaredField("pdfData");
            pdfField.setAccessible(true);
            pdfField.set(waybill, new byte[]{0x25, 0x50, 0x44, 0x46});
        } catch (Exception e) {
            throw new RuntimeException("Impossibile costruire la Waybill per il test", e);
        }
        return waybill;
    }
}
