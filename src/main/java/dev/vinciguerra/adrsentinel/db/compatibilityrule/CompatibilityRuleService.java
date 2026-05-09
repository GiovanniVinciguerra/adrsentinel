package dev.vinciguerra.adrsentinel.db.compatibilityrule;

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
import dev.vinciguerra.adrsentinel.db.adrclass.AdrClassService;
import dev.vinciguerra.adrsentinel.web.dto.compatibilityrule.CompatibilityRuleRequestDTO;

/**
 * Strato di Business Logic (Service Layer) per la gestione delle Regole di Compatibilità (Mixed Loading).
 * <p>
 * <b>Contesto di Dominio (Logistica ADR Sentinel):</b><br>
 * Questo servizio orchestra le logiche relative al "Carico in Comune". È il componente responsabile 
 * di stabilire quali classi di merci pericolose possono viaggiare sullo stesso veicolo in sicurezza, 
 * interrogando le matrici di compatibilità normate dall'accordo internazionale ADR.
 * </p>
 * <p>
 * <b>Architettura e Performance:</b><br>
 * Estende {@link AbstractGenericService} per ereditare un motore di gestione della cache customizzato. 
 * Implementa una strategia aggressiva di caching per ridurre a zero i colpi sul database (I/O Bound) 
 * durante la validazione in tempo reale di una spedizione, mantenendo però la coerenza dei dati in scrittura.
 * </p>
 *
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Service
public class CompatibilityRuleService extends AbstractGenericService {
	private final CompatibilityRuleRepository compatibilityRuleRepository;
	private final AdrClassService adrClassService;
	
	/**
	 * Costruttore principale per l'iniezione delle dipendenze (Dependency Injection) 
	 * e l'inizializzazione del servizio di orchestrazione delle Regole di Compatibilità ADR.
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
	 * @param compatibilityRuleRepository L'interfaccia JPA di accesso ai dati persistenti, 
	 * dedicata esclusivamente alle operazioni di mutazione e ricerca delle regole di 
	 * compatibilità.
	 * @param adrClassService Il servizio di dominio delle Classi ADR, utilizzato per 
	 * eseguire il lookup ottimizzato (tramite RAM) e sicuro delle entità genitore durante il 
	 * mapping dei DTO.
	 * @param cacheManager L'istanza del gestore centrale delle cache fornita dal contesto Spring Boot, 
	 * delegata alla classe astratta base.
	 */
	public CompatibilityRuleService(CompatibilityRuleRepository compatibilityRuleRepository, AdrClassService adrClassService, CacheManager cacheManager)  {
		super(cacheManager);
		this.adrClassService = adrClassService;
		this.compatibilityRuleRepository = compatibilityRuleRepository;
	}
	
	/**
	 * Recupera la lista di regole di compatibilità in cui la classe ADR specificata funge da origine (Classe A).
	 * <p>
	 * <b>Meccanica della Cache (Read-Through & SpEL):</b><br>
	 * Utilizza l'annotazione {@link Cacheable} combinata con la sintassi SpEL ({@code #adrClassA.classCode}). 
	 * Invece di usare l'indirizzo di memoria dell'oggetto come chiave (che causerebbe miss continui), 
	 * estrae dinamicamente la Business Key (il codice della classe, es. "3" o "8") per creare 
	 * una chiave di cache predicibile e idempotente.
	 * Se la lista per quella classe è già in RAM, il metodo intercetta la chiamata e non esegue la query SQL.
	 * </p>
	 * @param adrClassA l'entità completa della classe ADR da analizzare.
	 * @return una {@link List} di regole di compatibilità associate a tale classe (può essere vuota).
	 */
	@Cacheable(value = CaffeineCacheConfiguration.COMPATIBILITY_RULE_ADR_CLASS_A_CACHE, key = "#adrClassCodeA")
	public List<CompatibilityRule> getByAdrClassA(String adrClassCodeA) {
		logger.info("[DataBase CALL] Searching compatibility rule for the AdrClass by classCode: {}", adrClassCodeA);
		return compatibilityRuleRepository.findByAdrClassA_ClassCode(adrClassCodeA);
	}
	
	/**
	 * Persiste una nuova regola di compatibilità nel database e sincronizza lo stato della memoria (RAM).
	 * <p>
	 * <b>Pattern di Sincronizzazione (In-Place Mutation):</b><br>
	 * Dopo il salvataggio fisico su database, questo metodo non invalida brutalmente l'intera cache 
	 * (evitando il pattern {@code @CacheEvict} che degraderebbe le performance future). 
	 * Utilizza invece il metodo ereditato {@code updateCache} con parametro {@code CacheOperation.LIST_RECORD} 
	 * per recuperare la lista in memoria (identificata dal {@code classCode}) e "iniettarvi" il nuovo record 
	 * in coda. Questo garantisce che la successiva lettura trovi dati perfettamente allineati al database, 
	 * senza dover rieseguire una query di popolamento.
	 * </p>
	 * @param newCompatibilityRule l'entità transient contenente la nuova regola da salvare.
	 * @return l'entità persistent restituita da Hibernate, comprensiva dell'ID generato.
	 */
	@Transactional
	public CompatibilityRule save(CompatibilityRule newCompatibilityRule) {
		logger.info(
			"[DataBase CALL] Saving new CompatibilityRule for AdrClassA: {} and AdrClassB : {})",
			newCompatibilityRule.getAdrClassA().getClassCode(),
			newCompatibilityRule.getAdrClassB().getClassCode()
		);
		CompatibilityRule savedCompatibilityRule = compatibilityRuleRepository.save(newCompatibilityRule);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterInsert(savedCompatibilityRule); }
		});
		return savedCompatibilityRule;
	}
	
	/**
	 * Esegue un'operazione di allineamento della cache applicativa per le Regole di Compatibilità 
	 * applicando il pattern architetturale Write-Through.
	 * <p>
	 * Questo metodo garantisce la coerenza tra il database e la memoria RAM a seguito 
	 * dell'inserimento o dell'aggiornamento di una {@link CompatibilityRule}. Per prevenire 
	 * letture sporche o l'inserimento di "ghost record" in caso di eccezioni SQL, l'invocazione 
	 * di questo metodo deve avvenire <b>rigorosamente</b> a transazione conclusa, registrandolo 
	 * all'interno della fase {@code afterCommit} del {@code TransactionSynchronizationManager}.
	 * </p>
	 * <p>
	 * <b>Dettagli Implementativi:</b><br>
	 * A differenza delle cache a record singolo, le regole di compatibilità sono indicizzate 
	 * raggruppandole per la classe ADR primaria (Class A). Il metodo esegue le seguenti operazioni:
	 * <ul>
	 * <li>Estrae l'entità {@link AdrClass} (Class A) associata alla regola appena salvata.</li>
	 * <li>Utilizza il {@code classCode} di tale classe come chiave di raggruppamento.</li>
	 * <li>Tramite l'operazione {@code CacheOperation.LIST_RECORD}, intercetta la lista delle regole 
	 * già presenti in memoria per quel codice e vi accoda (o aggiorna) la nuova regola.</li>
	 * </ul>
	 * Questo approccio architetturale è altamente ottimizzato: evita lo svuotamento dell'intera 
	 * cache (eviction) e risparmia al database pesanti query di ricaricamento massivo, 
	 * mantenendo la lista in RAM sempre consistente e pronta per il frontend.
	 * </p>
	 * @param savedCompatibilityRule l'istanza della regola di compatibilità persistita con successo 
	 * nel database. L'oggetto deve essere nello stato "Managed" o comunque rappresentare i dati 
	 * consolidati definitivi.
	 */
	private void syncCacheAfterInsert(CompatibilityRule savedCompatibilityRule) {
		AdrClass adrClassA = savedCompatibilityRule.getAdrClassA();
		storeInCache(
			CaffeineCacheConfiguration.COMPATIBILITY_RULE_ADR_CLASS_A_CACHE,
			adrClassA.getClassCode(),
			savedCompatibilityRule,
			CacheOperation.LIST_RECORD
		);
	}
	
	/**
	 * Converte un Data Transfer Object (DTO) di richiesta in una nuova entità di dominio {@link CompatibilityRule}.
	 * <p>
	 * Questo metodo agisce da strato di traduzione (Mapper) tra il payload piatto fornito 
	 * dall'esterno (es. interfaccia web) e il modello relazionale complesso richiesto da JPA/Hibernate.
	 * </p>
	 * <p>
	 * <b>Flusso di Mappatura e Idratazione:</b>
	 * <ul>
	 * <li><b>1. Risoluzione delle Relazioni (Lookup):</b> Il metodo non si limita a copiare dati, 
	 * ma orchestra il recupero delle dipendenze. Utilizza i codici alfanumerici forniti nel DTO 
	 * ({@code classCodeA} e {@code classCodeB}) per interrogare il {@code adrClassService}. 
	 * Questo garantisce che le entità {@link AdrClass} iniettate nella regola siano nello stato 
	 * "Managed", requisito fondamentale affinché Hibernate possa valorizzare correttamente 
	 * le Foreign Key nel database durante l'operazione di salvataggio.</li>
	 * <li><b>2. Mappatura dei Campi Nativi:</b> Trasferisce direttamente e senza alterazioni 
	 * i dati di business base, ovvero il flag booleano di compatibilità ({@code isCompatible}) 
	 * e l'eventuale nota operativa ({@code warningNote}).</li>
	 * </ul>
	 * </p>
	 * @param dto l'oggetto di trasferimento dati (Data Transfer Object) contenente le chiavi di 
	 * business delle classi ADR da collegare e i dettagli della nuova regola.
	 * @return un'istanza "transiente" (non ancora persistita nel database) dell'entità 
	 * {@link CompatibilityRule}, completamente popolata, idratata con le relative relazioni 
	 * e pronta per essere validata e salvata dal Repository.
	 */
	public CompatibilityRule mapToEntity(CompatibilityRuleRequestDTO dto) {
		CompatibilityRule compatibilityRule = new CompatibilityRule();
		AdrClass adrClassA = adrClassService.getByClassCode(dto.classCodeA());
		AdrClass adrClassB = adrClassService.getByClassCode(dto.classCodeB());
		compatibilityRule.setAdrClassA(adrClassA);
		compatibilityRule.setAdrClassB(adrClassB);
		compatibilityRule.setCompatible(dto.isCompatible());
		compatibilityRule.setWarningNote(dto.warningNote());
		return compatibilityRule;
	}
}
