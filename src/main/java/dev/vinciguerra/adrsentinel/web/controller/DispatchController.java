package dev.vinciguerra.adrsentinel.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import dev.vinciguerra.adrsentinel.db.dispatch.DispatchService;
import dev.vinciguerra.adrsentinel.web.dto.dispatch.DriverDispatchRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.dispatch.DriverDispatchResponseDTO;
import dev.vinciguerra.adrsentinel.web.dto.dispatch.VehicleDispatchRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.dispatch.VehicleDispatchResponseDTO;
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
	 * @return Una {@link ResponseEntity} contenente il {@link VehicleDispatchResponseDTO} con stato HTTP 200 (OK). 
	 * Il body include l'array dei viaggi generati, dettagliando per ognuno il veicolo scelto, 
	 * le merci allocate e l'eventuale applicazione del regime di esenzione.
	 * @throws dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException (catturata dal RestControllerAdvice, restituisce HTTP 404)
	 * <ul>
	 * <li>Se un record ONU richiesto non esiste nell'anagrafica del database.</li>
	 * <li>Se il peso di un cluster supera la portata massima di qualsiasi veicolo isolato in flotta.</li>
	 * <li>Se la flotta idonea e disponibile si esaurisce prima di poter soddisfare l'intero piano di carico.</li>
	 * </ul>
	 */
	@PostMapping("/vehicle")
	public ResponseEntity<VehicleDispatchResponseDTO> dispatch(@RequestBody @Valid VehicleDispatchRequestDTO dispatchRequestDTO) {
		return ResponseEntity.ok(dispatchService.vehicleDispatcher(dispatchRequestDTO));
	}
	
	/**
	 * Endpoint REST per l'assegnazione ottimizzata e normativamente conforme degli autisti (Driver Dispatch).
	 * <p>
	 * Riceve in input i parametri di un trasporto ADR già preventivamente associato a un veicolo 
	 * (inclusa la durata stimata della tratta e le specificità chimico-fisiche del carico). 
	 * Il motore decisionale incrocia questi dati con la disponibilità in tempo reale della flotta, 
	 * applicando rigorosamente le normative di settore:
	 * <ul>
	 * <li><b>Equipaggio:</b> Assegnazione di un secondo conducente per tratte impegnative (stima > 10 ore di guida).</li>
	 * <li><b>Conformità CQC:</b> Verifica della Carta di Qualificazione del Conducente se la massa del mezzo supera le 3.5 tonnellate.</li>
	 * <li><b>Conformità ADR:</b> Verifica della validità e della congruenza dei patentini specialistici (Base, TANK, Esplosivi, ecc.) 
	 * in stretta osservanza del Capitolo 8.2 dell'Accordo ADR.</li>
	 * </ul>
	 * L'algoritmo implementa inoltre una logica di conservazione delle risorse, prediligendo l'assegnazione 
	 * di autisti con certificazioni base per trasporti semplici, preservando il personale specializzato.
	 * </p>
	 * @param dispatchRequestDTO Il Data Transfer Object contenente i dati del viaggio, le classi ADR e il veicolo. 
	 * L'annotazione {@link Valid} innesca le validazioni JSR-380 (Hibernate Validator) per intercettare 
	 * payload malformati prima dell'elaborazione del Service layer.
	 * @return Una {@link ResponseEntity} contenente il {@link DriverDispatchResponseDTO} con stato HTTP 200 (OK). 
	 * Il body include la lista degli autisti (1 o 2) validati e pronti per l'assegnazione al documento di trasporto.
	 * @throws dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException (catturata dal RestControllerAdvice, restituisce HTTP 404) 
	 * se nessun autista attualmente disponibile (non in transito) possiede i requisiti legali e certificativi richiesti.
	 */
	@PostMapping("/driver")
	public ResponseEntity<DriverDispatchResponseDTO> dispatch(@RequestBody @Valid DriverDispatchRequestDTO dispatchRequestDTO) {
		return ResponseEntity.ok(dispatchService.driverDispatcher(dispatchRequestDTO));
	}
}
