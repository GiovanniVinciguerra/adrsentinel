package dev.vinciguerra.adrsentinel.db.onunumber;

import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import dev.vinciguerra.adrsentinel.db.AbstractGenericService;
import dev.vinciguerra.adrsentinel.db.CaffeineCacheConfiguration;
import dev.vinciguerra.adrsentinel.db.adrclass.AdrClass;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;

/**
 * Strato di Business Logic (Service Layer) dedicato alla gestione del Catalogo ADR (Numeri ONU).
 * <p>
 * <b>Profilo Architetturale (Read-Heavy & Bounded Data):</b><br>
 * A differenza delle entità transazionali (es. Spedizioni), il catalogo ADR ha un profilo di traffico 
 * asimmetrico: milioni di letture contro pochissime (o nulle) scritture. Per questo motivo, l'intera 
 * classe è avvolta in una corazza di caching aggressiva basata su Caffeine L1. 
 * Il database fisico viene interrogato solo a sistema freddo (Cold Start) o in caso di Cache Miss.
 * </p>
 * <p>
 * <b>Strategia di Consistenza (Write-Through):</b><br>
 * Le operazioni di mutazione (es. {@link #save(OnuNumber)}) implementano un pattern di aggiornamento 
 * sincrono multiplo. Quando il catalogo viene alterato, il Service aggiorna chirurgicamente tutte le 
 * regioni di cache collegate, evitando l'Eviction totale (Flush) e mantenendo calde le risposte per gli utenti.
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Service
public class OnuNumberService extends AbstractGenericService {
	private final OnuNumberRepository onuNumberRepository;
	
	/**
	 * Costruttore con Dependency Injection nativa di Spring.
	 * @param onuNumberRepository il livello di accesso ai dati fisici (Catalogo DB).
	 * @param cacheManager l'orchestratore dell'infrastruttura di memoria (iniettato nella superclasse).
	 */
	public OnuNumberService(OnuNumberRepository onuNumberRepository, CacheManager cacheManager) {
		super(cacheManager);
		this.onuNumberRepository = onuNumberRepository;
	}
	
	/**
	 * Recupera la definizione esatta di una singola materia pericolosa tramite il suo Codice ONU.
	 * <p>
	 * <b>Meccanismo di Caching:</b><br>
	 * Intercetta la chiamata e cerca la stringa {@code onuCode} nella regione di memoria dedicata. 
	 * Essendo una ricerca 1-a-1 altamente ricorrente durante l'inserimento delle spedizioni, 
	 * la latenza garantita in caso di Hit è O(1).
	 * </p>
	 * @param onuCode il codice di 4 cifre identificativo della materia (es. "1203").
	 * @return l'entità {@link OnuNumber} popolata in tutti i suoi campi.
	 * @throws ResourceNotFoundException se il codice inserito non esiste nel manuale ADR.
	 */
	@Cacheable(value = CaffeineCacheConfiguration.ONU_NUMBER_BY_ONU_CODE_CACHE, key = "#onuCode")
	public OnuNumber getByOnuCode(String onuCode) throws ResourceNotFoundException {
		logger.info("[DataBase CALL] Searching for the OnuNumber by onuCode: {}", onuCode);
		return onuNumberRepository.findByOnuCode(onuCode)
			.orElseThrow(() -> new ResourceNotFoundException("OnuNumber not found: " + onuCode));
	}
	
	/**
	 * Estrae il sottoinsieme di Numeri ONU raggruppati per lo stesso grado/tipo di pericolo (Kemler).
	 * @param kemlerCode il Numero di Identificazione del Pericolo (es. "33", "80").
	 * @return la lista finita e cachata delle materie associate a quel rischio.
	 */
	@Cacheable(value = CaffeineCacheConfiguration.ONU_NUMBER_BY_KEMLER_CODE_CACHE, key = "#kemlerCode")
	public List<OnuNumber> getByKemlerCode(String kemlerCode) {
		logger.info("[DataBase CALL] Searching for the OnuNumber by kemlerCode: {}", kemlerCode);
		return onuNumberRepository.findByKemlerCode(kemlerCode);
	}
	
	/**
	 * Estrae l'intera libreria di merci pericolose appartenenti a una macro-classe ADR.
	 * <p>
	 * <b>Ottimizzazione SpEL (Spring Expression Language):</b><br>
	 * Il parametro {@code key = "#adrClass.classCode"} estrae proattivamente la stringa primitiva 
	 * dall'oggetto complesso, utilizzandola come chiave di cache. Questo previene colli di bottiglia 
	 * legati alla serializzazione e all'implementazione dei metodi {@code equals()/hashCode()} dell'entità.
	 * </p>
	 * @param adrClass l'oggetto di dominio rappresentante la classe di pericolo richiesta.
	 * @return l'elenco completo e cachato dei Numeri ONU appartenenti alla classe.
	 */
	@Cacheable(value = CaffeineCacheConfiguration.ONU_NUMBER_BY_ADR_CLASS_CACHE, key = "#adrClass.classCode")
	public List<OnuNumber> getByAdrClass(AdrClass adrClass) {
		logger.info("[DataBase CALL] Searching for the OnuNumber by AdrClass classCode: {}", adrClass.getClassCode());
		return onuNumberRepository.findByAdrClass(adrClass);
	}
	
	/**
	 * Esporta l'intero catalogo ADR mondiale per il Client-Side Caching.
	 * <p>
	 * <b>Network & UI Optimization:</b><br>
	 * Consente al frontend (es. React/Angular) di scaricare in blocco, in una singola e leggera 
	 * chiamata compressa (GZIP), gli interi ~3500 record del manuale.
	 * La chiave di cache è una costante letterale globale garantita dall'infrastruttura.
	 * </p>
	 * @return l'intera collezione dei Numeri ONU presenti nel sistema.
	 */
	@Cacheable(value = CaffeineCacheConfiguration.ONU_NUMBER_ALL_CACHE, key = "'" + CaffeineCacheConfiguration.ONU_NUMBER_ALL_KEY + "'")
	public List<OnuNumber> getAllOnuNumber() {
		logger.info("[DataBase CALL] Search for OnuNumber (ALL)");
		return onuNumberRepository.findAll();
	}
	
	/**
	 * Registra o aggiorna i dettagli di una materia pericolosa nel database fisico.
	 * <p>
	 * <b>Sincronizzazione della Memoria (Multi-Region Write-Through):</b><br>
	 * A seguito del commit su RDBMS, questa operazione si fa carico di aggiornare 
	 * simultaneamente e in modo granulare tutte le quattro regioni di memoria in RAM 
	 * dipendenti da questo record. In questo modo si garantisce che una ricerca successiva 
	 * (per Codice, per Kemler, per Classe o Globale) restituisca immediatamente il dato aggiornato.
	 * </p>
	 * @param onuNumber l'entità nuova o modificata proveniente dal Controller.
	 * @return l'entità consolidata salvata sul disco fisico.
	 */
	@Transactional
	public OnuNumber save(OnuNumber newOnuNumber) {
		logger.info("[DataBase CALL] Saving new OnuNumber with onuCode: {}", newOnuNumber.getOnuCode());
		OnuNumber savedOnuNumber = onuNumberRepository.save(newOnuNumber);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { writeThroughCacheIntegrityOperation(savedOnuNumber); }
		});
		return savedOnuNumber;
	}
	
	/**
	 * Esegue un'operazione di sincronizzazione multidimensionale della cache applicativa 
	 * per l'entità Numero ONU (UN Number), applicando rigorosamente il pattern Write-Through.
	 * <p>
	 * A causa dell'alta frequenza di lettura e delle molteplici chiavi di ricerca associate 
	 * a una singola materia pericolosa, l'architettura di caching prevede diversi indici 
	 * in memoria. Questo metodo garantisce che tutti gli indici (chiavi singole e raggruppamenti) 
	 * vengano aggiornati simultaneamente, mantenendo la coerenza assoluta con il database.
	 * </p>
	 * <p>
	 * <b>Vincolo Transazionale:</b> Per prevenire la corruzione della memoria (ghost records) 
	 * in caso di fallimento o rollback della transazione SQL, l'invocazione di questo metodo 
	 * deve avvenire <b>esclusivamente</b> a valle di un commit completato con successo, tramite 
	 * la registrazione nella fase {@code afterCommit} del {@code TransactionSynchronizationManager}.
	 * </p>
	 * <p>
	 * <b>Flusso di Sincronizzazione (Propagazione su 4 Livelli):</b>
	 * Il metodo propaga l'entità appena salvata sulle seguenti strutture Caffeine:
	 * <ul>
	 * <li><b>1. Indice Primario (Codice ONU):</b> Inserisce o sovrascrive il record singolo 
	 * nella cache {@code ONU_NUMBER_BY_ONU_CODE_CACHE} per le ricerche puntuali (O(1)).</li>
	 * <li><b>2. Indice di Pericolo (Codice Kemler):</b> Intercetta la lista in memoria associata 
	 * al Codice Kemler e vi accoda l'entità ({@code ONU_NUMBER_BY_KEMLER_CODE_CACHE}).</li>
	 * <li><b>3. Indice Categoriale (Classe ADR):</b> Estrae il {@code classCode} dalla relazione 
	 * {@link AdrClass} e accoda l'entità alla lista delle materie compatibili 
	 * ({@code ONU_NUMBER_BY_ADR_CLASS_CACHE}).</li>
	 * <li><b>4. Collezione Globale:</b> Aggiorna la lista omnicomprensiva utilizzata 
	 * tipicamente per popolare dropdown o tabelle massicce lato frontend ({@code ONU_NUMBER_ALL_CACHE}).</li>
	 * </ul>
	 * Questo approccio "aggressivo" in scrittura annulla la necessità di invalidare (evict) 
	 * le cache, risparmiando al database relazionale il costo di ricostruire intere liste 
	 * tramite pesanti query con JOIN.
	 * </p>
	 * @param savedOnuNumber l'istanza dell'entità {@link OnuNumber} persistita con successo 
	 * nel database. L'oggetto deve essere nello stato "Managed" e avere la relazione padre 
	 * ({@link AdrClass}) correttamente valorizzata per permettere l'estrazione delle chiavi composte.
	 */
	private void writeThroughCacheIntegrityOperation(OnuNumber savedOnuNumber) {
		AdrClass adrClass = savedOnuNumber.getAdrClass();
		storeInCache(
			CaffeineCacheConfiguration.ONU_NUMBER_BY_ONU_CODE_CACHE,
			savedOnuNumber.getOnuCode(),
			savedOnuNumber,
			CacheOperation.SINGLE_RECORD
		);
		storeInCache(
			CaffeineCacheConfiguration.ONU_NUMBER_BY_KEMLER_CODE_CACHE,
			savedOnuNumber.getKemlerCode(),
			savedOnuNumber,
			CacheOperation.LIST_RECORD
		);
		storeInCache(
			CaffeineCacheConfiguration.ONU_NUMBER_BY_ADR_CLASS_CACHE,
			adrClass.getClassCode(),
			savedOnuNumber,
			CacheOperation.LIST_RECORD
		);
		storeInCache(
			CaffeineCacheConfiguration.ONU_NUMBER_ALL_CACHE,
			CaffeineCacheConfiguration.ONU_NUMBER_ALL_KEY,
			savedOnuNumber,
			CacheOperation.LIST_RECORD
		);
	}
}
