package dev.vinciguerra.adrsentinel.db.shipment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import dev.vinciguerra.adrsentinel.db.AbstractGenericService;
import dev.vinciguerra.adrsentinel.db.CaffeineCacheConfiguration;
import dev.vinciguerra.adrsentinel.db.driver.Driver;
import dev.vinciguerra.adrsentinel.db.driver.DriverService;
import dev.vinciguerra.adrsentinel.db.driver.DriverSnapshot;
import dev.vinciguerra.adrsentinel.db.driver.DriverSnapshotService;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment.ShipmentStatus;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle;
import dev.vinciguerra.adrsentinel.db.vehicle.VehicleService;
import dev.vinciguerra.adrsentinel.db.vehicle.VehicleSnapshot;
import dev.vinciguerra.adrsentinel.db.vehicle.VehicleSnapshotService;
import dev.vinciguerra.adrsentinel.exception.IllegalShipmentStateException;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentUpdateDTO;
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
	
	/**
	 * Costruttore con Dependency Injection nativa di Spring.
	 * @param shipmentRepository il livello di accesso ai dati fisici (Database).
	 * @param cacheManager il gestore dell'infrastruttura di memoria (iniettato nella superclasse).
	 */
	public ShipmentService(ShipmentRepository shipmentRepository, VehicleService vehicleService, VehicleSnapshotService vehicleSnapshotService, DriverService driverService,
			DriverSnapshotService driverSnapshotService, CacheManager cacheManager) {
		super(cacheManager);
		this.shipmentRepository = shipmentRepository;
		this.vehicleService = vehicleService;
		this.vehicleSnapshotService = vehicleSnapshotService;
		this.driverService = driverService;
		this.driverSnapshotService = driverSnapshotService;
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
	 * Esegue l'aggiornamento chirurgico dello stato logistico di una spedizione esistente, 
	 * garantendo l'allineamento transazionale tra Database e Memoria Cache, e orchestrando 
	 * il ciclo di vita delle entità correlate (Veicoli e Snapshot).
	 * <p><b>Contesto Architetturale e Transazionalità:</b></p>
	 * Il metodo opera all'interno di un contesto {@code @Transactional}. L'aggiornamento sul 
	 * database (via Hibernate) e la successiva mutazione della memoria RAM (Caffeine) sono 
	 * strettamente disaccoppiati. L'orchestrazione della cache viene deferita alla fase di 
	 * <i>Post-Commit</i> tramite {@link TransactionSynchronizationManager}. Questo previene 
	 * scenari di "Dirty Read" o disallineamenti: se la transazione sul DB fallisce (Rollback), 
	 * la cache non viene inquinata.
	 * <p><b>Flusso Operativo e Trucco Architetturale (Cache Compass):</b></p>
	 * <ol>
	 * <li><b>Lookup:</b> Recupera l'entità tramite la sua Business Key (Tracking Number).</li>
	 * <li><b>State Capture (La Bussola della Cache):</b> Estrae e congela in memoria la data di spedizione 
	 * corrente ({@code oldDate}). Sebbene la data non subisca variazioni in questo specifico flusso, 
	 * essa viene passata al motore di cache ({@code syncCacheAfterUpdate}) per permettergli di 
	 * valutare l'assenza di un "Key Shift" e agire con una sostituzione (Upsert) sicura e 
	 * localizzata all'interno della corretta lista in RAM.</li>
	 * <li><b>Mutazione di Stato e Logica di Dominio:</b> Converte rigorosamente la stringa del DTO nel 
	 * valore {@link ShipmentStatus} e applica specifici side-effect in base al nuovo stato:
	 * <ul>
	 * <li>{@code PLANNED}: Imposta il veicolo ({@link Vehicle}) e gli autisti ({@link Driver}) associati in stato di transito.</li>
	 * <li>{@code TRANSIT}: Genera e persiste uno snapshot storico ({@link VehicleSnapshot}) del veicolo e degli autisti 
	 * ({@link DriverSnapshot})
	 * associati per cristallizzarne i dati al momento della partenza. Successivamente, <b>scollega</b> 
	 * la spedizione dal veicolo master (impostandolo a {@code null}) e dagli autisti (impostando {@code Set::clear}) permettendo al 
	 * veicolo di seguire e agli autisti di seguire il proprio ciclo di vita indipendenti.</li>
	 * <li><b>Altri Stati (es. Terminali):</b> Utilizza lo snapshot salvato in precedenza per risalire 
	 * alla targa del veicolo originale e ripristinarne lo stato di disponibilità (non più in transito).</li>
	 * </ul>
	 * </li>
	 * <li><b>Persistenza:</b> Salva l'entità aggiornata e registra la sincronizzazione della cache.</li>
	 * </ol>
	 * @param trackingNumber La Business Key (Targa alfanumerica) che identifica univocamente la spedizione nel sistema.
	 * @param updateStatusDTO Il payload contenente il nuovo stato logistico (es. "TRANSIT", "DELIVERED").
	 * @return L'istanza aggiornata di {@link Shipment}, ricaricata col nuovo stato e persistita.
	 * @throws ResourceNotFoundException Se il {@code trackingNumber} fornito non trova riscontro nel database.
	 * @throws IllegalArgumentException Se la stringa di stato fornita nel DTO non corrisponde esattamente 
	 * (tramite {@code Enum.valueOf}) a nessuna costante definita in {@link ShipmentStatus}.
	 * @throws RuntimeException (o derivate) se negli stati terminali non viene trovato un {@link VehicleSnapshot} 
	 * associato all'ID della spedizione, o se il veicolo originale non è più presente.
	 */
	@Transactional
	public Shipment updateStatusByTrackingNumber(String trackingNumber, ShipmentUpdateStatusDTO updateStatusDTO) {
		logger.info("[DataBase CALL] Updating Shipment status with trackingNumber: {}", trackingNumber);
		Shipment shipment = shipmentRepository.findByTrackingNumber(trackingNumber)
			.orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + trackingNumber));
		LocalDate oldDate = shipment.getShipmentDate().toLocalDate();
		shipment.setShipmentStatus(Enum.valueOf(ShipmentStatus.class, updateStatusDTO.status()));
		if(shipment.getShipmentStatus() == ShipmentStatus.PLANNED) {
			Vehicle vehicle = shipment.getVehicle();
			Set<Driver> drivers = shipment.getDrivers();
			/* Veicolo in transito (attributo inTransit = true) */
			vehicleService.updateInTransitStatusById(vehicle.getId(), true);
			/* Driver in transito (attributo inTransit = true) */
			drivers.stream().forEach(driver -> driverService.updateInTransitStatusById(driver.getId(), false));
		} else if(shipment.getShipmentStatus() == ShipmentStatus.TRANSIT) {
			logger.info("Shipment [{}] has changed status to {}", shipment.getTrackingNumber(), shipment.getShipmentStatus().name());
			/* Snapshot del Vehicle */
			logger.info("Snapshot action taken on associated vehicle [{}].", shipment.getVehicle().getLicensePlate());
			VehicleSnapshot vehicleSnapshot = new VehicleSnapshot(shipment);
			vehicleSnapshotService.save(vehicleSnapshot);
			shipment.setVehicle(null); /* Scollega questo Shipment dal Veicolo master che vive di vita propria. */
			/* Snapshot del Driver */
			shipment.getDrivers().stream().forEach(driver -> logger.info("Snapshot action taken on associated driver [{}].", driver.getLicense()));
			Set<DriverSnapshot> driverSnapshots = DriverSnapshot.fromDrivers(shipment);
			driverSnapshots.stream().forEach(driverSnap -> driverSnapshotService.save(driverSnap));
			shipment.getDrivers().clear(); /* Scollega questo Shipment dai Driver master che vivono di vita propria. */
			/* Snapshot del Customer */
			
		} else {
			/* Il veicolo master ritorna non in transito */
			VehicleSnapshot vehicleSnap = vehicleSnapshotService.getByShipmentId(shipment.getId());
			Vehicle vehicle = vehicleService.getByLicensePlate(vehicleSnap.getLicensePlateSnap());
			vehicleService.updateInTransitStatusById(vehicle.getId(), false);
			/* Gli autisti master ritornano non in transito */
			List<DriverSnapshot> driverSnaps = driverSnapshotService.getByShipmentId(shipment.getId());
			List<Driver> drivers = driverSnaps.stream().map(driverSnap -> driverService.getByLicense(driverSnap.getLicenseSnap())).toList();
			drivers.stream().forEach(driver -> driverService.updateInTransitStatusById(driver.getId(), false));
		}
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
	 * un oggetto strutturalmente coerente e pronto per il database.
	 * <p><b>Risoluzione delle Relazioni (Lookup & Hydration):</b></p>
	 * Il mapping non è puramente scalare (copia 1 a 1). Per l'associazione con il veicolo, 
	 * il metodo esegue una query di <i>lookup</i>. Utilizza la Business Key (targa) fornita dal DTO 
	 * per interrogare il {@code vehicleService} e agganciare all'entità {@link Shipment} 
	 * l'istanza corretta e gestita di {@link Vehicle}.
	 * <p><b>Gestione Rigorosa degli Enum:</b></p>
	 * La conversione dello stato logistico avviene tramite la funzione nativa {@code Enum.valueOf}. 
	 * Questa operazione è intrinsecamente case-sensitive e richiede una corrispondenza esatta 
	 * tra la stringa del DTO e la costante enumerata Java.
	 * @param dto Il payload di richiesta contenente i dati anagrafici e logistici della spedizione. 
	 * Si assume che i campi obbligatori siano già stati sanificati e verificati 
	 * (es. tramite annotazioni {@code @Valid}).
	 * @return Una nuova istanza di {@link Shipment} in stato <i>Transient</i> (non ancora gestita dall'EntityManager).
	 */
	public Shipment mapToEntity(ShipmentRequestDTO dto) {
		Shipment shipment = new Shipment();
		Vehicle vehicle = vehicleService.getByLicensePlate(dto.vehicleLicensePlate());
		shipment.setVehicle(vehicle);
		shipment.setShipmentDate(LocalDateTime.parse(dto.date()));
		shipment.setShipmentStatus(Enum.valueOf(ShipmentStatus.class, dto.status()));
		shipment.setOriginAddress(dto.origin());
		shipment.setDestinationAddresses(dto.destinations());
		return shipment;
	}
}
