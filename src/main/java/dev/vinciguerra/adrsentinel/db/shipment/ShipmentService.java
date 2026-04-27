package dev.vinciguerra.adrsentinel.db.shipment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
	 * Estrae le spedizioni filtrate per stato operativo (es. {@code PLANNED}, {@code DELIVERED}).
	 * <p>
	 * Utilizza un accesso paginato in quanto gli stati terminali (come le spedizioni consegnate) 
	 * accumulano volumi di dati illimitati nel tempo. L'ordinamento di default garantisce 
	 * che i risultati più recenti compaiano nelle prime pagine (descending su shipmentDate).
	 * </p>
	 * @param shipmentStatus l'enumerazione che definisce lo stato di avanzamento da cercare.
	 * @param page il numero della pagina richiesta (Zero-based index).
	 * @param size il numero massimo di record per pagina.
	 * @return un blocco (Page) contenente le spedizioni filtrate e i metadati per il frontend.
	 */
	public Page<Shipment> getByShipmentStatus(ShipmentStatus shipmentStatus, int page, int size) {
		logger.info("[DataBase CALL] Paginated search for Shipment Status: {}", shipmentStatus.name());
		Pageable pageable = PageRequest.of(page, size, Sort.by("shipmentDate").descending());
		return shipmentRepository.findByShipmentStatus(shipmentStatus, pageable);
	}
	
	/**
	 * Accesso globale al registro di tutte le spedizioni presenti nel sistema.
	 * <p>
	 * Metodo pensato per griglie dati amministrative, esportazioni a blocchi o audit. 
	 * L'accesso massivo è bloccato e forzato all'uso di finestre logiche (Paginazione).
	 * </p>
	 * @param page indice della pagina (partendo da 0).
	 * @param size dimensione del blocco (chunk).
	 * @return un oggetto {@link Page} ordinato dal più recente al più vecchio.
	 */
	public Page<Shipment> getAllShipment(int page, int size) {
		logger.info("[DataBase CALL] Paginated search for the Shipment (ALL)");
		Pageable pageable = PageRequest.of(page, size, Sort.by("shipmentDate").descending());
		return shipmentRepository.findAll(pageable);
	}
	
	/**
	 * Costruisce il registro storico (Logbook) dei viaggi di uno specifico mezzo aziendale.
	 * <p>
	 * Anche se limitato a un singolo mezzo, il volume dei dati può rappresentare anni 
	 * di operatività, rendendo l'uso delle collezioni standard un rischio per la memoria. 
	 * Questo metodo estrae i dati a blocchi direttamente dal RDBMS.
	 * </p>
	 * @param vehicle l'istanza del veicolo di cui interrogare lo storico.
	 * @param page indice della pagina.
	 * @param size numero di record per pagina.
	 * @return lo storico paginato dei trasporti associati a quel veicolo.
	 */
	public Page<Shipment> getByVehicle(Vehicle vehicle, int page, int size) {
		logger.info("[DataBase CALL] Paginated search for the Shipment by Vehicle license plate: {}", vehicle.getLicensePlate());
		Pageable pageable = PageRequest.of(page, size, Sort.by("shipmentDate").descending());
		return shipmentRepository.findByVehicle(vehicle, pageable);
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
