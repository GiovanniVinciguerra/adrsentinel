package dev.vinciguerra.adrsentinel.db.shipment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
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
import dev.vinciguerra.adrsentinel.db.shipment.Shipment.ShipmentStatus;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;

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
 * Le operazioni di mutazione (Save/Delete) sono responsabili dell'aggiornamento atomico 
 * sia del database fisico che delle regioni di memoria in RAM, prevenendo letture 
 * di dati obsoleti (Stale Data).
 * </p>
 *
 * @author Giovanni Vinciguerra
 * @version 3.0 (Paginazione Veicoli Inclusa)
 * @since 1.0
 */
@Service
public class ShipmentService extends AbstractGenericService {
	private final ShipmentRepository shipmentRepository;
	
	/**
	 * Costruttore con Dependency Injection nativa di Spring.
	 * @param shipmentRepository il livello di accesso ai dati fisici (Database).
	 * @param cacheManager il gestore dell'infrastruttura di memoria (iniettato nella superclasse).
	 */
	public ShipmentService(ShipmentRepository shipmentRepository, CacheManager cacheManager) {
		super(cacheManager);
		this.shipmentRepository = shipmentRepository;
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
	 * Recupera lo storico paginato delle Spedizioni (Shipments) effettuate da uno specifico Veicolo.
	 * <p>
	 * <b>Design della Query (Entity-Based Filtering):</b><br>
	 * Questo strato di servizio applica il pattern della navigazione a oggetti. Accettando come parametro 
	 * l'intera entità {@link Vehicle} anziché una stringa primitiva, si delega a Hibernate il compito 
	 * di estrapolare la chiave primaria (Primary Key) per costruire una query SQL basata sulla 
	 * Foreign Key (es. {@code WHERE vehicle_id = ?}). Questo garantisce l'uso ottimale degli indici 
	 * B-Tree del database relazionale.
	 * </p>
	 * <p>
	 * <b>Sicurezza della Memoria (Big Data Ready):</b><br>
	 * Poiché un singolo veicolo operativo può generare decine di migliaia di distinte di spedizione 
	 * nel corso della sua vita utile, l'incapsulamento della query in un costrutto {@link Pageable} 
	 * funge da valvola di sicurezza. Impone al database l'estrazione parziale (LIMIT/OFFSET), 
	 * mantenendo il consumo di RAM del server costantemente basso e prevedibile.
	 * </p>
	 * <p>
	 * <b>Osservabilità Avanzata (Human-Readable Logging):</b><br>
	 * La direttiva di logging implementa una best practice diagnostica: mentre il motore di ricerca 
	 * lavora per ID numerici relazionali, il logger estrae dinamicamente la targa 
	 * ({@code vehicle.getLicensePlate()}). Questo garantisce che le tracce su sistemi di monitoraggio 
	 * (es. ELK, Datadog, Splunk) parlino il linguaggio del dominio di business, facilitando enormemente 
	 * l'analisi e il troubleshooting da parte degli operatori di supporto.
	 * </p>
	 * @param licensePlate  La targa del veicolo di cui si desidera estrarre lo storico.
	 * @param pageable L'involucro contenente i limiti della finestra di ricerca (numero pagina e grandezza) 
	 * e gli eventuali criteri di ordinamento richiesti dal Presentation Layer.
	 * @return Un raccoglitore {@link Page} contenente le entità {@link Shipment} filtrate per il veicolo 
	 * richiesto, oltre ai metadati di paginazione necessari al client per renderizzare l'interfaccia.
	 */
	public Page<Shipment> getByVehicle(String licensePlate, Pageable pageable) {
		logger.info("[DataBase CALL] Paginated search for the Shipment by Vehicle license plate: {}", licensePlate);
		return shipmentRepository.findByVehicle_licensePlate(licensePlate, pageable);
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
			public void afterCommit() { writeThroughCacheIntegrityOperation(savedShipment); }
		});
		return savedShipment;
	}
	
	/**
	 * Esegue l'operazione di allineamento della cache applicativa per l'entità {@link Shipment}, 
	 * implementando il pattern Write-Through per garantire l'integrità dei dati operativi in tempo reale.
	 * <p>
	 * L'entità Spedizione rappresenta il core transazionale del sistema AdrSentinel. Essendo 
	 * soggetta a frequenti letture sia puntuali (es. verifica di un singolo trasporto) sia 
	 * massive (es. dashboard giornaliera), la strategia di caching prevede il mantenimento 
	 * simultaneo di due indici in memoria RAM.
	 * </p>
	 * <p>
	 * <b>Vincolo Architetturale:</b> Per evitare discrepanze critiche tra il database e la 
	 * cache (dirty reads o ghost records in caso di eccezioni SQL), questo metodo deve essere 
	 * registrato e invocato <b>esclusivamente</b> all'interno del blocco {@code afterCommit} 
	 * fornito dal {@code TransactionSynchronizationManager}.
	 * </p>
	 * <p>
	 * <b>Logica di Sincronizzazione a Doppio Indice:</b>
	 * <ul>
	 * <li><b>Indice Operativo Puntuale (Tracking Number):</b> Inserisce o sovrascrive l'entità 
	 * all'interno della cache dedicata alle ricerche dirette O(1) 
	 * ({@code SHIPMENT_BY_TRACKING_NUMBER_CACHE}). Questo garantisce che gli aggiornamenti di 
	 * stato di una spedizione siano immediatamente visibili a chi ne interroga il tracking.</li>
	 * <li><b>Indice Temporale (Data di Spedizione):</b> Estrae la componente {@code LocalDate} 
	 * dal timestamp della spedizione e utilizza tale data come chiave di aggregazione. 
	 * Il record viene dinamicamente accodato (o aggiornato se preesistente) nella lista delle 
	 * spedizioni di quella specifica giornata ({@code SHIPMENT_BY_SHIPMENT_DATE_CACHE}).
	 * Questa ottimizzazione permette al frontend di renderizzare le dashboard giornaliere 
	 * senza sollecitare il database relazionale.</li>
	 * </ul>
	 * </p>
	 * @param savedShipment l'istanza consolidata dell'entità {@link Shipment}, 
	 * comprensiva di ID autogenerato, Tracking Number e Data di Spedizione, 
	 * appena persistita con successo nel database sottostante.
	 */
	private void writeThroughCacheIntegrityOperation(Shipment savedShipment) {
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
}
