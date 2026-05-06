package dev.vinciguerra.adrsentinel.db;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Superclasse astratta che centralizza la gestione manuale della cache in memoria.
 * <p>
 * Implementa il pattern <b>Template Method / Base Class</b> per fornire ai Service specializzati
 * (es. {@code AdrClassService}, {@code UnNumberService}) metodi standardizzati, sicuri e
 * <i>type-safe</i> (tramite Java Generics) per l'inserimento, l'aggiornamento e la rimozione
 * manuale di oggetti o liste all'interno del {@link CacheManager} di Spring Boot.
 * </p>
 * <h3>Design Principles:</h3>
 * <ul>
 * <li><b>DRY (Don't Repeat Yourself):</b> Evita la duplicazione dei check per la nullità della cache e della chiave.</li>
 * <li><b>Single Responsibility (per Enum):</b> Separa concettualmente le operazioni su record singoli da quelle su collezioni.</li>
 * <li><b>Lazy Loading Support:</b> Rispetta il caricamento ritardato delle liste: se una lista non è ancora in RAM, non la crea forzatamente.</li>
 * </ul>
 *
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
public abstract class AbstractGenericService {
	/**
     * Enum che definisce le possibili strategie operative quando si interagisce con una cache.
     * Serve come "libretto di istruzioni" per i metodi di update e delete.
     */
	public enum CacheOperation {
		/**
         * Operazione su un record singolo (relazione 1:1). 
         * Sovrascrive (o elimina) direttamente il valore associato alla chiave senza ulteriori controlli.
         */
		SINGLE_RECORD,
		/**
         * Operazione su una collezione (relazione 1:N). 
         * Estrae la lista esistente, modifica l'elemento e reinserisce la lista aggiornata.
         * Se la lista non esiste in memoria (non ancora caricata dal DB), l'operazione viene ignorata.
         */
		LIST_RECORD
	}
	
	/** Il gestore centralizzato della cache iniettato da Spring. */
	protected final CacheManager cacheManager;
	/** Logger dinamico configurato sulla classe figlia (a runtime 'getClass()' restituirà la classe concreta).*/
	protected final Logger logger = LoggerFactory.getLogger(getClass());
	
	/**
     * Costruttore protetto utilizzato per la Dependency Injection tramite le sottoclassi.
     * @param cacheManager L'istanza del CacheManager configurata nel contesto Spring.
     */
	protected AbstractGenericService(CacheManager cacheManager) {
		this.cacheManager = cacheManager;
	}
	
	/**
	 * Motore di sincronizzazione (Write-Through) per l'inserimento di dati in Cache (Caffeine).
	 * <p>
	 * <b>Policy di Dominio: Null-Safety</b><br>
	 * Questo metodo implementa una rigorosa politica di esclusione dei valori nulli. Se il valore 
	 * da persistere è {@code null}, l'operazione viene bloccata e loggata, proteggendo la RAM 
	 * dalla corruzione di stato (no "buchi neri" nelle liste o nelle chiavi singole).
	 * </p>
	 * <p>
	 * <b>Logica di Upsert (Update or Insert) per le Liste:</b><br>
	 * Nel caso di {@code LIST_RECORD}, il metodo non si limita ad accodare il dato. 
	 * Sfrutta il metodo {@link Object#equals(Object)} dell'entità per verificare se una versione 
	 * precedente dell'oggetto (stessa Business Key) è già presente in memoria. In caso affermativo, 
	 * la rimuove e la sostituisce con l'istanza aggiornata. Questo previene i duplicati e risolve 
	 * alla radice il problema dello "Stale Data" (dati vecchi) dopo un'operazione di UPDATE.
	 * </p>
	 * <p>
	 * <b>Prevenzione della Corruzione (Liste Parziali):</b><br>
	 * Nel caso di {@code LIST_RECORD}, se la lista madre non è presente in memoria (Cache Miss), 
	 * il metodo si astiene intenzionalmente dall'inizializzarla con il singolo elemento. Questo 
	 * presidio architetturale è vitale: se la RAM venisse popolata con una "lista parziale", 
	 * alla successiva richiesta di lettura (es. un {@code findAll}), il Proxy di Spring Cache 
	 * intercetterebbe la chiave valorizzata e restituirebbe solo quell'unico elemento, bypassando 
	 * completamente l'interrogazione al database (Hibernate) e nascondendo tutti i restanti record. 
	 * Lasciando deliberatamente la cache vuota, si garantisce un Cache Miss legittimo che forzerà 
	 * il ricaricamento sicuro e completo dell'intera lista dal DB.
	 * </p>
	 * @param <T> Il tipo di dato dell'entità gestita.
	 * @param cacheName Il nome della regione di cache in cui operare.
	 * @param key La chiave univoca di accesso alla cache.
	 * @param value L'oggetto da inserire o aggiornare (non ammesso null).
	 * @param cacheOperation La strategia operativa (SINGLE_RECORD o LIST_RECORD).
	 * @throws IllegalArgumentException se i parametri operativi di base (key o cacheOperation) sono nulli.
	 */
	protected <T> void storeInCache(String cacheName, Object key, T value, CacheOperation cacheOperation) throws IllegalArgumentException {
		Cache cache = cacheManager.getCache(cacheName);
		ifNullThrowException(key, "Cache key cannot be null for save");
		ifNullThrowException(cacheOperation, "[SAVE] No cache operation, value was null");
		if(!canProceedWithCacheOperation(cache, cacheName, value, "SAVE")) 
			return;
		switch(cacheOperation) {
			case SINGLE_RECORD:
				cache.put(key, value);
				logger.info("Cache [{}] - Single record inserted for key: {}", cacheName, key);
				break;
			case LIST_RECORD:
				Cache.ValueWrapper cacheValue = cache.get(key);
				if(cacheValue != null && cacheValue.get() != null) {
					@SuppressWarnings("unchecked")
					List<T> temp = (List<T>) cacheValue.get();
					boolean isAlreadyInList = temp.contains(value);
					if(isAlreadyInList)
						temp.remove(value);
					temp.add(value);
					cache.put(key, temp);
					logger.info("Cache [{}] - New item appended to list for key: {}", cacheName, key);
				} else
					logger.warn("Cache [{}] - List for key {} not yet in memory. No append performed.", cacheName, key);
				break;
			default:
				logger.warn("Unsupported cache operation: {}", cacheOperation);
		}
	}
	
	/**
	 * Orchestratore avanzato per le operazioni di UPDATE in cache, dotato di gestione automatica 
	 * del "Key Shift" (Mutazione della Chiave) e meccanismo di "Fail-Safe Eviction".
	 * <p><b>Contesto Architetturale:</b></p>
	 * Durante l'aggiornamento di un'entità, i campi utilizzati come chiavi di raggruppamento 
	 * (es. la data di spedizione) possono subire variazioni. Questo metodo agisce come un 
	 * intercettore intelligente per garantire la <i>Strict Consistency</i> tra Database e RAM, 
	 * prevenendo attivamente la permanenza di dati fantasma ("Stale Data") nelle vecchie 
	 * locazioni di memoria.
	 * <p><b>Flusso Operativo e Pattern Applicati:</b></p>
	 * <ol>
	 * <li><b>Validazione Iniziale (Fail-Fast):</b> Verifica rigorosa anti-null per entrambe le chiavi 
	 * (necessarie per il confronto).</li>
	 * <li><b>Rilevamento Key Shift:</b> Valuta l'espressione {@code !oldKey.equals(key)}. 
	 * Se rileva una mutazione, innesca preventivamente {@link #deleteFromCache} per epurare 
	 * il record obsoleto dalla vecchia allocazione.</li>
	 * <li><b>Delega Upsert:</b> Invoca il motore di base (a 4 parametri) per eseguire l'inserimento 
	 * (o l'aggiornamento) dell'entità nella nuova allocazione di memoria.</li>
	 * <li><b>Fail-Safe Eviction (Protezione della RAM):</b> In caso di fallimento della catena 
	 * operativa, il sistema intercetta l'eccezione e adotta una strategia di distruzione difensiva. 
	 * Esegue l'<i>eviction</i> forzata sia per la vecchia che per la nuova chiave, riportando 
	 * la memoria a uno stato pulito. Alla successiva richiesta di lettura, un Cache Miss forzerà 
	 * il sistema a rileggere il dato coerente e allineato direttamente dal database.</li>
	 * </ol>
	 * @param <T> Il tipo generico dell'oggetto da persistere in memoria.
	 * @param cacheName L'identificativo testuale della regione di cache in cui operare.
	 * @param key La nuova chiave (attuale) su cui l'oggetto deve essere mappato. Non ammette {@code null}.
	 * @param oldKey La chiave originaria (pre-aggiornamento) dell'oggetto. È il parametro trigger 
	 * vitale per innescare la logica di Key Shift. Non ammette {@code null}.
	 * @param value L'istanza dell'oggetto aggiornato da persistere/sostituire in cache.
	 * @param cacheOperation La strategia operativa (es. {@code SINGLE_RECORD} per sovrascrittura diretta, 
	 * {@code LIST_RECORD} per logica di Upsert all'interno di una collezione).
	 * @throws IllegalArgumentException se le chiavi passate al metodo in fase di invocazione sono nulle 
	 * (le eccezioni interne al blocco try-catch vengono invece inghiottite dal meccanismo di Fail-Safe).
	 */
	protected <T> void storeInCache(String cacheName, Object key, Object oldKey, T value, CacheOperation cacheOperation) throws IllegalArgumentException {
		ifNullThrowException(oldKey, "Old cache key cannot be null for update");
		ifNullThrowException(key, "New cache key cannot be null for update");
		try {
			if(!oldKey.equals(key)) {
				logger.info("UPDATE - Key Shift detected ({} -> {}). Triggering cleanup for old key.", oldKey, key);
				deleteFromCache(
					cacheName,
					oldKey,
					value,
					cacheOperation
				);
			}
			storeInCache(
				cacheName,
				key,
				value,
				cacheOperation
			);
		} catch(RuntimeException error) {
			logger.error(
				"[CACHE FATAL] Error during cache Upsert [{}]. Risk of data corruption. Forcing eviction for keys {} and {} to guarantee cache consistency. Error: {}",
				cacheName,
				oldKey,
				key,
				error.getMessage()
			);
			Cache cache = cacheManager.getCache(cacheName);
			if (cache != null) {
				cache.evictIfPresent(oldKey);
				cache.evictIfPresent(key);
			}
		}
	}
	
	/**
	 * Esegue la rimozione controllata e selettiva di un dato dalla cache applicativa (Caffeine).
	 * <p>
	 * Questo metodo funge da motore centrale per l'invalidazione della memoria, progettato per 
	 * essere invocato a valle di una cancellazione fisica sul database (pattern Delete-Through). 
	 * Gestisce in totale autonomia la differenza tra la distruzione di un record singolo e 
	 * l'estrazione di un elemento da una collezione preesistente.
	 * </p>
	 * * <p><b>Flusso di Validazione (Fail-Fast e Pre-Check):</b></p>
	 * <ul>
	 * <li><b>Controllo Parametri Obbligatori:</b> Il metodo blocca immediatamente l'esecuzione 
	 * lanciando una {@link IllegalArgumentException} se la chiave ({@code key}) o l'operazione 
	 * ({@code cacheOperation}) risultano assenti (null).</li>
	 * <li><b>Validazione di Contesto:</b> Delega un controllo di ammissibilità al metodo 
	 * {@code canProceedWithCacheOperation}. Se questo restituisce {@code false} (es. cache inesistente 
	 * o valore nullo non processabile), il metodo interrompe l'esecuzione in modo pulito ({@code return}) 
	 * senza lanciare eccezioni.</li>
	 * </ul>
	 * * <p><b>Strategie Operative (Switch Logic):</b></p>
	 * <ul>
	 * <li><b>{@code SINGLE_RECORD}:</b> Esegue un'evizione diretta tramite {@code cache.evictIfPresent(key)}.
	 * Interroga il motore di cache per sapere se la chiave era effettivamente allocata in RAM, 
	 * permettendo di discriminare nei log tra una pulizia reale (INFO) e un'operazione a vuoto (WARN).</li>
	 * <li><b>{@code LIST_RECORD}:</b> Adotta un approccio di "Rimozione Chirurgica in Mutazione":
	 * <ol>
	 * <li>Tenta l'estrazione della lista dalla cache. Se la lista non esiste (Cache Miss), 
	 * interrompe l'operazione loggando un WARN, evitando di inizializzare strutture vuote.</li>
	 * <li>Se la lista esiste, effettua un cast a {@code List<T>} e invoca {@code temp.remove(value)}. 
	 * <i>Nota architetturale:</i> Questo passaggio fa affidamento sull'implementazione del 
	 * metodo {@link Object#equals(Object)} dell'entità per individuare l'esatta occorrenza da scartare.</li>
	 * <li>Solo se la rimozione ha avuto successo ({@code isRemoved == true}), il metodo sovrascrive 
	 * la cache chiamando {@code cache.put(key, temp)} per persistere lo stato aggiornato della lista. 
	 * Se l'elemento non viene trovato nella lista, la cache non subisce mutazioni (WARN log).</li>
	 * </ol>
	 * </li>
	 * </ul>
	 * * <p><b>Tracciabilità e Feedback (Observability):</b><br>
	 * Il metodo non è mai silente. Ogni ramo condizionale produce un output di logging mirato, 
	 * indicando chiaramente il nome della cache, la chiave coinvolta e l'esito dell'operazione 
	 * (successo, chiave non trovata, elemento non trovato nella lista, o operazione non supportata).
	 * </p>
	 * @param <T> Il tipo generico dell'entità o del DTO da gestire.
	 * @param cacheName Il nome identificativo della cache target (es. configurata nel CacheManager).
	 * @param key La chiave di lookup associata al dato da invalidare (singolo) o alla lista madre.
	 * @param value L'oggetto target da rimuovere. Fondamentale per l'operazione {@code LIST_RECORD} 
	 * in quanto viene passato al metodo {@code List.remove()}.
	 * @param cacheOperation L'enumeratore che istruisce il motore sulla struttura dati da manipolare 
	 * ({@code SINGLE_RECORD} o {@code LIST_RECORD}).
	 * @throws IllegalArgumentException Se {@code key} o {@code cacheOperation} vengono passati come nulli.
	 */
	protected <T> void deleteFromCache(String cacheName, Object key, T value, CacheOperation cacheOperation) throws IllegalArgumentException {
		Cache cache = cacheManager.getCache(cacheName);
		ifNullThrowException(key, "Cache key cannot be null for deletion");
		ifNullThrowException(cacheOperation, "[DELETE] No cache operation, value was null");
		if(!canProceedWithCacheOperation(cache, cacheName, value, "DELETE")) 
			return;
		switch(cacheOperation) {
			case SINGLE_RECORD:
				boolean isKeyEvicted = cache.evictIfPresent(key);
				if(isKeyEvicted)
					logger.info("Cache [{}] - Single record deleted for key: {}", cacheName, key);
				else
					logger.warn("Cache [{}] - Key not found: {}. Cache unchanged.", cacheName, key);
				break;
			case LIST_RECORD:
				Cache.ValueWrapper cacheValue = cache.get(key);
				if(cacheValue != null && cacheValue.get() != null) {
					@SuppressWarnings("unchecked")
					List<T> temp = (List<T>) cacheValue.get();
					boolean isRemoved = temp.remove(value);
					if(isRemoved) {
						cache.put(key, temp);
						logger.info("Cache [{}] - Item successfully removed from list for key: {}", cacheName, key);
					} else
						logger.warn("Cache [{}] - Item not found in list for key: {}. Cache unchanged.", cacheName, key);
				} else
					logger.warn("Cache [{}] - List for key {} not yet in memory. No delete performed.", cacheName, key);
				break;
			default:
				logger.warn("Unsupported cache operation: {}", cacheOperation);
		}
	}
	
	/**
	 * Metodo helper dichiarativo per le Guard Clauses.
	 * <p>
	 * <b>Pattern Architetturale (Assertion / Fail-Fast):</b><br>
	 * Centralizza la logica di validazione degli argomenti obbligatori. Se l'input è nullo, 
	 * interrompe immediatamente l'esecuzione lanciando un'eccezione chiara e contestualizzata, 
	 * proteggendo i metodi chiamanti da NullPointerException tardivi e difficili da debuggare.
	 * </p>
	 *
	 * @param valueToCheck l'oggetto da validare.
	 * @param message il messaggio di errore da iniettare nell'eccezione in caso di fallimento.
	 * @throws IllegalArgumentException se l'oggetto verificato risulta nullo.
	 */
	private void ifNullThrowException(Object valueToCheck, String message) throws IllegalArgumentException {
		if(valueToCheck == null)
			throw new IllegalArgumentException(message);
	}
	
	/**
	 * Boolean Guard Method per validare silenziosamente lo stato della Cache e del Valore.
	 * Se la cache non esiste o il valore è nullo, logga il warning appropriato e restituisce false, 
	 * indicando al chiamante di interrompere pacificamente l'operazione (Graceful Degradation).
	 *
	 * @param cache l'istanza della cache recuperata dal CacheManager.
	 * @param cacheName il nome della regione di cache (usato per i log).
	 * @param value l'oggetto da validare (policy Null-Safety).
	 * @param operationLabel etichetta dell'operazione (es. "SAVE", "DELETE") per contestualizzare i log.
	 * @return true se l'operazione può procedere, false se deve essere abortita.
	 */
	private boolean canProceedWithCacheOperation(Cache cache, String cacheName, Object value, String operationLabel) {
		if(cache == null) {
			logger.warn("Cache [{}] not found (no {} operation performed)", cacheName, operationLabel);
			return false;
		} else if(value == null) {
			logger.warn("Cache [{}] - Value was null. Cache unchanged during {}.", cacheName, operationLabel);
			return false;
		} else
			return true;
	}
}
