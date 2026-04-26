package dev.vinciguerra.adrsentinel.web.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import dev.vinciguerra.adrsentinel.db.adrclass.AdrClass;
import dev.vinciguerra.adrsentinel.db.adrclass.AdrClassService;
import dev.vinciguerra.adrsentinel.web.annotation.adrclass.ValidatorAdrClassCode;
import dev.vinciguerra.adrsentinel.web.dto.adrclass.AdrClassRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.adrclass.AdrClassResponseDTO;
import jakarta.validation.Valid;

/**
 * REST Controller che espone le API per la gestione del catalogo delle Classi ADR.
 * <p>
 * <b>Ruolo Architetturale (Boundary Layer):</b><br>
 * Questa classe funge da barriera d'ingresso per le richieste HTTP provenienti dai client (es. Frontend Web, 
 * Sistemi Terzi). Il suo scopo è puramente orchestrativo e difensivo: riceve le richieste, attiva 
 * i meccanismi di validazione strutturale, delega la logica di business al {@link AdrClassService} 
 * e formatta le risposte utilizzando il pattern DTO (Data Transfer Object) per isolare il modello dati interno.
 * </p>
 * <p>
 * <b>Meccanismi di Validazione:</b><br>
 * L'annotazione di classe {@link Validated} innesca un proxy AOP che intercetta i parametri "sfusi" 
 * dell'URL (es. {@code @PathVariable}), garantendo che le annotazioni custom (come {@code @ValidatorAdrClassCode}) 
 * vengano valutate rigorosamente prima dell'ingresso nel metodo (Fail-Fast pattern).
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0 (REST Interface & Validation Boundary)
 * @since 1.0
 */
@RestController
@RequestMapping("/adr-sentinel/adr-classes")
@Validated
public class AdrClassController {
	private final AdrClassService adrClassService;
	
	/**
	 * Costruttore per l'iniezione delle dipendenze (Dependency Injection).
	 * L'uso di campi {@code final} e dell'iniezione via costruttore garantisce l'immutabilità 
	 * del controller e previene problemi di stato in un ambiente Multi-Thread (Thread-Safety nativa).
	 * @param adrClassService il servizio che incapsula la logica di business del dominio AdrClass.
	 */
	public AdrClassController(AdrClassService adrClassService) {
		this.adrClassService = adrClassService;
	}
	
	/**
	 * Recupera l'intero catalogo delle Classi ADR.
	 * @return una {@link ResponseEntity} contenente la lista immutabile di {@link AdrClassResponseDTO} 
	 * e lo status HTTP 200 (OK). Ritorna una lista vuota se il database non contiene record.
	 */
	@GetMapping
	public ResponseEntity<List<AdrClassResponseDTO>> getAll() {
		List<AdrClass> classes = adrClassService.getAllAdrClasses();
		List<AdrClassResponseDTO> response = classes.stream().map(this::mapToDTO).toList();
		return ResponseEntity.ok(response);
	}
	
	/**
	 * Recupera i dettagli di una singola Classe ADR in base al suo codice normativo.
	 * @param classCode il codice della classe ADR fornito nell'URL. Validato in ingresso 
	 * dall'annotazione custom per prevenire l'esecuzione di query con formati palesemente errati.
	 * @return una {@link ResponseEntity} contenente il DTO richiesto (HTTP 200).
	 * @throws ResourceNotFoundException (gestita globalmente) se il codice non esiste nel DB.
	 */
	@GetMapping("/{classCode}")
	public ResponseEntity<AdrClassResponseDTO> getByClassCode(@PathVariable @ValidatorAdrClassCode String classCode) {
		AdrClass entity = adrClassService.getByClassCode(classCode);
		return ResponseEntity.ok(mapToDTO(entity));
	}
	
	/**
	 * Crea una nuova Classe ADR nel sistema.
	 * <p>
	 * <b>Flusso di Validazione:</b><br>
	 * L'annotazione {@link Valid} invoca l'Hibernate Validator sul payload del body. Se il JSON non 
	 * rispetta i vincoli del {@link AdrClassRequestDTO}, l'esecuzione viene bloccata istantaneamente, 
	 * restituendo un HTTP 400 (Bad Request) senza sovraccaricare il Service.
	 * </p>
	 * @param adrClassRequestDTO il payload JSON deserializzato e validato.
	 * @return una {@link ResponseEntity} contenente il DTO generato e lo status HTTP 200 (OK).
	 */
	@PostMapping
	public ResponseEntity<AdrClassResponseDTO> create(@RequestBody @Valid AdrClassRequestDTO adrClassRequestDTO) {
		AdrClass adrClassToSave = adrClassService.mapToEntity(adrClassRequestDTO);
		AdrClass savedAdrClass = adrClassService.save(adrClassToSave);
		return ResponseEntity.ok(mapToDTO(savedAdrClass));
	}
	
	/**
	 * Aggiorna i dati (Idempotente) di una Classe ADR esistente.
	 * <p>
	 * Implementa una validazione ibrida: il codice nell'URL viene validato tramite il proxy AOP 
	 * della classe, mentre il payload viene validato dall'ispezione standard dei parametri.
	 * </p>
	 * @param classCode il codice identificativo della classe da modificare (dall'URL).
	 * @param adrClassRequestDTO i nuovi dati da applicare (dal Body).
	 * @return una {@link ResponseEntity} contenente l'entità aggiornata e lo status HTTP 200 (OK).
	 */
	@PutMapping("/{classCode}")
	public ResponseEntity<AdrClassResponseDTO> update(@PathVariable @ValidatorAdrClassCode String classCode, @RequestBody @Valid AdrClassRequestDTO adrClassRequestDTO) {
		AdrClass updatedAdrClass = adrClassService.update(classCode, adrClassRequestDTO);
		return ResponseEntity.ok(mapToDTO(updatedAdrClass));
	}
	
	/**
	 * Fabbrica interna (Mapper) per la conversione dallo strato di Persistenza allo strato Web.
	 * Estrae i dati dall'entità di database e li sigilla in un Record  immutabile prima della 
	 * serializzazione JSON.
	 * @param entity l'entità recuperata dal database.
	 * @return una vista di sola lettura (DTO) formattata per il client.
	 */
	private AdrClassResponseDTO mapToDTO(AdrClass entity) {
		return new AdrClassResponseDTO(
			entity.getId(),
			entity.getClassCode(),
			entity.getDescription()
		);
	}
}
