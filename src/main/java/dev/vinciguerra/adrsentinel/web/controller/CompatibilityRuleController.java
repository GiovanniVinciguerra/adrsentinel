package dev.vinciguerra.adrsentinel.web.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import dev.vinciguerra.adrsentinel.db.compatibilityrule.CompatibilityRule;
import dev.vinciguerra.adrsentinel.db.compatibilityrule.CompatibilityRuleService;
import dev.vinciguerra.adrsentinel.web.annotation.adrclass.ValidatorAdrClassCode;
import dev.vinciguerra.adrsentinel.web.dto.compatibilityrule.CompatibilityRuleRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.compatibilityrule.CompatibilityRuleResponseDTO;
import jakarta.validation.Valid;

/**
 * Controller REST (Presentation Layer) dedicato all'esposizione delle API per la gestione 
 * delle Regole di Compatibilità ADR.
 * <p>
 * <b>Responsabilità Architetturali:</b>
 * <ul>
 * <li><b>Gestione Edge (Entry-Point):</b> Intercetta le richieste HTTP in ingresso, isolando 
 * il Service Layer dalle logiche di rete (parsing JSON, protocollo HTTP, serializzazione).</li>
 * <li><b>Validazione Perimetrale (Fail-Fast):</b> Grazie all'annotazione {@code @Validated} a livello 
 * di classe, abilita la validazione dei vincoli (Constraint Validation) direttamente sui parametri 
 * dei metodi (es. {@code @PathVariable}), respingendo input malformati prima ancora di invocare 
 * la logica di business.</li>
 * <li><b>Pattern DTO (Data Transfer Object):</b> Assicura la netta separazione tra il modello 
 * relazionale interno (Entity) e i contratti API esposti verso l'esterno, prevenendo l'esposizione 
 * accidentale di dati sensibili o l'alterazione non autorizzata (Mass Assignment).</li>
 * </ul>
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0 (REST Interface & Validation Boundary)
 * @since 1.0
 */
@RestController
@RequestMapping("/adr-sentinel/compatibility-rules")
@Validated
public class CompatibilityRuleController {
	private final CompatibilityRuleService compatibilityRuleService;
	
	/**
	 * Costruttore per l'iniezione delle dipendenze (Constructor Injection).
	 * <p>
	 * L'utilizzo di campi {@code final} promuove l'immutabilità del controller e facilita 
	 * il testing isolato (Mocking) rispetto al deprecato utilizzo di {@code @Autowired} sui campi.
	 * </p>
	 * @param compatibilityRuleService Il servizio di dominio responsabile della logica di business, 
	 * del mapping e dell'orchestrazione delle cache.
	 */
	public CompatibilityRuleController(CompatibilityRuleService compatibilityRuleService) {
		this.compatibilityRuleService = compatibilityRuleService;
	}
	
	/**
	 * Endpoint per il recupero della lista delle regole di compatibilità associate a una specifica Classe ADR.
	 * <p>
	 * <b>Flusso di Validazione:</b> L'annotazione custom {@link ValidatorAdrClassCode} presidia il parametro 
	 * in ingresso. Garantisce che la stringa passata nell'URL rispetti il formato standard 
	 * ancor prima che il sistema impegni risorse per interrogare la cache o il database.
	 * </p>
	 * @param adrClassCodeA Il codice identificativo della classe ADR (es. "3", "8", "6.1") 
	 * passato come parametro di percorso.
	 * @return {@link ResponseEntity} contenente una lista di {@link CompatibilityRuleResponseDTO} 
	 * con stato HTTP 200 (OK). Restituisce un array JSON vuoto se non esistono regole 
	 * censite per la classe specificata.
	 */
	@GetMapping("/{adrClassCodeA}")
	public ResponseEntity<List<CompatibilityRuleResponseDTO>> getAdrClassACompatibilityRule(@PathVariable @ValidatorAdrClassCode String adrClassCodeA) {
		List<CompatibilityRule> rules = compatibilityRuleService.getByAdrClassA(adrClassCodeA);
		List<CompatibilityRuleResponseDTO> response = rules.stream().map(this::mapToDTO).toList();
		return ResponseEntity.ok(response);
	}
	
	/**
	 * Endpoint per la creazione e il salvataggio di una nuova Regola di Compatibilità.
	 * <p>
	 * <b>Sicurezza e Validazione (Edge Validation):</b><br>
	 * L'annotazione {@code @Valid} sul parametro {@code @RequestBody} innesca il motore di validazione 
	 * di Spring sul DTO piatto in ingresso. Se le regole compositive (come {@code @ValidatorWarningNote} 
	 * o {@code @ValidatorAdrClassCode} definite all'interno del record) falliscono, il framework 
	 * interrompe il flusso lanciando una {@code MethodArgumentNotValidException}, che si tradurrà 
	 * in una risposta HTTP 400 (Bad Request) pulita per il frontend.
	 * </p>
	 * @param compatibilityRuleRequestDTO Il payload piatto (Flat DTO) contenente esclusivamente 
	 * i codici delle classi da collegare e le specifiche della regola.
	 * @return {@link ResponseEntity} contenente l'istanza consolidata (persistita) della regola, 
	 * convertita in un DTO di risposta sicuro, con stato HTTP 200 (OK).
	 */
	@PostMapping
	public ResponseEntity<CompatibilityRuleResponseDTO> create(@RequestBody @Valid CompatibilityRuleRequestDTO compatibilityRuleRequestDTO) {
		CompatibilityRule compatibilityRuleToSave = compatibilityRuleService.mapToEntity(compatibilityRuleRequestDTO);
		CompatibilityRule savedCompatibilityRule = compatibilityRuleService.save(compatibilityRuleToSave);
		return ResponseEntity.status(HttpStatus.CREATED).body(mapToDTO(savedCompatibilityRule));
	}
	
	/**
	 * Mapper interno per la conversione unidirezionale da Entità di Dominio (JPA) a DTO di Risposta (Projection).
	 * <p>
	 * Questa funzione di utilità nasconde i dettagli interni dell'implementazione 
	 * (es. metadati di Hibernate, proxy transazionali) ed espone al client esclusivamente 
	 * l'albero di dati previsto dal contratto API, formattato in modo ottimale.
	 * </p>
	 * @param entity L'entità {@link CompatibilityRule} recuperata dal DB o appena persistita.
	 * @return L'oggetto {@link CompatibilityRuleResponseDTO} pronto per la serializzazione Jackson.
	 */
	private CompatibilityRuleResponseDTO mapToDTO(CompatibilityRule entity) {
		return new CompatibilityRuleResponseDTO(
			entity.getId(),
			entity.getAdrClassA(),
			entity.getAdrClassB(),
			entity.isCompatible(),
			entity.getWarningNote()
		);
	}
}
