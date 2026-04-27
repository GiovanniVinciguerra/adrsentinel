package dev.vinciguerra.adrsentinel.db.vehicle;

import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import dev.vinciguerra.adrsentinel.db.AbstractGenericService;
import dev.vinciguerra.adrsentinel.db.CaffeineCacheConfiguration;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;

/**
 * Strato di Business Logic (Service Layer) per la gestione dell'entità {@link Vehicle}.
 * <p>
 * Questa classe orchestra le operazioni di dominio e funge da intermediario tra i Controller REST 
 * e l'accesso ai dati ({@link VehicleRepository}). Implementa un'architettura altamente ottimizzata 
 * basata su un sistema di <b>Caching Custom</b> ereditato da {@link AbstractGenericService}.
 * </p>
 * <p>
 * <b>Scelte Architetturali:</b>
 * <ul>
 * <li><b>Defense in Depth (Validazione):</b> La classe è annotata con {@code @Validated} per garantire che 
 * i parametri in ingresso ai metodi vengano sanificati (es. {@code @NotBlank}, {@code @Positive}) 
 * <i>prima</i> di eseguire inutili letture in cache o costose query sul database.</li>
 * <li><b>Sincronizzazione della Cache (Write-Through / Eviction):</b> I metodi di scrittura ({@code save}, {@code delete}) 
 * non si limitano ad aggiornare il database, ma mantengono rigorosamente coerente lo stato della memoria RAM, 
 * invocando i metodi {@code updateCache} e {@code deleteCache} sulle singole entità o sulle liste.</li>
 * </ul>
 * </p>
 *
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Service
public class VehicleService extends AbstractGenericService {
	private final VehicleRepository vehicleRepository;

	/**
	 * Costruttore per l'iniezione delle dipendenze (Dependency Injection).
	 *
	 * @param vehicleRepository il DAO per l'accesso fisico ai dati dei veicoli.
	 * @param cacheManager il gestore della memoria cache configurato nell'applicazione.
	 */
	public VehicleService(VehicleRepository vehicleRepository, CacheManager cacheManager) {
		super(cacheManager);
		this.vehicleRepository = vehicleRepository;
	}
	
	/**
	 * Recupera un veicolo in base alla sua chiave di business (Targa).
	 * <p>
	 * <b>Flusso (Read-Through):</b> Controlla prima la cache {@code vehicle_by_license_plate}. 
	 * Se si verifica un <i>Cache Miss</i>, interroga il database e salva il risultato in RAM per le chiamate future.
	 * </p>
	 *
	 * @param licensePlate la targa esatta da cercare. Protetta da {@code @NotBlank} per evitare query vuote.
	 * @return l'istanza del veicolo trovata.
	 * @throws ResourceNotFoundException se la targa non esiste nel database (Domain Exception).
	 */
	@Cacheable(value = CaffeineCacheConfiguration.VEHICLE_BY_LICENSE_PLATE_CACHE, key = "#licensePlate")
	public Vehicle getByLicensePlate(String licensePlate) throws ResourceNotFoundException {
		logger.info("[DataBase CALL] Searching for the Vehicle by licensePlate: {}", licensePlate);
		return vehicleRepository.findByLicensePlate(licensePlate)
			.orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + licensePlate));
	}
	
	/**
	 * Ricerca una flotta di veicoli capaci di trasportare <b>almeno</b> il peso utile specificato.
	 * <p>
	 * <b>Logica di Dominio:</b> Modella la necessità operativa di trovare veicoli idonei a un carico.
	 * Interroga il DB cercando veicoli con portata utile {@code >=} al parametro richiesto.
	 * </p>
	 *
	 * @param maxUsefulWeightkg il peso minimo che il veicolo deve poter caricare. Protetto da {@code @Positive}.
	 * @return una lista di veicoli idonei al carico (può essere vuota).
	 */
	@Cacheable(value = CaffeineCacheConfiguration.VEHICLE_BY_MAX_USEFUL_WEIGHT_CACHE, key = "#maxUsefulWeightkg")
	public List<Vehicle> getByMaxUsefulWeight(int maxUsefulWeightkg) {
		logger.info("[DataBase CALL] Searching for the Vehicle by maxUsefulWeightkg: {}", maxUsefulWeightkg);
		return vehicleRepository.findByMaxUsefulWeightkgGreaterThanEqual(maxUsefulWeightkg);
	}
	
	/**
	 * Recupera l'intero parco mezzi dal database.
	 * L'intera lista viene messa in cache per massimizzare le performance sulle visualizzazioni globali.
	 *
	 * @return la lista di tutti i veicoli registrati nel sistema.
	 */
	@Cacheable(value = CaffeineCacheConfiguration.ALL_VEHICLE_CACHE, key = "'" + CaffeineCacheConfiguration.ALL_VEHICLE_KEY + "'")
	public List<Vehicle> getAllVehicle() {
		logger.info("[DataBase CALL] Retrieving all Vehicle");
		return vehicleRepository.findAll();
	}
	
	/**
	 * Persiste un nuovo veicolo o aggiorna uno esistente, propagando le modifiche alla cache.
	 * <p>
	 * <b>Sincronizzazione:</b> Dopo aver salvato il record (garantendo l'ID), il metodo esegue 
	 * un aggiornamento granulare delle tre cache principali (Singolo record, Lista per peso, Lista totale) 
	 * per evitare dati stantii (<i>Stale Data</i>) alle successive letture.
	 * La validazione dell'entità è demandata automaticamente ad Hibernate Validator durante la {@code INSERT/UPDATE}.
	 * </p>
	 *
	 * @param newVehicle il veicolo da salvare nel database.
	 * @return il veicolo salvato, comprensivo dell'ID generato dal database.
	 */
	@Transactional
	public Vehicle save(Vehicle newVehicle) {
		logger.info("[DataBase CALL] Saving new Vehicle with licensePlate: {}", newVehicle.getLicensePlate());
		Vehicle savedVehicle = vehicleRepository.save(newVehicle);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { writeThroughCacheIntegrityOperation(savedVehicle); }
		});
		return savedVehicle;
	}
	
	/**
	 * Esegue l'operazione di sincronizzazione e allineamento della cache applicativa per 
	 * l'entità {@link Vehicle} (Veicolo/Automezzo), implementando il pattern Write-Through 
	 * per garantire la coerenza in tempo reale della flotta logistica.
	 * <p>
	 * All'interno del dominio gestionale, l'automezzo rappresenta una risorsa fisica 
	 * critica per la pianificazione dei trasporti ADR. Per supportare le diverse esigenze 
	 * operative (ricerca puntuale, pianificazione dei carichi, gestione anagrafica), 
	 * la strategia di caching mantiene simultaneamente tre indici in memoria RAM.
	 * </p>
	 * <p>
	 * <b>Vincolo Transazionale:</b> Onde prevenire la corruzione della flotta in memoria 
	 * (es. veicoli fantasma creati da un rollback del database), questo metodo deve essere 
	 * registrato e invocato <b>esclusivamente</b> all'interno della fase {@code afterCommit} 
	 * del {@code TransactionSynchronizationManager}.
	 * </p>
	 * <p>
	 * <b>Logica di Sincronizzazione a Triplo Indice:</b>
	 * <ul>
	 * <li><b>1. Indice Univoco Operativo (Targa):</b> Inserisce o sovrascrive il veicolo 
	 * all'interno della cache dedicata alle ricerche dirette O(1) 
	 * ({@code VEHICLE_BY_LICENSE_PLATE_CACHE}). È fondamentale per le operazioni di spunta, 
	 * assegnazione diretta e controlli ai varchi.</li>
	 * <li><b>2. Indice di Capacità Logistica (Portata Massima):</b> Raggruppa i veicoli 
	 * in base al loro peso utile massimo consentito ({@code VEHICLE_BY_MAX_USEFUL_WEIGHT_CACHE}). 
	 * L'accodamento in questa cache a lista permette al motore di calcolo e al frontend 
	 * di filtrare istantaneamente i mezzi idonei per una specifica spedizione basandosi 
	 * sul tonnellaggio, senza interrogare il database.</li>
	 * <li><b>3. Indice Flotta Globale:</b> Accoda l'entità alla lista omnicomprensiva 
	 * di tutti i mezzi ({@code ALL_VEHICLE_CACHE}), utilizzata tipicamente per popolare 
	 * le dropdown di assegnazione o le dashboard di fleet management nel frontend.</li>
	 * </ul>
	 * L'utilizzo dell'approccio Write-Through su queste tre dimensioni annulla la necessità 
	 * di invalidare la cache (eviction), mantenendo le performance in lettura sempre massimizzate.
	 * </p>
	 * @param savedVehicle l'istanza consolidata dell'entità {@link Vehicle} appena persistita 
	 * con successo nel database. L'oggetto deve trovarsi nello stato "Managed" e avere 
	 * i campi chiave ({@code licensePlate} e {@code maxUsefulWeightkg}) obbligatoriamente valorizzati.
	 */
	private void writeThroughCacheIntegrityOperation(Vehicle savedVehicle) {
		storeInCache(
			CaffeineCacheConfiguration.VEHICLE_BY_LICENSE_PLATE_CACHE,
			savedVehicle.getLicensePlate(),
			savedVehicle,
			CacheOperation.SINGLE_RECORD
		);
		storeInCache(
			CaffeineCacheConfiguration.VEHICLE_BY_MAX_USEFUL_WEIGHT_CACHE,
			savedVehicle.getMaxUsefulWeightkg(),
			savedVehicle,
			CacheOperation.LIST_RECORD
		);
		storeInCache(
			CaffeineCacheConfiguration.ALL_VEHICLE_CACHE,
			CaffeineCacheConfiguration.ALL_VEHICLE_KEY,
			savedVehicle,
			CacheOperation.LIST_RECORD
		);
	}
	
	/**
	 * Rimuove fisicamente un veicolo dal database e invalida le relative entry in cache.
	 * <p>
	 * Previene l'effetto "Veicolo Fantasma", assicurandosi che il record rimosso dal database 
	 * sparisca istantaneamente anche dalle cache delle liste e dalle ricerche per targa.
	 * </p>
	 *
	 * @param vehicleToDelete l'istanza del veicolo da rimuovere definitivamente.
	 */
	@Transactional
	public void delete(Vehicle vehicleToDelete) {
		logger.info("[DataBase CALL] Deleting Vehicle with licensePlate: {}", vehicleToDelete.getLicensePlate());
		vehicleRepository.delete(vehicleToDelete);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { deleteThroughCacheIntegrityOperation(vehicleToDelete); }
		});
	}
	
	/**
	 * Esegue l'operazione di pulizia e allineamento della cache applicativa a seguito 
	 * dell'eliminazione fisica (Hard Delete) di un'entità {@link Vehicle}, implementando 
	 * il pattern architetturale Delete-Through.
	 * <p>
	 * Questo metodo garantisce la totale consistenza tra il database relazionale e la 
	 * memoria RAM, prevenendo l'insorgenza del cosiddetto "Effetto Veicolo Fantasma" 
	 * (Ghost Record), ovvero la situazione in cui un mezzo dismesso o rimosso dal sistema 
	 * continui ad essere visibile nelle liste operative o suggerito per nuove spedizioni.
	 * </p>
	 * <p>
	 * <b>Vincolo Transazionale:</b> Onde evitare di corrompere la cache a fronte di un 
	 * potenziale Rollback (ad esempio, se l'eliminazione fallisce per una violazione di 
	 * chiave esterna - Foreign Key Constraint - legata a spedizioni preesistenti), 
	 * l'invocazione di questo metodo deve avvenire <b>rigorosamente</b> a transazione 
	 * conclusa con successo, delegandola al blocco {@code afterCommit} del 
	 * {@code TransactionSynchronizationManager}.
	 * </p>
	 * <p>
	 * <b>Flusso di Rimozione Chirurgica a Triplo Indice:</b>
	 * Il metodo intercetta ed elimina il veicolo da tutte le dimensioni di caching:
	 * <ul>
	 * <li><b>1. Indice Univoco Operativo (Targa):</b> Invalida la entry singola nella 
	 * cache {@code VEHICLE_BY_LICENSE_PLATE_CACHE}. Questo previene anche eventuali bug di 
	 * collisione nel caso in cui, in futuro, la stessa targa venga riciclata per un nuovo mezzo.</li>
	 * <li><b>2. Indice di Capacità Logistica (Portata Massima):</b> Cerca il veicolo all'interno 
	 * della specifica lista di portata ({@code VEHICLE_BY_MAX_USEFUL_WEIGHT_CACHE}) e lo rimuove 
	 * puntualmente. L'approccio chirurgico evita di invalidare le liste di mezzi con altre portate.</li>
	 * <li><b>3. Indice Flotta Globale:</b> Rimuove dinamicamente il mezzo dalla collezione 
	 * omnicomprensiva ({@code ALL_VEHICLE_CACHE}). Grazie all'operazione {@code LIST_RECORD}, 
	 * l'intera flotta rimanente viene preservata in RAM, evitando pesanti query di 
	 * ri-popolamento al successivo accesso alla dashboard del frontend.</li>
	 * </ul>
	 * </p>
	 * @param vehicleToDelete l'istanza dell'entità {@link Vehicle} appena rimossa con successo 
	 * dal database. È assolutamente cruciale che l'oggetto in input mantenga intatti i 
	 * propri valori originari (in particolare {@code licensePlate} e {@code maxUsefulWeightkg}), 
	 * in quanto tali attributi vengono utilizzati dal motore di cache per localizzare 
	 * e distruggere le esatte occorrenze in RAM basandosi sul metodo {@code equals()}.
	 */
	private void deleteThroughCacheIntegrityOperation(Vehicle vehicleToDelete) {
		deleteFromCache(
			CaffeineCacheConfiguration.VEHICLE_BY_LICENSE_PLATE_CACHE,
			vehicleToDelete.getLicensePlate(),
			vehicleToDelete,
			CacheOperation.SINGLE_RECORD
		);
		deleteFromCache(
			CaffeineCacheConfiguration.VEHICLE_BY_MAX_USEFUL_WEIGHT_CACHE,
			vehicleToDelete.getMaxUsefulWeightkg(),
			vehicleToDelete,
			CacheOperation.LIST_RECORD
		);
		deleteFromCache(
			CaffeineCacheConfiguration.ALL_VEHICLE_CACHE,
			CaffeineCacheConfiguration.ALL_VEHICLE_KEY,
			vehicleToDelete,
			CacheOperation.LIST_RECORD
		);
	}
}
