package dev.vinciguerra.adrsentinel.db.onunumber;

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
import dev.vinciguerra.adrsentinel.db.adrclass.AdrClass;
import dev.vinciguerra.adrsentinel.db.adrclass.AdrClassService;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.PackingGroup;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.PhysicalState;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.TunnelRestriction;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;
import dev.vinciguerra.adrsentinel.web.dto.onunumber.OnuNumberRequestDTO;

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
	private final AdrClassService adrClassService;
	
	/**
	 * Costruttore principale per l'iniezione delle dipendenze (Dependency Injection) 
	 * e l'inizializzazione del servizio di orchestrazione delle Onu Number ADR.
	 * <p>
	 * <b>Dettagli Architetturali:</b>
	 * <ul>
	 * <li><b>Ereditarietà e Gestione Memoria:</b> L'istanza del {@link CacheManager} viene 
	 * passata al costruttore della superclasse tramite {@code super()}. Questo approccio 
	 * abilita l'utilizzo nativo dei motori centralizzati di Write-Through e Delete-Through 
	 * (es. i metodi operativi di inserimento ed evizione chirurgica dalla RAM).</li>
	 * <li><b>Service-to-Service Communication:</b> Per la risoluzione delle entità anagrafiche, 
	 * viene intenzionalmente iniettato il {@link AdrClassService} in luogo del suo Repository. 
	 * Questa configurazione forza l'attraversamento dei Proxy di Spring, garantendo 
	 * lo sfruttamento della cache di primo livello (Cache Hit) e la centralizzazione 
	 * della logica di validazione (es. controlli di disattivazione logica o eccezioni custom), 
	 * mantenendo rigidi i confini di dominio (Domain Boundaries).</li>
	 * </ul>
	 * </p>
	 * @param onuNumberRepository L'interfaccia JPA di accesso ai dati persistenti, 
	 * dedicata esclusivamente alle operazioni di mutazione e ricerca delle Onu Number.
	 * @param adrClassService Il servizio di dominio delle Classi ADR, utilizzato per 
	 * eseguire il lookup ottimizzato (tramite RAM) e sicuro delle entità genitore durante il 
	 * mapping dei DTO.
	 * @param cacheManager L'istanza del gestore centrale delle cache fornita dal contesto Spring Boot, 
	 * delegata alla classe astratta base.
	 */
	public OnuNumberService(OnuNumberRepository onuNumberRepository, AdrClassService adrClassService, CacheManager cacheManager) {
		super(cacheManager);
		this.onuNumberRepository = onuNumberRepository;
		this.adrClassService = adrClassService;
	}
	
	/**
	 * Esegue un lookup (ricerca puntuale) per recuperare l'anagrafica normativa di una materia 
	 * pericolosa utilizzando la sua chiave di business composita (Codice ONU + Gruppo di Imballaggio).
	 * <p><b>Contesto di Dominio (Chiave Composita ADR):</b></p>
	 * Nella normativa internazionale ADR, il solo Codice ONU (es. 1263 per "Pitture") spesso non è 
	 * sufficiente per identificare univocamente le restrizioni di trasporto. La stessa sostanza può 
	 * presentare gradi di pericolo chimico differenti, classificati tramite il Gruppo di Imballaggio 
	 * (I = Alto, II = Medio, III = Basso). Questo metodo interroga la base dati utilizzando questa 
	 * esatta combinazione per garantire l'estrazione della corretta scheda normativa (esenzioni, 
	 * etichette, restrizioni tunnel).
	 * <p><b>Design Pattern e Osservabilità (Fail-Fast & Telemetry):</b></p>
	 * <ul>
	 * <li><b>Observability:</b> Il metodo traccia l'accesso al database tramite un logger strategico 
	 * ({@code [DataBase CALL]}). Questo è vitale in ambienti Enterprise per monitorare le performance 
	 * (es. tracciare i <i>Cache Miss</i>) e agevolare il debugging distribuito.</li>
	 * <li><b>Fail-Fast & Exception Translation:</b> Utilizzando il costrutto funzionale {@code orElseThrow}, 
	 * il metodo converte elegantemente un potenziale stato di assenza ({@code Optional.empty}) 
	 * in un'eccezione di dominio esplicita ({@link ResourceNotFoundException}). Questo impedisce 
	 * la propagazione di valori nulli ai layer superiori (Controller), demandando la gestione 
	 * dell'errore (es. traduzione in HTTP 404) a un gestore centralizzato ({@code @ControllerAdvice}).</li>
	 * </ul>
	 * @param onuCode Il codice ONU a 4 cifre identificativo della materia (es. "1202").
	 * @param packingGroup L'enumeratore rappresentante il grado di pericolo e il gruppo d'imballaggio.
	 * @return L'entità {@link OnuNumber} completamente idratata dal database.
	 * @throws ResourceNotFoundException Se nessuna materia attiva corrisponde all'accoppiata fornita, 
	 * interrompendo immediatamente il flusso di esecuzione (Fail-Fast).
	 */
	@Cacheable(value = CaffeineCacheConfiguration.ONU_NUMBER_BY_ONU_CODE_AND_PACKING_GROUP_CACHE, key = "#onuCode + '-' + #packingGroup.name()")
	public OnuNumber getByOnuCodeAndPackingGroup(String onuCode, PackingGroup packingGroup) throws ResourceNotFoundException {
		logger.info("[DataBase CALL] Searching for the OnuNumber by onuCode and packingGroup: {} {}", onuCode, packingGroup);
		return onuNumberRepository.findByOnuCodeAndPackingGroup(onuCode, packingGroup)
			.orElseThrow(
				() -> new ResourceNotFoundException(
					new StringBuilder()
						.append("OnuNumber not found: {")
						.append(onuCode)
						.append(", ")
						.append(packingGroup)
						.append("}")
						.toString()
				)
			);
	}
	
	/**
	 * Recupera l'elenco delle varianti di Numeri ONU associate a uno specifico codice a 4 cifre, 
	 * orchestrando l'accesso ai dati tramite un pattern architetturale di Read-Through Cache.
	 * <p>
	 * <b>Strategia di Caching (Proxy Interception):</b><br>
	 * L'annotazione {@code @Cacheable} delega a Spring l'intercettazione dell'invocazione. 
	 * Utilizzando la SpEL (Spring Expression Language) {@code #onuCode} come chiave di 
	 * indirizzamento univoca all'interno della regione {@code ONU_NUMBER_BY_ONU_CODE_CACHE}:
	 * <ul>
	 * <li><b>Cache Hit:</b> Se il dato risiede già in memoria RAM (Caffeine), il corpo del metodo 
	 * viene del tutto ignorato. La lista viene restituita al chiamante con latenza quasi nulla.</li>
	 * <li><b>Cache Miss:</b> In assenza della chiave, il Proxy esegue il blocco di codice, 
	 * interroga il database relazionale, idrata la cache con il risultato e conclude l'operazione.</li>
	 * </ul>
	 * </p>
	 * <p>
	 * <b>Osservabilità e Diagnostica (Observability):</b><br>
	 * La stampa del log (livello INFO) funge da "sentinella" prestazionale. Essendo eseguita 
	 * unicamente in caso di Cache Miss, fornisce una prova visiva immediata nei log di produzione 
	 * di quando il sistema sta effettivamente sostenendo il costo di una query su PostgreSQL.
	 * </p>
	 * <p>
	 * <b>Dinamiche di Dominio (Gestione Varianti ADR):</b><br>
	 * Il tipo di ritorno {@link List} modella la reale complessità normativa: un singolo codice 
	 * identificativo (es. "1993") può diramarsi in molteplici record fisici differenziati 
	 * per Gruppo di Imballaggio o Disposizioni Speciali.
	 * </p>
	 * @param onuCode La Business Key (es. "1203", "1993") utilizzata come filtro di ricerca 
	 * sul database e come chiave di memorizzazione nel motore di cache.
	 * @return Una {@link List} contenente le entità {@link OnuNumber} corrispondenti. Restituisce 
	 * una lista vuota qualora il codice non sia censito.
	 */
	@Cacheable(value = CaffeineCacheConfiguration.ONU_NUMBER_BY_ONU_CODE_CACHE, key = "#onuCode")
	public List<OnuNumber> getByOnuCode(String onuCode) {
		logger.info("[DataBase CALL] Searching for the OnuNumber by onuCode: {}", onuCode);
		return onuNumberRepository.findByOnuCode(onuCode);
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
	 * Recupera la lista dei Numeri ONU associati a una specifica Classe di Pericolo ADR, 
	 * implementando il pattern architetturale Read-Through Cache.
	 * <p>
	 * <b>Strategia di Caching (Proxy Interception):</b><br>
	 * Grazie all'annotazione {@code @Cacheable}, questo metodo viene avvolto da un Proxy di Spring. 
	 * All'invocazione, il framework verifica la presenza della chiave (risolta tramite SpEL 
	 * {@code #adrClassCode}) all'interno della regione {@code ONU_NUMBER_BY_ADR_CLASS_CACHE}:
	 * <ul>
	 * <li><b>Cache Hit:</b> Se la lista è già in RAM, il metodo originale <i>non viene eseguito</i>. 
	 * La lista viene restituita istantaneamente al chiamante a costo zero per il database.</li>
	 * <li><b>Cache Miss:</b> Se la chiave è assente, il metodo procede con l'esecuzione, 
	 * interroga il database relazionale, salva il risultato in cache e infine lo restituisce.</li>
	 * </ul>
	 * </p>
	 * <p>
	 * <b>Osservabilità e Diagnostica (Observability):</b><br>
	 * La stampa del log (livello INFO) all'interno del corpo del metodo svolge un ruolo diagnostico 
	 * cruciale. Poiché il corpo del metodo viene ignorato in caso di Cache Hit, la presenza di 
	 * questo log in console funge da prova inequivocabile di un Cache Miss (e della conseguente 
	 * query fisica su PostgreSQL).
	 * </p>
	 * <p>
	 * <b>Accesso ai Dati (Property Traversal):</b><br>
	 * Il delegato al livello di persistenza ({@code onuNumberRepository.findByAdrClass_classCode}) 
	 * sfrutta in modo sicuro l'operatore di navigazione di Spring Data (underscore {@code _}) 
	 * per attraversare la relazione {@code @ManyToOne} ed estrarre i record basandosi sulla 
	 * Business Key della classe padre, garantendo una query di JOIN ottimizzata da Hibernate.
	 * </p>
	 * @param adrClassCode il codice alfanumerico identificativo della Classe ADR (es. "3", "8", "6.1"). 
	 * Tale parametro funge da parametro di ricerca per la query e da chiave esatta di indirizzamento 
	 * per il motore di cache Caffeine.
	 * @return una {@link List} contenente le entità {@link OnuNumber} associate alla classe. 
	 * Restituisce una lista vuota se il database non contiene alcun numero ONU per la classe specificata.
	 */
	@Cacheable(value = CaffeineCacheConfiguration.ONU_NUMBER_BY_ADR_CLASS_CACHE, key = "#adrClassCode")
	public List<OnuNumber> getByAdrClass(String adrClassCode) {
		logger.info("[DataBase CALL] Searching for the OnuNumber by AdrClass classCode: {}", adrClassCode);
		return onuNumberRepository.findByAdrClass_classCode(adrClassCode);
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
		final String adrClassCode = savedOnuNumber.getAdrClass().getClassCode();
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterInsert(savedOnuNumber, adrClassCode); }
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
	 * <li><b>1. Indice Primario (Codice ONU + Codice Imballaggio):</b> Inserisce o sovrascrive il record 
	 * nella cache {@code ONU_NUMBER_BY_ONU_CODE_AND_PACKING_GROUP_CACHE} per la ricerca puntuale (O(1)). </li>
	 * <li><b>2. Indice Primario (Codice ONU):</b> Intercetta la lista in memoria associata al Codice ONU 
	 * e vi accoda l'entità ({@code ONU_NUMBER_BY_ONU_CODE_CACHE})
	 * <li><b>3. Indice di Pericolo (Codice Kemler):</b> Intercetta la lista in memoria associata 
	 * al Codice Kemler e vi accoda l'entità ({@code ONU_NUMBER_BY_KEMLER_CODE_CACHE}).</li>
	 * <li><b>4. Indice Categoriale (Classe ADR):</b> Estrae il {@code classCode} dalla relazione 
	 * {@link AdrClass} e accoda l'entità alla lista delle materie compatibili 
	 * ({@code ONU_NUMBER_BY_ADR_CLASS_CACHE}).</li>
	 * <li><b>5. Collezione Globale:</b> Aggiorna la lista omnicomprensiva utilizzata 
	 * tipicamente per popolare dropdown o tabelle massicce lato frontend ({@code ONU_NUMBER_ALL_CACHE}).</li>
	 * </ul>
	 * Questo approccio "aggressivo" in scrittura annulla la necessità di invalidare (evict) 
	 * le cache, risparmiando al database relazionale il costo di ricostruire intere liste 
	 * tramite pesanti query con JOIN.
	 * </p>
	 * @param savedOnuNumber l'istanza dell'entità {@link OnuNumber} persistita con successo 
	 * nel database. L'oggetto deve essere nello stato "Managed" e avere la relazione padre 
	 * ({@link AdrClass}) correttamente valorizzata per permettere l'estrazione delle chiavi composte.
	 * @param adrClassCode il codice classe adr associato allo numero onu. Viene utilizzato per 
	 * gestire il comportamento {@code FetchType.LAZY} associato alla relazione {@code ManyToOne} evitando 
	 * che il metodo chiamato alla chiusura di una transizione lanci una {@link LazyInitializationException}
	 */
	private void syncCacheAfterInsert(OnuNumber savedOnuNumber, String adrClassCode) {
		storeInCache(
			CaffeineCacheConfiguration.ONU_NUMBER_BY_ONU_CODE_AND_PACKING_GROUP_CACHE,
			savedOnuNumber.getOnuCode() + "'-'" + savedOnuNumber.getPackingGroup().name(),
			savedOnuNumber,
			CacheOperation.SINGLE_RECORD
		);
		storeInCache(
			CaffeineCacheConfiguration.ONU_NUMBER_BY_ONU_CODE_CACHE,
			savedOnuNumber.getOnuCode(),
			savedOnuNumber,
			CacheOperation.LIST_RECORD
		);
		storeInCache(
			CaffeineCacheConfiguration.ONU_NUMBER_BY_KEMLER_CODE_CACHE,
			savedOnuNumber.getKemlerCode(),
			savedOnuNumber,
			CacheOperation.LIST_RECORD
		);
		storeInCache(
			CaffeineCacheConfiguration.ONU_NUMBER_BY_ADR_CLASS_CACHE,
			adrClassCode,
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
	
	/**
	 * Converte un Data Transfer Object (DTO) in ingresso in una nuova entità di dominio {@link OnuNumber}.
	 * <p>
	 * Questo metodo funge da strato di traduzione (Mapper / Anti-Corruption Layer) tra il contratto 
	 * API "piatto" esposto al frontend e il complesso modello relazionale e tipizzato richiesto 
	 * dal motore di persistenza JPA/Hibernate.
	 * </p>
	 * <p>
	 * <b>Flusso di Orchestrazione e Idratazione:</b>
	 * <ul>
	 * <li><b>Risoluzione della Relazione (Lookup):</b> Il metodo interroga il {@code adrClassService} 
	 * utilizzando la Business Key ({@code adrClassCode}). Questo passaggio garantisce che il numero ONU 
	 * venga agganciato a un'entità {@link AdrClass} nello stato "Managed" (supervisionata da Hibernate), 
	 * delegando la ricerca al Service Layer per beneficiare appieno dell'infrastruttura di Caching.</li>
	 * <li><b>Type-Safety e Parsing delle Enumerazioni:</b> I campi ricevuti come primitive testuali 
	 * (String) dal payload JSON vengono convertiti in enumerazioni Java fortemente tipizzate 
	 * (es. {@link PhysicalState}, {@link PackingGroup}, {@link TunnelRestriction}). L'invocazione di 
	 * {@code Enum.valueOf()} risulta intrinsecamente sicura (nessun rischio di {@code IllegalArgumentException}) 
	 * in quanto l'esattezza lessicale delle stringhe è già stata garantita a monte dalle annotazioni di 
	 * Edge Validation applicate sul DTO.</li>
	 * <li><b>Mappatura Diretta:</b> I dati anagrafici base (codice ONU, denominazione, categoria di trasporto) 
	 * vengono riversati direttamente nell'entità.</li>
	 * </ul>
	 * </p>
	 * @param dto L'oggetto immutabile di trasferimento dati (Flat Record DTO) contenente il payload 
	 * validato in fase di attraversamento del Controller REST.
	 * @return Un'istanza "transiente" (priva di Primary Key e non ancora persistita su database) 
	 * dell'entità {@link OnuNumber}, idratata con le relazioni e pronta per l'operazione di {@code save()}.
	 */
	public OnuNumber mapToEntity(OnuNumberRequestDTO dto) {
		OnuNumber number = new OnuNumber();
		AdrClass adrClass = adrClassService.getByClassCode(dto.adrClassCode());
		number.setAdrClass(adrClass);
		number.setOnuCode(dto.onuCode());
		number.setName(dto.name());
		number.setPhysicalState(Enum.valueOf(PhysicalState.class, dto.physicalState()));
		number.setKemlerCode(dto.kemlerCode());
		number.setPackingGroup(Enum.valueOf(PackingGroup.class, dto.packingGroup()));
		number.setTunnelRestriction(Enum.valueOf(TunnelRestriction.class, dto.tunnelRestriction()));
		number.setTransportCategory(dto.transportCategory());
		return number;
	}
}
