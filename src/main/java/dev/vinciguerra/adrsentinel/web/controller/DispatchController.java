package dev.vinciguerra.adrsentinel.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import dev.vinciguerra.adrsentinel.db.dispatch.DispatchService;
import dev.vinciguerra.adrsentinel.web.dto.dispatch.DispatchRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.dispatch.DispatchResponseDTO;
import jakarta.validation.Valid;

/**
 * Controller REST responsabile della pianificazione logistica e dell'assegnazione 
 * dei veicoli per il trasporto di merci pericolose (normativa ADR).
 * <p>
 * Questo componente funge da interfaccia di ingresso (Presentation Layer) per il 
 * motore di ottimizzazione logistica. Riceve le distinte di carico dal client, 
 * applica una validazione rigorosa dei parametri formali tramite {@link Validated} 
 * e delega al {@link DispatchService} il calcolo del piano di viaggio.
 * </p>
 * <p>
 * Il sistema garantisce che la risposta generata rispetti tre pilastri fondamentali:
 * <ul>
 * <li><b>Sicurezza Chimica:</b> Segregazione delle materie incompatibili (es. Classe 3 separata dalla Classe 8).</li>
 * <li><b>Efficienza Economica:</b> Applicazione automatica dell'esenzione ADR (regola dei 1000 punti) ove consentito.</li>
 * <li><b>Conformità Strutturale:</b> Matchmaking perfetto tra i requisiti della merce (stato fisico, limitazioni) e le caratteristiche del veicolo (portata, allestimento, omologazioni FL/AT/EX).</li>
 * </ul>
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0 (REST Interface & Validation Boundary)
 * @since 1.0
 */
@RestController
@RequestMapping("/adr-sentinel/dispatch")
@Validated
public class DispatchController {
	private final DispatchService dispatchService;
	/**
	 * Inietta le dipendenze necessarie per l'elaborazione delle richieste di spedizione.
	 * @param dispatchService Il servizio di dominio contenente il motore algoritmico a grafo 
	 * per il partizionamento e il matchmaking della flotta.
	 */
	public DispatchController(DispatchService dispatchService) {
		this.dispatchService = dispatchService;
	}
	
	/**
	 * Endpoint principale per la generazione di un piano di carico e assegnazione veicoli (Load Plan).
	 * <p>
	 * Riceve in input una lista di sostanze chimiche da trasportare. Elabora il payload suddividendo 
	 * le merci in cluster sicuri (in caso di incompatibilità) e ricerca nel database i veicoli ottimali 
	 * disponibili, consumandoli progressivamente per evitare doppie assegnazioni (Double-Booking).
	 * </p>
	 * @param dispatchRequestDTO Il Data Transfer Object contenente gli articoli da spedire. 
	 * L'annotazione {@link Valid} innesca le validazioni JSR-380 (Hibernate Validator)
	 * sui campi interni (es. pattern targa, limiti di peso, codici ONU).
	 * @return Una {@link ResponseEntity} contenente il {@link DispatchResponseDTO} con stato HTTP 200 (OK). 
	 * Il body include l'array dei viaggi generati, dettagliando per ognuno il veicolo scelto, 
	 * le merci allocate e l'eventuale applicazione del regime di esenzione.
	 * @throws dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException (catturata dal RestControllerAdvice, restituisce HTTP 404)
	 * <ul>
	 * <li>Se un record ONU richiesto non esiste nell'anagrafica del database.</li>
	 * <li>Se il peso di un cluster supera la portata massima di qualsiasi veicolo isolato in flotta.</li>
	 * <li>Se la flotta idonea e disponibile si esaurisce prima di poter soddisfare l'intero piano di carico.</li>
	 * </ul>
	 */
	@PostMapping
	public ResponseEntity<DispatchResponseDTO> dispatch(@RequestBody @Valid DispatchRequestDTO dispatchRequestDTO) {
		return ResponseEntity.ok(dispatchService.dispatcher(dispatchRequestDTO));
	}
}
