package dev.vinciguerra.adrsentinel.db.shipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import dev.vinciguerra.adrsentinel.db.CaffeineCacheConfiguration;
import dev.vinciguerra.adrsentinel.db.customer.Customer;
import dev.vinciguerra.adrsentinel.db.customer.CustomerService;
import dev.vinciguerra.adrsentinel.db.customer.CustomerSnapshot;
import dev.vinciguerra.adrsentinel.db.customer.CustomerSnapshotService;
import dev.vinciguerra.adrsentinel.db.driver.Driver;
import dev.vinciguerra.adrsentinel.db.driver.Driver.DriverApproval;
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
import dev.vinciguerra.adrsentinel.exception.IllegalShipmentStateException;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentRequestDTO.CustomerContainerDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentUpdateDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentUpdateReasonDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentUpdateStatusDTO;

/**
 * Suite di test unitari per {@link ShipmentService}.
 * <p>
 * Verifica l'orchestrazione del dominio spedizioni ADR in isolamento puro (Mockito),
 * coprendo query bounded/unbounded, mutazioni transazionali, macchina a stati,
 * generazione snapshot immutabili e sincronizzazione Write-Through della cache Caffeine.
 * </p>
 *
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShipmentService - Unit Tests")
public class ShipmentServiceTests {
	private static final String TRACKING = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
	private static final String LICENSE_PLATE = "AB123CD";
	private static final String DRIVER_LICENSE = "MI1234567A";
	private static final String SENDER_VAT = "IT12345678901";
	private static final String CARRIER_VAT = "IT98765432109";
	private static final String RECEIVER_VAT = "IT11122233344";
	private static final LocalDateTime SHIPMENT_DATE_TIME = LocalDateTime.of(2026, 7, 15, 10, 0);
	private static final String ISO_DATE = "2026-07-15T10:00:00";

	@Mock
	private ShipmentRepository shipmentRepository;
	@Mock
	private VehicleService vehicleService;
	@Mock
	private VehicleSnapshotService vehicleSnapshotService;
	@Mock
	private DriverService driverService;
	@Mock
	private DriverSnapshotService driverSnapshotService;
	@Mock
	private CustomerService customerService;
	@Mock
	private CustomerSnapshotService customerSnapshotService;
	@Mock
	private WaybillService waybillService;

	private CacheManager cacheManager;
	private ShipmentService shipmentService;

	@BeforeEach
	void setUp() {
		SimpleCacheManager simpleCacheManager = new SimpleCacheManager();
		simpleCacheManager.setCaches(List.of(
			new ConcurrentMapCache(CaffeineCacheConfiguration.SHIPMENT_BY_TRACKING_NUMBER_CACHE),
			new ConcurrentMapCache(CaffeineCacheConfiguration.SHIPMENT_BY_SHIPMENT_DATE_CACHE)
		));
		simpleCacheManager.afterPropertiesSet();
		cacheManager = simpleCacheManager;

		shipmentService = new ShipmentService(
			shipmentRepository,
			vehicleService,
			vehicleSnapshotService,
			driverService,
			driverSnapshotService,
			customerService,
			customerSnapshotService,
			waybillService,
			cacheManager
		);
	}

	// -------------------------------------------------------------------------
	// Test fixtures
	// -------------------------------------------------------------------------

	private Vehicle buildVehicle() {
		VehicleCategory category = new VehicleCategory();
		category.setVehicleType(VehicleType.CURTAINSIDE);
		category.setLoadType(LoadType.SOLID);
		category.setVehicleApprovals(new HashSet<>(Set.of(VehicleApproval.AT, VehicleApproval.FL)));

		Vehicle vehicle = new Vehicle();
		vehicle.setId(10L);
		vehicle.setLicensePlate(LICENSE_PLATE);
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

	private Driver buildDriver() {
		Driver driver = new Driver();
		driver.setId(20L);
		driver.setFullName("Mario Rossi");
		driver.setTaxCode("RSSMRA80A01F205X");
		driver.setPhoneNumber("+393331234567");
		driver.setLicense(DRIVER_LICENSE);
		driver.setLicenseExpireDate(LocalDate.of(2030, 1, 1));
		driver.setDriverApprovals(new HashSet<>(Set.of(DriverApproval.BASIC)));
		return driver;
	}

	private Customer buildCustomer(String name, String vat) {
		Customer customer = new Customer();
		customer.setId(vat.hashCode() & 0xFFFFL);
		customer.setCompanyName(name);
		customer.setVatNumber(vat);
		customer.setLegalAddress("Via Legale 1, Milano");
		customer.setActive(true);
		return customer;
	}

	private Shipment buildPlannedShipment() {
		Shipment shipment = new Shipment();
		shipment.setId(1L);
		shipment.setShipmentDate(SHIPMENT_DATE_TIME);
		shipment.setShipmentStatus(ShipmentStatus.PLANNED);
		shipment.setOriginAddress("Via Roma 1, Milano");
		shipment.setDestinationAddresses(new ArrayList<>(List.of("Via Torino 2, Torino")));
		shipment.setShipmentReason(ShipmentReason.SALE);
		shipment.setTunnelRestriction(TunnelRestriction.B);
		shipment.setVehicle(buildVehicle());
		shipment.setDrivers(new HashSet<>(Set.of(buildDriver())));
		shipment.setSender(buildCustomer("Mittente SpA", SENDER_VAT));
		shipment.setCarrier(buildCustomer("Vettore SpA", CARRIER_VAT));
		shipment.setReceivers(new ArrayList<>(List.of(buildCustomer("Destinatario SpA", RECEIVER_VAT))));
		return shipment;
	}

	private Shipment buildShipmentWithStatus(ShipmentStatus status) {
		Shipment shipment = buildPlannedShipment();
		shipment.setShipmentStatus(status);
		if (status != ShipmentStatus.PLANNED) {
			shipment.setVehicle(null);
			shipment.getDrivers().clear();
			shipment.setSender(null);
			shipment.setCarrier(null);
			shipment.getReceivers().clear();
		}
		return shipment;
	}

	private ShipmentRequestDTO buildValidRequestDto() {
		return new ShipmentRequestDTO(
			ISO_DATE,
			"PLANNED",
			"Via Roma 1, Milano",
			List.of("Via Torino 2, Torino"),
			"B",
			"SALE",
			LICENSE_PLATE,
			Set.of(DRIVER_LICENSE),
			List.of(
				new CustomerContainerDTO("SENDER", SENDER_VAT),
				new CustomerContainerDTO("CARRIER", CARRIER_VAT),
				new CustomerContainerDTO("RECEIVER", RECEIVER_VAT)
			)
		);
	}

	private void runWithTransactionSync(Runnable action, Consumer<TransactionSynchronization> afterCommitAction) {
		try (MockedStatic<TransactionSynchronizationManager> txSync = mockStatic(TransactionSynchronizationManager.class)) {
			ArgumentCaptor<TransactionSynchronization> captor = ArgumentCaptor.forClass(TransactionSynchronization.class);
			txSync.when(() -> TransactionSynchronizationManager.registerSynchronization(captor.capture()))
				.thenAnswer(invocation -> null);

			action.run();

			if (!captor.getAllValues().isEmpty()) {
				afterCommitAction.accept(captor.getValue());
			}
		}
	}

	private void seedDateCacheList(Shipment shipment) {
		Cache dateCache = cacheManager.getCache(CaffeineCacheConfiguration.SHIPMENT_BY_SHIPMENT_DATE_CACHE);
		List<Shipment> existing = new ArrayList<>();
		existing.add(shipment);
		dateCache.put(shipment.getShipmentDate().toLocalDate(), existing);
	}

	// -------------------------------------------------------------------------
	// getByTrackingNumber
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("getByTrackingNumber")
	class GetByTrackingNumberTests {

		/**
		 * Verifica il recupero corretto di una spedizione esistente tramite tracking number.
		 * Mock: {@link ShipmentRepository#findByTrackingNumber(String)} restituisce Optional populated.
		 * Output atteso: l'entità {@link Shipment} corrispondente.
		 */
		@Test
		@DisplayName("shouldReturnShipmentWhenTrackingNumberExists")
		void shouldReturnShipmentWhenTrackingNumberExists() {
			Shipment expected = buildPlannedShipment();
			when(shipmentRepository.findByTrackingNumber(TRACKING)).thenReturn(Optional.of(expected));

			Shipment result = shipmentService.getByTrackingNumber(TRACKING);

			assertThat(result).isSameAs(expected);
			verify(shipmentRepository).findByTrackingNumber(TRACKING);
		}

		/**
		 * Verifica il fail-fast quando il tracking number non esiste nel repository.
		 * Mock: repository restituisce Optional.empty().
		 * Output atteso: {@link ResourceNotFoundException} con messaggio descrittivo.
		 */
		@Test
		@DisplayName("shouldThrowResourceNotFoundExceptionWhenTrackingNumberDoesNotExist")
		void shouldThrowResourceNotFoundExceptionWhenTrackingNumberDoesNotExist() {
			when(shipmentRepository.findByTrackingNumber(TRACKING)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> shipmentService.getByTrackingNumber(TRACKING))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining(TRACKING);
		}

		/**
		 * Edge case: tracking number null non valido.
		 * Fix: validazione a monte nel service layer (lancia IllegalArgumentException).
		 */
		@Test
		@DisplayName("shouldThrowIllegalArgumentExceptionWhenTrackingNumberIsNull")
		void shouldThrowIllegalArgumentExceptionWhenTrackingNumberIsNull() {
			assertThatThrownBy(() -> shipmentService.getByTrackingNumber(null))
				.isInstanceOf(IllegalArgumentException.class);
		}
	}

	// -------------------------------------------------------------------------
	// getByShipmentDate
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("getByShipmentDate")
	class GetByShipmentDateTests {

		/**
		 * Verifica la conversione da {@link LocalDate} a intervallo {@link LocalDateTime}
		 * e il delegato al repository per le daily dashboard.
		 */
		@Test
		@DisplayName("shouldReturnShipmentsForGivenDateRange")
		void shouldReturnShipmentsForGivenDateRange() {
			LocalDate targetDate = LocalDate.of(2026, 7, 15);
			LocalDateTime startOfDay = targetDate.atStartOfDay();
			LocalDateTime endOfDay = targetDate.atTime(LocalTime.MAX);
			List<Shipment> expected = List.of(buildPlannedShipment());

			when(shipmentRepository.findByShipmentDateBetween(startOfDay, endOfDay)).thenReturn(expected);

			List<Shipment> result = shipmentService.getByShipmentDate(targetDate);

			assertThat(result).isEqualTo(expected);
			verify(shipmentRepository).findByShipmentDateBetween(startOfDay, endOfDay);
		}

		/**
		 * Edge case: nessuna spedizione per la data richiesta.
		 */
		@Test
		@DisplayName("shouldReturnEmptyListWhenNoShipmentsOnDate")
		void shouldReturnEmptyListWhenNoShipmentsOnDate() {
			LocalDate targetDate = LocalDate.of(2026, 1, 1);
			when(shipmentRepository.findByShipmentDateBetween(any(), any())).thenReturn(List.of());

			List<Shipment> result = shipmentService.getByShipmentDate(targetDate);

			assertThat(result).isEmpty();
		}
	}

	// -------------------------------------------------------------------------
	// getByShipmentStatus
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("getByShipmentStatus")
	class GetByShipmentStatusTests {

		/**
		 * Verifica il recupero paginato filtrato per stato operativo tipizzato.
		 */
		@Test
		@DisplayName("shouldReturnPaginatedShipmentsByStatus")
		void shouldReturnPaginatedShipmentsByStatus() {
			Pageable pageable = PageRequest.of(0, 10);
			Shipment shipment = buildPlannedShipment();
			Page<Shipment> expectedPage = new PageImpl<>(List.of(shipment), pageable, 1);

			when(shipmentRepository.findByShipmentStatus(ShipmentStatus.PLANNED, pageable)).thenReturn(expectedPage);

			Page<Shipment> result = shipmentService.getByShipmentStatus(ShipmentStatus.PLANNED, pageable);

			assertThat(result.getContent()).containsExactly(shipment);
			verify(shipmentRepository).findByShipmentStatus(ShipmentStatus.PLANNED, pageable);
		}

		/**
		 * Edge case: pagina vuota per stato senza record.
		 */
		@Test
		@DisplayName("shouldReturnEmptyPageWhenNoShipmentsMatchStatus")
		void shouldReturnEmptyPageWhenNoShipmentsMatchStatus() {
			Pageable pageable = PageRequest.of(0, 5);
			Page<Shipment> emptyPage = Page.empty(pageable);

			when(shipmentRepository.findByShipmentStatus(ShipmentStatus.DELIVERED, pageable)).thenReturn(emptyPage);

			Page<Shipment> result = shipmentService.getByShipmentStatus(ShipmentStatus.DELIVERED, pageable);

			assertThat(result.getContent()).isEmpty();
		}
	}

	// -------------------------------------------------------------------------
	// getAllShipment
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("getAllShipment")
	class GetAllShipmentTests {

		/**
		 * Verifica il recupero paginato dell'intero storico spedizioni.
		 */
		@Test
		@DisplayName("shouldReturnPaginatedAllShipments")
		void shouldReturnPaginatedAllShipments() {
			Pageable pageable = PageRequest.of(1, 20);
			Page<Shipment> expectedPage = new PageImpl<>(List.of(buildPlannedShipment()), pageable, 50);

			when(shipmentRepository.findAll(pageable)).thenReturn(expectedPage);

			Page<Shipment> result = shipmentService.getAllShipment(pageable);

			assertThat(result.getTotalElements()).isEqualTo(50);
			verify(shipmentRepository).findAll(pageable);
		}
	}

	// -------------------------------------------------------------------------
	// save
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("save")
	class SaveTests {

		/**
		 * Verifica la persistenza e la sincronizzazione Write-Through post-commit
		 * su entrambe le regioni cache (tracking number e data spedizione).
		 */
		@Test
		@DisplayName("shouldPersistShipmentAndSyncCacheAfterCommit")
		void shouldPersistShipmentAndSyncCacheAfterCommit() {
			Shipment toSave = buildPlannedShipment();
			Shipment saved = buildPlannedShipment();
			when(shipmentRepository.save(toSave)).thenReturn(saved);

			LocalDate shipmentDate = saved.getShipmentDate().toLocalDate();
			seedDateCacheList(saved);

			runWithTransactionSync(
				() -> {
					Shipment result = shipmentService.save(toSave);
					assertThat(result).isSameAs(saved);
				},
				sync -> sync.afterCommit()
			);

			verify(shipmentRepository).save(toSave);

			Cache trackingCache = cacheManager.getCache(CaffeineCacheConfiguration.SHIPMENT_BY_TRACKING_NUMBER_CACHE);
			assertThat(trackingCache.get(saved.getTrackingNumber(), Shipment.class)).isEqualTo(saved);

			Cache dateCache = cacheManager.getCache(CaffeineCacheConfiguration.SHIPMENT_BY_SHIPMENT_DATE_CACHE);
			@SuppressWarnings("unchecked")
			List<Shipment> dateList = dateCache.get(shipmentDate, List.class);
			assertThat(dateList).contains(saved);
		}
	}

	// -------------------------------------------------------------------------
	// updateDetailsByTrackingNumber
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("updateDetailsByTrackingNumber")
	class UpdateDetailsByTrackingNumberTests {

		/**
		 * Happy path: aggiornamento anagrafico su spedizione in stato PLANNED.
		 */
		@Test
		@DisplayName("shouldUpdateDetailsWhenShipmentIsPlanned")
		void shouldUpdateDetailsWhenShipmentIsPlanned() {
			Shipment existing = buildPlannedShipment();
			Vehicle newVehicle = buildVehicle();
			newVehicle.setLicensePlate("XY999ZZ");
			newVehicle.setId(99L);

			ShipmentUpdateDTO updateDto = new ShipmentUpdateDTO(
				"2026-07-20T08:00:00",
				List.of("Nuova Destinazione 1", "Nuova Destinazione 2"),
				"XY999ZZ"
			);

			when(shipmentRepository.findByTrackingNumber(existing.getTrackingNumber())).thenReturn(Optional.of(existing));
			when(vehicleService.getByLicensePlate("XY999ZZ")).thenReturn(newVehicle);
			when(shipmentRepository.save(existing)).thenReturn(existing);
			seedDateCacheList(existing);

			runWithTransactionSync(
				() -> shipmentService.updateDetailsByTrackingNumber(existing.getTrackingNumber(), updateDto),
				sync -> sync.afterCommit()
			);

			assertThat(existing.getVehicle()).isSameAs(newVehicle);
			assertThat(existing.getShipmentDate()).isEqualTo(LocalDateTime.parse("2026-07-20T08:00:00"));
			assertThat(existing.getDestinationAddresses()).containsExactly("Nuova Destinazione 1", "Nuova Destinazione 2");
		}

		/**
		 * Unhappy path: spedizione non trovata.
		 */
		@Test
		@DisplayName("shouldThrowResourceNotFoundExceptionWhenShipmentNotFound")
		void shouldThrowResourceNotFoundExceptionWhenShipmentNotFound() {
			when(shipmentRepository.findByTrackingNumber(TRACKING)).thenReturn(Optional.empty());

			ShipmentUpdateDTO updateDto = new ShipmentUpdateDTO(ISO_DATE, List.of("Dest"), LICENSE_PLATE);

			assertThatThrownBy(() -> shipmentService.updateDetailsByTrackingNumber(TRACKING, updateDto))
				.isInstanceOf(ResourceNotFoundException.class);
		}

		/**
		 * Unhappy path: spedizione non più in PLANNED.
		 */
		@Test
		@DisplayName("shouldThrowIllegalShipmentStateExceptionWhenNotPlanned")
		void shouldThrowIllegalShipmentStateExceptionWhenNotPlanned() {
			Shipment inTransit = buildShipmentWithStatus(ShipmentStatus.TRANSIT);
			when(shipmentRepository.findByTrackingNumber(inTransit.getTrackingNumber())).thenReturn(Optional.of(inTransit));

			ShipmentUpdateDTO updateDto = new ShipmentUpdateDTO(ISO_DATE, List.of("Dest"), LICENSE_PLATE);

			assertThatThrownBy(() -> shipmentService.updateDetailsByTrackingNumber(inTransit.getTrackingNumber(), updateDto))
				.isInstanceOf(IllegalShipmentStateException.class)
				.hasMessageContaining("PLANNED");
		}

		/**
		 * Unhappy path: targa veicolo inesistente propagata da {@link VehicleService}.
		 */
		@Test
		@DisplayName("shouldPropagateResourceNotFoundWhenVehicleNotFound")
		void shouldPropagateResourceNotFoundWhenVehicleNotFound() {
			Shipment existing = buildPlannedShipment();
			when(shipmentRepository.findByTrackingNumber(existing.getTrackingNumber())).thenReturn(Optional.of(existing));
			when(vehicleService.getByLicensePlate("UNKNOWN")).thenThrow(new ResourceNotFoundException("Vehicle not found"));

			ShipmentUpdateDTO updateDto = new ShipmentUpdateDTO(ISO_DATE, List.of("Dest"), "UNKNOWN");

			assertThatThrownBy(() -> shipmentService.updateDetailsByTrackingNumber(existing.getTrackingNumber(), updateDto))
				.isInstanceOf(ResourceNotFoundException.class);
		}

		/**
		 * Edge case: data ISO malformata nel DTO.
		 * Fix: aggiunta gestione dell'eccezione DateTimeParseException (lancia IllegalArgumentException).
		 */
		@Test
		@DisplayName("shouldThrowIllegalArgumentExceptionWhenDateIsMalformed")
		void shouldThrowIllegalArgumentExceptionWhenDateIsMalformed() {
			Shipment existing = buildPlannedShipment();
			when(shipmentRepository.findByTrackingNumber(existing.getTrackingNumber())).thenReturn(Optional.of(existing));
			when(vehicleService.getByLicensePlate(LICENSE_PLATE)).thenReturn(buildVehicle());

			ShipmentUpdateDTO updateDto = new ShipmentUpdateDTO("not-a-date", List.of("Dest"), LICENSE_PLATE);

			assertThatThrownBy(() -> shipmentService.updateDetailsByTrackingNumber(existing.getTrackingNumber(), updateDto))
				.isInstanceOf(IllegalArgumentException.class);
		}
	}

	// -------------------------------------------------------------------------
	// updateShipmentReasonByTrackingNumber
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("updateShipmentReasonByTrackingNumber")
	class UpdateShipmentReasonByTrackingNumberTests {

		/**
		 * Happy path: aggiornamento causale ADR su spedizione PLANNED.
		 */
		@Test
		@DisplayName("shouldUpdateShipmentReasonWhenPlanned")
		void shouldUpdateShipmentReasonWhenPlanned() {
			Shipment existing = buildPlannedShipment();
			when(shipmentRepository.findByTrackingNumber(existing.getTrackingNumber())).thenReturn(Optional.of(existing));
			when(shipmentRepository.save(existing)).thenReturn(existing);

			ShipmentUpdateReasonDTO updateDto = new ShipmentUpdateReasonDTO("WASTE_DISPOSAL");

			runWithTransactionSync(
				() -> shipmentService.updateShipmentReasonByTrackingNumber(existing.getTrackingNumber(), updateDto),
				sync -> sync.afterCommit()
			);

			assertThat(existing.getShipmentReason()).isEqualTo(ShipmentReason.WASTE_DISPOSAL);
		}

		/**
		 * Unhappy path: spedizione non trovata.
		 */
		@Test
		@DisplayName("shouldThrowResourceNotFoundExceptionWhenNotFound")
		void shouldThrowResourceNotFoundExceptionWhenNotFound() {
			when(shipmentRepository.findByTrackingNumber(TRACKING)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> shipmentService.updateShipmentReasonByTrackingNumber(
				TRACKING, new ShipmentUpdateReasonDTO("SALE")))
				.isInstanceOf(ResourceNotFoundException.class);
		}

		/**
		 * Unhappy path: stato non PLANNED.
		 */
		@Test
		@DisplayName("shouldThrowIllegalShipmentStateExceptionWhenNotPlanned")
		void shouldThrowIllegalShipmentStateExceptionWhenNotPlanned() {
			Shipment delivered = buildShipmentWithStatus(ShipmentStatus.DELIVERED);
			when(shipmentRepository.findByTrackingNumber(delivered.getTrackingNumber())).thenReturn(Optional.of(delivered));

			assertThatThrownBy(() -> shipmentService.updateShipmentReasonByTrackingNumber(
				delivered.getTrackingNumber(), new ShipmentUpdateReasonDTO("SALE")))
				.isInstanceOf(IllegalShipmentStateException.class);
		}

		/**
		 * Unhappy path: enum causale invalida.
		 */
		@Test
		@DisplayName("shouldThrowIllegalArgumentExceptionWhenReasonIsInvalid")
		void shouldThrowIllegalArgumentExceptionWhenReasonIsInvalid() {
			Shipment existing = buildPlannedShipment();
			when(shipmentRepository.findByTrackingNumber(existing.getTrackingNumber())).thenReturn(Optional.of(existing));

			assertThatThrownBy(() -> shipmentService.updateShipmentReasonByTrackingNumber(
				existing.getTrackingNumber(), new ShipmentUpdateReasonDTO("INVALID_REASON")))
				.isInstanceOf(IllegalArgumentException.class);
		}
	}

	// -------------------------------------------------------------------------
	// updateTunnelRestrictionByTrackingNumber
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("updateTunnelRestrictionByTrackingNumber")
	class UpdateTunnelRestrictionByTrackingNumberTests {

		/**
		 * Happy path: aggiornamento restrizione gallerie su spedizione PLANNED.
		 */
		@Test
		@DisplayName("shouldUpdateTunnelRestrictionWhenPlanned")
		void shouldUpdateTunnelRestrictionWhenPlanned() {
			Shipment existing = buildPlannedShipment();
			when(shipmentRepository.findByTrackingNumber(existing.getTrackingNumber())).thenReturn(Optional.of(existing));
			when(shipmentRepository.save(existing)).thenReturn(existing);

			runWithTransactionSync(
				() -> shipmentService.updateTunnelRestrictionByTrackingNumber(TunnelRestriction.D, existing.getTrackingNumber()),
				sync -> sync.afterCommit()
			);

			assertThat(existing.getTunnelRestriction()).isEqualTo(TunnelRestriction.D);
		}

		/**
		 * Unhappy path: spedizione non trovata.
		 */
		@Test
		@DisplayName("shouldThrowResourceNotFoundExceptionWhenNotFound")
		void shouldThrowResourceNotFoundExceptionWhenNotFound() {
			when(shipmentRepository.findByTrackingNumber(TRACKING)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> shipmentService.updateTunnelRestrictionByTrackingNumber(TunnelRestriction.C, TRACKING))
				.isInstanceOf(ResourceNotFoundException.class);
		}

		/**
		 * Unhappy path: stato non PLANNED.
		 */
		@Test
		@DisplayName("shouldThrowIllegalShipmentStateExceptionWhenNotPlanned")
		void shouldThrowIllegalShipmentStateExceptionWhenNotPlanned() {
			Shipment transit = buildShipmentWithStatus(ShipmentStatus.TRANSIT);
			when(shipmentRepository.findByTrackingNumber(transit.getTrackingNumber())).thenReturn(Optional.of(transit));

			assertThatThrownBy(() -> shipmentService.updateTunnelRestrictionByTrackingNumber(TunnelRestriction.E, transit.getTrackingNumber()))
				.isInstanceOf(IllegalShipmentStateException.class);
		}
	}

	// -------------------------------------------------------------------------
	// updateStatusByTrackingNumber
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("updateStatusByTrackingNumber")
	class UpdateStatusByTrackingNumberTests {

		private void stubPlannedExitMocks(Shipment shipment) {
			when(shipmentRepository.findByTrackingNumber(shipment.getTrackingNumber())).thenReturn(Optional.of(shipment));
			when(shipmentRepository.save(shipment)).thenReturn(shipment);
			when(waybillService.save(shipment.getTrackingNumber())).thenReturn(buildWaybill(shipment));
		}

		private Waybill buildWaybill(Shipment shipment) {
			return new Waybill(
				"DDT-2026-001",
				"DDT-2026-001.pdf",
				"application/pdf",
				new byte[] { 1, 2, 3 },
				LocalDate.of(2026, 7, 15),
				shipment
			);
		}

		/**
		 * Transizione PLANNED → TRANSIT: blocca risorse, genera snapshot immutabili,
		 * scollega master e crea DDT.
		 */
		@Test
		@DisplayName("shouldTransitionFromPlannedToTransitWithSnapshotsAndWaybill")
		void shouldTransitionFromPlannedToTransitWithSnapshotsAndWaybill() {
			Shipment shipment = buildPlannedShipment();
			stubPlannedExitMocks(shipment);

			runWithTransactionSync(
				() -> shipmentService.updateStatusByTrackingNumber(
					shipment.getTrackingNumber(), new ShipmentUpdateStatusDTO("TRANSIT")),
				sync -> sync.afterCommit()
			);

			assertThat(shipment.getShipmentStatus()).isEqualTo(ShipmentStatus.TRANSIT);
			assertThat(shipment.getVehicle()).isNull();
			assertThat(shipment.getDrivers()).isEmpty();
			assertThat(shipment.getSender()).isNull();
			assertThat(shipment.getCarrier()).isNull();
			assertThat(shipment.getReceivers()).isEmpty();

			verify(vehicleService).updateInTransitStatusById(10L, true);
			verify(driverService).updateInTransitStatusById(20L, true);
			verify(vehicleSnapshotService).save(any(VehicleSnapshot.class));
			verify(driverSnapshotService, atLeastOnce()).save(any(DriverSnapshot.class));
			verify(customerSnapshotService, atLeastOnce()).save(any(CustomerSnapshot.class));
			verify(waybillService).save(shipment.getTrackingNumber());
		}

		/**
		 * Transizione PLANNED → CANCELLED: snapshot e DDT senza blocco inTransit.
		 */
		@Test
		@DisplayName("shouldTransitionFromPlannedToCancelledWithoutInTransitLock")
		void shouldTransitionFromPlannedToCancelledWithoutInTransitLock() {
			Shipment shipment = buildPlannedShipment();
			stubPlannedExitMocks(shipment);

			runWithTransactionSync(
				() -> shipmentService.updateStatusByTrackingNumber(
					shipment.getTrackingNumber(), new ShipmentUpdateStatusDTO("CANCELLED")),
				sync -> sync.afterCommit()
			);

			assertThat(shipment.getShipmentStatus()).isEqualTo(ShipmentStatus.CANCELLED);
			verify(vehicleService, never()).updateInTransitStatusById(anyLong(), eq(true));
			verify(waybillService).save(shipment.getTrackingNumber());
		}

		/**
		 * Transizione TRANSIT → DELIVERED: rilascio risorse tramite snapshot.
		 */
		@Test
		@DisplayName("shouldTransitionFromTransitToDeliveredAndReleaseResources")
		void shouldTransitionFromTransitToDeliveredAndReleaseResources() {
			Shipment shipment = buildShipmentWithStatus(ShipmentStatus.TRANSIT);
			Vehicle masterVehicle = buildVehicle();
			Driver masterDriver = buildDriver();
			VehicleSnapshot vehicleSnap = new VehicleSnapshot(buildPlannedShipment());
			DriverSnapshot driverSnap = DriverSnapshot.fromDrivers(buildPlannedShipment()).iterator().next();

			when(shipmentRepository.findByTrackingNumber(shipment.getTrackingNumber())).thenReturn(Optional.of(shipment));
			when(shipmentRepository.save(shipment)).thenReturn(shipment);
			when(vehicleSnapshotService.getByShipmentId(1L)).thenReturn(vehicleSnap);
			when(vehicleService.getByLicensePlate(vehicleSnap.getLicensePlateSnap())).thenReturn(masterVehicle);
			when(driverSnapshotService.getByShipmentId(1L)).thenReturn(List.of(driverSnap));
			when(driverService.getByLicense(driverSnap.getLicenseSnap())).thenReturn(masterDriver);

			runWithTransactionSync(
				() -> shipmentService.updateStatusByTrackingNumber(
					shipment.getTrackingNumber(), new ShipmentUpdateStatusDTO("DELIVERED")),
				sync -> sync.afterCommit()
			);

			assertThat(shipment.getShipmentStatus()).isEqualTo(ShipmentStatus.DELIVERED);
			verify(vehicleService).updateInTransitStatusById(10L, false);
			verify(driverService).updateInTransitStatusById(20L, false);
		}

		/**
		 * Transizione TRANSIT → CANCELLED: rilascio risorse anche in caso di annullamento.
		 */
		@Test
		@DisplayName("shouldTransitionFromTransitToCancelledAndReleaseResources")
		void shouldTransitionFromTransitToCancelledAndReleaseResources() {
			Shipment shipment = buildShipmentWithStatus(ShipmentStatus.TRANSIT);
			VehicleSnapshot vehicleSnap = new VehicleSnapshot(buildPlannedShipment());
			DriverSnapshot driverSnap = DriverSnapshot.fromDrivers(buildPlannedShipment()).iterator().next();

			when(shipmentRepository.findByTrackingNumber(shipment.getTrackingNumber())).thenReturn(Optional.of(shipment));
			when(shipmentRepository.save(shipment)).thenReturn(shipment);
			when(vehicleSnapshotService.getByShipmentId(1L)).thenReturn(vehicleSnap);
			when(vehicleService.getByLicensePlate(LICENSE_PLATE)).thenReturn(buildVehicle());
			when(driverSnapshotService.getByShipmentId(1L)).thenReturn(List.of(driverSnap));
			when(driverService.getByLicense(DRIVER_LICENSE)).thenReturn(buildDriver());

			runWithTransactionSync(
				() -> shipmentService.updateStatusByTrackingNumber(
					shipment.getTrackingNumber(), new ShipmentUpdateStatusDTO("CANCELLED")),
				sync -> sync.afterCommit()
			);

			assertThat(shipment.getShipmentStatus()).isEqualTo(ShipmentStatus.CANCELLED);
			verify(vehicleService).updateInTransitStatusById(10L, false);
		}

		/**
		 * Unhappy path: spedizione non trovata.
		 */
		@Test
		@DisplayName("shouldThrowResourceNotFoundExceptionWhenNotFound")
		void shouldThrowResourceNotFoundExceptionWhenNotFound() {
			when(shipmentRepository.findByTrackingNumber(TRACKING)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> shipmentService.updateStatusByTrackingNumber(
				TRACKING, new ShipmentUpdateStatusDTO("TRANSIT")))
				.isInstanceOf(ResourceNotFoundException.class);
		}

		/**
		 * Unhappy path: stato terminale DELIVERED non mutabile.
		 */
		@Test
		@DisplayName("shouldRejectUpdateFromDeliveredTerminalState")
		void shouldRejectUpdateFromDeliveredTerminalState() {
			Shipment delivered = buildShipmentWithStatus(ShipmentStatus.DELIVERED);
			when(shipmentRepository.findByTrackingNumber(delivered.getTrackingNumber())).thenReturn(Optional.of(delivered));

			assertThatThrownBy(() -> shipmentService.updateStatusByTrackingNumber(
				delivered.getTrackingNumber(), new ShipmentUpdateStatusDTO("CANCELLED")))
				.isInstanceOf(IllegalShipmentStateException.class)
				.hasMessageContaining("DELIVERED");
		}

		/**
		 * Unhappy path: stato terminale CANCELLED non mutabile.
		 */
		@Test
		@DisplayName("shouldRejectUpdateFromCancelledTerminalState")
		void shouldRejectUpdateFromCancelledTerminalState() {
			Shipment cancelled = buildShipmentWithStatus(ShipmentStatus.CANCELLED);
			when(shipmentRepository.findByTrackingNumber(cancelled.getTrackingNumber())).thenReturn(Optional.of(cancelled));

			assertThatThrownBy(() -> shipmentService.updateStatusByTrackingNumber(
				cancelled.getTrackingNumber(), new ShipmentUpdateStatusDTO("TRANSIT")))
				.isInstanceOf(IllegalShipmentStateException.class)
				.hasMessageContaining("CANCELLED");
		}

		/**
		 * Transizione illegale PLANNED → DELIVERED (salto stato TRANSIT).
		 */
		@Test
		@DisplayName("shouldRejectPlannedToDeliveredTransition")
		void shouldRejectPlannedToDeliveredTransition() {
			Shipment planned = buildPlannedShipment();
			when(shipmentRepository.findByTrackingNumber(planned.getTrackingNumber())).thenReturn(Optional.of(planned));

			assertThatThrownBy(() -> shipmentService.updateStatusByTrackingNumber(
				planned.getTrackingNumber(), new ShipmentUpdateStatusDTO("DELIVERED")))
				.isInstanceOf(IllegalShipmentStateException.class)
				.hasMessageContaining("PLANNED");
		}

		/**
		 * Transizione illegale PLANNED → PLANNED (no-op non consentita).
		 */
		@Test
		@DisplayName("shouldRejectPlannedToPlannedTransition")
		void shouldRejectPlannedToPlannedTransition() {
			Shipment planned = buildPlannedShipment();
			when(shipmentRepository.findByTrackingNumber(planned.getTrackingNumber())).thenReturn(Optional.of(planned));

			assertThatThrownBy(() -> shipmentService.updateStatusByTrackingNumber(
				planned.getTrackingNumber(), new ShipmentUpdateStatusDTO("PLANNED")))
				.isInstanceOf(IllegalShipmentStateException.class);
		}

		/**
		 * Transizione illegale TRANSIT → PLANNED (regressione non consentita).
		 */
		@Test
		@DisplayName("shouldRejectTransitToPlannedTransition")
		void shouldRejectTransitToPlannedTransition() {
			Shipment transit = buildShipmentWithStatus(ShipmentStatus.TRANSIT);
			when(shipmentRepository.findByTrackingNumber(transit.getTrackingNumber())).thenReturn(Optional.of(transit));

			assertThatThrownBy(() -> shipmentService.updateStatusByTrackingNumber(
				transit.getTrackingNumber(), new ShipmentUpdateStatusDTO("PLANNED")))
				.isInstanceOf(IllegalShipmentStateException.class)
				.hasMessageContaining("TRANSIT");
		}

		/**
		 * Transizione illegale TRANSIT → TRANSIT (no-op non consentita).
		 */
		@Test
		@DisplayName("shouldRejectTransitToTransitTransition")
		void shouldRejectTransitToTransitTransition() {
			Shipment transit = buildShipmentWithStatus(ShipmentStatus.TRANSIT);
			when(shipmentRepository.findByTrackingNumber(transit.getTrackingNumber())).thenReturn(Optional.of(transit));

			assertThatThrownBy(() -> shipmentService.updateStatusByTrackingNumber(
				transit.getTrackingNumber(), new ShipmentUpdateStatusDTO("TRANSIT")))
				.isInstanceOf(IllegalShipmentStateException.class);
		}

		/**
		 * Vulnerabilità Fix: uscita da TRANSIT senza snapshot veicolo gestita prima dello sblocco risorse.
		 */
		@Test
		@DisplayName("shouldHandleMissingVehicleSnapshotGracefullyOnTransitExit")
		void shouldHandleMissingVehicleSnapshotGracefullyOnTransitExit() {
			Shipment transit = buildShipmentWithStatus(ShipmentStatus.TRANSIT);
			when(shipmentRepository.findByTrackingNumber(transit.getTrackingNumber())).thenReturn(Optional.of(transit));
			when(shipmentRepository.save(transit)).thenReturn(transit);
			when(vehicleSnapshotService.getByShipmentId(1L)).thenThrow(new ResourceNotFoundException("Snapshot not found"));

			// Deve sopravvivere senza eccezioni e passare allo stato DELIVERED
			runWithTransactionSync(
				() -> shipmentService.updateStatusByTrackingNumber(
					transit.getTrackingNumber(), new ShipmentUpdateStatusDTO("DELIVERED")),
				sync -> sync.afterCommit()
			);

			assertThat(transit.getShipmentStatus()).isEqualTo(ShipmentStatus.DELIVERED);
		}

		/**
		 * Stato DTO invalido causa IllegalArgumentException da Enum.valueOf.
		 */
		@Test
		@DisplayName("shouldThrowIllegalArgumentExceptionWhenStatusIsInvalid")
		void shouldThrowIllegalArgumentExceptionWhenStatusIsInvalid() {
			Shipment planned = buildPlannedShipment();
			when(shipmentRepository.findByTrackingNumber(planned.getTrackingNumber())).thenReturn(Optional.of(planned));

			assertThatThrownBy(() -> shipmentService.updateStatusByTrackingNumber(
				planned.getTrackingNumber(), new ShipmentUpdateStatusDTO("INVALID_STATUS")))
				.isInstanceOf(IllegalArgumentException.class);
		}
	}

	// -------------------------------------------------------------------------
	// mapToEntity
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("mapToEntity")
	class MapToEntityTests {

		/**
		 * Happy path: idratazione completa dell'aggregato Shipment dal DTO di creazione.
		 */
		@Test
		@DisplayName("shouldMapRequestDtoToFullyHydratedShipment")
		void shouldMapRequestDtoToFullyHydratedShipment() {
			ShipmentRequestDTO dto = buildValidRequestDto();
			Vehicle vehicle = buildVehicle();
			Driver driver = buildDriver();
			Customer sender = buildCustomer("Mittente SpA", SENDER_VAT);
			Customer carrier = buildCustomer("Vettore SpA", CARRIER_VAT);
			Customer receiver = buildCustomer("Destinatario SpA", RECEIVER_VAT);

			when(vehicleService.getByLicensePlate(LICENSE_PLATE)).thenReturn(vehicle);
			when(driverService.getByLicense(DRIVER_LICENSE)).thenReturn(driver);
			when(customerService.getByVatNumber(SENDER_VAT)).thenReturn(sender);
			when(customerService.getByVatNumber(CARRIER_VAT)).thenReturn(carrier);
			when(customerService.getByVatNumber(RECEIVER_VAT)).thenReturn(receiver);

			Shipment result = shipmentService.mapToEntity(dto);

			assertThat(result.getVehicle()).isSameAs(vehicle);
			assertThat(result.getDrivers()).containsExactly(driver);
			assertThat(result.getSender()).isSameAs(sender);
			assertThat(result.getCarrier()).isSameAs(carrier);
			assertThat(result.getReceivers()).containsExactly(receiver);
			assertThat(result.getShipmentDate()).isEqualTo(LocalDateTime.parse(ISO_DATE));
			assertThat(result.getShipmentStatus()).isEqualTo(ShipmentStatus.PLANNED);
			assertThat(result.getOriginAddress()).isEqualTo("Via Roma 1, Milano");
			assertThat(result.getDestinationAddresses()).containsExactly("Via Torino 2, Torino");
		}

		/**
		 * Unhappy path: veicolo non trovato.
		 */
		@Test
		@DisplayName("shouldPropagateResourceNotFoundWhenVehicleNotFound")
		void shouldPropagateResourceNotFoundWhenVehicleNotFound() {
			when(vehicleService.getByLicensePlate(LICENSE_PLATE))
				.thenThrow(new ResourceNotFoundException("Vehicle not found"));

			assertThatThrownBy(() -> shipmentService.mapToEntity(buildValidRequestDto()))
				.isInstanceOf(ResourceNotFoundException.class);
		}

		/**
		 * Unhappy path: autista non trovato.
		 */
		@Test
		@DisplayName("shouldPropagateResourceNotFoundWhenDriverNotFound")
		void shouldPropagateResourceNotFoundWhenDriverNotFound() {
			when(vehicleService.getByLicensePlate(LICENSE_PLATE)).thenReturn(buildVehicle());
			when(driverService.getByLicense(DRIVER_LICENSE))
				.thenThrow(new ResourceNotFoundException("Driver not found"));

			assertThatThrownBy(() -> shipmentService.mapToEntity(buildValidRequestDto()))
				.isInstanceOf(ResourceNotFoundException.class);
		}

		/**
		 * Unhappy path: cliente non trovato.
		 */
		@Test
		@DisplayName("shouldPropagateResourceNotFoundWhenCustomerNotFound")
		void shouldPropagateResourceNotFoundWhenCustomerNotFound() {
			when(vehicleService.getByLicensePlate(LICENSE_PLATE)).thenReturn(buildVehicle());
			when(driverService.getByLicense(DRIVER_LICENSE)).thenReturn(buildDriver());
			when(customerService.getByVatNumber(SENDER_VAT))
				.thenThrow(new ResourceNotFoundException("Customer not found"));

			assertThatThrownBy(() -> shipmentService.mapToEntity(buildValidRequestDto()))
				.isInstanceOf(ResourceNotFoundException.class);
		}

		/**
		 * Vulnerabilità Fix: assenza di SENDER nel payload lancia IllegalArgumentException.
		 */
		@Test
		@DisplayName("shouldThrowIllegalArgumentExceptionWhenSenderRoleMissing")
		void shouldThrowIllegalArgumentExceptionWhenSenderRoleMissing() {
			ShipmentRequestDTO dto = new ShipmentRequestDTO(
				ISO_DATE, "PLANNED", "Origin", List.of("Dest"), "B", "SALE",
				LICENSE_PLATE, Set.of(DRIVER_LICENSE),
				List.of(new CustomerContainerDTO("CARRIER", CARRIER_VAT))
			);

			when(vehicleService.getByLicensePlate(LICENSE_PLATE)).thenReturn(buildVehicle());
			when(driverService.getByLicense(DRIVER_LICENSE)).thenReturn(buildDriver());
			when(customerService.getByVatNumber(CARRIER_VAT)).thenReturn(buildCustomer("Vettore", CARRIER_VAT));

			assertThatThrownBy(() -> shipmentService.mapToEntity(dto))
				.isInstanceOf(IllegalArgumentException.class);
		}

		/**
		 * Vulnerabilità Fix: {@code tunnelRestriction} e {@code shipmentReason} del DTO
		 * mappati correttamente su {@link Shipment}.
		 */
		@Test
		@DisplayName("shouldMapTunnelRestrictionAndShipmentReasonFromDto")
		void shouldMapTunnelRestrictionAndShipmentReasonFromDto() {
			ShipmentRequestDTO dto = buildValidRequestDto();
			when(vehicleService.getByLicensePlate(LICENSE_PLATE)).thenReturn(buildVehicle());
			when(driverService.getByLicense(DRIVER_LICENSE)).thenReturn(buildDriver());
			when(customerService.getByVatNumber(SENDER_VAT)).thenReturn(buildCustomer("S", SENDER_VAT));
			when(customerService.getByVatNumber(CARRIER_VAT)).thenReturn(buildCustomer("C", CARRIER_VAT));
			when(customerService.getByVatNumber(RECEIVER_VAT)).thenReturn(buildCustomer("R", RECEIVER_VAT));

			Shipment result = shipmentService.mapToEntity(dto);

			assertThat(result.getTunnelRestriction()).isEqualTo(TunnelRestriction.B);
			assertThat(result.getShipmentReason()).isEqualTo(ShipmentReason.SALE);
		}

		/**
		 * Unhappy path: status enum invalido.
		 */
		@Test
		@DisplayName("shouldThrowIllegalArgumentExceptionWhenStatusIsInvalid")
		void shouldThrowIllegalArgumentExceptionWhenStatusIsInvalid() {
			ShipmentRequestDTO dto = new ShipmentRequestDTO(
				ISO_DATE, "NOT_A_STATUS", "Origin", List.of("Dest"), "B", "SALE",
				LICENSE_PLATE, Set.of(DRIVER_LICENSE),
				List.of(new CustomerContainerDTO("SENDER", SENDER_VAT))
			);

			when(vehicleService.getByLicensePlate(LICENSE_PLATE)).thenReturn(buildVehicle());
			when(driverService.getByLicense(DRIVER_LICENSE)).thenReturn(buildDriver());
			when(customerService.getByVatNumber(SENDER_VAT)).thenReturn(buildCustomer("S", SENDER_VAT));

			assertThatThrownBy(() -> shipmentService.mapToEntity(dto))
				.isInstanceOf(IllegalArgumentException.class);
		}
	}

	// -------------------------------------------------------------------------
	// Cache sync (private methods via public API)
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Cache synchronization (syncCacheAfterInsert / syncCacheAfterUpdate)")
	class CacheSynchronizationTests {

		/**
		 * Verifica Key Shift Eviction: spostamento data spedizione invalida la vecchia lista cache.
		 */
		@Test
		@DisplayName("shouldEvictOldDateKeyWhenShipmentDateChanges")
		void shouldEvictOldDateKeyWhenShipmentDateChanges() {
			Shipment existing = buildPlannedShipment();
			LocalDate oldDate = existing.getShipmentDate().toLocalDate();
			LocalDate newDate = LocalDate.of(2026, 8, 1);

			ShipmentUpdateDTO updateDto = new ShipmentUpdateDTO(
				"2026-08-01T09:00:00",
				List.of("Dest aggiornata"),
				LICENSE_PLATE
			);

			when(shipmentRepository.findByTrackingNumber(existing.getTrackingNumber())).thenReturn(Optional.of(existing));
			when(vehicleService.getByLicensePlate(LICENSE_PLATE)).thenReturn(buildVehicle());
			when(shipmentRepository.save(existing)).thenAnswer(inv -> {
				existing.setShipmentDate(LocalDateTime.of(newDate, LocalTime.of(9, 0)));
				return existing;
			});

			List<Shipment> oldDateList = new ArrayList<>(List.of(existing));
			List<Shipment> newDateList = new ArrayList<>();
			Cache dateCache = cacheManager.getCache(CaffeineCacheConfiguration.SHIPMENT_BY_SHIPMENT_DATE_CACHE);
			dateCache.put(oldDate, oldDateList);
			dateCache.put(newDate, newDateList);

			runWithTransactionSync(
				() -> shipmentService.updateDetailsByTrackingNumber(existing.getTrackingNumber(), updateDto),
				sync -> sync.afterCommit()
			);

			@SuppressWarnings("unchecked")
			List<Shipment> updatedOldList = dateCache.get(oldDate, List.class);
			@SuppressWarnings("unchecked")
			List<Shipment> updatedNewList = dateCache.get(newDate, List.class);

			assertThat(updatedOldList).doesNotContain(existing);
			assertThat(updatedNewList).contains(existing);
		}

		/**
		 * Verifica che syncCacheAfterInsert non popoli la lista se la cache date non è pre-caricata
		 * (policy anti-lista-parziale di {@link dev.vinciguerra.adrsentinel.db.AbstractGenericService}).
		 */
		@Test
		@DisplayName("shouldNotAppendToDateListWhenCacheMissOnInsert")
		void shouldNotAppendToDateListWhenCacheMissOnInsert() {
			Shipment toSave = buildPlannedShipment();
			when(shipmentRepository.save(toSave)).thenReturn(toSave);

			runWithTransactionSync(
				() -> shipmentService.save(toSave),
				sync -> sync.afterCommit()
			);

			Cache dateCache = cacheManager.getCache(CaffeineCacheConfiguration.SHIPMENT_BY_SHIPMENT_DATE_CACHE);
			assertThat(dateCache.get(toSave.getShipmentDate().toLocalDate())).isNull();
		}
	}
}
