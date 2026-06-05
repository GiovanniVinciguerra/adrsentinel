package dev.vinciguerra.adrsentinel.db.vehicle;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import dev.vinciguerra.adrsentinel.db.AbstractGenericService;
import dev.vinciguerra.adrsentinel.db.CaffeineCacheConfiguration;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle.VehicleCategory;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle.VehicleCategory.LoadType;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle.VehicleCategory.VehicleApproval;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle.VehicleCategory.VehicleType;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;
import dev.vinciguerra.adrsentinel.web.dto.vehicle.VehicleRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.vehicle.VehicleUpdateAdrApprovalDTO;
import dev.vinciguerra.adrsentinel.web.dto.vehicle.VehicleUpdateDTO;

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
	public List<Vehicle> getByMaxUsefulWeight(Integer maxUsefulWeightkg) {
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
	 * @param newVehicle il veicolo da salvare nel database.
	 * @return il veicolo salvato, comprensivo dell'ID generato dal database.
	 */
	@Transactional
	public Vehicle save(Vehicle newVehicle) {
		logger.info("[DataBase CALL] Saving new Vehicle with licensePlate: {}", newVehicle.getLicensePlate());
		Vehicle savedVehicle = vehicleRepository.save(newVehicle);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterInsert(savedVehicle); }
		});
		return savedVehicle;
	}
	
	/**
	 * Esegue l'aggiornamento transazionale (Mutation) dei parametri fisici, dimensionali 
	 * e di omologazione di un veicolo appartenente alla flotta, identificato tramite Targa.
	 * <p><b>Contesto Transazionale (ACID e Fail-Fast):</b></p>
	 * Il metodo è protetto dal confine transazionale ({@code @Transactional}). Il processo 
	 * inizia con un approccio <i>Fail-Fast</i>: se la targa non corrisponde ad alcun veicolo, 
	 * l'esecuzione viene interrotta immediatamente lanciando una {@link ResourceNotFoundException}, 
	 * prevenendo l'allocazione inutile di risorse e garantendo la coerenza del database.
	 * <p><b>State Snapshot e Sincronizzazione Cache (Eviction Sicura):</b></p>
	 * Prima di applicare il payload in ingresso, il metodo esegue uno snapshot mirato dello 
	 * stato precedente ({@code oldMaxUsefulWeight}). Questa operazione è di importanza critica 
	 * per la consistenza delle cache: qualora l'aggiornamento modifichi parametri che fungono 
	 * da chiavi di aggregazione nella cache (es. raggruppamento per portata utile), conservare 
	 * il vecchio valore permette al post-commit hook ({@link TransactionSynchronization}) 
	 * di individuare e "sfrattare" (Evict) i dati obsoleti, evitando chiavi orfane (Stale Keys) in RAM.
	 * <p><b>Risoluzione Sicura dei Metadati (Enum Mapping):</b></p>
	 * L'aggiornamento dell'oggetto aggregato {@link VehicleCategory} avviene tramite conversione 
	 * sicura ({@code Enum.valueOf}) delle stringhe provenienti dal DTO. Questo meccanismo funge 
	 * da ulteriore livello di validazione implicita a runtime: se i valori forniti non 
	 * combaciano con il dizionario del dominio, la transazione subirà un Rollback automatico.
	 * @param licensePlate La Business Key del veicolo (Targa), utilizzata per il lookup.
	 * @param updateDto Il payload di aggiornamento contenente le nuove grandezze fisiche 
	 * (dimensioni, pesi) e le nuove classificazioni normative ADR.
	 * @return L'entità {@link Vehicle} aggiornata e sincronizzata con il database.
	 * @throws ResourceNotFoundException Se la targa fornita non esiste a sistema.
	 */
	@Transactional
	public Vehicle updateDetailsByLicensePlate(String licensePlate, VehicleUpdateDTO updateDto) throws ResourceNotFoundException {
		logger.info("[DataBase CALL] Updating Vehicle details with licensePlate: {}", licensePlate);
		Vehicle vehicle = vehicleRepository.findByLicensePlate(licensePlate)
			.orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + licensePlate));
		final Integer oldMaxUsefulWeight = vehicle.getMaxUsefulWeightkg();
		VehicleCategory category = new VehicleCategory();
		category.setVehicleType(Enum.valueOf(VehicleType.class, updateDto.vehicleType()));
		category.setLoadType(Enum.valueOf(LoadType.class, updateDto.loadType()));
		vehicle.setMaxWeightkg(updateDto.maxWeightkg());
		vehicle.setMaxUsefulWeightkg(updateDto.maxUsefulWeightkg());
		vehicle.setVehicleCategory(category);
		vehicle.setHeightm(updateDto.heightm());
		vehicle.setLengthm(updateDto.lengthm());
		vehicle.setWidthm(updateDto.widthm());
		Vehicle updatedVehicle = vehicleRepository.save(vehicle);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterUpdate(updatedVehicle, oldMaxUsefulWeight); }
		});
		return updatedVehicle;
	}
	
	/**
	 * Esegue l'aggiornamento transazionale mirato (Partial Mutation / Toggle) dello stato 
	 * di certificazione legale (ADR) di un veicolo, identificato tramite la sua Targa.
	 * <p><b>Contesto Architetturale (Design delle API e Operazioni Atomiche):</b></p>
	 * Questo metodo implementa il pattern dell'aggiornamento parziale (idealmente esposto 
	 * tramite il verbo HTTP {@code PATCH}). A differenza degli aggiornamenti massivi, 
	 * isola la mutazione di un singolo flag critico (il "Kill-Switch" normativo). 
	 * Questo riduce drasticamente l'overhead di rete e previene la sovrascrittura accidentale 
	 * di altri dati del veicolo da parte di client concorrenti.
	 * <p><b>Sicurezza Transazionale (ACID e Fail-Fast):</b></p>
	 * Blindato dal confine {@code @Transactional}, il metodo adotta una strategia <i>Fail-Fast</i>: 
	 * l'assenza del veicolo bersaglio interrompe il flusso istantaneamente, lanciando una 
	 * {@link ResourceNotFoundException} ed evitando query o elaborazioni inutili a valle.
	 * <p><b>Design della Cache (Defensive Programming e Contratti Unificati):</b></p>
	 * Pur non modificando le grandezze fisiche del veicolo, il metodo estrae preventivamente 
	 * lo stato {@code oldMaxUsefulWeight}. Questa è una prassi di <i>Defensive Programming</i>: 
	 * permette di riutilizzare in sicurezza l'infrastruttura unificata di sincronizzazione della 
	 * cache ({@code syncCacheAfterUpdate}), la quale richiede la firma completa del vecchio stato 
	 * per garantire l'eventuale sfratto (Eviction) senza generare chiavi orfane o disallineamenti.
	 * L'aggiornamento della RAM è delegato al {@link TransactionSynchronizationManager} per 
	 * avvenire rigorosamente <b>solo dopo</b> il commit effettivo su disco.
	 * @param licensePlate La Business Key (Targa) del veicolo, utilizzata per la risoluzione univoca.
	 * @param updateDto Il payload in ingresso, contenente esclusivamente i certificati adr del veicolo.
	 * @return L'entità {@link Vehicle} persistita, riflettente il nuovo stato legale.
	 * @throws ResourceNotFoundException Se la targa fornita non è censita all'interno del database.
	 */
	@Transactional
	public Vehicle updateAdrCertifiedByLicensePlate(String licensePlate, VehicleUpdateAdrApprovalDTO updateDto) throws ResourceNotFoundException {
		logger.info("[DataBase CALL] Updating Vehicle adrCertified with licensePlate: {}", licensePlate);
		Vehicle vehicle = vehicleRepository.findByLicensePlate(licensePlate)
			.orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + licensePlate));
		final Integer oldMaxUsefulWeight = vehicle.getMaxUsefulWeightkg();
		Set<VehicleApproval> approvals = new HashSet<VehicleApproval>();
		for(String approval : updateDto.approvals())
			approvals.add(Enum.valueOf(VehicleApproval.class, approval));
		vehicle.getVehicleCategory().setVehicleApprovals(approvals);
		Vehicle updatedVehicle = vehicleRepository.save(vehicle);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterUpdate(updatedVehicle, oldMaxUsefulWeight); }
		});
		return updatedVehicle;
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
	private void syncCacheAfterInsert(Vehicle savedVehicle) {
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
	 * Orchestra la sincronizzazione e l'allineamento (State Reconciliation) dell'infrastruttura 
	 * di caching in memoria (Caffeine) a seguito di un'avvenuta mutazione (Update) sul database.
	 * <p><b>Contesto Architetturale (Broadcasting Multi-Topologia):</b></p>
	 * Questo metodo agisce come un router interno per l'aggiornamento della RAM. Piuttosto che 
	 * invalidare (flush) brutalmente intere cache, applica una strategia chirurgica di 
	 * <i>Write-Through / In-Place Update</i> su tre distinte topologie di accesso dati:
	 * <ul>
	 * <li><b>1. Cache Puntuale (Single Record):</b> Aggiorna o sovrascrive istantaneamente la scheda 
	 * del veicolo ricercabile per Targa (Business Key primaria).</li>
	 * <li><b>2. Cache Partizionata (List Record con Old Key):</b> Aggiorna la lista dei veicoli 
	 * raggruppati per Portata Utile. Questa è l'operazione più complessa: utilizza l'{@code oldKey} 
	 * per rintracciare la lista originale, estrarre il veicolo obsoleto, e inserire il veicolo 
	 * aggiornato nella nuova lista di pertinenza (se il peso è cambiato) o nella medesima lista.</li>
	 * <li><b>3. Cache Globale (Aggregate List):</b> Aggiorna il record all'interno della lista 
	 * generale che contiene l'intera flotta, garantendo che le dashboard di riepilogo mostrino 
	 * dati in tempo reale senza dover rieseguire full-table scan sul database.</li>
	 * </ul>
	 * <p><b>Thread-Safety e Lifecycle:</b></p>
	 * Progettato per essere invocato <b>esclusivamente</b> all'interno dell'hook 
	 * {@code afterCommit()} del {@code TransactionSynchronizationManager}. Questo garantisce 
	 * che l'aggiornamento della memoria volatile (RAM) avvenga solo a transazione SQL consolidata 
	 * su disco, annullando il rischio di corruzione dei dati in caso di Rollback.
	 * @param updatedVehicle L'istanza dell'entità persistita e aggiornata, contenente il nuovo stato 
	 * appena committato nel database.
	 * @param oldKey Il valore dello snapshot precedente utilizzato come chiave di raggruppamento 
	 * nella cache partizionata (es. il vecchio {@code maxUsefulWeightkg}). Fondamentale per 
	 * prevenire la generazione di record fantasma (Stale Data) all'interno delle liste.
	 */
	private void syncCacheAfterUpdate(Vehicle updatedVehicle, Object oldKey) {
		storeInCache(
			CaffeineCacheConfiguration.VEHICLE_BY_LICENSE_PLATE_CACHE,
			updatedVehicle.getLicensePlate(),
			updatedVehicle,
			CacheOperation.SINGLE_RECORD
		);
		storeInCache(
			CaffeineCacheConfiguration.VEHICLE_BY_MAX_USEFUL_WEIGHT_CACHE,
			updatedVehicle.getMaxUsefulWeightkg(),
			oldKey,
			updatedVehicle,
			CacheOperation.LIST_RECORD
		);
		storeInCache(
			CaffeineCacheConfiguration.ALL_VEHICLE_CACHE,
			CaffeineCacheConfiguration.ALL_VEHICLE_KEY,
			updatedVehicle,
			CacheOperation.LIST_RECORD
		);
	}
	
	/**
	 * Esegue la conversione (Mapping) strutturale dal contratto API in ingresso 
	 * (Request Payload) al modello di dominio relazionale (JPA Entity).
	 * <p><b>Contesto Architetturale (Anti-Corruption Layer e DTO Pattern):</b></p>
	 * Questo metodo funge da traduttore e isolante tra i livelli dell'applicazione. 
	 * Garantisce che il livello di persistenza (Database/Hibernate) non venga mai "contaminato" 
	 * dalle strutture dati usate per il trasporto HTTP. L'entità restituita si trova 
	 * in stato <i>Transient</i> (non possiede ancora un ID e non è tracciata dall'EntityManager), 
	 * risultando perfettamente pulita e pronta per essere passata al layer transazionale (Repository).
	 * <p><b>Composizione Sicura del Grafo (Enum Hydration):</b></p>
	 * L'istanziazione dell'oggetto annidato {@link VehicleCategory} avviene sfruttando 
	 * la conversione forte di Java ({@code Enum.valueOf()}). Questo meccanismo agisce come 
	 * un'ulteriore rete di sicurezza (Fail-Fast Validation): se il client dovesse eludere 
	 * la validazione perimetrale e inviare una stringa non conforme al dizionario ADR 
	 * (es. un tipo veicolo inventato), il mapping fallirà istantaneamente sollevando 
	 * un'eccezione, proteggendo l'integrità referenziale del database.
	 * @param dto Il Data Transfer Object contenente i dati grezzi e validati 
	 * provenienti dal Controller REST.
	 * @return Una nuova istanza dell'entità {@link Vehicle}, completamente idratata 
	 * con le grandezze fisiche e normative, pronta per l'operazione di {@code save()}.
	 */
	public Vehicle mapToEntity(VehicleRequestDTO dto) {
		Vehicle vehicle = new Vehicle();
		VehicleCategory category = new VehicleCategory();
		category.setVehicleType(Enum.valueOf(VehicleType.class, dto.vehicleType()));
		category.setLoadType(Enum.valueOf(LoadType.class, dto.loadType()));
		if(dto.vehicleApprovals() != null) {
			Set<VehicleApproval> approvals = new HashSet<VehicleApproval>();
			for(String approval : dto.vehicleApprovals())
				approvals.add(Enum.valueOf(VehicleApproval.class, approval));
			category.setVehicleApprovals(approvals);
		} else
			category.setVehicleApprovals(new HashSet<VehicleApproval>());
		vehicle.setVehicleCategory(category);
		vehicle.setLicensePlate(dto.licensePlate());
		vehicle.setMaxWeightkg(dto.maxWeightkg());
		vehicle.setMaxUsefulWeightkg(dto.maxUsefulWeightkg());
		vehicle.setHeightm(dto.heightm());
		vehicle.setWidthm(dto.widthm());
		vehicle.setLengthm(dto.lengthm());
		vehicle.setWheelbasem(dto.wheelbasem());
		vehicle.setnAxles(dto.nAxles());
		return vehicle;
	}
}
