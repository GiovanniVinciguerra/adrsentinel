package dev.vinciguerra.adrsentinel.db.adrclass;

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
import dev.vinciguerra.adrsentinel.web.dto.adrclass.AdrClassRequestDTO;

/**
 * Service Layer (Business Logic) dedicato alla gestione dell'entità {@link AdrClass}.
 * <p>
 * Questa classe agisce da "Direttore d'Orchestra" tra il Database (tramite {@link AdrClassRepository})
 * e la Memoria RAM (tramite {@link AbstractGenericService}). Applica il pattern del <b>Cache-Aside</b>
 * ibridato: le letture sono gestite in modo trasparente dalle annotazioni Spring ({@code @Cacheable}), 
 * mentre le scritture/aggiornamenti forzano un riallineamento proattivo della cache (Write-Through manuale).
 * </p>
 * <h3>Responsabilità Architetturali:</h3>
 * <ul>
 * <li>Disaccoppiare i Controller (Frontend) dalla logica di persistenza.</li>
 * <li>Garantire il principio <i>Fail-Fast</i> lanciando eccezioni specifiche ({@link ResourceNotFoundException}) se i dati richiesti mancano.</li>
 * <li>Mantenere la consistenza dei dati tra PostgreSQL e il CacheManager ad ogni operazione di salvataggio.</li>
 * </ul>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Service
@Transactional(readOnly = true)
public class AdrClassService extends AbstractGenericService {
	private final AdrClassRepository adrClassRepository;
	
	/**
     * Costruttore per la Constructor-based Dependency Injection.
     * <p>
     * <b>Best Practice:</b> L'uso del costruttore (anziché {@code @Autowired} sui campi) garantisce
     * l'immutabilità delle dipendenze ({@code final}) e facilita i test unitari.
     * Il {@code CacheManager} viene passato al costruttore della superclasse tramite {@code super()}.
     * </p>
     *
     * @param adrClassRepository Il Data Access Object per AdrClass.
     * @param cacheManager Il gestore della cache di Spring Boot.
     */
	public AdrClassService(AdrClassRepository adrClassRepository, CacheManager cacheManager) {
		super(cacheManager);
		this.adrClassRepository = adrClassRepository;
	}
	
	/**
     * Recupera una singola classe ADR partendo dal suo codice (Business Key).
     * <p>
     * <b>Flusso Cache:</b> Cerca prima nel cassetto {@code adr_class_by_class_code}. 
     * Se trova l'oggetto, lo restituisce istantaneamente (nessun log, zero I/O su DB).
     * Altrimenti, interroga il database, logga l'evento, e salva automaticamente il risultato in RAM.
     * </p>
     *
     * @param classCode Il codice identificativo univoco (es. "3", "8").
     * @return L'entità {@link AdrClass} richiesta, se esistente.
     * @throws ResourceNotFoundException Se il database non contiene alcuna classe con il codice fornito 
     * (genera un HTTP 404 a livello di Controller).
     */
	@Cacheable(value = CaffeineCacheConfiguration.ADR_CLASS_BY_CLASS_CODE_CACHE, key = "#classCode")
	public AdrClass getByClassCode(String classCode) throws ResourceNotFoundException {
		logger.info("[DataBase CALL] Searching for the AdrClass by classCode: {}", classCode);
		return adrClassRepository.findByClassCode(classCode)
			.orElseThrow(() -> new ResourceNotFoundException("AdrClass not found: " + classCode));
	}
	
	/**
     * Recupera l'intera collezione delle classi ADR.
     * <p>
     * <b>Flusso Cache:</b> Utilizza la chiave statica risolta a tempo di compilazione per intercettare 
     * l'esatta cartellina ({@code "all_class"}) nel cassetto globale. 
     * A differenza della singola risorsa, questo metodo non lancia eccezioni: un database vuoto 
     * restituisce semplicemente una lista vuota (HTTP 200 OK), pienamente conforme agli standard REST.
     * </p>
     *
     * @return Una lista di tutti gli oggetti {@link AdrClass} presenti nel sistema.
     */
	@Cacheable(value = CaffeineCacheConfiguration.ALL_ADR_CLASS_CACHE, key = "'" + CaffeineCacheConfiguration.ALL_ADR_CLASS_KEY + "'")
	public List<AdrClass> getAllAdrClasses() {
		logger.info("[DataBase CALL] Retrieving all AdrClass");
		return adrClassRepository.findAll();
	}
	
	/**
     * Persiste una nuova classe ADR nel database e sincronizza immediatamente l'infrastruttura di caching.
     * <p>
     * Dopo l'esecuzione del salvataggio fisico ({@code repository.save()}), questo metodo sfrutta 
     * la superclasse {@link AbstractGenericService} per aggiornare la memoria su due fronti:
     * <ol>
     * <li>Sovrascrive o inserisce il record singolo nel cassetto delle ricerche specifiche (1:1).</li>
     * <li>Tenta di aggiungere il nuovo record alla lista globale, operando un <i>Append</i> solo se la lista 
     * è già stata inizializzata in memoria (rispetto del pattern Lazy Loading).</li>
     * </ol>
     * </p>
     * @param newAdrClass L'entità AdrClass da salvare (i dati devono essere già validati).
     * @return L'entità salvata, arricchita con l'ID autogenerato dal Database.
     */
	@Transactional
	public AdrClass save(AdrClass newAdrClass) {
		logger.info("[DataBase CALL] Saving new AdrClass with classCode: {}", newAdrClass.getClassCode());
		AdrClass savedAdrClass = adrClassRepository.save(newAdrClass);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { writeThroughCacheIntegrityOperation(savedAdrClass); }
		});
		return savedAdrClass;
	}
	
	/**
	 * Esegue un'operazione di allineamento e sincronizzazione della cache applicativa 
	 * (Caffeine) applicando il pattern architetturale Write-Through.
	 * <p>
	 * Questo metodo garantisce la coerenza assoluta tra lo stato dei dati in memoria (RAM) 
	 * e quelli persistiti nel database. Per prevenire la corruzione della cache (es. 
	 * inserimento di "ghost record" a seguito di un rollback del database), questo metodo 
	 * deve essere invocato <b>esclusivamente</b> a valle di una transazione completata con 
	 * successo, preferibilmente registrandolo all'interno della fase {@code afterCommit} 
	 * del {@code TransactionSynchronizationManager}.
	 * </p>
	 * <p>
	 * Il processo di mantenimento dell'integrità si articola in due passaggi sequenziali:
	 * <ul>
	 * <li><b>Sincronizzazione Entità Singola:</b> Inserisce ex-novo o sovrascrive il record 
	 * all'interno della cache dedicata alle ricerche puntuali 
	 * ({@code ADR_CLASS_BY_CLASS_CODE_CACHE}), utilizzando il Class Code come chiave.</li>
	 * <li><b>Sincronizzazione Collezione Globale:</b> Accoda o aggiorna dinamicamente l'entità 
	 * all'interno della lista globale in memoria ({@code ALL_ADR_CLASS_CACHE}). Questa 
	 * ottimizzazione permette di mantenere la lista aggiornata senza dover scatenare 
	 * un'invalidazione totale (eviction) e una successiva rilettura massiva dal database.</li>
	 * </ul>
	 * </p>
	 * @param savedAdrClass l'istanza dell'entità {@link AdrClass} appena persistita o 
	 * aggiornata con successo nel DB. L'oggetto fornito deve 
	 * rappresentare lo stato consolidato (incluso di eventuali ID o 
	 * campi generati).
	 */
	private void writeThroughCacheIntegrityOperation(AdrClass savedAdrClass) {
		// 1. Aggiorna o crea il record singolo nella cache specifica
		storeInCache(
			CaffeineCacheConfiguration.ADR_CLASS_BY_CLASS_CODE_CACHE,
			savedAdrClass.getClassCode(),
			savedAdrClass,
			CacheOperation.SINGLE_RECORD
		);
		// 2. Accoda il record alla lista globale (se caricata in precedenza)
		storeInCache(
			CaffeineCacheConfiguration.ALL_ADR_CLASS_CACHE,
			CaffeineCacheConfiguration.ALL_ADR_CLASS_KEY,
			savedAdrClass,
			CacheOperation.LIST_RECORD
		);
	}
	
	/**
	 * Fabbrica di conversione (Mapper) strutturale da Data Transfer Object a Entità di Dominio.
	 * <p>
	 * <b>Ruolo Architetturale (Boundary Isolation):</b><br>
	 * Questo metodo implementa il pattern strutturale <i>Data Mapper / Assembler</i>. 
	 * Funge da barriera d'ingresso per il livello di Business Logic (Service Layer). Prende in carico 
	 * un oggetto puramente legato al trasporto web e privo di logica (il {@link AdrClassRequestDTO}), 
	 * già rigorosamente validato dal Controller, e lo "forgia" in una vera e propria Entità di Dominio.
	 * In questo modo, il Core dell'applicazione rimane totalmente agnostico rispetto ai protocolli 
	 * di rete (JSON, HTTP).
	 * </p>
	 * <p>
	 * <b>Stato del Ciclo di Vita (JPA Lifecycle):</b><br>
	 * L'oggetto {@link AdrClass} restituito si trova in uno stato <b>Transient</b>. Non possiede 
	 * ancora un ID generato dal database e non è tracciato dall'{@code EntityManager} di Hibernate. 
	 * Sarà responsabilità del metodo invocante (es. la fase operativa di Creazione) applicare eventuali 
	 * ulteriori regole di business (es. controllo di univocità del {@code classCode}) prima di inviarlo 
	 * al Repository per la transazione finale (passaggio allo stato <i>Persistent</i>).
	 * </p>
	 * @param dto il payload in ingresso ricevuto dal client web. È garantito che i suoi campi siano 
	 * formalmente validi (es. formato Regex corretto, limiti di caratteri rispettati) 
	 * grazie alla barriera di validazione (Fail-Fast) posta nel Controller REST.
	 * @return una nuova istanza pulita di {@link AdrClass}, popolata esclusivamente con i dati 
	 * consentiti dal contratto DTO e pronta per l'inserimento nel contesto di persistenza.
	 */
	public AdrClass mapToEntity(AdrClassRequestDTO dto) {
		AdrClass adrClass = new AdrClass();
		adrClass.setClassCode(dto.classCode());
		adrClass.setDescription(dto.description());
		return adrClass;
	}
}
