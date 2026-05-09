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
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumberService;
import dev.vinciguerra.adrsentinel.web.annotation.adrclass.ValidatorAdrClassCode;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorKemlerCode;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorOnuNumberCode;
import dev.vinciguerra.adrsentinel.web.dto.onunumber.OnuNumberRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.onunumber.OnuNumberResponseDTO;
import jakarta.validation.Valid;

/**
 * Controller REST (Presentation Layer) delegato all'esposizione e all'orchestrazione delle API 
 * per la gestione dell'anagrafica dei Numeri ONU (UN Numbers) previsti dalla normativa ADR.
 * <p>
 * <b>Responsabilità Architetturali:</b>
 * <ul>
 * <li><b>Isolamento del Dominio:</b> Funge da barriera tra il protocollo HTTP (Web) e la logica di 
 * business (Service Layer). Si occupa esclusivamente di routing, negoziazione dei contenuti (JSON) 
 * e definizione dei codici di stato HTTP.</li>
 * <li><b>Edge Validation Extensiva:</b> L'annotazione {@code @Validated} a livello di classe istruisce 
 * Spring a valutare i vincoli custom (es. {@link ValidatorOnuNumberCode}) direttamente sui parametri 
 * estratti dall'URL (Path Variables). Qualsiasi parametro fuori standard viene respinto istantaneamente 
 * con un errore 400 Bad Request, azzerando il carico computazionale sui livelli sottostanti.</li>
 * <li><b>Pattern DTO Asimmetrico:</b> Garantisce l'asimmetria architetturale tra ingresso (DTO Piatti 
 * e snelli in lettura) e uscita (DTO Ricchi e idratati per ottimizzare il rendering del frontend).</li>
 * </ul>
 * </p>
 * author Giovanni Vinciguerra
 * @version 1.0 (REST Interface & Validation Boundary)
 * @since 1.0
 */
@RestController
@RequestMapping("/adr-sentinel/onu-numbers")
@Validated
public class OnuNumberController {
	private final OnuNumberService onuNumberService;
	
	/**
	 * Costruttore per l'iniezione delle dipendenze (Constructor Injection).
	 * <p>L'uso di campi {@code final} garantisce l'immutabilità e la thread-safety del controller.</p>
	 * @param onuNumberService Il servizio di dominio responsabile dell'interazione con il database 
	 * e della gestione strategica delle cache.
	 */
	public OnuNumberController(OnuNumberService onuNumberService) {
		this.onuNumberService = onuNumberService;
	}
	
	/**
	 * Recupera l'intero catalogo dei Numeri ONU censiti a sistema.
	 * @return {@link ResponseEntity} contenente la lista completa convertita in {@link OnuNumberResponseDTO} 
	 * e lo stato HTTP 200 (OK).
	 */
	@GetMapping
	public ResponseEntity<List<OnuNumberResponseDTO>> getAll() {
		List<OnuNumber> numbers = onuNumberService.getAllOnuNumber();
		List<OnuNumberResponseDTO> response = numbers.stream().map(OnuNumberResponseDTO::fromEntity).toList();
		return ResponseEntity.ok(response);
	}
	
	/**
	 * Ricerca le varianti di un Numero ONU partendo dal suo codice identificativo standard.
	 * <p>
	 * <b>Dinamica 1-a-N:</b> Poiché un singolo codice ONU (es. "1993") può diramarsi in diverse 
	 * configurazioni (Gruppi di Imballaggio, Disposizioni Speciali), l'API restituisce coerentemente 
	 * una lista di occorrenze.
	 * </p>
	 * @param onuCode Il codice ONU a 4 cifre (validato perimetralmente tramite {@code @ValidatorOnuNumberCode}).
	 * @return {@link ResponseEntity} contenente le varianti trovate (HTTP 200). Se il codice non è 
	 * presente, restituisce un array JSON vuoto.
	 */
	@GetMapping("/{onuCode}")
	public ResponseEntity<List<OnuNumberResponseDTO>> getByOnuCode(@PathVariable @ValidatorOnuNumberCode String onuCode) {
		List<OnuNumber> numbers = onuNumberService.getByOnuCode(onuCode);
		List<OnuNumberResponseDTO> response = numbers.stream().map(OnuNumberResponseDTO::fromEntity).toList();
		return ResponseEntity.ok(response);
	}
	
	/**
	 * Ricerca i Numeri ONU filtrandoli in base al Codice Kemler (Numero di Identificazione del Pericolo).
	 * <p>
	 * Endpoint utile per scenari operativi in cui l'utente rileva un pannello arancione su un veicolo 
	 * e necessita di individuare le possibili merci trasportate in base al grado di pericolo indicato 
	 * nella parte superiore del pannello.
	 * </p>
	 * @param kemlerCode Il codice alfanumerico di pericolo (es. "33", "X88").
	 * @return {@link ResponseEntity} contenente i risultati corrispondenti (HTTP 200).
	 */
	@GetMapping("/kemler-code/{kemlerCode}")
	public ResponseEntity<List<OnuNumberResponseDTO>> getByKemlerCode(@PathVariable @ValidatorKemlerCode String kemlerCode) {
		List<OnuNumber> numbers = onuNumberService.getByKemlerCode(kemlerCode);
		List<OnuNumberResponseDTO> response = numbers.stream().map(OnuNumberResponseDTO::fromEntity).toList();
		return ResponseEntity.ok(response);
	}
	
	/**
	 * Recupera tutti i Numeri ONU subordinati a una specifica Classe di Pericolo ADR.
	 * @param adrClassCode La Business Key della classe ADR (es. "3", "8").
	 * @return {@link ResponseEntity} contenente le anagrafiche filtrate (HTTP 200).
	 */
	@GetMapping("/adr-class/{adrClassCode}")
	public ResponseEntity<List<OnuNumberResponseDTO>> getByAdrClass(@PathVariable @ValidatorAdrClassCode String adrClassCode) {
		List<OnuNumber> numbers = onuNumberService.getByAdrClass(adrClassCode);
		List<OnuNumberResponseDTO> response = numbers.stream().map(OnuNumberResponseDTO::fromEntity).toList();
		return ResponseEntity.ok(response);
	}
	
	/**
	 * Inserisce un nuovo record nell'anagrafica dei Numeri ONU.
	 * <p>
	 * <b>Conformità RESTful:</b> In ottemperanza agli standard HTTP, l'avvenuta creazione della 
	 * risorsa restituisce un codice semantico <b>201 Created</b> anziché un generico 200 OK.
	 * </p>
	 * <p>
	 * <b>Validazione Payload:</b> L'annotazione {@code @Valid} attiva il motore di validazione 
	 * profonda sul DTO in ingresso, bloccando preventivamente richieste contenenti enumerazioni 
	 * non valide, campi mancanti o relazioni fittizie.
	 * </p>
	 * @param onuNumberRequestDTO Il payload piatto contenente i dati grezzi del nuovo ONU.
	 * @return {@link ResponseEntity} contenente la rappresentazione finale della risorsa (Rich DTO), 
	 * con stato HTTP 201 Created.
	 */
	@PostMapping
	public ResponseEntity<OnuNumberResponseDTO> create(@RequestBody @Valid OnuNumberRequestDTO onuNumberRequestDTO) {
		OnuNumber onuNumberToSave = onuNumberService.mapToEntity(onuNumberRequestDTO);
		OnuNumber savedOnuNumber = onuNumberService.save(onuNumberToSave);
		return ResponseEntity.status(HttpStatus.CREATED).body(OnuNumberResponseDTO.fromEntity(savedOnuNumber));
	}
}
