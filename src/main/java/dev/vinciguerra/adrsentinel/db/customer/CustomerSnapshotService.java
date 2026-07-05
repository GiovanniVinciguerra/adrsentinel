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

/**
 * Service di dominio (Domain Service) orchestratore dedicato alla gestione del ciclo di vita storico delle anagrafiche cliente.
 * <p><b>Ruolo Architetturale e Design "Append-Only":</b></p>
 * In perfetto allineamento con la natura immutabile del "Manifest" di spedizione, questo service governa esclusivamente 
 * la persistenza (Write) e l'estrazione (Read) dei reperti storici ({@link CustomerSnapshot}). Trattandosi di un registro 
 * di log generato in uscita dallo stato PLANNED, il service omette intenzionalmente qualsiasi direttiva di aggiornamento 
 * (Update) o cancellazione (Delete), blindando architetturalmente la storicità e l'inalterabilità del dato ai fini di auditing.
 * <p><b>Gestione Transazionale e Disaccoppiamento RAM:</b></p>
 * Estendendo {@link AbstractGenericService}, la classe eredita il motore di gestione della cache Caffeine. Le operazioni 
 * di scrittura sono confinate all'interno di un perimetro {@code @Transactional} e delegano la mutazione della memoria 
 * alla fase di Post-Commit (tramite {@link TransactionSynchronizationManager}). Questo previene la propagazione di scenari 
 * "Dirty Read" o l'inquinamento delle liste in cache qualora il database inneschi un Rollback.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Service
public class CustomerSnapshotService extends AbstractGenericService {
	private final CustomerSnapshotRepository customerSnapshotRepository;
	
	/**
	 * Costruttore per l'iniezione delle dipendenze (Constructor Injection).
	 * Garantisce l'immutabilità dello stato a runtime e semplifica l'isolamento del componente durante i test unitari.
	 * @param customerSnapshotRepository Il layer di accesso ai dati (Spring Data JPA) per l'entità storica.
	 * @param cacheManager Il gestore della cache propagato alla superclasse per l'orchestrazione manuale.
	 */
	protected CustomerSnapshotService(CustomerSnapshotRepository customerSnapshotRepository, CacheManager cacheManager) {
		super(cacheManager);
		this.customerSnapshotRepository = customerSnapshotRepository;
	}
	
	/**
	 * Recupera l'elenco dei reperti storici (snapshot anagrafici) associati univocamente a una specifica spedizione.
	 * <p><b>Strategia "Cache-Aside" e Router dei Dati:</b></p>
	 * Questo metodo viene interrogato dal livello architetturale superiore quando il sistema necessita di ricostruire 
	 * il manifest anagrafico per spedizioni negli stati operativi o terminali (TRANSIT, DELIVERED, CANCELED). Il fetch 
	 * viene intercettato dal motore Caffeine per garantire latenze minime; in caso di "Miss" in RAM, la chiamata prosegue 
	 * verso la Derived Query del repository relazionale.
	 * @param id La Chiave Primaria (Surrogate Key) identificativa della spedizione master ({@link Shipment}).
	 * @return Una lista di entità {@link CustomerSnapshot} contenenti le anagrafiche cristallizzate. Ritorna una lista vuota in 
	 * assenza di match.
	 * @throws ResourceNotFoundException (Eventualità predisposta per integrazioni di validazione custom a monte della chiamata).
	 */
	@Cacheable(value = CaffeineCacheConfiguration.CUSTOMER_SNAPSHOT_BY_SHIPMENT_ID_CACHE, key = "#id")
	public List<CustomerSnapshot> getByShipmentId(Long id) throws ResourceNotFoundException {
		logger.info("[DataBase CALL] Searching for the CustomerSnapshot by Shipment.Id: {}", id);
		return customerSnapshotRepository.findByShipment_Id(id);
	}
	
	/**
	 * Persiste un nuovo reperto storico a database e innesca la conseguente sincronizzazione asincrona della RAM.
	 * <p><b>Temporizzazione Post-Commit:</b></p>
	 * L'operazione di persistenza fisica (Flush JPA) e l'inserimento in cache sono strettamente disaccoppiati. L'aggiunta 
	 * dell'entità all'interno dell'elenco in memoria avviene esclusivamente se il database relazionale conferma il Commit 
	 * della transazione con successo, garantendo la coerenza strutturale tra disco e RAM.
	 * @param newCustomerSnapshot L'entità transiente generata dal factory method al momento della partenza del viaggio.
	 * @return L'entità consolidata (Managed) arricchita dell'identificatore autogenerato dal database.
	 */
	@Transactional
	public CustomerSnapshot save(CustomerSnapshot newCustomerSnapshot) {
		logger.info("[DataBase CALL] Saving new CustomerSnapshot with vat number: {}", newCustomerSnapshot.getVatNumberSnap());
		CustomerSnapshot savedCustomerSnapshot = customerSnapshotRepository.save(newCustomerSnapshot);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterInsert(savedCustomerSnapshot); }
		});
		return savedCustomerSnapshot;
	}
	
	/**
	 * Metodo interno di orchestrazione (Fase di Insert RAM).
	 * Viene invocato dinamicamente nella fase di Post-Commit per accodare il nuovo snapshot all'interno 
	 * della corretta lista in memoria, utilizzando l'ID della spedizione master come chiave di raggruppamento.
	 * @param savedCustomerSnapshot Il record storico consolidato da iniettare nella topologia di cache dedicata.
	 */
	private void syncCacheAfterInsert(CustomerSnapshot savedCustomerSnapshot) {
		storeInCache(
			CaffeineCacheConfiguration.CUSTOMER_SNAPSHOT_BY_SHIPMENT_ID_CACHE,
			savedCustomerSnapshot.getShipment().getId(),
			savedCustomerSnapshot,
			CacheOperation.LIST_RECORD
		);
	}
}
