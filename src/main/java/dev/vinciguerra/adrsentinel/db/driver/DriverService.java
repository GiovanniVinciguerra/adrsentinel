package dev.vinciguerra.adrsentinel.db.driver;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import dev.vinciguerra.adrsentinel.db.AbstractGenericService;
import dev.vinciguerra.adrsentinel.db.CaffeineCacheConfiguration;
import dev.vinciguerra.adrsentinel.db.driver.Driver.DriverApproval;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;
import dev.vinciguerra.adrsentinel.web.dto.UpdateActiveStatusDTO;
import dev.vinciguerra.adrsentinel.web.dto.driver.DriverUpdateAdrApprovalDTO;
import dev.vinciguerra.adrsentinel.web.dto.driver.DriverUpdateDTO;

public class DriverService extends AbstractGenericService {
	private final DriverRepository driverRepository;
	
	protected DriverService(DriverRepository driverRepository, CacheManager cacheManager) {
		super(cacheManager);
		this.driverRepository = driverRepository;
	}
	
	@Cacheable(value = CaffeineCacheConfiguration.DRIVER_BY_LICENSE_CACHE, key = "#license")
	public Driver getByLicense(String license) throws ResourceNotFoundException {
		logger.info("[DataBase CALL] Searching for the Driver by license: {}", license);
		return driverRepository.findByLicense(license)
			.orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + license));
	}
	
	@Cacheable(value = CaffeineCacheConfiguration.ALL_DRIVER_CACHE, key = "'" + CaffeineCacheConfiguration.ALL_DRIVER_KEY + "'")
	public List<Driver> getAllDriver() {
		logger.info("[DataBase CALL] Retrieving all Driver");
		return driverRepository.findAll();
	}
	
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
	
	@Transactional
	public Driver updateDetailsByLicense(String license, DriverUpdateDTO updateDto) throws ResourceNotFoundException {
		logger.info("[DataBase CALL] Updating Driver details with license: {}", license);
		Driver driver = driverRepository.findByLicense(license)
			.orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + license));
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
	 * @param license Il numero di patente alfanumerico dell'autista (chiave logica di business).
	 * @param updateDto Il payload di richiesta contenente il nuovo stato desiderato ({@code active: true/false}).
	 * @return L'entità {@link Driver} aggiornata, gestita (Managed) dal Persistence Context di Hibernate.
	 * @throws ResourceNotFoundException Se il numero patente fornito non corrisponde ad alcuna anagrafica 
	 * presente a sistema (approccio Fail-Fast).
	 */
	@Transactional
	public Driver updateActiveStatusByLicense(String license, UpdateActiveStatusDTO updateDto) throws ResourceNotFoundException {
		logger.info("[DataBase CALL] Updating Driver details with license: {}", license);
		Driver driver = driverRepository.findByLicense(license)
			.orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + license));
		driver.setActive(updateDto.active());
		Driver updatedDriver = driverRepository.save(driver);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterInsertOrUpdate(updatedDriver); }
		});
		return updatedDriver;
	}
	
	@Transactional
	public Driver updateAdrCertifiedByLicense(String license, DriverUpdateAdrApprovalDTO updateDto) throws ResourceNotFoundException {
		logger.info("[DataBase CALL] Updating Driver adrCertified with license: {}", license);
		Driver driver = driverRepository.findByLicense(license)
			.orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + license));
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
}
