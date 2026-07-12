package dev.vinciguerra.adrsentinel.db.shipment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import dev.vinciguerra.adrsentinel.db.AbstractGenericService;
import dev.vinciguerra.adrsentinel.db.CaffeineCacheConfiguration;
import dev.vinciguerra.adrsentinel.db.customer.Customer;
import dev.vinciguerra.adrsentinel.db.customer.Customer.CustomerRole;
import dev.vinciguerra.adrsentinel.db.customer.CustomerService;
import dev.vinciguerra.adrsentinel.db.customer.CustomerSnapshot;
import dev.vinciguerra.adrsentinel.db.customer.CustomerSnapshotService;
import dev.vinciguerra.adrsentinel.db.driver.Driver;
import dev.vinciguerra.adrsentinel.db.driver.DriverService;
import dev.vinciguerra.adrsentinel.db.driver.DriverSnapshot;
import dev.vinciguerra.adrsentinel.db.driver.DriverSnapshotService;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.TunnelRestriction;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment.ShipmentReason;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment.ShipmentStatus;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle;
import dev.vinciguerra.adrsentinel.db.vehicle.VehicleService;
import dev.vinciguerra.adrsentinel.db.vehicle.VehicleSnapshot;
import dev.vinciguerra.adrsentinel.db.vehicle.VehicleSnapshotService;
import dev.vinciguerra.adrsentinel.db.waybill.Waybill;
import dev.vinciguerra.adrsentinel.db.waybill.WaybillService;
import dev.vinciguerra.adrsentinel.exception.IllegalShipmentStateException;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentUpdateDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentUpdateReasonDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentUpdateStatusDTO;

/**
 * Strato di Business Logic (Service Layer) per l'orchestrazione del dominio {@link Shipment}.
 * <p>
 * <b>Architettura Ibrida (Hybrid Data Strategy):</b><br>
 * Questa classe implementa una rigorosa politica di accesso ai dati basata sull'analisi 
 * volumetrica e sul Capacity Planning, dividendo le operazioni in due ecosistemi:
 * <ul>
 * <li><b>Bounded Data (L1 Cache - RAM):</b> Query che generano un set di risultati finito 
 * e prevedibile (es. ricerca per singolo Tracking Number o per singola giornata operativa). 
 * Delegate al motore Caffeine per garantire latenza zero (O(1)).</li>
 * <li><b>Unbounded Data (Paginazione Diretta - DB):</b> Query potenzialmente infinite 
 * (es. storico globale, storico decennale di un veicolo, raggruppamenti per stato). 
 * Bypassano intenzionalmente la L1 Cache per prevenire fenomeni di <i>Cache Thrashing</i> 
 * e l'esaurimento della memoria (OutOfMemoryError), delegando il carico al Database 
 * tramite impaginazione rigida.</li>
 * </ul>
 * </p>
 * <p>
 * <b>Sincronizzazione (Write-Through Pattern):</b><br>
 * Le operazioni di mutazione (Save/Update) sono responsabili dell'aggiornamento atomico 
 * sia del database fisico che delle regioni di memoria in RAM, prevenendo letture 
 * di dati obsoleti (Stale Data).
 * </p>
 * @author Giovanni Vinciguerra
 * @version 3.0 (Paginazione Veicoli Inclusa)
 * @since 1.0
 */
@Service
public class ShipmentService extends AbstractGenericService {
	private final ShipmentRepository shipmentRepository;
	private final VehicleService vehicleService;
	private final VehicleSnapshotService vehicleSnapshotService;
	private final DriverService driverService;
	private final DriverSnapshotService driverSnapshotService;
	private final CustomerService customerService;
	private final CustomerSnapshotService customerSnapshotService;
	private final WaybillService waybillService;
	
	/**
	 * Costruttore con Dependency Injection nativa di Spring.
	 * @param shipmentRepository il livello di accesso ai dati fisici (Database).
	 * @param cacheManager il gestore dell'infrastruttura di memoria (iniettato nella superclasse).
	 */
	public ShipmentService(ShipmentRepository shipmentRepository, VehicleService vehicleService, VehicleSnapshotService vehicleSnapshotService, DriverService driverService,
			DriverSnapshotService driverSnapshotService, CustomerService customerService, CustomerSnapshotService customerSnapshotService, @Lazy WaybillService waybillService,
			CacheManager cacheManager) {
		super(cacheManager);
		this.shipmentRepository = shipmentRepository;
		this.vehicleService = vehicleService;
		this.vehicleSnapshotService = vehicleSnapshotService;
		this.driverService = driverService;
		this.driverSnapshotService = driverSnapshotService;
		this.customerService = customerService;
		this.customerSnapshotService = customerSnapshotService;
		this.waybillService = waybillService;
	}
	
	// --- SEZIONE 1: BOUNDED DATA (CACHED) ---
	/**
	 * Recupera una singola spedizione basandosi sulla sua chiave di business univoca (Tracking Number).
	 * <p>
	 * <b>Meccanismo di Caching:</b><br>
	 * Interroga preventivamente la regione di cache {@code SHIPMENT_BY_TRACKING_NUMBER_CACHE}. 
	 * Se si verifica un <i>Cache Hit</i>, il database non viene interrogato. 
	 * In caso di <i>Cache Miss</i>, esegue la query fisica, popola la RAM e restituisce l'oggetto.
	 * </p>
	 * @param trackingNumber il codice alfanumerico univoco identificativo della spedizione.
	 * @return l'entità {@link Shipment} trovata.
	 * @throws ResourceNotFoundException se il tracking number non esiste nel sistema.
	 */
	@Cacheable(value = CaffeineCacheConfiguration.SHIPMENT_BY_TRACKING_NUMBER_CACHE, key = "#trackingNumber")
	public Shipment getByTrackingNumber(String trackingNumber) throws ResourceNotFoundException {
		logger.info("[DataBase CALL] Searching for the Shipment by trackingNumber: {}", trackingNumber);
		return shipmentRepository.findByTrackingNumber(trackingNumber)
			.orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + trackingNumber));
	}
	
	/**
	 * Recupera il piano operativo di tutte le spedizioni previste o avvenute in una specifica giornata.
	 * <p>
	 * Esegue una conversione implicita da {@link LocalDate} a un intervallo {@link LocalDateTime} 
	 * (dalle 00:00:00 alle 23:59:59) per interfacciarsi correttamente con il Timestamp del database.
	 * Ottimizzato tramite caching per supportare le <i>Daily Dashboards</i> ad alto traffico.
	 * </p>
	 * @param targetDate la data operativa da analizzare.
	 * @return la lista finita di spedizioni appartenenti a quella specifica data.
	 */
	@Cacheable(value = CaffeineCacheConfiguration.SHIPMENT_BY_SHIPMENT_DATE_CACHE, key = "#targetDate")
	public List<Shipment> getByShipmentDate(LocalDate targetDate) {
		logger.info("[DataBase CALL] Searching for the Shipment by shipmentDate: {}", targetDate);
		LocalDateTime startOfDay = targetDate.atStartOfDay();
		LocalDateTime endOfDay = targetDate.atTime(LocalTime.MAX);
		return shipmentRepository.findByShipmentDateBetween(startOfDay, endOfDay);
	}
	
	// --- SEZIONE 2: UNBOUNDED DATA (PAGINATED & DIRECT DB) ---
	
	/**
	 * Recupera un blocco paginato di Spedizioni (Shipments) filtrandole in base al loro 
	 * attuale stato nel ciclo di vita operativo (es. in bozza, in transito, completata).
	 * <p>
	 * <b>Sicurezza del Dominio (Type-Safe Querying):</b><br>
	 * Il parametro di filtro è fortemente tipizzato tramite l'enumerazione {@link ShipmentStatus}. 
	 * A differenza dei filtri testuali basati su Stringhe, questo approccio elimina alla radice 
	 * la possibilità di eseguire query per stati inesistenti, refusi o attacchi di manipolazione. 
	 * Lo strato di validazione a monte (Controller) garantisce che al Service arrivino esclusivamente 
	 * stati previsti dalla logica di business.
	 * </p>
	 * <p>
	 * <b>Osservabilità Strutturata:</b><br>
	 * L'invocazione di {@code shipmentStatus.name()} all'interno del logger è una best practice 
	 * per l'aggregazione dei log. Assicura che i sistemi di monitoraggio (es. ELK Stack, Datadog) 
	 * ricevano un valore letterale puro e indicizzabile (es. "IN_TRANSIT"), isolando il formato del log 
	 * da eventuali implementazioni custom del metodo {@code toString()} all'interno dell'Enum.
	 * </p>
	 * <p>
	 * <b>Gestione della Memoria (Paginazione):</b><br>
	 * L'estrazione per stato può generare volumi di dati imponenti (es. interrogare tutte le spedizioni 
	 * "CONSEGNATE" di un intero anno). L'uso obbligatorio di {@link Pageable} impone un'estrazione 
	 * a blocchi (Chunking) tramite costrutti SQL {@code LIMIT/OFFSET}, preservando la stabilità della RAM 
	 * del server (prevenzione di OutOfMemoryError).
	 * </p>
	 * @param shipmentStatus Lo stato operativo che funge da criterio di ricerca (es. CREATA, IN_VIAGGIO). 
	 * Parametro tipizzato e garantito non nullo dalle logiche di Edge Validation.
	 * @param pageable       L'oggetto di trasporto contenente le direttive di paginazione (numero di pagina, 
	 * grandezza del blocco) e di ordinamento (es. ordinamento per data di aggiornamento stato) 
	 * delegate dal Controller.
	 * @return Un involucro {@link Page} popolato con le entità {@link Shipment} che si trovano nello stato 
	 * richiesto, completo dei metadati utili al Presentation Layer per la gestione delle griglie dati.
	 */
	public Page<Shipment> getByShipmentStatus(ShipmentStatus shipmentStatus, Pageable pageable) {
		logger.info("[DataBase CALL] Paginated search for Shipment Status: {}", shipmentStatus.name());
		return shipmentRepository.findByShipmentStatus(shipmentStatus, pageable);
	}
	
	/**
	 * Esegue il recupero massivo e paginato dell'intero storico delle Spedizioni (Shipments) registrate a sistema.
	 * <p>
	 * <b>Architettura della Paginazione (Big Data Ready):</b><br>
	 * Questo metodo è progettato per operare in totale sicurezza su tabelle ad alta densità. 
	 * Sfruttando l'incapsulamento fornito da {@link Pageable}, previene il caricamento in memoria 
	 * dell'intera collezione (scongiurando crolli prestazionali e OutOfMemoryError). Il framework 
	 * tradurrà dinamicamente la richiesta in query SQL ottimizzate con clausole di {@code LIMIT} e {@code OFFSET}.
	 * </p>
	 * <p>
	 * <b>Strategia di Ordinamento (Agnostic Sorting):</b><br>
	 * Per precisa scelta architetturale, questo strato di servizio risulta "unopinionated" (neutrale) 
	 * rispetto all'ordinamento dei dati. Se l'oggetto {@code pageable} in ingresso non contiene 
	 * direttive di sorting, il metodo non applicherà alcuna regola di business forzata (es. ordinamento 
	 * per data decrescente). I record verranno estratti nell'ordine naturale (Default Order) stabilito 
	 * dal motore relazionale (tipicamente l'ordine di inserimento basato sulla Primary Key). 
	 * La responsabilità del sorting è interamente demandata al Presentation Layer (Controller) o al Client.
	 * </p>
	 * <p>
	 * <b>Osservabilità e Diagnostica:</b><br>
	 * La traccia log a livello INFO funge da sentinella prestazionale, garantendo trasparenza 
	 * in fase di audit e permettendo di monitorare la frequenza delle query massive inviate a PostgreSQL.
	 * </p>
	 * @param pageable L'oggetto di trasporto contenente le coordinate spaziali della richiesta: 
	 * il numero di pagina (Zero-Based), la dimensione del blocco (Chunk Size) e gli eventuali 
	 * criteri di ordinamento dinamico richiesti a monte.
	 * @return Un contenitore {@link Page} popolato con il frammento di entità {@link Shipment} richiesto. 
	 * Include nativamente tutti i metadati di contesto (numero totale di record, totale pagine, 
	 * flag di prima/ultima pagina) necessari al frontend per il corretto rendering delle Data Grid.
	 */
	public Page<Shipment> getAllShipment(Pageable pageable) {
		logger.info("[DataBase CALL] Paginated search for the Shipment (ALL)");
		return shipmentRepository.findAll(pageable);
	}
	
	/**
	 * Persiste una nuova spedizione o aggiorna lo stato di una esistente (Upsert).
	 * <p>
	 * <b>Motore Write-Through:</b><br>
	 * Dopo aver consolidato il dato su disco (RDBMS), questo metodo scatena eventi di 
	 * sincronizzazione granulare per riflettere il nuovo stato nelle regioni L1 Cache 
	 * gestite da Caffeine, garantendo la coerenza finale dei dati (Eventual Consistency).
	 * </p>
	 * @param newShipment il DTO di dominio da persistere.
	 * @return l'entità consolidata restituita da Hibernate (con ID generato/aggiornato).
	 */
	@Transactional
	public Shipment save(Shipment newShipment) {
		logger.info("[DataBase CALL] Saving new Shipment with trackingNumber: {}", newShipment.getTrackingNumber());
		Shipment savedShipment = shipmentRepository.save(newShipment);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterInsert(savedShipment); }
		});
		return savedShipment;
	}
	
	/**
	 * Aggiorna i dati logistici anagrafici di una spedizione esistente tramite il suo Tracking Number.
	 * <p>
	 * <b>Pattern Architetturale (Full Update via PUT):</b><br>
	 * Questo metodo implementa la semantica rigorosa del verbo HTTP PUT. Il client è tenuto 
	 * a fornire un payload completo (garantito dallo strato di validazione {@code @Valid} a monte). 
	 * Il servizio sovrascriverà i campi bersaglio senza necessità di controlli di esistenza (null checks), 
	 * mantenendo il codice lineare e privo di logica condizionale complessa.
	 * </p>
	 * <p>
	 * <b>Gestione Relazioni e Dirty Checking:</b><br>
	 * L'entità {@link Shipment} recuperata entra in stato 'Managed'. Le assegnazioni successive 
	 * scatenano il meccanismo di Dirty Checking di Hibernate, che genererà una query di UPDATE 
	 * ottimizzata in fase di commit transazionale.
	 * </p>
	 * @param trackingNumber L'identificativo di business univoco (Business Key) della spedizione.
	 * @param updateDto Il DTO contenente l'intero set di dati anagrafici (mai parziale).
	 * @return L'entità aggiornata.
	 * @throws ResourceNotFoundException Se il tracking number o la targa fornita non esistono a sistema.
	 * @throws IllegalShipmentStateException Se questo Shipment non è più nello stato PLANNED.
	 */
	@Transactional
	public Shipment updateDetailsByTrackingNumber(String trackingNumber, ShipmentUpdateDTO updateDto) throws ResourceNotFoundException, IllegalShipmentStateException {
		logger.info("[DataBase CALL] Updating Shipment with trackingNumber: {}", trackingNumber);
		Shipment shipment = shipmentRepository.findByTrackingNumber(trackingNumber)
			.orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + trackingNumber));
		if(shipment.getShipmentStatus() != ShipmentStatus.PLANNED)
			throw new IllegalShipmentStateException("Update denied: shipment is no longer in PLANNED status.");
		final LocalDate oldDate = shipment.getShipmentDate().toLocalDate();
		Vehicle vehicle = vehicleService.getByLicensePlate(updateDto.vehicleLicensePlate());
		shipment.setVehicle(vehicle);
		shipment.setShipmentDate(LocalDateTime.parse(updateDto.date()));
		shipment.setDestinationAddresses(updateDto.destinations());
		Shipment updatedShipment = shipmentRepository.save(shipment);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterUpdate(updatedShipment, oldDate); }
		});
		return updatedShipment;
	}
	
	/**
	 * Aggiorna la causale di trasporto (Shipment Reason) per una specifica spedizione ADR,
	 * localizzata tramite il suo Tracking Number univoco.
	 * <p>
	 * <b>Logica di Business e Vincoli di Stato:</b><br>
	 * Il metodo agisce come un guardiano per la coerenza dei dati operativi. Prima di 
	 * applicare qualsiasi modifica, verifica che il ciclo di vita della spedizione lo 
	 * consenta. L'aggiornamento è permesso <i>esclusivamente</i> se la spedizione si trova 
	 * nello stato {@code PLANNED}. Tentativi di alterare la causale su spedizioni già 
	 * avviate, in transito o completate vengono respinti in modo fail-fast.
	 * </p>
	 * <p>
	 * <b>Gestione Transazionale e Sicurezza della Cache:</b><br>
	 * Operando all'interno di un contesto {@link Transactional}, il metodo garantisce 
	 * l'assoluta consistenza tra il database e il livello di caching. L'invocazione del 
	 * metodo di allineamento della cache ({@code syncCacheAfterUpdate}) viene deliberatamente 
	 * posticipata e registrata tramite il {@link TransactionSynchronizationManager}. 
	 * Questo pattern avanzato assicura che la cache venga modificata o invalidata 
	 * <i>solo e soltanto se</i> la transazione sul database esegue il commit con successo, 
	 * annullando il rischio di disallineamenti in caso di rollback (Cache Poisoning).
	 * </p>
	 * @param trackingNumber la stringa alfanumerica univoca (es. UUID) che identifica la spedizione
	 * @param updateDto il DTO (Data Transfer Object) immutabile contenente la nuova 
	 * causale di trasporto, preventivamente validata dallo strato Web
	 * @return l'entità {@link Shipment} aggiornata, persistita e pronta per essere mappata 
	 * nella response
	 * @throws ResourceNotFoundException se il tracking number fornito non corrisponde 
	 * ad alcuna spedizione presente nel database
	 * @throws IllegalShipmentStateException se la spedizione esiste, ma il suo stato attuale 
	 * non è {@code PLANNED} (es. la merce è già in viaggio)
	 */
	@Transactional
	public Shipment updateShipmentReasonByTrackingNumber(String trackingNumber, ShipmentUpdateReasonDTO updateDto) 
			throws ResourceNotFoundException, IllegalShipmentStateException {
		logger.info("[DataBase CALL] Updating Shipment shipment reason with trackingNumber: {}", trackingNumber);
		Shipment shipment = shipmentRepository.findByTrackingNumber(trackingNumber)
			.orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + trackingNumber));
		if(shipment.getShipmentStatus() != ShipmentStatus.PLANNED)
			throw new IllegalShipmentStateException("Update denied: shipment is no longer in PLANNED status.");
		final LocalDate oldDate = shipment.getShipmentDate().toLocalDate();
		shipment.setShipmentReason(Enum.valueOf(ShipmentReason.class, updateDto.shipmentReason()));
		Shipment updatedShipment = shipmentRepository.save(shipment);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterUpdate(updatedShipment, oldDate); }
		});
		return updatedShipment;
	}
	
	/**
	 * Aggiorna la classe di restrizione per le gallerie (Tunnel Restriction) di una specifica spedizione 
	 * identificata dal suo Tracking Number.
	 * <p>
	 * <b>Logica di Business:</b><br>
	 * L'operazione di aggiornamento è soggetta a un rigido vincolo di stato: è consentita <b>esclusivamente</b> 
	 * se la spedizione si trova nello stato {@link ShipmentStatus#PLANNED}. Qualsiasi tentativo di modifica su 
	 * spedizioni già in transito, consegnate o annullate solleverà un'eccezione per preservare l'integrità 
	 * e la legalità del tracciato di viaggio.
	 * </p>
	 * <p>
	 * <b>Dettagli Architetturali e Gestione della Cache:</b><br>
	 * Il metodo è transazionale e ottimizzato per ambienti a singola istanza (Single-Node). 
	 * Per prevenire il fenomeno del <i>Cache Poisoning</i> (Avvelenamento della Cache) e la <i>Race Condition</i> 
	 * in caso di fallimento del database o accessi concorrenti, la sincronizzazione della cache applicativa (Caffeine) 
	 * è completamente disaccoppiata dall'esecuzione principale tramite {@link TransactionSynchronizationManager}.
	 * <ul>
	 * <li><b>Salvataggio:</b> Viene sfruttato il <i>Dirty Checking</i> di Hibernate (con un {@code save()} esplicito 
	 * per eventuali trigger o listener JPA).</li>
	 * <li><b>Hook Transazionale:</b> L'aggiornamento <i>In-Place</i> della RAM ({@code syncCacheAfterUpdate}) viene 
	 * accodato ed eseguito <b>solo ed esclusivamente</b> a seguito di un {@code COMMIT} confermato sul database.</li>
	 * <li><b>Cache Key Integrity:</b> Lo stato della data originale ({@code oldDate}) viene congelato prima 
	 * dell'aggiornamento per garantire una corretta rilocazione/aggiornamento nell'indice della cache in memoria.</li>
	 * </ul>
	 * </p>
	 * @param tunnelRestriction La nuova classe di restrizione ADR per gallerie (es. B, C, D, E) da applicare 
	 * all'intero carico della spedizione.
	 * @param trackingNumber L'identificativo alfanumerico univoco della spedizione (Business Key).
	 * @return L'entità {@link Shipment} aggiornata, gestita (<i>Managed</i>) dal Persistence Context.
	 * @throws ResourceNotFoundException Se non esiste alcuna spedizione nel database associata al 
	 * Tracking Number fornito.
	 * @throws IllegalShipmentStateException Se la spedizione viene trovata, ma il suo stato attuale non è 
	 * {@code PLANNED} (es. {@code IN_TRANSIT}, {@code DELIVERED}).
	 */
	@Transactional
	public Shipment updateTunnelRestrictionByTrackingNumber(TunnelRestriction tunnelRestriction, String trackingNumber)
			throws ResourceNotFoundException, IllegalShipmentStateException {
		logger.info("[DataBase CALL] Updating Shipment with trackingNumber: {}", trackingNumber);
		Shipment shipment = shipmentRepository.findByTrackingNumber(trackingNumber)
			.orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + trackingNumber));
		if(shipment.getShipmentStatus() != ShipmentStatus.PLANNED)
			throw new IllegalShipmentStateException("Update denied: shipment is no longer in PLANNED status.");
		final LocalDate oldDate = shipment.getShipmentDate().toLocalDate();
		shipment.setTunnelRestriction(tunnelRestriction);
		Shipment updatedShipment = shipmentRepository.save(shipment);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterUpdate(updatedShipment, oldDate); }
		});
		return updatedShipment;
	}
	
	/**
	 * Esegue l'aggiornamento chirurgico dello stato logistico di una spedizione esistente, 
	 * garantendo l'allineamento transazionale tra Database e Memoria Cache, e orchestrando 
	 * il ciclo di vita delle entità correlate (Veicoli, Autisti e Snapshot).
	 * <p><b>Contesto Architetturale e Transazionalità:</b></p>
	 * Il metodo opera all'interno di un contesto {@code @Transactional}. L'aggiornamento sul 
	 * database (via Hibernate) e la successiva mutazione della memoria RAM (Caffeine) sono 
	 * strettamente disaccoppiati. L'orchestrazione della cache viene deferita alla fase di 
	 * <i>Post-Commit</i> tramite {@link TransactionSynchronizationManager}. Questo previene 
	 * scenari di "Dirty Read" o disallineamenti: se la transazione sul DB fallisce (Rollback), 
	 * la cache non viene inquinata.
	 * <p><b>Flusso Operativo e Macchina a Stati:</b></p>
	 * <ol>
	 * <li><b>Lookup:</b> Recupera l'entità tramite la sua Business Key (Tracking Number).</li>
	 * <li><b>State Capture (La Bussola della Cache):</b> Estrae e congela in memoria la data di spedizione 
	 * corrente ({@code oldDate}). Sebbene la data non subisca variazioni in questo specifico flusso, 
	 * essa viene passata al motore di cache ({@code syncCacheAfterUpdate}) per permettergli di 
	 * valutare l'assenza di un "Key Shift" e agire con una sostituzione (Upsert) sicura e 
	 * localizzata all'interno della corretta lista in RAM.</li>
	 * <li><b>Validazione Nodi Pozzo:</b> Verifica che lo stato non sia già terminale ({@code DELIVERED} o {@code CANCELED}). 
	 * In tal caso, rifiuta immediatamente qualsiasi mutazione.</li>
	 * <li><b>Mutazione di Stato e Logica di Dominio:</b> Applica le seguenti rigide regole di transizione:
	 * <ul>
	 * <li><b>Uscita da {@code PLANNED}:</b> Rappresenta il trigger univoco per la storicizzazione. Indipendentemente 
	 * dalla destinazione ({@code TRANSIT} o {@code CANCELED}), genera la bolla di viaggio (DDT), genera e persiste uno snapshot storico 
	 * ({@link VehicleSnapshot}, {@link DriverSnapshot}) e {@link CustomerSnapshot} per cristallizzare i dati. Successivamente, 
	 * <b>scollega</b> la spedizione dai master ({@link Vehicle}, {@link Driver}), {@link Customer} , permettendo loro di seguire 
	 * un ciclo di vita indipendente. Nel caso specifico della transizione verso {@code TRANSIT}, blocca le risorse 
	 * fisiche impostandole in stato di transito ({@code inTransit = true}).</li>
	 * <li><b>Uscita da {@code TRANSIT}:</b> Al termine del viaggio (verso {@code DELIVERED} o {@code CANCELED}), 
	 * le risorse bloccate vengono liberate. Il sistema attinge esclusivamente agli snapshot precedentemente generati 
	 * (unica fonte di verità del "manifest") per recuperare le entità master originali e ripristinare il loro 
	 * stato di disponibilità ({@code inTransit = false}).</li>
	 * </ul>
	 * </li>
	 * <li><b>Persistenza:</b> Salva l'entità aggiornata e registra la sincronizzazione della cache.</li>
	 * </ol>
	 * <p>
	 * Tutti i passaggi di stato consentiti e mappati da questo metodo sono:
	 * <ul>
	 * <li>{@code PLANNED -to-> TRANSIT -to-> DELIVERED}</li>
	 * <li>{@code PLANNED -to-> CANCELLED}</li>
	 * <li>{@code TRANSIT -to-> CANCELLED}</li>
	 * </ul>
	 * </p>
	 * @param trackingNumber La Business Key (Targa alfanumerica) che identifica univocamente la spedizione nel sistema.
	 * @param updateStatusDTO Il payload contenente il nuovo stato logistico (es. "TRANSIT", "DELIVERED", "CANCELED").
	 * @return L'istanza aggiornata di {@link Shipment}, ricaricata col nuovo stato e persistita.
	 * @throws ResourceNotFoundException Se il {@code trackingNumber} fornito non trova riscontro nel database. Oppure se 
	 * gli snapshot del veicolo e degli autisti non sono presenti nel Database.
	 * @throws IllegalShipmentStateException Se si tenta di alterare uno stato terminale (Nodo Pozzo) o se la 
	 * transizione richiesta viola i vincoli della macchina a stati (es. {@code PLANNED -> DELIVERED} o {@code TRANSIT -> PLANNED}).
	 */
	@Transactional
	public Shipment updateStatusByTrackingNumber(String trackingNumber, ShipmentUpdateStatusDTO updateStatusDTO) throws IllegalShipmentStateException {
		logger.info("[DataBase CALL] Updating Shipment status with trackingNumber: {}", trackingNumber);
		Shipment shipment = shipmentRepository.findByTrackingNumber(trackingNumber)
			.orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + trackingNumber));
		ShipmentStatus oldStatus = shipment.getShipmentStatus();
		ShipmentStatus newStatus = Enum.valueOf(ShipmentStatus.class, updateStatusDTO.status());
		LocalDate oldDate = shipment.getShipmentDate().toLocalDate();
		// 1. GESTIONE STATI TERMINALI (Nodi Pozzo)
		if(oldStatus == ShipmentStatus.DELIVERED || oldStatus == ShipmentStatus.CANCELLED) {
			throw new IllegalShipmentStateException(
				String.format(
					"Update status denied: previous status is %s. No changes are permitted.",
					oldStatus
				)
			);
		}
		// 2. LOGICA IN USCITA DALLO STATO INIZIALE (PLANNED)
	    if (oldStatus == ShipmentStatus.PLANNED) {
	    	// Da PLANNED posso andare solo in TRANSIT o CANCELED
	        if (newStatus == ShipmentStatus.DELIVERED || newStatus == ShipmentStatus.PLANNED) {
	            throw new IllegalShipmentStateException(
	                String.format("Update status denied: cannot transition from PLANNED to %s. Allowed: TRANSIT, CANCELED.", newStatus)
	            );
	        }
	        // Se la spedizione entra effettivamente in transito, blocco le risorse
	        if (newStatus == ShipmentStatus.TRANSIT) {
	        	vehicleService.updateInTransitStatusById(shipment.getVehicle().getId(), true);
	        	shipment.getDrivers().forEach(driver -> driverService.updateInTransitStatusById(driver.getId(), true));
	        }
	        // Se va in CANCELED, non tocca 'inTransit' (le risorse non sono mai partite, rimangono false)
	        // Indipendentemente da TRANSIT o CANCELED, sta lasciando lo stato PLANNED.
	        // È questo l'unico momento in cui crea lo snapshot e scollega definitivamente i master.
	        logger.info("Shipment [{}] leaving PLANNED state. Executing one-time snapshot and detachment.", trackingNumber);
	        vehicleSnapshotService.save(new VehicleSnapshot(shipment));
	        shipment.setVehicle(null);
	        DriverSnapshot.fromDrivers(shipment).forEach(driverSnapshotService::save);
	        shipment.getDrivers().clear();
	        CustomerSnapshot.fromCustomers(shipment).forEach(customerSnapshotService::save);
	        shipment.setSender(null);
	        shipment.setCarrier(null);
	        shipment.getReceivers().clear();
	        /* Creazione della bolla di viaggio (DDT) */
	        logger.info("Shipment [{}] left PLANNED state changing to {}. Triggering automatic Waybill (D.D.T.) generation.", trackingNumber, newStatus);
	        Waybill waybill = waybillService.save(trackingNumber);
	        logger.info(
	        	"[Waybill-Engine] D.D.T. [{}] successfully generated and persisted for Shipment [{}]. Payload size: {} bytes.",
	        	waybill.getDdtNumber(),
	        	trackingNumber,
	        	waybill.getPdfData().length
	        );
	    } else if(oldStatus == ShipmentStatus.TRANSIT) { // 3. LOGICA IN USCITA DALLO STATO OPERATIVO (TRANSIT)
	    	// Da TRANSIT può andare solo in DELIVERED o CANCELED
	        if (newStatus == ShipmentStatus.PLANNED || newStatus == ShipmentStatus.TRANSIT) {
	            throw new IllegalShipmentStateException(
	                String.format("Update status denied: cannot transition from TRANSIT to %s. Allowed: DELIVERED, CANCELED.", newStatus)
	            );
	        }
	        // Il viaggio è finito (o con successo o annullato).
	        // I master sono già stati scollegati durante l'uscita da PLANNED.
	        // Usa gli snapshot (fonte di verità) per sbloccare i veicoli e gli autisti originali.
	        logger.info("Shipment [{}] leaving TRANSIT state. Releasing inTransit lock on resources.", trackingNumber);
	        VehicleSnapshot vehicleSnap = vehicleSnapshotService.getByShipmentId(shipment.getId());
	        Vehicle vehicleMaster = vehicleService.getByLicensePlate(vehicleSnap.getLicensePlateSnap());
	        vehicleService.updateInTransitStatusById(vehicleMaster.getId(), false);
	        List<DriverSnapshot> driverSnaps = driverSnapshotService.getByShipmentId(shipment.getId());
	        driverSnaps.forEach(driverSnap -> {
	        	Driver driverMaster = driverService.getByLicense(driverSnap.getLicenseSnap());
	        	driverService.updateInTransitStatusById(driverMaster.getId(), false);
	        });
	    }
	    // 4. PERSISTENZA E SINCRONIZZAZIONE CACHE
	    shipment.setShipmentStatus(newStatus);
	    Shipment updatedShipment = shipmentRepository.save(shipment);
	    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
	        @Override
	        public void afterCommit() { syncCacheAfterUpdate(updatedShipment, oldDate); }
	    });
	    return updatedShipment;
	}
	
	/**
	 * Orchestratore di allineamento delle cache (Write-Through) invocato a seguito 
	 * della persistenza di una nuova entità nel database (INSERT).
	 * <p><b>Contesto Architetturale:</b></p>
	 * Questo metodo garantisce la "Strict Consistency" tra il database relazionale e la 
	 * memoria RAM (Caffeine) immediatamente dopo il commit di una transazione di salvataggio.
	 * <p><b>Comportamento Operativo:</b></p>
	 * Essendo il ciclo di vita dell'entità appena iniziato, non esiste uno stato precedente.
	 * Di conseguenza, non vi è alcun rischio di "Key Shift" (mutazione della chiave). 
	 * Il metodo delega l'operazione al motore di Upsert di base (4 parametri), popolando 
	 * simultaneamente:
	 * <ul>
	 * <li><b>Cache Immutabile (Single Record):</b> Indicizza l'entità per la sua Business Key primaria ({@code TrackingNumber}).</li>
	 * <li><b>Cache Mutabile (List Record):</b> Accoda l'entità nella lista corrispondente alla sua data di competenza ({@code ShipmentDate}).</li>
	 * </ul>
	 * @param savedShipment L'entità {@link Shipment} appena salvata sul database (deve contenere i valori generati, es. ID o chiavi finali).
	 */
	private void syncCacheAfterInsert(Shipment savedShipment) {
		storeInCache(
			CaffeineCacheConfiguration.SHIPMENT_BY_TRACKING_NUMBER_CACHE,
			savedShipment.getTrackingNumber(),
			savedShipment,
			CacheOperation.SINGLE_RECORD
		);
		storeInCache(
			CaffeineCacheConfiguration.SHIPMENT_BY_SHIPMENT_DATE_CACHE,
			savedShipment.getShipmentDate().toLocalDate(),
			savedShipment,
			CacheOperation.LIST_RECORD
		);
	}
	
	/**
	 * Orchestratore avanzato di allineamento delle cache invocato a seguito della 
	 * mutazione di stato di un'entità esistente (UPDATE).
	 * <p><b>Contesto Architetturale:</b></p>
	 * A differenza di un normale salvataggio, un aggiornamento può alterare i campi 
	 * utilizzati come chiavi di raggruppamento nelle cache (es. lo spostamento di una spedizione 
	 * da una data all'altra). Questo metodo implementa una strategia ibrida per prevenire la 
	 * corruzione dei dati in RAM ("Stale Data" o "Dati Fantasma").
	 * <p><b>Comportamento Operativo Differenziato:</b></p>
	 * <ul>
	 * <li><b>Chiavi Immutabili ({@code TrackingNumber}):</b> Essendo il Tracking una chiave di dominio 
	 * inalterabile, non subisce mutazioni. Viene invocato il motore di Upsert di base 
	 * (4 parametri) per sovrascrivere silenziosamente il vecchio valore in memoria.</li>
	 * <li><b>Chiavi Mutabili ({@code ShipmentDate}):</b> La data può variare. Viene invocato 
	 * il motore di Eviction avanzato (5 parametri), iniettando la {@code oldKey}. 
	 * Se il motore rileva un "Key Shift" ({@code oldKey != newKey}), provvederà a epurare 
	 * il record fantasma dalla vecchia lista prima di inserire il record aggiornato nella nuova lista.</li>
	 * </ul>
	 * @param updatedShipment L'entità {@link Shipment} aggiornata e sincronizzata con il database.
	 * @param oldKey Il valore della chiave mutabile (es. la vecchia {@link LocalDate}) <i>prima</i> che l'entità venisse aggiornata. 
	 * Utilizzato come trigger vitale per il meccanismo di Key Shift Eviction.
	 */
	private void syncCacheAfterUpdate(Shipment updatedShipment, Object oldKey) {
		storeInCache(
			CaffeineCacheConfiguration.SHIPMENT_BY_TRACKING_NUMBER_CACHE,
			updatedShipment.getTrackingNumber(),
			updatedShipment,
			CacheOperation.SINGLE_RECORD
		);
		storeInCache(
			CaffeineCacheConfiguration.SHIPMENT_BY_SHIPMENT_DATE_CACHE,
			updatedShipment.getShipmentDate().toLocalDate(),
			oldKey,
			updatedShipment,
			CacheOperation.LIST_RECORD
		);
	}
	
	/**
	 * Converte un Data Transfer Object (DTO) di richiesta in una nuova entità di dominio {@link Shipment}.
	 * <p><b>Contesto Architetturale (Data Mapper Pattern):</b></p>
	 * Questo metodo agisce come strato anti-corruzione (Anti-Corruption Layer) tra l'interfaccia 
	 * API (frontend) e il modello dati JPA (backend). Assicura che la logica di persistenza riceva 
	 * un oggetto strutturalmente coerente, aggregando i dati e risolvendo i riferimenti al database.
	 * <p><b>Risoluzione delle Relazioni (Lookup & Hydration):</b></p>
	 * Il mapping non è puramente scalare (copia 1 a 1). Il metodo esegue un'operazione di "idratazione" 
	 * dell'aggregato recuperando le entità relazionate tramite le rispettive chiavi di business:
	 * <ul>
	 * <li><b>Veicolo (Vehicle):</b> Interrogato tramite la targa (Business Key).</li>
	 * <li><b>Equipaggio (Drivers):</b> Risoluzione massiva tramite Stream. Converte un {@code Set} di numeri 
	 * di patente nelle corrispondenti entità {@link Driver}, garantendo l'assegnazione dell'equipaggio.</li>
	 * <li><b>Attori Logistici (Customers):</b> Aggrega la lista in ingresso dal DTO in una {@link EnumMap} 
	 * ottimizzata, raggruppando i clienti per ruolo. <br>
	 * <i>Nota di Sicurezza (SRP):</i> Questo strato assume una conformità rigorosa del payload. La validazione 
	 * della cardinalità (presenza di un singolo SENDER, un singolo CARRIER e destinazioni multiple) 
	 * è delegata interamente a monte tramite validatori custom sul DTO, garantendo un'estrazione 
	 * dei dati diretta, lineare e priva di difese condizionali ridondanti (fail-fast architecture).</li>
	 * </ul>
	 * <p><b>Gestione Rigorosa degli Enum e del Tempo:</b></p>
	 * La conversione dello stato logistico e dei ruoli avviene tramite la funzione nativa {@code Enum.valueOf}. 
	 * Questa operazione è intrinsecamente case-sensitive e richiede una corrispondenza esatta 
	 * tra la stringa del DTO e la costante enumerata Java.
	 * @param dto Il payload di richiesta contenente i dati scalari e i riferimenti logistici della spedizione. 
	 * Si assume che i campi obbligatori e le regole di business sulla cardinalità siano 
	 * già stati sanificati dal Validation Context.
	 * @return Una nuova istanza di {@link Shipment} in stato <i>Transient</i> (non ancora gestita dall'EntityManager), 
	 * completamente idratata e pronta per la persistenza.
	 */
	public Shipment mapToEntity(ShipmentRequestDTO dto) {
		Shipment shipment = new Shipment();
		Vehicle vehicle = vehicleService.getByLicensePlate(dto.vehicleLicensePlate());
		Set<Driver> drivers = dto.drivers()
			.stream()
			.map(license -> driverService.getByLicense(license))
			.collect(Collectors.toSet());
		Map<CustomerRole, List<Customer>> customers = new EnumMap<Customer.CustomerRole, List<Customer>>(CustomerRole.class);
		dto.customers().forEach(container -> {
			// 1. MAPPING DEL CONTAINER
			CustomerRole role = Enum.valueOf(CustomerRole.class, container.role());
			Customer customer = customerService.getByVatNumber(container.vatNumber());
			// 2. ESTRAZIONE E INSERIMENTO
			List<Customer> customerListByRole = customers.getOrDefault(role, new ArrayList<Customer>());
			customerListByRole.add(customer);
			customers.put(role, customerListByRole);
		});
		shipment.setVehicle(vehicle);
		shipment.setDrivers(drivers);
		shipment.setSender(customers.get(CustomerRole.SENDER).get(0));
		shipment.setCarrier(customers.get(CustomerRole.CARRIER).get(0));
		shipment.setReceivers(customers.get(CustomerRole.RECEIVER));
		shipment.setShipmentDate(LocalDateTime.parse(dto.date()));
		shipment.setShipmentStatus(Enum.valueOf(ShipmentStatus.class, dto.status()));
		shipment.setOriginAddress(dto.origin());
		shipment.setDestinationAddresses(dto.destinations());
		return shipment;
	}
}
