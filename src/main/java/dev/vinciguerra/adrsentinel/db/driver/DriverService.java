package dev.vinciguerra.adrsentinel.db.driver;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import dev.vinciguerra.adrsentinel.db.AbstractGenericService;
import dev.vinciguerra.adrsentinel.db.CaffeineCacheConfiguration;
import dev.vinciguerra.adrsentinel.db.driver.Driver.DriverApproval;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;
import dev.vinciguerra.adrsentinel.web.dto.driver.DriverRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.driver.DriverUpdateActiveStatusDTO;
import dev.vinciguerra.adrsentinel.web.dto.driver.DriverUpdateAdrApprovalDTO;
import dev.vinciguerra.adrsentinel.web.dto.driver.DriverUpdateDTO;

/**
 * Livello Service (Business Logic) dedicato alla gestione del ciclo di vita dell'entità {@link Driver}.
 * <p>
 * Questa classe funge da intermediario tra il livello esposto (Controller) e il livello 
 * di persistenza (Repository), incapsulando la logica transazionale e le regole di business 
 * relative agli autisti della flotta.
 * </p>
 * <p><b>Scelte Architetturali Chiave:</b></p>
 * <ul>
 * <li><b>Caching Distribuito/Locale:</b> Implementa strategie di lettura ottimizzata tramite 
 * {@code @Cacheable} (es. Caffeine Cache) per ridurre il carico sul database durante 
 * le operazioni di fetch frequenti.</li>
 * <li><b>Transaction-Aware Cache Sync:</b> Le operazioni di scrittura (Insert/Update) 
 * non invalidano o aggiornano la cache in modo sincrono, ma delegano l'operazione al 
 * {@link TransactionSynchronizationManager}. Questo garantisce che la cache venga allineata 
 * <i>solo ed esclusivamente</i> se la transazione SQL va a buon fine (prevenzione del Cache Poisoning).</li>
 * <li><b>Identificazione tramite Business Key:</b> Le operazioni primarie utilizzano 
 * il Numero di Patente ({@code license}) come chiave logica (Business Key) garantendo 
 * l'idempotenza e l'isolamento dalle chiavi surrogate (Primary Key numeriche).</li>
 * </ul>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Service
public class DriverService extends AbstractGenericService {
	private final DriverRepository driverRepository;
	
	/**
	 * Costruttore per l'Iniezione delle Dipendenze (Dependency Injection).
	 * @param driverRepository Il repository JPA per l'accesso ai dati persistenti degli autisti.
	 * @param cacheManager Il gestore della cache ereditato dalla superclasse.
	 */
	protected DriverService(DriverRepository driverRepository, CacheManager cacheManager) {
		super(Objects.requireNonNull(cacheManager, "cacheManager must be not null"));
		this.driverRepository = Objects.requireNonNull(driverRepository, "driverRepository must not be null.");
	}
	
	/**
	 * Recupera le informazioni di un autista ricercandolo tramite il suo Numero di Patente.
	 * <p>
	 * L'operazione è ottimizzata tramite cache: le chiamate successive con la stessa 
	 * chiave non interrogheranno il database ma restituiranno il dato in memoria.
	 * </p>
	 * @param license Il numero di patente alfanumerico univoco dell'autista.
	 * @return L'entità {@link Driver} corrispondente.
	 * @throws ResourceNotFoundException Se nessun autista è associato alla patente fornita.
	 */
	@Cacheable(value = CaffeineCacheConfiguration.DRIVER_BY_LICENSE_CACHE, key = "#license")
	public Driver getByLicense(String license) throws ResourceNotFoundException {
		logger.info("[DataBase CALL] Searching for the Driver by license: {}", license);
		return driverRepository.findByLicense(license)
			.orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + license));
	}
	
	/**
	 * Recupera l'elenco completo di tutti gli autisti registrati a sistema.
	 * <p>
	 * L'intero dataset viene posto in cache per ottimizzare la renderizzazione di 
	 * eventuali dashboard o griglie dati lato frontend.
	 * </p>
	 * @return Una {@link List} contenente tutti i {@link Driver}.
	 */
	@Cacheable(value = CaffeineCacheConfiguration.ALL_DRIVER_CACHE, key = "'" + CaffeineCacheConfiguration.ALL_DRIVER_KEY + "'")
	public List<Driver> getAllDriver() {
		logger.info("[DataBase CALL] Retrieving all Driver");
		return driverRepository.findAll();
	}
	
	/**
	 * Persiste un nuovo autista nel database.
	 * <p>
	 * L'operazione gode di integrità transazionale. Una volta ultimata la commit 
	 * su database, viene innescato l'aggiornamento asincrono delle strutture di cache 
	 * per riflettere il nuovo inserimento.
	 * </p>
	 * @param newDriver L'entità {@link Driver} (transient) da salvare.
	 * @return L'entità {@link Driver} persistita (managed), arricchita di Primary Key.
	 */
	@Transactional
	public Driver save(Driver newDriver) {
		logger.info("[DataBase CALL] Saving new Driver with license: {}", newDriver.getLicense());
		Driver savedDriver = driverRepository.save(newDriver);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterInsertOrUpdate(savedDriver); }
		});
		return savedDriver;
	}
	
	/**
	 * Aggiorna i dati anagrafici e documentali di un autista esistente.
	 * I dati aggiornabili sono:
	 * <ul>
	 * <li><b>fullName</b>: nome completo dell'autista.</li>
	 * <li><b>phoneNumber</b>: numero di telefono (cellulare) dell'autista.</li>
	 * <li><b>licenseExpireDate</b>: data di scadenza della patente dell'autista.</li>
	 * <li><b>cqcExpireDate</b>: data di scadenza del cqc dell'autista.</li>
	 * </ul>
	 * <p>
	 * <b>Integrità Transazionale e Sicurezza della Cache (Anti-Poisoning):</b><br>
	 * L'esecuzione è racchiusa in un contesto {@code @Transactional} per garantire l'atomicità (ACID). 
	 * Una peculiarità architetturale fondamentale di questo metodo è la protezione della Cache: 
	 * l'aggiornamento dei dati (tramite {@code syncCacheAfterInsertOrUpdate}) 
	 * è delegato al {@link TransactionSynchronizationManager}. Questo assicura che l'allineamento 
	 * della cache avvenga <i>esclusivamente</i> nella fase di {@code afterCommit()}. 
	 * Qualora il database dovesse subire un Rollback (es. caduta di rete improvvisa o vincoli violati), 
	 * la cache rimarrà intatta, prevenendo disallineamenti di stato tra memoria e disco (Dirty Reads).
	 * </p>
	 * <b>Observability e Log Aggregation:</b><br>
	 * L'operazione traccia un log diagnostico strutturato. Il numero patente dell'autista viene iniettato 
	 * isolandolo per favorire l'indicizzazione e le ricerche mirate tramite sistemi di log 
	 * monitoring (es. stack ELK o Datadog).
	 * </p>
	 * @param updateDto Il payload ({@link DriverUpdateDTO}) contenente i nuovi dati validati.
	 * @return L'entità {@link Driver} aggiornata e salvata sul database.
	 * @throws ResourceNotFoundException Se l'autista richiesto non è presente a sistema.
	 */
	@Transactional
	public Driver updateDetailsByLicense(DriverUpdateDTO updateDto) throws ResourceNotFoundException {
		logger.info("[DataBase CALL] Updating Driver details with license: {}", updateDto.license());
		Driver driver = driverRepository.findByLicense(updateDto.license())
			.orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + updateDto.license()));
		driver.setFullName(updateDto.fullName());
		driver.setPhoneNumber(updateDto.phoneNumber());
		driver.setLicenseExpireDate(LocalDate.parse(updateDto.licenseExpireDate()));
		driver.setCqcExpireDate(LocalDate.parse(updateDto.cqcExpireDate()));
		Driver updatedDriver = driverRepository.save(driver);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterInsertOrUpdate(updatedDriver); }
		});
		return updatedDriver;
	}
	
	/**
	 * Esegue l'aggiornamento dello stato operativo (Soft Delete o Riattivazione) di un autista, 
	 * identificato in modo univoco tramite la sua Business Key (Numero Patente).
	 * <p>
	 * <b>Pattern Architetturale: Soft Delete & Audit Trail</b><br>
	 * All'interno del dominio gestionale, i veicoli che hanno effettuato dei trasporti non vengono 
	 * mai eliminati fisicamente dal database (Hard Delete) per proteggere l'integrità referenziale 
	 * dei viaggi storici (Shipment) e rispettare i rigidi vincoli degli Audit normativi. 
	 * Questo metodo manipola esclusivamente il flag di visibilità operativa ({@code active}), 
	 * escludendo o reintroducendo il mezzo nelle logiche di assegnazione per le nuove spedizioni.
	 * </p>
	 * <p>
	 * <b>Integrità Transazionale e Sicurezza della Cache (Anti-Poisoning):</b><br>
	 * L'esecuzione è racchiusa in un contesto {@code @Transactional} per garantire l'atomicità (ACID). 
	 * Una peculiarità architetturale fondamentale di questo metodo è la protezione della Cache: 
	 * l'aggiornamento dei dati (tramite {@code syncCacheAfterInsertOrUpdate}) 
	 * è delegato al {@link TransactionSynchronizationManager}. Questo assicura che l'allineamento 
	 * della cache avvenga <i>esclusivamente</i> nella fase di {@code afterCommit()}. 
	 * Qualora il database dovesse subire un Rollback (es. caduta di rete improvvisa o vincoli violati), 
	 * la cache rimarrà intatta, prevenendo disallineamenti di stato tra memoria e disco (Dirty Reads).
	 * </p>
	 * <p>
	 * <b>Observability e Log Aggregation:</b><br>
	 * L'operazione traccia un log diagnostico strutturato. Il numero patente dell'autista viene iniettato 
	 * isolandolo per favorire l'indicizzazione e le ricerche mirate tramite sistemi di log 
	 * monitoring (es. stack ELK o Datadog).
	 * </p>
	 * @param updateDto Il payload di richiesta contenente il nuovo stato desiderato ({@code active: true/false}).
	 * @return L'entità {@link Driver} aggiornata, gestita (Managed) dal Persistence Context di Hibernate.
	 * @throws ResourceNotFoundException Se il numero patente fornito non corrisponde ad alcuna anagrafica 
	 * presente a sistema (approccio Fail-Fast).
	 */
	@Transactional
	public Driver updateActiveStatusByLicense(DriverUpdateActiveStatusDTO updateDto) throws ResourceNotFoundException {
		logger.info("[DataBase CALL] Updating Driver active status with license: {}", updateDto.license());
		Driver driver = driverRepository.findByLicense(updateDto.license())
			.orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + updateDto.license()));
		driver.setActive(updateDto.active());
		Driver updatedDriver = driverRepository.save(driver);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterInsertOrUpdate(updatedDriver); }
		});
		return updatedDriver;
	}
	
	/**
	 * Aggiorna le certificazioni ADR (Accord Dangereuses Route) in possesso dell'autista.
	 * <p>
	 * L'ADR è la regolamentazione europea essenziale per il trasporto di merci pericolose. 
	 * Questo metodo mappa le stringhe ricevute dal client (es. classi di rischio infiammabile, 
	 * tossico, esplosivo) nei rispettivi enumeratori di dominio {@link DriverApproval}.
	 * </p>
	 * <p>
	 * <b>Integrità Transazionale e Sicurezza della Cache (Anti-Poisoning):</b><br>
	 * L'esecuzione è racchiusa in un contesto {@code @Transactional} per garantire l'atomicità (ACID). 
	 * Una peculiarità architetturale fondamentale di questo metodo è la protezione della Cache: 
	 * l'aggiornamento dei dati (tramite {@code syncCacheAfterInsertOrUpdate}) 
	 * è delegato al {@link TransactionSynchronizationManager}. Questo assicura che l'allineamento 
	 * della cache avvenga <i>esclusivamente</i> nella fase di {@code afterCommit()}. 
	 * Qualora il database dovesse subire un Rollback (es. caduta di rete improvvisa o vincoli violati), 
	 * la cache rimarrà intatta, prevenendo disallineamenti di stato tra memoria e disco (Dirty Reads).
	 * </p>
	 * <p>
	 * <b>Observability e Log Aggregation:</b><br>
	 * L'operazione traccia un log diagnostico strutturato. Il numero patente dell'autista viene iniettato 
	 * isolandolo per favorire l'indicizzazione e le ricerche mirate tramite sistemi di log 
	 * monitoring (es. stack ELK o Datadog).
	 * </p>
	 * @param updateDto Il payload contenente il set aggiornato di certificazioni ADR.
	 * @return L'entità {@link Driver} aggiornata con le nuove abilitazioni.
	 * @throws ResourceNotFoundException Se l'autista non viene trovato.
	 */
	@Transactional
	public Driver updateAdrCertifiedByLicense(DriverUpdateAdrApprovalDTO updateDto) throws ResourceNotFoundException {
		logger.info("[DataBase CALL] Updating Driver adrCertified with license: {}", updateDto.license());
		Driver driver = driverRepository.findByLicense(updateDto.license())
			.orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + updateDto.license()));
		Set<DriverApproval> approvals = new HashSet<DriverApproval>();
		for(String approval : updateDto.approvals())
			approvals.add(Enum.valueOf(DriverApproval.class, approval));
		driver.setDriverApprovals(approvals);
		Driver updatedDriver = driverRepository.save(driver);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterInsertOrUpdate(updatedDriver); }
		});
		return updatedDriver;
	}
	
	/**
	 * Aggiorna lo stato di transito ({@code inTransit}) di un autista specifico identificato dal suo ID.
	 * <p>
	 * Il metodo è annotato con {@code @Transactional}, garantendo che l'intera operazione 
	 * (recupero, modifica e salvataggio) avvenga all'interno di una singola transazione 
	 * atomica sul database.
	 * <p>
	 * <b>Flusso di esecuzione:</b>
	 * <ol>
	 * <li>Registra un log informativo a livello INFO per tracciare l'inizio dell'operazione sul database.</li>
	 * <li>Interroga il {@code driverRepository} per recuperare l'entità {@link Driver}. Se l'autista 
	 * non esiste nel sistema, interrompe il flusso lanciando una {@link ResourceNotFoundException}.</li>
	 * <li>Applica il nuovo stato di transito impostando il flag sul record recuperato 
	 * e invoca il salvataggio tramite il repository.</li>
	 * <li><b>Sincronizzazione della Cache:</b> Per garantire la coerenza tra i dati persistiti e la cache, 
	 * il metodo registra un hook tramite {@link TransactionSynchronizationManager}. L'aggiornamento 
	 * della cache ({@code syncCacheAfterInsertOrUpdate}) viene innescato <i>esclusivamente</i> dopo 
	 * che la transazione SQL ha effettuato il commit con successo, prevenendo disallineamenti 
	 * (dirty reads) in caso di eccezioni e rollback del database.</li>
	 * </ol>
	 * @param id L'identificativo univoco (Primary Key) dell'autista da aggiornare.
	 * @param status Il nuovo stato booleano da assegnare al flag {@code inTransit} 
	 * ({@code true} se attualmente impegnato in un viaggio, {@code false} altrimenti).
	 * @return L'entità {@link Driver} aggiornata e persistita nel database.
	 * @throws ResourceNotFoundException Se nessun autista corrisponde all'ID fornito.
	 */
	@Transactional
	public Driver updateInTransitStatusById(Long id, boolean status) throws ResourceNotFoundException {
		logger.info("[DataBase CALL] Updating Driver inTransit with id: {}", id);
		Driver driver = driverRepository.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + id));
		driver.setInTransit(status);
		Driver updatedDriver = driverRepository.save(driver);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterInsertOrUpdate(updatedDriver); }
		});
		return updatedDriver;
	}
	
	/**
	 * Routine interna per la sincronizzazione selettiva della cache in seguito a 
	 * operazioni di Insert o Update (Mutating operations).
	 * <p>
	 * Il metodo aggiorna sia la cache della singola entità (ricerca per patente), 
	 * sia la cache contenente la lista globale degli autisti. Deve essere richiamato 
	 * <b>esclusivamente</b> a valle di un commit transazionale confermato sul Database.
	 * </p>
	 * @param savedDriver L'entità {@link Driver} contenente lo stato aggiornato (Truth).
	 */
	private void syncCacheAfterInsertOrUpdate(Driver savedDriver) {
		storeInCache(
			CaffeineCacheConfiguration.DRIVER_BY_LICENSE_CACHE,
			savedDriver.getLicense(),
			savedDriver,
			CacheOperation.SINGLE_RECORD
		);
		storeInCache(
			CaffeineCacheConfiguration.ALL_DRIVER_CACHE,
			CaffeineCacheConfiguration.ALL_DRIVER_KEY,
			savedDriver,
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
	 * L'istanziazione dell'oggetto {@code Set<DriverApproval>} avviene sfruttando 
	 * la conversione forte di Java ({@code Enum.valueOf()}). Questo meccanismo agisce come 
	 * un'ulteriore rete di sicurezza (Fail-Fast Validation): se il client dovesse eludere 
	 * la validazione perimetrale e inviare una stringa non conforme al dizionario ADR 
	 * (es. un tipo veicolo inventato), il mapping fallirà istantaneamente sollevando 
	 * un'eccezione, proteggendo l'integrità referenziale del database.
	 * @param dto Il Data Transfer Object contenente i dati grezzi e validati 
	 * provenienti dal Controller REST.
	 * @return Una nuova istanza dell'entità {@link Vehicle}, completamente idratata 
	 * con le caratteristiche e normative, pronta per l'operazione di {@code save()}.
	 */
	public Driver mapToEntity(DriverRequestDTO dto) {
		Driver driver = new Driver();
		driver.setFullName(dto.fullName());
		driver.setTaxCode(dto.taxCode());
		driver.setPhoneNumber(dto.phoneNumber());
		driver.setLicense(dto.license());
		driver.setLicenseExpireDate(LocalDate.parse(dto.licenseExpireDate()));
		driver.setCqcExpireDate(LocalDate.parse(dto.cqcExpireDate()));
		if(dto.driverApprovals() != null) {
			Set<DriverApproval> approvals = dto.driverApprovals().stream()
				.map(approval -> Enum.valueOf(DriverApproval.class, approval))
				.collect(Collectors.toSet());
			driver.setDriverApprovals(approvals);
		} else {
			driver.setDriverApprovals(new HashSet<DriverApproval>());
		}
		return driver;
	}
}
