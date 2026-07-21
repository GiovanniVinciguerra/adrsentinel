package dev.vinciguerra.adrsentinel.db.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import dev.vinciguerra.adrsentinel.db.CaffeineCacheConfiguration;
import dev.vinciguerra.adrsentinel.db.customer.Customer.CustomerRole;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment;

/**
 * Classe di test unitario per {@link CustomerSnapshotService}.
 * <p>
 * Verifica le logiche di estrazione e salvataggio degli snapshot anagrafici (immutabili).
 * Applicando un rigoroso approccio TDD difensivo (Mindset SDET, Zero-Trust),
 * sono stati inclusi test in fase RED per evidenziare mancanze di validazione 
 * strutturale e fail-fast nel codice di produzione.
 * L'isolamento è puro (senza contesto Spring, no H2).
 * </p>
 *
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
class CustomerSnapshotServiceTests {

    @Mock
    private CustomerSnapshotRepository customerSnapshotRepository;

    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private CustomerSnapshotService customerSnapshotService;

    /**
     * Suite di test dedicata al costruttore della classe (Constructor Injection).
     */
    @Nested
    @DisplayName("Costruttore e Iniezione Dipendenze")
    class ConstructorTests {

        /**
         * Verifica che l'istanza venga creata correttamente quando le dipendenze fornite sono valide.
         * Mock: Nessuno aggiuntivo richiesto.
         * Output atteso: L'istanza del service non è nulla e viene inizializzata correttamente.
         */
        @Test
        @DisplayName("Happy Path: Deve istanziare il service correttamente")
        void shouldInstantiateServiceCorrectly() {
            // Arrange & Act
            CustomerSnapshotService service = new CustomerSnapshotService(customerSnapshotRepository, cacheManager);
            
            // Assert
            assertThat(service).isNotNull();
        }

        /**
         * Verifica il fallimento costruttivo in caso di dipendenza repository mancante (null).
         * Il costruttore impiega Objects.requireNonNull che funge da guard clause.
         * Mock: Nessuno.
         * Output atteso: Lancio di NullPointerException con messaggio specifico.
         */
        @Test
        @DisplayName("Unhappy Path: Deve lanciare NullPointerException se il repository è null")
        void shouldThrowNullPointerExceptionWhenRepositoryIsNull() {
            // Arrange, Act & Assert
            assertThatThrownBy(() -> new CustomerSnapshotService(null, cacheManager))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("customerSnapshotRepository must not be null.");
        }
    }

    /**
     * Suite di test dedicata al metodo getByShipmentId(Long id).
     */
    @Nested
    @DisplayName("Metodo: getByShipmentId")
    class GetByShipmentIdTests {

        /**
         * Verifica l'estrazione corretta degli snapshot per un ID di spedizione valido e presente a sistema.
         * Mock: customerSnapshotRepository istruito per restituire una lista contenente uno snapshot.
         * Output atteso: La lista restituita dal service corrisponde a quella fornita dal repository (1 elemento).
         */
        @Test
        @DisplayName("Happy Path: Deve restituire la lista di snapshot per un ID spedizione valido")
        void shouldReturnListOfSnapshotsWhenShipmentExists() {
        	// Arrange
            Long shipmentId = 1L;

            CustomerSnapshot snapshot = createMockedSnapshot();

            List<CustomerSnapshot> expectedList = List.of(snapshot);

            when(customerSnapshotRepository.findByShipment_Id(shipmentId))
                .thenReturn(expectedList);

            // Act
            List<CustomerSnapshot> result =
                customerSnapshotService.getByShipmentId(shipmentId);

            // Assert
            assertThat(result)
                .isNotNull()
                .hasSize(1)
                .isEqualTo(expectedList);

            verify(customerSnapshotRepository)
                .findByShipment_Id(shipmentId);
        }

        /**
         * Verifica il comportamento Edge Case in caso di assenza di snapshot per l'ID richiesto.
         * Mock: customerSnapshotRepository istruito per restituire una lista vuota.
         * Output atteso: Viene restituita una lista vuota al chiamante, senza lancio di eccezioni.
         */
        @Test
        @DisplayName("Edge Case: Deve restituire una lista vuota se non ci sono snapshot associati alla spedizione")
        void shouldReturnEmptyListWhenNoSnapshotsFound() {
            // Arrange
            Long shipmentId = 999L;
            when(customerSnapshotRepository.findByShipment_Id(shipmentId)).thenReturn(Collections.emptyList());

            // Act
            List<CustomerSnapshot> result = customerSnapshotService.getByShipmentId(shipmentId);

            // Assert
            assertThat(result)
                .isNotNull()
                .isEmpty();
            verify(customerSnapshotRepository).findByShipment_Id(shipmentId);
        }

        /**
         * [TDD-RED] Verifica vulnerabilità: assenza di validazione null-check.
         * Il codice in produzione non controlla se l'ID passato è null prima di interrogare il DB.
         * Questo test DEVE fallire intenzionalmente (Fase RED), poichè attesta la mancanza
         * di una validazione fondamentale (Fail-Fast) nel Service Layer.
         * Mock: Nessuno (il controllo dovrebbe fermarsi prima).
         * Output atteso: IllegalArgumentException (che il sistema non lancia, provocando il fallimento del test).
         */
        @Test
        @DisplayName("Fase RED: Deve lanciare IllegalArgumentException se l'ID spedizione è null [TDD-RED]")
        void shouldThrowIllegalArgumentExceptionWhenIdIsNull_RED() {
            // Arrange & Act & Assert (Aspettativa TDD RED - Il codice andrà in errore o non lancerà l'eccezione attesa)
            assertThatThrownBy(() -> customerSnapshotService.getByShipmentId(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Shipment ID cannot be null");
        }
    }

    /**
     * Suite di test dedicata al metodo save e alla sincronizzazione della cache in Post-Commit.
     * Utilizza il TransactionSynchronizationManager per simulare il perimetro transazionale Spring.
     */
    @Nested
    @DisplayName("Metodo: save")
    class SaveTests {

        /**
         * Setup del contesto di transazione Mock per poter testare il blocco "registerSynchronization".
         */
        @BeforeEach
        void setUpTransaction() {
            TransactionSynchronizationManager.initSynchronization();
        }

        /**
         * Chiusura e pulizia del contesto di transazione post-esecuzione.
         */
        @AfterEach
        void tearDownTransaction() {
            TransactionSynchronizationManager.clear();
        }

        /**
         * Verifica il salvataggio nominale dell'entità Snapshot e l'accodamento asincrono della cache.
         * Mock: customerSnapshotRepository, cacheManager, Cache. Simula un "Cache Miss" iniziale.
         * Output atteso: L'entità viene salvata e, all'innesco di afterCommit(), l'AbstractGenericService
         * tenta correttamente il fetch della lista dalla cache per applicare l'append.
         */
        @Test
        @DisplayName("Happy Path: Deve salvare lo snapshot e registrare l'aggiornamento in cache")
        void shouldSaveSnapshotAndRegisterCacheSynchronization() {
        	// Arrange
            Long shipmentId = 100L;

            CustomerSnapshot snapshotToSave =
                createMockedSnapshotWithShipmentId(shipmentId);

            when(customerSnapshotRepository.save(snapshotToSave))
                .thenReturn(snapshotToSave);

            Cache mockCache = mock(Cache.class);

            when(cacheManager.getCache(
                    CaffeineCacheConfiguration.CUSTOMER_SNAPSHOT_BY_SHIPMENT_ID_CACHE))
                .thenReturn(mockCache);

            // Simula Cache Miss per la query di append in RAM
            when(mockCache.get(shipmentId))
                .thenReturn(null);

            // Act
            CustomerSnapshot result =
                customerSnapshotService.save(snapshotToSave);

            // Assert
            assertThat(result)
                .isNotNull()
                .isEqualTo(snapshotToSave);

            verify(customerSnapshotRepository)
                .save(snapshotToSave);

            // Simula il database che esegue il Commit,
            // triggerando la fase di Post-Commit (syncCacheAfterInsert)
            List<TransactionSynchronization> syncs =
                TransactionSynchronizationManager.getSynchronizations();

            assertThat(syncs)
                .hasSize(1);

            syncs.get(0).afterCommit();
            
            // Verifica che il motore Write-Through della superclasse
            // abbia tentato il recupero della cache
            verify(cacheManager)
                .getCache(
                    CaffeineCacheConfiguration.CUSTOMER_SNAPSHOT_BY_SHIPMENT_ID_CACHE);

            verify(mockCache)
                .get(shipmentId);
        }

        /**
         * [TDD-RED] Verifica vulnerabilità: assenza di validazione null sull'entità in ingresso.
         * Il service accede al vatNumber per il log senza verificare se newCustomerSnapshot è null, 
         * causando una NPE, invece di fermare il flusso con IllegalArgumentException logica.
         * Mock: Nessuno.
         * Output atteso: IllegalArgumentException (che il sistema non lancia, provocando il fallimento).
         */
        @Test
        @DisplayName("Fase RED: Deve lanciare IllegalArgumentException se lo snapshot da salvare è null [TDD-RED]")
        void shouldThrowIllegalArgumentExceptionWhenSnapshotIsNull_RED() {
            // Arrange, Act & Assert (Aspettativa TDD RED)
            assertThatThrownBy(() -> customerSnapshotService.save(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CustomerSnapshot cannot be null");
        }

        /**
         * [TDD-RED] Verifica vulnerabilità: assenza di validazione strutturale dell'entità Snapshot.
         * Lo snapshot generato deve avere un collegamento alla Shipment, altrimenti la fase "afterCommit"
         * va in NPE quando cerca di ricavare la chiave per la cache (getShipment().getId()).
         * Questa vulnerabilità dev'essere pre-cettata all'ingresso nel metodo.
         * Mock: Solo le dipendenze di istanziazione, nessuna operazione sul DB deve partire.
         * Output atteso: IllegalArgumentException dal blocco try (che non c'è, dunque fallisce il test).
         */
        @Test
        @DisplayName("Fase RED: Deve lanciare IllegalArgumentException se lo snapshot non ha Shipment associata [TDD-RED]")
        void shouldThrowIllegalArgumentExceptionWhenShipmentIsNull_RED() {
            // Arrange
            Customer customer = mock(Customer.class);
            when(customer.getCompanyName()).thenReturn("Acme Corp");
            when(customer.getVatNumber()).thenReturn("IT999999999");
            when(customer.getLegalAddress()).thenReturn("Via Roma 1");
            
            // Utilizzo la riflessione costruttiva interna per generare uno stato parzialmente invalido
            // L'entità così creata possiede i dati anagrafici, ma non la chiave esterna shipment.
            CustomerSnapshot invalidSnapshot = new CustomerSnapshot(customer, CustomerRole.SENDER);

            // Act & Assert (Aspettativa TDD RED - Il servizio accetta l'entità malformata provocando poi danni collaterali)
            assertThatThrownBy(() -> customerSnapshotService.save(invalidSnapshot))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Shipment associated to snapshot cannot be null");
        }
    }

    /**
     * Crea un'istanza di {@link CustomerSnapshot} utilizzando una spedizione mockata
     * contenente un cliente mittente con dati anagrafici predefiniti.
     * <p>
     * Il metodo costruisce un mock di {@link Shipment} configurando la relazione con
     * un insieme di clienti tramite {@link Shipment#getCustomerAsMap()}.
     * Viene inoltre creato un mock di {@link Customer} valorizzando le principali
     * informazioni anagrafiche utilizzate durante la generazione dello snapshot:
     * </p>
     *
     * <ul>
     *     <li>nome azienda;</li>
     *     <li>partita IVA;</li>
     *     <li>indirizzo della sede legale.</li>
     * </ul>
     *
     * <p>
     * La spedizione mockata viene poi passata al metodo factory
     * {@link CustomerSnapshot#fromCustomers(Shipment)}, che genera l'insieme degli
     * snapshot cliente. Il metodo restituisce il primo snapshot prodotto.
     * </p>
     *
     * <p>
     * Questa variante della factory è destinata ai test che necessitano solamente
     * di un {@link CustomerSnapshot} valido e non dipendono dall'identificativo
     * della spedizione o da comportamenti specifici della cache.
     * </p>
     *
     * @return un {@link CustomerSnapshot} generato a partire dalla spedizione mockata
     */
    private CustomerSnapshot createMockedSnapshot() {
    	Shipment shipment = mock(Shipment.class);

        Customer customer = mock(Customer.class);
        when(customer.getCompanyName()).thenReturn("Global Logistics LLC");
        when(customer.getVatNumber()).thenReturn("US123456789");
        when(customer.getLegalAddress()).thenReturn("123 Tech Lane, NY");

        Map<CustomerRole, List<Customer>> customerMap = new EnumMap<>(CustomerRole.class);
        customerMap.put(CustomerRole.SENDER, List.of(customer));

        when(shipment.getCustomerAsMap()).thenReturn(customerMap);

        Set<CustomerSnapshot> snapshots = CustomerSnapshot.fromCustomers(shipment);

        return snapshots.iterator().next();
    }
    
    /**
     * Crea un'istanza di {@link CustomerSnapshot} simulando una spedizione associata
     * ad un identificativo specifico.
     * <p>
     * Il metodo costruisce un mock di {@link Shipment} configurando:
     * <ul>
     *     <li>l'identificativo della spedizione tramite {@code shipmentId};</li>
     *     <li>una mappa clienti contenente un mittente ({@link CustomerRole#SENDER});</li>
     *     <li>i dati anagrafici del cliente necessari alla generazione dello snapshot.</li>
     * </ul>
     * Successivamente utilizza il metodo factory {@link CustomerSnapshot#fromCustomers(Shipment)}
     * per generare lo snapshot corrispondente e restituisce il primo elemento prodotto.
     * </p>
     *
     * <p>
     * Questa variante della factory è destinata ai test che verificano flussi in cui
     * l'identificativo della spedizione viene utilizzato durante l'elaborazione,
     * ad esempio la sincronizzazione della cache post-commit.
     * </p>
     *
     * @param shipmentId identificativo della spedizione da associare allo snapshot simulato
     * @return un {@link CustomerSnapshot} generato a partire dalla spedizione mockata
     */
    private CustomerSnapshot createMockedSnapshotWithShipmentId(Long shipmentId) {
        Shipment shipment = mock(Shipment.class);
        when(shipment.getId()).thenReturn(shipmentId);

        Customer customer = mock(Customer.class);
        when(customer.getCompanyName()).thenReturn("Global Logistics LLC");
        when(customer.getVatNumber()).thenReturn("US123456789");
        when(customer.getLegalAddress()).thenReturn("123 Tech Lane, NY");

        Map<CustomerRole, List<Customer>> customerMap = new EnumMap<>(CustomerRole.class);
        customerMap.put(CustomerRole.SENDER, List.of(customer));

        when(shipment.getCustomerAsMap()).thenReturn(customerMap);

        Set<CustomerSnapshot> snapshots = CustomerSnapshot.fromCustomers(shipment);

        return snapshots.iterator().next();
    }
}
