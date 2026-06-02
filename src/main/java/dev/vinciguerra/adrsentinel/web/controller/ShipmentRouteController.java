package dev.vinciguerra.adrsentinel.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import dev.vinciguerra.adrsentinel.db.adrclass.shipmentroute.ShipmentRouteService;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment;
import dev.vinciguerra.adrsentinel.db.shipment.ShipmentService;
import dev.vinciguerra.adrsentinel.web.annotation.ValidatorUUID;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.ShipmentRouteResponseDTO;

/**
 * Controller REST (Presentation Layer) responsabile dell'esposizione degli endpoint HTTP 
 * per l'orchestrazione, il calcolo e il recupero delle rotte logistiche (Shipment Routes) 
 * specifiche per il trasporto di merci (con e senza vincoli ADR).
 * <p>
 * <b>Ruolo Architetturale (Thin Controller Pattern):</b><br>
 * Questa classe agisce esclusivamente come interfaccia di frontiera (Boundary) tra il mondo 
 * esterno (client web, app mobili) e il nucleo dell'applicazione. Delega interamente 
 * le complesse logiche di business, la geolocalizzazione e l'integrazione con OpenRouteService 
 * ai servizi sottostanti ({@link ShipmentRouteService}), rispettando il principio di Singola 
 * Responsabilità (SRP) e mantenendo il codice pulito e testabile.
 * </p>
 * <p>
 * <b>Sicurezza e Validazione (Defensive Programming):</b><br>
 * L'annotazione {@link Validated} a livello di classe è fondamentale: istruisce Spring Boot 
 * ad abilitare la validazione JSR-380 direttamente sui parametri dei metodi (come il {@code @PathVariable}). 
 * Questo garantisce uno strato di protezione "Fail-Fast": richieste con stringhe malformate 
 * o potenzialmente dannose vengono respinte con un HTTP 400 Bad Request prima ancora di 
 * allocare risorse nel Service Layer o toccare il database.
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0 (REST Interface & Validation Boundary)
 * @since 1.0
 */
@RestController
@RequestMapping("/adr-sentinel/routing")
@Validated
public class ShipmentRouteController {
	private final ShipmentRouteService shipmentRouteService;
	private final ShipmentService shipmentService;
	
	/**
	 * Costruttore per l'iniezione delle dipendenze (Constructor Injection).
	 * <p>
	 * <b>Design Pattern:</b><br>
	 * L'utilizzo dell'iniezione tramite costruttore (invece di {@code @Autowired} sui campi) 
	 * garantisce l'immutabilità dei servizi (dichiarati {@code final}), previene la creazione 
	 * di bean in stati inconsistenti e facilita notevolmente la scrittura di Unit Test tramite 
	 * l'iniezione di Mock (es. con Mockito).
	 * </p>
	 * @param shipmentRouteService Il servizio orchestratore che gestisce il motore di calcolo delle rotte.
	 * @param shipmentService Il servizio deputato all'interazione con il database per le entità Spedizione.
	 */
	public ShipmentRouteController(ShipmentRouteService shipmentRouteService, ShipmentService shipmentService) {
		this.shipmentRouteService = shipmentRouteService;
		this.shipmentService = shipmentService;
	}
	
	/**
	 * Endpoint REST (GET) deputato al calcolo e all'estrazione della rotta stradale (profilo HGV) 
	 * ottimizzata per una specifica spedizione, valutando attivamente le restrizioni gallerie 
	 * applicabili in base alle distinte di carico (merci pericolose).
	 * <p>
	 * <b>Flusso di Esecuzione (Workflow):</b>
	 * <ol>
	 * <li>Il client invia la richiesta HTTP GET fornendo il Tracking Number.</li>
	 * <li>La sintassi dell'input viene validata formalmente dalla custom annotation {@code @ValidatorUUID}.</li>
	 * <li>Il controller richiede al Database l'intera entità {@link Shipment} associata.</li>
	 * <li>L'entità viene passata all'orchestratore di routing che si occupa della traduzione degli 
	 * indirizzi (Geocoding), dell'estrazione dei limiti ADR e del calcolo cartografico.</li>
	 * <li>Il risultato viene incapsulato e restituito al client.</li>
	 * </ol>
	 * </p>
	 * <p>
	 * <b>Sicurezza (Anti-IDOR Strategy):</b><br>
	 * L'utilizzo del Tracking Number nel formato UUID come parametro di ricerca, al posto della 
	 * Primary Key sequenziale (ID), è una misura di sicurezza proattiva contro gli attacchi di tipo 
	 * Insecure Direct Object Reference (IDOR) e di enumerazione delle risorse.
	 * </p>
	 * @param shipmentTrackingNumber L'identificativo pubblico univoco della spedizione, 
	 * strutturato e validato come UUIDv4 (es. "293cf13b-738d-4831-9fcd-da1917ee6171").
	 * @return Un oggetto {@link ResponseEntity} contenente il Data Transfer Object 
	 * {@link ShipmentRouteResponseDTO} (con status code HTTP 200 OK) che espone i chilometri 
	 * calcolati, l'ETA, i vincoli ADR e la geometry Polyline per il rendering su mappa.
	 * <p>
	 * <b>Eccezioni gestite globalmente (tramite GlobalExceptionHandler):</b><br>
	 * - <b>HTTP 404 (Not Found):</b> Se il Tracking Number non esiste nel database.<br>
	 * - <b>HTTP 400 (Bad Request):</b> Se la stringa passata non rispetta il pattern UUID.<br>
	 * - <b>HTTP 503 (Service Unavailable):</b> Se il provider esterno (OpenRouteService) è irraggiungibile.
	 * </p>
	 */
	@GetMapping("/{shipmentTrackingNumber}")
	public ResponseEntity<ShipmentRouteResponseDTO> routing(@PathVariable @ValidatorUUID String shipmentTrackingNumber) {
		Shipment shipment = shipmentService.getByTrackingNumber(shipmentTrackingNumber);
		return ResponseEntity.ok(shipmentRouteService.routing(shipment));
	}
}
