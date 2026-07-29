package dev.vinciguerra.adrsentinel.db.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import dev.vinciguerra.adrsentinel.db.adrclass.AdrClass;
import dev.vinciguerra.adrsentinel.db.adrclass.AdrClassService;
import dev.vinciguerra.adrsentinel.db.compatibilityrule.CompatibilityRuleService;
import dev.vinciguerra.adrsentinel.db.driver.Driver;
import dev.vinciguerra.adrsentinel.db.driver.Driver.DriverApproval;
import dev.vinciguerra.adrsentinel.db.driver.DriverService;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.PackingGroup;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.PhysicalState;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumberService;
import dev.vinciguerra.adrsentinel.db.shipmentroute.ShipmentRoute;
import dev.vinciguerra.adrsentinel.db.shipmentroute.ShipmentRouteService;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle.VehicleCategory;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle.VehicleCategory.LoadType;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle.VehicleCategory.VehicleApproval;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle.VehicleCategory.VehicleType;
import dev.vinciguerra.adrsentinel.db.vehicle.VehicleService;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;
import dev.vinciguerra.adrsentinel.web.dto.dispatch.DriverDispatchRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.dispatch.DriverDispatchResponseDTO;
import dev.vinciguerra.adrsentinel.web.dto.dispatch.VehicleDispatchRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.dispatch.VehicleDispatchRequestDTO.OnuItemRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.dispatch.VehicleDispatchResponseDTO;

/**
 * Suite di test unitari per la classe {@link DispatchService}.
 * <p>
 * I test verificano i comportamenti di dominio relativi all'ottimizzazione
 * logistica, testando flussi felici, flussi di errore e documentando tramite
 * test RED la mancanza di validazioni nel codice in esame (mindset Zero-Trust).
 * </p>
 * 
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {

    @Mock
    private AdrClassService adrClassService;

    @Mock
    private CompatibilityRuleService compatibilityRuleService;

    @Mock
    private OnuNumberService onuNumberService;

    @Mock
    private ShipmentRouteService shipmentRouteService;

    @Mock
    private VehicleService vehicleService;

    @Mock
    private DriverService driverService;

    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private DispatchService dispatchService;

    /**
     * Test annidati dedicati al metodo vehicleDispatcher.
     * 
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("Tests for vehicleDispatcher method")
    class VehicleDispatcherTests {

        /**
         * Verifica la mancanza del controllo di nullità sulla richiesta in ingresso (RED Test).
         * <p>
         * Intento Logico: Ci si aspetta che il servizio validi il payload per null per evitare NullPointerException a runtime.
         * Mock Intervenuti: Nessuno.
         * Output Atteso: Lancio della NullPointerException a causa dell'accesso a request.items() mancando un if esplicito.
         * </p>
         */
        @Test
        @DisplayName("Should throw IllegalArgumentException when request is null")
        void shouldThrowNullPointerExceptionWhenRequestIsNull() {
            // Arrange
            VehicleDispatchRequestDTO request = null;

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> dispatchService.vehicleDispatcher(request));
        }

        /**
         * Verifica la mancanza del controllo sui valori null della categoria di trasporto in OnuNumber (RED Test).
         * <p>
         * Intento Logico: La proprietà transportCategory è di tipo Integer e può essere null. 
         * L'assegnazione alla variabile primitiva int in calculateAdrPoints causerà un'eccezione di auto-unboxing 
         * che non è stata adeguatamente controllata o gestita con un fallback.
         * Mock Intervenuti: onuNumberService per la ricerca del numero ONU e compatibilityRuleService per le regole.
         * Output Atteso: NullPointerException.
         * </p>
         */
        @Test
        @DisplayName("Should throw IllegalArgumentException when transport category is null")
        void shouldThrowIllegalArgumentExceptionWhenTransportCategoryIsNull() {
            // Arrange
            OnuItemRequestDTO item = new OnuItemRequestDTO("1203", "II", "Benzina", 100, "TANK");
            VehicleDispatchRequestDTO request = new VehicleDispatchRequestDTO(List.of(item));

            OnuNumber onuNumber = new OnuNumber();
            onuNumber.setOnuCode("1203");
            onuNumber.setPhysicalState(PhysicalState.LIQUID);
            onuNumber.setTransportCategory(null);
            
            AdrClass adrClass = new AdrClass();
            adrClass.setClassCode("3");
            onuNumber.setAdrClass(adrClass);
            
            Vehicle vehicle = new Vehicle();
            vehicle.setMaxUsefulWeightkg(1000);
            vehicle.setActive(true);
            vehicle.setInTransit(false);
            vehicle.setVehicleCategory(null);
            
            when(onuNumberService.getByOnuCodeAndPackingGroupAndName("1203", PackingGroup.II, "Benzina")).thenReturn(onuNumber);
            when(compatibilityRuleService.getByAdrClassA("3")).thenReturn(Collections.emptyList());
            when(vehicleService.getByMaxUsefulWeightGreaterThanEqual(100)).thenReturn(List.of(vehicle));

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> dispatchService.vehicleDispatcher(request));
        }

        /**
         * Verifica il grave bug di immutabilità nella rimozione dei veicoli già assegnati (RED Test / Vulnerabilità).
         * <p>
         * Intento Logico: Il ciclo di iterazione tenta di rimuovere elementi tramite .remove(i) su una lista 
         * creata da .stream().toList(). In Java moderno, toList() restituisce una lista immutabile, causando 
         * una UnsupportedOperationException quando il carico è ripartito su più cluster e più veicoli.
         * Mock Intervenuti: onuNumberService, compatibilityRuleService e vehicleService.
         * Output Atteso: UnsupportedOperationException.
         * </p>
         */
        @Test
        @DisplayName("Should not throw UnsupportedOperationException when multiple clusters require multiple vehicles")
        void shouldThrowUnsupportedOperationExceptionWhenMultipleClustersRequireMultipleVehicles() {
            // Arrange
            OnuItemRequestDTO item1 = new OnuItemRequestDTO("1203", "II", "Benzina", 1000, "TANK");
            OnuItemRequestDTO item2 = new OnuItemRequestDTO("1789", "II", "Acido Cloridrico", 1000, "TANK");
            VehicleDispatchRequestDTO request = new VehicleDispatchRequestDTO(List.of(item1, item2));

            AdrClass class3 = new AdrClass();
            class3.setClassCode("3");

            AdrClass class8 = new AdrClass();
            class8.setClassCode("8");

            OnuNumber onu1 = new OnuNumber();
            onu1.setOnuCode("1203");
            onu1.setTransportCategory(2);
            onu1.setPhysicalState(PhysicalState.LIQUID);
            onu1.setAdrClass(class3);

            OnuNumber onu2 = new OnuNumber();
            onu2.setOnuCode("1789");
            onu2.setTransportCategory(2);
            onu2.setPhysicalState(PhysicalState.LIQUID);
            onu2.setAdrClass(class8);

            when(onuNumberService.getByOnuCodeAndPackingGroupAndName("1203", PackingGroup.II, "Benzina")).thenReturn(onu1);
            when(onuNumberService.getByOnuCodeAndPackingGroupAndName("1789", PackingGroup.II, "Acido Cloridrico")).thenReturn(onu2);

            when(compatibilityRuleService.getByAdrClassA("3")).thenReturn(Collections.emptyList());
            when(compatibilityRuleService.getByAdrClassA("8")).thenReturn(Collections.emptyList());

            Vehicle vehicle1 = new Vehicle();
            vehicle1.setLicensePlate("AA111AA");
            vehicle1.setActive(true);
            vehicle1.setMaxUsefulWeightkg(20000);
            VehicleCategory cat1 = new VehicleCategory();
            cat1.setVehicleType(VehicleType.TANKER);
            cat1.setLoadType(LoadType.LIQUID);
            cat1.setVehicleApprovals(Set.of(VehicleApproval.FL));
            vehicle1.setVehicleCategory(cat1);

            Vehicle vehicle2 = new Vehicle();
            vehicle2.setLicensePlate("BB222BB");
            vehicle2.setActive(true);
            vehicle2.setMaxUsefulWeightkg(20000);
            VehicleCategory cat2 = new VehicleCategory();
            cat2.setVehicleType(VehicleType.TANKER);
            cat2.setLoadType(LoadType.LIQUID);
            cat2.setVehicleApprovals(Set.of(VehicleApproval.AT));
            vehicle2.setVehicleCategory(cat2);

            when(vehicleService.getByMaxUsefulWeightGreaterThanEqual(1000))
                .thenReturn(List.of(vehicle1, vehicle2));

            // Act & Assert
            VehicleDispatchResponseDTO response = assertDoesNotThrow(() -> dispatchService.vehicleDispatcher(request));
            assertNotNull(response);
        }

        /**
         * Verifica l'assegnazione corretta di un veicolo a un singolo gruppo compatibile (Happy Path).
         * <p>
         * Intento Logico: Testare il comportamento base atteso dal dispatcher quando l'item in spedizione
         * trova un mezzo conforme in termini di portata e caratteristiche ADR e viene applicata la regola dei 1000 punti.
         * Mock Intervenuti: onuNumberService, compatibilityRuleService e vehicleService.
         * Output Atteso: Risposta valida con un veicolo associato al cluster ed esenzione attivata.
         * </p>
         */
        @Test
        @DisplayName("Should successfully assign vehicle to single cluster of compatible items")
        void shouldSuccessfullyAssignVehicleToSingleCluster() {
            // Arrange
            OnuItemRequestDTO item = new OnuItemRequestDTO("1203", "II", "Benzina", 100, "TANK");
            VehicleDispatchRequestDTO request = new VehicleDispatchRequestDTO(List.of(item));

            AdrClass class3 = new AdrClass();
            class3.setClassCode("3");

            OnuNumber onu1 = new OnuNumber();
            onu1.setOnuCode("1203");
            onu1.setTransportCategory(2);
            onu1.setPhysicalState(PhysicalState.LIQUID);
            onu1.setAdrClass(class3);

            when(onuNumberService.getByOnuCodeAndPackingGroupAndName("1203", PackingGroup.II, "Benzina")).thenReturn(onu1);
            when(compatibilityRuleService.getByAdrClassA("3")).thenReturn(Collections.emptyList());

            Vehicle vehicle = new Vehicle();
            vehicle.setLicensePlate("AA111AA");
            vehicle.setActive(true);
            vehicle.setMaxUsefulWeightkg(15000);
            VehicleCategory cat = new VehicleCategory();
            cat.setVehicleType(VehicleType.TANKER);
            cat.setLoadType(LoadType.LIQUID);
            cat.setVehicleApprovals(Set.of(VehicleApproval.FL));
            vehicle.setVehicleCategory(cat);

            List<Vehicle> vehicleList = new ArrayList<>();
            vehicleList.add(vehicle);
            when(vehicleService.getByMaxUsefulWeightGreaterThanEqual(100)).thenReturn(vehicleList);

            // Act
            VehicleDispatchResponseDTO response = dispatchService.vehicleDispatcher(request);

            // Assert
            assertThat(response.dispatches()).hasSize(1);
            assertThat(response.dispatches().get(0).assignedVehicleDTO().licensePlate()).isEqualTo("AA111AA");
            assertThat(response.dispatches().get(0).isExempt()).isTrue();
        }

        /**
         * Verifica la gestione dell'indisponibilità di flotta idonea (Failure Path).
         * <p>
         * Intento Logico: Il dispatcher deve rifiutare la transazione sollevando ResourceNotFoundException
         * qualora i pesi della spedizione superino la capacità dei mezzi aziendali.
         * Mock Intervenuti: onuNumberService, compatibilityRuleService, vehicleService (ritorna lista vuota).
         * Output Atteso: Eccezione di tipo ResourceNotFoundException recante indicazione del deficit di flotta.
         * </p>
         */
        @Test
        @DisplayName("Should throw ResourceNotFoundException when no vehicles match required payload weight")
        void shouldThrowResourceNotFoundExceptionWhenNoVehiclesFound() {
            // Arrange
            OnuItemRequestDTO item = new OnuItemRequestDTO("1203", "II", "Benzina", 50000, "TANK");
            VehicleDispatchRequestDTO request = new VehicleDispatchRequestDTO(List.of(item));

            AdrClass class3 = new AdrClass();
            class3.setClassCode("3");

            OnuNumber onu1 = new OnuNumber();
            onu1.setOnuCode("1203");
            onu1.setTransportCategory(2);
            onu1.setPhysicalState(PhysicalState.LIQUID);
            onu1.setAdrClass(class3);

            when(onuNumberService.getByOnuCodeAndPackingGroupAndName("1203", PackingGroup.II, "Benzina")).thenReturn(onu1);
            when(compatibilityRuleService.getByAdrClassA("3")).thenReturn(Collections.emptyList());
            when(vehicleService.getByMaxUsefulWeightGreaterThanEqual(50000)).thenReturn(Collections.emptyList());

            // Act & Assert
            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> dispatchService.vehicleDispatcher(request));
            assertThat(ex.getMessage()).contains("No vehicle found with payload capacity");
        }
    }

    /**
     * Test annidati dedicati al metodo driverDispatcher.
     * 
     * @author Giovanni Vinciguerra
     * @version 1.0
     * @since 1.0
     */
    @Nested
    @DisplayName("Tests for driverDispatcher method")
    class DriverDispatcherTests {

        /**
         * Verifica la mancanza del controllo di nullità sulla richiesta di driver dispatch (RED Test).
         * <p>
         * Intento Logico: Intercettare errori base mancanti dove un input null causa danni architetturali 
         * ai consumatori invece di essere bloccato nativamente all'ingresso del metodo.
         * Mock Intervenuti: Nessuno.
         * Output Atteso: NullPointerException in assenza di difese.
         * </p>
         */
        @Test
        @DisplayName("Should throw IllegalArgumentException when request is null")
        void shouldThrowIllegalArgumentExceptionWhenRequestIsNull() {
            // Arrange
            DriverDispatchRequestDTO request = null;

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> dispatchService.driverDispatcher(request));
        }

        /**
         * Verifica la mancanza del controllo di disponibilità reale per una shipmentRoute che non esiste (RED Test).
         * <p>
         * Intento Logico: Il servizio accede alla rotta recuperata senza verificarne la consistenza. Se la rotta
         * risulta introvabile nel DB, il codice andrà in crash al richiamo del metodo getEtaMinutes().
         * Mock Intervenuti: shipmentRouteService che ritorna maliziosamente null.
         * Output Atteso: NullPointerException per falla nei controlli di sicurezza logica.
         * </p>
         */
        @Test
        @DisplayName("Should throw NullPointerException when shipment route is not found in database")
        void shouldThrowNullPointerExceptionWhenRouteNotFound() {
            // Arrange
            DriverDispatchRequestDTO request = new DriverDispatchRequestDTO("AA111AA", Set.of("3"), false, UUID.randomUUID().toString());
            when(shipmentRouteService.getByRouteUUID(request.routeUUID())).thenReturn(null);

            // Act & Assert
            assertThrows(NullPointerException.class, () -> dispatchService.driverDispatcher(request));
        }

        /**
         * Verifica la corretta assegnazione di un solo autista se le tempistiche sono regolari (Happy Path).
         * <p>
         * Intento Logico: Testare le policy standard con ETA < 600 min, valutando correttamente i requisiti 
         * normativi CQC e patentini e il filtering sugli autisti per selezionarne uno idoneo.
         * Mock Intervenuti: shipmentRouteService, adrClassService, vehicleService, driverService.
         * Output Atteso: Risposta con un autista correttamente profilato (Mario Rossi).
         * </p>
         */
        @Test
        @DisplayName("Should return one eligible driver when ETA is strictly less than 600 minutes")
        void shouldReturnOneDriverWhenEtaIsBelow600() {
            // Arrange
            DriverDispatchRequestDTO request = new DriverDispatchRequestDTO("AA111AA", Set.of("3"), false, UUID.randomUUID().toString());
            
            ShipmentRoute route = new ShipmentRoute(request.routeUUID());
            route.setEtaMinutes(500); 
            
            AdrClass class3 = new AdrClass();
            class3.setClassCode("3");
            
            Vehicle vehicle = new Vehicle();
            vehicle.setMaxWeightkg(4000); 
            VehicleCategory cat = new VehicleCategory();
            cat.setVehicleApprovals(Set.of(VehicleApproval.FL)); 
            vehicle.setVehicleCategory(cat);
            
            Driver driver = new Driver();
            driver.setFullName("Mario Rossi");
            driver.setActive(true);
            driver.setLicenseExpireDate(LocalDate.now().plusYears(1));
            driver.setCqcExpireDate(LocalDate.now().plusYears(1));
            driver.setDriverApprovals(Set.of(DriverApproval.BASIC, DriverApproval.TANK));

            when(shipmentRouteService.getByRouteUUID(request.routeUUID())).thenReturn(route);
            when(adrClassService.getByClassCode("3")).thenReturn(class3);
            when(vehicleService.getByLicensePlate("AA111AA")).thenReturn(vehicle);
            when(driverService.getAllDriver()).thenReturn(List.of(driver));

            // Act
            DriverDispatchResponseDTO response = dispatchService.driverDispatcher(request);

            // Assert
            assertThat(response.dispatches()).hasSize(1);
            assertThat(response.dispatches().get(0).fullName()).isEqualTo("Mario Rossi");
        }

        /**
         * Verifica lo scarso assorbimento di guidatori non idonei o con patenti scadute (Failure Path).
         * <p>
         * Intento Logico: La logica deve rigettare con assoluta spietatezza conducenti inidonei al viaggio o 
         * in violazione del Codice della Strada (licenze e certificati CQC non conformi o passati).
         * Mock Intervenuti: shipmentRouteService, adrClassService, vehicleService, driverService.
         * Output Atteso: ResourceNotFoundException poiché nessuno supera i filtri ADR/CQC e di scadenza.
         * </p>
         */
        @Test
        @DisplayName("Should throw ResourceNotFoundException when available drivers have expired licenses or miss CQC constraints")
        void shouldThrowResourceNotFoundExceptionWhenDriversNotCompliant() {
            // Arrange
            DriverDispatchRequestDTO request = new DriverDispatchRequestDTO("AA111AA", Set.of("3"), false, UUID.randomUUID().toString());
            
            ShipmentRoute route = new ShipmentRoute(request.routeUUID());
            route.setEtaMinutes(500);
            
            AdrClass class3 = new AdrClass();
            class3.setClassCode("3");
            
            Vehicle vehicle = new Vehicle();
            vehicle.setMaxWeightkg(4000); 
            VehicleCategory cat = new VehicleCategory();
            cat.setVehicleApprovals(Set.of(VehicleApproval.FL));
            vehicle.setVehicleCategory(cat);
            
            Driver expiredDriver = new Driver();
            expiredDriver.setActive(true);
            expiredDriver.setLicenseExpireDate(LocalDate.now().minusDays(1));
            
            Driver noCqcDriver = new Driver();
            noCqcDriver.setActive(true);
            noCqcDriver.setLicenseExpireDate(LocalDate.now().plusYears(1));
            noCqcDriver.setCqcExpireDate(null);
            
            when(shipmentRouteService.getByRouteUUID(request.routeUUID())).thenReturn(route);
            when(adrClassService.getByClassCode("3")).thenReturn(class3);
            when(vehicleService.getByLicensePlate("AA111AA")).thenReturn(vehicle);
            when(driverService.getAllDriver()).thenReturn(List.of(expiredDriver, noCqcDriver));

            // Act & Assert
            assertThrows(ResourceNotFoundException.class, () -> dispatchService.driverDispatcher(request));
        }

        /**
         * Verifica l'attivazione della procedura "doppio guidatore" sui lunghi tragitti (> 10 ore) (Happy Path).
         * <p>
         * Intento Logico: Quando il calcolo dei tempi (ETA) supera il limite normativo, l'algoritmo
         * deve ritornare tassativamente una lista di 2 autisti idonei per gestire l'alternanza alla guida.
         * Mock Intervenuti: shipmentRouteService, adrClassService, vehicleService, driverService.
         * Output Atteso: Dispatch con assegnazione multipla e due driver estratti dal DB.
         * </p>
         */
        @Test
        @DisplayName("Should return two drivers when ETA is greater than 600 minutes")
        void shouldReturnTwoDriversWhenEtaIsGreaterThan600() {
            // Arrange
            DriverDispatchRequestDTO request = new DriverDispatchRequestDTO("AA111AA", Set.of("3"), true, UUID.randomUUID().toString());
            
            ShipmentRoute route = new ShipmentRoute(request.routeUUID());
            route.setEtaMinutes(601);
            
            AdrClass class3 = new AdrClass();
            class3.setClassCode("3");
            
            Vehicle vehicle = new Vehicle();
            vehicle.setMaxWeightkg(3000); 
            VehicleCategory cat = new VehicleCategory();
            cat.setVehicleApprovals(Set.of()); 
            vehicle.setVehicleCategory(cat);
            
            Driver driver1 = new Driver();
            driver1.setFullName("Aldo Baglio");
            driver1.setActive(true);
            driver1.setInTransit(false);
            driver1.setLicenseExpireDate(LocalDate.now().plusYears(1));
            driver1.setCqcExpireDate(LocalDate.now().plusYears(1));
            driver1.setDriverApprovals(EnumSet.noneOf(DriverApproval.class));

            Driver driver2 = new Driver();
            driver2.setFullName("Giovanni Storti");
            driver2.setActive(true);
            driver2.setInTransit(false);
            driver2.setLicenseExpireDate(LocalDate.now().plusYears(1));
            driver2.setCqcExpireDate(LocalDate.now().plusYears(1));
            driver2.setDriverApprovals(EnumSet.noneOf(DriverApproval.class));

            when(shipmentRouteService.getByRouteUUID(request.routeUUID())).thenReturn(route);
            when(adrClassService.getByClassCode("3")).thenReturn(class3);
            when(vehicleService.getByLicensePlate("AA111AA")).thenReturn(vehicle);
            when(driverService.getAllDriver()).thenReturn(List.of(driver1, driver2));

            // Act
            DriverDispatchResponseDTO response = dispatchService.driverDispatcher(request);

            // Assert
            assertThat(response.dispatches()).hasSize(2);
        }
    }
}
