package dev.vinciguerra.adrsentinel.db.shipmentitem;

import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import dev.vinciguerra.adrsentinel.db.AbstractGenericService;
import dev.vinciguerra.adrsentinel.db.CaffeineCacheConfiguration;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;

/**
 * Service Layer dedicato alla gestione della logica di business e dell'accesso ai dati 
 * per l'entità {@link ShipmentItem} (Dettaglio della Spedizione).
 * <p>
 * <b>Architettura e Performance (Strategia di Caching Avanzata):</b><br>
 * Questa classe estende {@code AbstractGenericService} per ereditare funzionalità avanzate 
 * di manipolazione diretta della cache. Implementa un pattern ibrido di lettura e scrittura:
 * <ul>
 * <li><b>Read-Through:</b> Le operazioni di lettura tentano prima il recupero (Cache Hit) 
 * dalla memoria (Caffeine). In caso di Miss, interrogano il DB e popolano la cache.</li>
 * <li><b>Write-Through (Allineamento Attivo):</b> Durante il salvataggio, il servizio 
 * inietta proattivamente i nuovi dati nelle rispettive cache (sia per il singolo elemento 
 * che per la collezione genitore), garantendo coerenza assoluta (Eventual Consistency) 
 * senza dover invalidare brutalmente le cache esistenti.</li>
 * </ul>
 * </p>
 * <p>
 * <b>Gestione Transazionale:</b><br>
 * La classe è annotata con {@code @Transactional(readOnly = true)} di default, 
 * disabilitando l'overhead dell'Hibernate Dirty Checking per ottimizzare le operazioni di lettura. 
 * I metodi di scrittura sovrascrivono questa direttiva.
 * </p>
 * @author Giovanni Vinciguerra
 * @version 3.0 (Cached ShipmentItem Service)
 * @since 1.0
 */
@Service
public class ShipmentItemService extends AbstractGenericService {
	private final ShipmentItemRepository shipmentItemRepository;
	
	/**
	 * Costruttore per l'iniezione delle dipendenze.
	 * @param shipmentItemRepository il repository Spring Data JPA per l'accesso fisico al DB.
	 * @param cacheManager il gestore delle cache (es. Caffeine) passato alla superclasse.
	 */
	public ShipmentItemService(ShipmentItemRepository shipmentItemRepository, CacheManager cacheManager) {
		super(cacheManager);
		this.shipmentItemRepository = shipmentItemRepository;
	}
	
	/**
	 * Recupera un singolo Item di spedizione utilizzando la sua Surrogate Business Key (UUID).
	 * <p>
	 * Interroga preventivamente la cache {@code SHIPMENT_ITEM_BY_ITEM_UUID_CACHE}. 
	 * Costituisce un punto di accesso a complessità O(1) in memoria, riducendo drasticamente 
	 * il carico sul database relazionale per le ricerche puntuali.
	 * </p>
	 * @param itemUUID l'identificatore univoco immutabile della riga di carico.
	 * @return l'istanza dell'entità {@link ShipmentItem}.
	 * @throws ResourceNotFoundException se l'UUID non esiste né in cache né a database.
	 */
	@Cacheable(value = CaffeineCacheConfiguration.SHIPMENT_ITEM_BY_ITEM_UUID_CACHE, key = "#itemUUID")
	public ShipmentItem getByItemUUID(String itemUUID) throws ResourceNotFoundException {
		logger.info("[DataBase CALL] Searching for the ShipmentItem by itemUUID: {}", itemUUID);
		return  shipmentItemRepository.findByItemUUID(itemUUID)
			.orElseThrow(() -> new ResourceNotFoundException("ShipmentItem not found: " + itemUUID));
	}
	
	/**
	 * Recupera l'intero set di righe di carico associate a una specifica Spedizione.
	 * <p>
	 * Utilizza il {@code trackingNumber} della spedizione padre come chiave di cache. 
	 * Questo approccio previene il noto problema del "LazyInitializationException" nei DTO 
	 * e fornisce una vista istantanea e cachata del contenuto della spedizione.
	 * </p>
	 * @param shipment l'entità padre (Spedizione) di cui si vogliono recuperare i dettagli.
	 * @return una lista (potenzialmente vuota ma non nulla) di {@link ShipmentItem}.
	 */
	@Cacheable(value = CaffeineCacheConfiguration.SHIPMENT_ITEM_BY_SHIPMENT_CACHE, key = "#shipment.trackingNumber")
	public List<ShipmentItem> getByShipment(Shipment shipment) {
		logger.info("[DataBase CALL] Searching for the ShipmentItem by Shipment trackingNumber: {}", shipment.getTrackingNumber());
		return shipmentItemRepository.findByShipment(shipment);
	}
	
	/**
	 * Persiste in modo transazionale una nuova riga di carico (ShipmentItem) e orchestra 
	 * l'allineamento garantito delle cache in memoria.
	 * <p>
	 * <b>Integrità Transazionale e Prevenzione dei "Ghost Record":</b><br>
	 * Questo metodo risolve il classico problema dell'incoerenza tra Database e RAM in caso 
	 * di eccezioni. Sfruttando il {@link TransactionSynchronizationManager}, le operazioni di 
	 * aggiornamento della cache vengono "messe in pausa" e delegate alla fase di 
	 * <b>{@code afterCommit}</b>. <br>
	 * Questo garantisce che la cache (Caffeine) venga popolata <i>solo ed esclusivamente</i> 
	 * se il Database ha confermato la scrittura (Commit) con successo. In caso di fallimento 
	 * e conseguente Rollback SQL, il blocco di sincronizzazione viene ignorato, mantenendo 
	 * la cache pulita e prevenendo l'apparizione di entità fantasma.
	 * </p>
	 * <p>
	 * <b>Topologie di Cache Aggiornate (Write-Through):</b><br>
	 * <ol>
	 * <li><b>Cache Singola (UUID):</b> Inietta la nuova entità con accesso O(1).</li>
	 * <li><b>Cache Collezione (Tracking Number):</b> Appende la nuova entità alla lista 
	 * già presente in memoria per la spedizione padre, evitando il costo di una invalidazione 
	 * totale e di un successivo ricaricamento (Lazy Loading prevention).</li>
	 * </ol>
	 * </p>
	 * @param newShipmentItem l'entità transitoria (transient) contenente i dati validati.
	 * @return l'entità gestita (managed) da Hibernate, completa di ID autogenerato.
	 */
	@Transactional
	public ShipmentItem save(ShipmentItem newShipmentItem) {
		logger.info("[DataBase CALL] Saving new ShipmentItem with itemUUID: {}", newShipmentItem.getItemUUID());
		ShipmentItem savedShipmentItem = shipmentItemRepository.save(newShipmentItem);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				Shipment shipment = savedShipmentItem.getShipment();
				updateCache(
					CaffeineCacheConfiguration.SHIPMENT_ITEM_BY_ITEM_UUID_CACHE,
					savedShipmentItem.getItemUUID(),
					savedShipmentItem,
					CacheOperation.SINGLE_RECORD
				);
				updateCache(
					CaffeineCacheConfiguration.SHIPMENT_ITEM_BY_SHIPMENT_CACHE,
					shipment.getTrackingNumber(),
					savedShipmentItem,
					CacheOperation.LIST_RECORD
				);
			}
		});
		return savedShipmentItem;
	}
}
