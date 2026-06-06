package dev.vinciguerra.adrsentinel.db.shipmentitem;

import java.util.List;
import org.hibernate.LazyInitializationException;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import dev.vinciguerra.adrsentinel.db.AbstractGenericService;
import dev.vinciguerra.adrsentinel.db.CaffeineCacheConfiguration;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.PackingGroup;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumberService;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment.ShipmentStatus;
import dev.vinciguerra.adrsentinel.db.shipment.ShipmentService;
import dev.vinciguerra.adrsentinel.db.shipmentitem.ShipmentItem.UnitOfMeasure;
import dev.vinciguerra.adrsentinel.exception.IllegalShipmentStateException;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;
import dev.vinciguerra.adrsentinel.web.dto.shipmentitem.ShipmentItemRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipmentitem.ShipmentItemUpdateDTO;

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
	private final ShipmentService shipmentService;
	private final OnuNumberService onuNumberService;
	
	/**
	 * Costruttore per l'iniezione delle dipendenze.
	 * @param shipmentItemRepository il repository Spring Data JPA per l'accesso fisico al DB.
	 * @param cacheManager il gestore delle cache (es. Caffeine) passato alla superclasse.
	 */
	public ShipmentItemService(ShipmentItemRepository shipmentItemRepository, ShipmentService shipmentService, OnuNumberService onuNumberService, CacheManager cacheManager) {
		super(cacheManager);
		this.shipmentItemRepository = shipmentItemRepository;
		this.shipmentService = shipmentService;
		this.onuNumberService = onuNumberService;
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
	 * @param shipmentTrackingNumber il numero di tracking della spedizione di cui si vogliono recuperare i dettagli.
	 * @return una lista (potenzialmente vuota ma non nulla) di {@link ShipmentItem}.
	 */
	@Cacheable(value = CaffeineCacheConfiguration.SHIPMENT_ITEM_BY_SHIPMENT_CACHE, key = "#shipmentTrackingNumber")
	public List<ShipmentItem> getByShipment(String shipmentTrackingNumber) {
		logger.info("[DataBase CALL] Searching for the ShipmentItem by Shipment trackingNumber: {}", shipmentTrackingNumber);
		return shipmentItemRepository.findByShipmentTrackingNumber(shipmentTrackingNumber);
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
		final String shipmentTrackingNumber = savedShipmentItem.getShipment().getTrackingNumber();
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterInsertOrUpdate(savedShipmentItem, shipmentTrackingNumber); }
		});
		return savedShipmentItem;
	}
	
	/**
	 * Esegue l'aggiornamento parziale (Mutation) dei dati logistici e normativi di una riga 
	 * di carico (Shipment Item) identificata tramite la sua Business Key (UUID).
	 * <p><b>Contesto Transazionale (ACID e Dirty Checking):</b></p>
	 * Il metodo è blindato all'interno di un confine transazionale ({@code @Transactional}). 
	 * Questo garantisce che tutte le operazioni di lettura (lookup dell'Item e dell'anagrafica ONU) 
	 * e di mutazione avvengano in modo <i>Atomico</i>. Se il recupero dell'anagrafica fallisce 
	 * o se la conversione degli Enum lancia un'eccezione, l'intera transazione subisce un 
	 * <i>Rollback</i>, lasciando il database in uno stato coerente e incontaminato.
	 * <p><b>Risoluzione delle Relazioni (Business Driven):</b></p>
	 * L'aggiornamento della scheda normativa ({@link OnuNumber}) non avviene tramit ID fisici 
	 * del database, ma interrogando il servizio di dominio tramite la chiave composita 
	 * (Codice ONU + Gruppo di Imballaggio). I valori testuali (Enum) in ingresso dal DTO vengono 
	 * decodificati a runtime in modo sicuro.
	 * <p><b>Strategia di Caching Avanzata (Post-Commit Synchronization):</b></p>
	 * Questa è una sezione critica dell'architettura. Per evitare la pericolosa desincronizzazione 
	 * tra Database e Cache (che si verificherebbe se la cache venisse aggiornata prima di un 
	 * improvviso rollback del database), l'aggiornamento in memoria è delegato al 
	 * {@link TransactionSynchronizationManager}. 
	 * L'hook {@code afterCommit()} garantisce che la RAM venga toccata <b>solo ed esclusivamente</b> 
	 * se il salvataggio su disco è andato a buon fine. 
	 * <br>
	 * <i>Nota di Design:</i> Poiché le chiavi primarie della cache ({@code itemUUID} e il 
	 * {@code trackingNumber} della spedizione padre) sono architetturalmente <b>immutabili</b>, 
	 * non vi è alcun rischio di chiavi orfane (Stale Keys). Di conseguenza, si riutilizza in modo 
	 * ottimizzato l'operazione unificata {@code syncCacheAfterInsertOrUpdate}, senza la necessità 
	 * di tracciare la "vecchia" chiave (oldKey) per le operazioni di sfratto (Eviction).
	 * @param itemUUID L'identificatore univoco universale e immutabile della riga di carico.
	 * @param updateDto Il payload validato contenente esclusivamente le grandezze mutabili 
	 * (quantità, unità di misura) e i riferimenti per la risoluzione della nuova anagrafica ADR.
	 * @return L'entità {@link ShipmentItem} completamente idratata e persistita post-aggiornamento.
	 * @throws ResourceNotFoundException Se l'UUID fornito non corrisponde ad alcun articolo esistente 
	 * (attivando il Fail-Fast e il conseguente Rollback transazionale).
	 * @throws IllegalShipmentStateException Se lo Shipment collegato a questa rotta non è più nello stato PLANNED.
	 */
	@Transactional
	public ShipmentItem updateDetailsByItemUUID(String itemUUID, ShipmentItemUpdateDTO updateDto) throws ResourceNotFoundException, IllegalShipmentStateException {
		ShipmentItem item = shipmentItemRepository.findByItemUUID(itemUUID)
			.orElseThrow(() -> new ResourceNotFoundException("ShipmentItem not found: " + itemUUID));
		if(item.getShipment().getShipmentStatus() != ShipmentStatus.PLANNED)
			throw new IllegalShipmentStateException("Update denied: shipment is no longer in PLANNED status.");
		final String shipmentTrackingNumber = item.getShipment().getTrackingNumber();
		OnuNumber number = onuNumberService.getByOnuCodeAndPackingGroupAndName(
			updateDto.onuCode(),
			Enum.valueOf(PackingGroup.class, updateDto.packingGroup()),
			updateDto.name()
		);
		item.setOnuNumber(number);
		item.setQuantity(updateDto.quantity());
		item.setUnitOfMeasure(Enum.valueOf(UnitOfMeasure.class, updateDto.unitOfMeasure()));
		ShipmentItem updatedShipmentItem = shipmentItemRepository.save(item);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterInsertOrUpdate(updatedShipmentItem, shipmentTrackingNumber); }
		});
		return updatedShipmentItem;
	}
	
	/**
	 * Esegue l'operazione di sincronizzazione e allineamento della cache applicativa per 
	 * l'entità {@link ShipmentItem} (Dettaglio Merce/Collo), implementando rigorosamente 
	 * il pattern architetturale Write-Through.
	 * <p>
	 * All'interno del dominio gestionale, il {@code ShipmentItem} rappresenta l'entità "figlia" 
	 * in una forte relazione di composizione con l'entità "padre" {@link Shipment}. 
	 * La strategia di caching adottata riflette i due pattern di accesso principali a questa risorsa: 
	 * l'accesso puntuale (es. scansione di un singolo collo tramite terminale) e l'accesso 
	 * aggregato (es. caricamento della distinta base di un'intera spedizione).
	 * </p>
	 * <p>
	 * <b>Vincolo Transazionale:</b> Onde evitare inconsistenze critiche (es. colli presenti in RAM 
	 * ma non salvati nel database relazionale a causa di un rollback), l'invocazione di questo metodo 
	 * deve essere delegata <b>esclusivamente</b> al {@code TransactionSynchronizationManager}, 
	 * registrandola all'interno della fase di {@code afterCommit}.
	 * </p>
	 * <p>
	 * <b>Flusso di Sincronizzazione (Doppia Risoluzione):</b>
	 * <ul>
	 * <li><b>1. Indice di Granularità Singola (Item UUID):</b> Inserisce o sovrascrive l'entità 
	 * all'interno della cache {@code SHIPMENT_ITEM_BY_ITEM_UUID_CACHE}. L'utilizzo dell'UUID come 
	 * chiave garantisce un accesso O(1) ultra-veloce, fondamentale per operazioni real-time 
	 * come la spunta logistica o la lettura tramite barcode scanner.</li>
	 * <li><b>2. Indice di Aggregazione Relazionale (Tracking Number Padre):</b> Estrae il 
	 * {@code trackingNumber} dall'entità padre ({@link Shipment}) e utilizza questo valore per 
	 * intercettare la lista dei colli associata alla spedizione ({@code SHIPMENT_ITEM_BY_SHIPMENT_CACHE}).
	 * L'accodamento dinamico del nuovo item in questa lista previene il problema delle query N+1, 
	 * consentendo al frontend di caricare l'intera distinta della spedizione interrogando unicamente la RAM.</li>
	 * </ul>
	 * </p>
	 * @param savedShipmentItem l'istanza dell'entità {@link ShipmentItem} appena persistita 
	 * con successo nel database. L'oggetto deve trovarsi nello stato "Managed" e avere sia il 
	 * proprio {@code itemUUID} valorizzato, sia la relazione {@code shipment} (padre) 
	 * correttamente caricata (non-proxy) per consentire l'estrazione del Tracking Number.
	 * @param shipmentTrackingNumber il tracking number associato allo shipment. Viene utilizzato per 
	 * gestire il comportamento {@code FetchType.LAZY} associato alla relazione {@code ManyToOne} evitando 
	 * che il metodo chiamato alla chiusura di una transizione lanci una {@link LazyInitializationException}
	 */
	private void syncCacheAfterInsertOrUpdate(ShipmentItem savedShipmentItem, String shipmentTrackingNumber) {
		storeInCache(
			CaffeineCacheConfiguration.SHIPMENT_ITEM_BY_ITEM_UUID_CACHE,
			savedShipmentItem.getItemUUID(),
			savedShipmentItem,
			CacheOperation.SINGLE_RECORD
		);
		storeInCache(
			CaffeineCacheConfiguration.SHIPMENT_ITEM_BY_SHIPMENT_CACHE,
			shipmentTrackingNumber,
			savedShipmentItem,
			CacheOperation.LIST_RECORD
		);
	}
	
	/**
	 * Converte un Data Transfer Object (DTO) {@link ShipmentItemRequestDTO} nella rispettiva entità di dominio {@link ShipmentItem}.
	 * <p>
	 * Questo metodo non si limita a una semplice copia dei campi scalari, ma si occupa di:
	 * <ul>
	 * <li><b>Risoluzione delle relazioni:</b> Interroga i service layer dedicati per recuperare dal database
	 * le entità correlate esistenti. Nello specifico, recupera l'entità {@link Shipment} tramite il tracking number
	 * e l'entità {@link OnuNumber} tramite il codice ONU e il gruppo di imballaggio.</li>
	 * <li><b>Conversione dei tipi (Type Casting):</b> Converte le rappresentazioni testuali (String) provenienti 
	 * dal DTO nei rispettivi tipi fortemente tipizzati (Enum) del dominio, come {@link PackingGroup} e {@link UnitOfMeasure}.</li>
	 * </ul>
	 * </p>
	 * <p><b>Nota sull'implementazione:</b> Il metodo istanzia un nuovo oggetto {@link ShipmentItem} ad ogni chiamata.
	 * È quindi indicato per operazioni di creazione (POST) e non di aggiornamento (PUT/PATCH) di entità preesistenti.</p>
	 * @param dto l'oggetto di trasferimento dati contenente le informazioni dell'articolo della spedizione. 
	 * Non dovrebbe essere {@code null}.
	 * @return una nuova istanza dell'entità {@link ShipmentItem} completamente popolata e pronta per essere persistita.
	 */
	public ShipmentItem mapToEntity(ShipmentItemRequestDTO dto) {
		ShipmentItem item = new ShipmentItem();
		Shipment shipment = shipmentService.getByTrackingNumber(dto.shipmentTrackingNumber());
		OnuNumber onuNumber = onuNumberService.getByOnuCodeAndPackingGroupAndName(
			dto.onuNumberCode(),
			Enum.valueOf(PackingGroup.class, dto.packingGroup()),
			dto.name()
		);
		item.setShipment(shipment);
		item.setOnuNumber(onuNumber);
		item.setQuantity(dto.quantity());
		item.setUnitOfMeasure(Enum.valueOf(UnitOfMeasure.class, dto.unitOfMeasure()));
		return item;
	}
}
