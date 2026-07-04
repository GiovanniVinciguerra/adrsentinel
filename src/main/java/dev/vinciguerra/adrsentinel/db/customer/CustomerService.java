package dev.vinciguerra.adrsentinel.db.customer;

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
import dev.vinciguerra.adrsentinel.web.dto.customer.CustomerRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.customer.CustomerUpdateActiveStatusDTO;
import dev.vinciguerra.adrsentinel.web.dto.customer.CustomerUpdateDTO;

/**
 * Service di dominio (Domain Service) orchestratore per il ciclo di vita dell'entità {@link Customer}.
 * <p><b>Ruolo Architetturale e Transazionalità:</b></p>
 * La classe funge da strato transazionale (Boundary Layer) tra i controller REST e il livello di persistenza (JPA).
 * Gestisce attivamente il disaccoppiamento tra il database relazionale e la memoria RAM (Caffeine Cache).
 * Ogni operazione di mutazione (Insert, Update) viene confinata in un contesto {@code @Transactional}.
 * Per prevenire scenari di "Dirty Read" (inquinamento della cache in caso di Rollback), la sincronizzazione
 * in memoria viene deferita alla fase di Post-Commit avvalendosi del {@link TransactionSynchronizationManager}.
 * <p><b>Gestione del Key Shifting (Bussola della Cache):</b></p>
 * Nelle operazioni di aggiornamento, il service adotta una strategia di conservazione dello stato precedente
 * (es. cattura preventiva di {@code oldCompanyName}). Questo artefatto agisce come "bussola" per il motore
 * di cache ereditato da {@link AbstractGenericService}, permettendo di rintracciare e rimuovere l'entità
 * dalla lista associata alla vecchia chiave prima di re-inserirla sotto la nuova, neutralizzando il rischio
 * di duplicazioni fantasma in RAM.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Service
public class CustomerService extends AbstractGenericService {
	private final CustomerRepository customerRepository;

	/**
	 * Costruttore per la Dependency Injection basata su costruttore.
	 * @param customerRepository Il layer di accesso ai dati (Spring Data JPA) per l'entità Customer.
	 * @param cacheManager Il gestore della cache (es. Caffeine) propagato alla superclasse per le operazioni manuali.
	 */
	protected CustomerService(CustomerRepository customerRepository, CacheManager cacheManager) {
		super(cacheManager);
		this.customerRepository = customerRepository;
	}
	
	/**
	 * Recupera un singolo cliente avvalendosi della sua Business Key immutabile (Partita IVA).
	 * Il metodo opera con strategia "Cache-Aside": interroga prima la cache Caffeine designata e, 
	 * solo in caso di "Miss", esegue la query a database tramite repository.
	 * @param vatNumber La Partita IVA (Business Key) del cliente da ricercare.
	 * @return L'entità {@link Customer} associata univocamente alla Partita IVA fornita.
	 * @throws ResourceNotFoundException Se nessun record corrisponde alla Partita IVA indicata.
	 */
	@Cacheable(value = CaffeineCacheConfiguration.CUSTOMER_BY_VAT_NUMBER_CACHE, key = "#vatNumber")
	public Customer getByVatNumber(String vatNumber) {
		logger.info("[DataBase CALL] Searching for the Customer by vatNumber: {}", vatNumber);
		return customerRepository.findByVatNumber(vatNumber)
			.orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + vatNumber));
	}
	
	/**
	 * Recupera una lista di clienti applicando un criterio di match esatto (Exact Match) sulla Ragione Sociale.
	 * Il tipo di ritorno {@link List} gestisce intrinsecamente le omonimie (es. filiali diverse con lo stesso nome).
	 * La strategia di Caching utilizza il nome esatto dell'azienda come chiave deterministica.
	 * @param companyName La Ragione Sociale esatta da ricercare.
	 * @return Una lista di entità {@link Customer} corrispondenti. Ritorna lista vuota in assenza di match.
	 */
	@Cacheable(value = CaffeineCacheConfiguration.CUSTOMER_BY_COMPANY_NAME_CACHE, key = "#companyName")
	public List<Customer> getByCompanyName(String companyName) {
		logger.info("[DataBase CALL] Searching for the Customer by companyName: {}", companyName);
		return customerRepository.findByCompanyName(companyName);
	}
	
	/**
	 * Recupera l'intero set di dati dei clienti censiti a sistema.
	 * Utilizza una chiave di cache statica (Hardcoded Key) per immagazzinare l'intero aggregato in RAM,
	 * ottimizzando i caricamenti massivi in dashboard di Frontend.
	 * @return La lista onnicomprensiva di tutti i {@link Customer} presenti a database.
	 */
	@Cacheable(value = CaffeineCacheConfiguration.ALL_CUSTOMER_CACHE, key = "'" + CaffeineCacheConfiguration.ALL_CUSTOMER_KEY + "'")
	public List<Customer> getAllCustomer() {
		logger.info("[DataBase CALL] Retrieving all Customer");
		return customerRepository.findAll();
	}
	
	/**
	 * Persiste un nuovo cliente a database e innesca la sincronizzazione della RAM.
	 * L'operazione di salvataggio (Write) e l'iniezione in Cache sono temporizzate in modo asincrono:
	 * la RAM viene mutata solo se la transazione JPA esegue un Commit con successo.
	 * @param newCustomer L'entità transiente da persistere.
	 * @return L'entità consolidata (Managed) arricchita degli identificatori generati.
	 */
	@Transactional
	public Customer save(Customer newCustomer) {
		logger.info("[DataBase CALL] Saving new Customer with Vat Number: {}", newCustomer.getVatNumber());
		Customer savedCustomer = customerRepository.save(newCustomer);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterInsert(savedCustomer); }
		});
		return savedCustomer;
	}
	
	/**
	 * Esegue un aggiornamento chirurgico dei dati anagrafici (Nome Azienda, Sede Legale) di un cliente.
	 * <p><b>Gestione Key Shifting:</b></p>
	 * Prima di applicare la mutazione di stato, il metodo estrae e "congela" l'attuale {@code companyName}. 
	 * Questo dato (oldKey) risulta vitale per la fase di sincronizzazione Post-Commit, in quanto istruisce 
	 * l'infrastruttura di cache a dislocare il record dall'eventuale vecchia chiave qualora il DTO
	 * imponga un cambio di ragione sociale (prevenendo dati orfani o duplicati in RAM).
	 * @param vatNumber La Business Key immutabile usata come target per la mutazione.
	 * @param updateDto Il DTO (Inbound Payload) contenente i nuovi valori anagrafici da sovrascrivere.
	 * @return L'entità aggiornata e reidratata post-flush.
	 * @throws ResourceNotFoundException Se la P.IVA bersaglio non esiste a sistema.
	 */
	@Transactional
	public Customer updateDetailsByVatNumber(CustomerUpdateDTO updateDto) throws ResourceNotFoundException {
		logger.info("[DataBase CALL] Updating Customer details with vatNumber: {}", updateDto.vatNumber());
		Customer customer = customerRepository.findByVatNumber(updateDto.vatNumber())
			.orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + updateDto.vatNumber()));
		final String oldCompanyName = customer.getCompanyName();
		customer.setCompanyName(updateDto.companyName());
		customer.setLegalAddress(updateDto.legalAddress());
		Customer updatedCustomer = customerRepository.save(customer);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterUpdate(updatedCustomer, oldCompanyName); }
		});
		return updatedCustomer;
	}
	
	/**
	 * Modifica unicamente il flag operativo (Attivo/Inattivo) disaccoppiando l'azione dai dati anagrafici sensibili.
	 * Anche in questa operazione di toggle, la stringa {@code oldCompanyName} viene catturata per
	 * assicurare il corretto attraversamento e aggiornamento delle strutture a lista in RAM.
	 * @param vatNumber La Business Key del cliente target.
	 * @param updateDto Payload isolato contenente il nuovo flag di attività.
	 * @return L'entità aggiornata post-mutazione logica.
	 * @throws ResourceNotFoundException Se il target non viene individuato.
	 */
	@Transactional
	public Customer updateActiveStatusByVatNumber(CustomerUpdateActiveStatusDTO updateDto) throws ResourceNotFoundException {
		logger.info("[DataBase CALL] Updating Customer active status with vatNumber: {}", updateDto.vatNumber());
		Customer customer = customerRepository.findByVatNumber(updateDto.vatNumber())
			.orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + updateDto.vatNumber()));
		final String oldCompanyName = customer.getCompanyName();
		customer.setActive(updateDto.active());
		Customer updatedCustomer = customerRepository.save(customer);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterUpdate(updatedCustomer, oldCompanyName); }
		});
		return updatedCustomer;
	}
	
	/**
	 * Metodo interno di orchestrazione (Fase di Upsert RAM).
	 * Viene invocato post-commit per propagare il nuovo inserimento attraverso tutte le topologie 
	 * di cache censite (ricerca per P.IVA diretta, per Liste Omonime e per la Collection totale).
	 * @param savedCustomer Il record consolidato da posizionare in memoria.
	 */
	private void syncCacheAfterInsert(Customer savedCustomer) {
		storeInCache(
			CaffeineCacheConfiguration.CUSTOMER_BY_VAT_NUMBER_CACHE,
			savedCustomer.getVatNumber(),
			savedCustomer,
			CacheOperation.SINGLE_RECORD
		);
		storeInCache(
			CaffeineCacheConfiguration.CUSTOMER_BY_COMPANY_NAME_CACHE,
			savedCustomer.getCompanyName(),
			savedCustomer,
			CacheOperation.LIST_RECORD
		);
		storeInCache(
			CaffeineCacheConfiguration.ALL_CUSTOMER_CACHE,
			CaffeineCacheConfiguration.ALL_CUSTOMER_KEY,
			savedCustomer,
			CacheOperation.LIST_RECORD
		);
	}
	
	/**
	 * Metodo interno di orchestrazione dinamica (Fase di Allineamento RAM).
	 * Sfrutta il parametro di tracciamento {@code oldKey} (il nome azienda antecedente la modifica) 
	 * per scansionare accuratamente le cache di tipo {@code LIST_RECORD}, eseguendo l'eviction parziale
	 * dalla vecchia locazione prima di eseguire l'upsert sulla chiave aggiornata.
	 * @param updatedCustomer Il record modificato.
	 * @param oldKey Il valore originario della proprietà (companyName) soggetta a possibile Key Shifting.
	 */
	private void syncCacheAfterUpdate(Customer updatedCustomer, Object oldKey) {
		storeInCache(
			CaffeineCacheConfiguration.CUSTOMER_BY_VAT_NUMBER_CACHE,
			updatedCustomer.getVatNumber(),
			updatedCustomer,
			CacheOperation.SINGLE_RECORD
		);
		storeInCache(
			CaffeineCacheConfiguration.CUSTOMER_BY_COMPANY_NAME_CACHE,
			updatedCustomer.getCompanyName(),
			oldKey,
			updatedCustomer,
			CacheOperation.LIST_RECORD
		);
		storeInCache(
			CaffeineCacheConfiguration.ALL_CUSTOMER_CACHE,
			CaffeineCacheConfiguration.ALL_CUSTOMER_KEY,
			updatedCustomer,
			CacheOperation.LIST_RECORD
		);
	}
	
	/**
	 * Metodo di utilità per la conversione Inbound (DTO -> Entity).
	 * Funge da factory transiente, instanziando un nuovo Customer e mappando rigidamente
	 * le sole proprietà sicure esposte dalla barriera del Controller.
	 * @param dto Il Data Transfer Object contenente il payload in ingresso.
	 * @return Un'istanza transiente dell'entità {@link Customer}.
	 */
	public Customer mapToEntity(CustomerRequestDTO dto) {
		Customer customer = new Customer();
		customer.setCompanyName(dto.companyName());
		customer.setVatNumber(dto.vatNumber());
		customer.setLegalAddress(dto.legalAddress());
		return customer;
	}
}
