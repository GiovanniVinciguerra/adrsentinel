package dev.vinciguerra.adrsentinel.db.compatibilityrule;

import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import dev.vinciguerra.adrsentinel.db.AbstractGenericService;
import dev.vinciguerra.adrsentinel.db.CaffeineCacheConfiguration;
import dev.vinciguerra.adrsentinel.db.adrclass.AdrClass;

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
	
	/**
	 * Costruttore per l'iniezione delle dipendenze (Dependency Injection).
	 * @param compatibilityRuleRepository il DAO per l'accesso fisico alle regole di compatibilità.
	 * @param cacheManager il gestore della memoria cache configurato nel container di Spring.
	 */
	public CompatibilityRuleService(CompatibilityRuleRepository compatibilityRuleRepository, CacheManager cacheManager)  {
		super(cacheManager);
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
	@Cacheable(value = CaffeineCacheConfiguration.COMPATIBILITY_RULE_ADR_CLASS_A_CACHE, key = "#adrClassA.classCode")
	public List<CompatibilityRule> getByAdrClassA(AdrClass adrClassA) {
		logger.info("[DataBase CALL] Searching compatibility rule for the AdrClass by classCode: {}", adrClassA.getClassCode());
		return compatibilityRuleRepository.findByAdrClassA(adrClassA);
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
	public CompatibilityRule save(CompatibilityRule newCompatibilityRule) {
		logger.info(
			"[DataBase CALL] Saving new CompatibilityRule for AdrClassA: {} and AdrClassB : {})",
			newCompatibilityRule.getAdrClassA().getClassCode(),
			newCompatibilityRule.getAdrClassB().getClassCode()
		);
		CompatibilityRule savedCompatibilityRule = compatibilityRuleRepository.save(newCompatibilityRule);
		AdrClass savedAdrClassA = savedCompatibilityRule.getAdrClassA();
		updateCache(
			CaffeineCacheConfiguration.COMPATIBILITY_RULE_ADR_CLASS_A_CACHE,
			savedAdrClassA.getClassCode(),
			savedCompatibilityRule,
			CacheOperation.LIST_RECORD
		);
		return savedCompatibilityRule;
	}
}
