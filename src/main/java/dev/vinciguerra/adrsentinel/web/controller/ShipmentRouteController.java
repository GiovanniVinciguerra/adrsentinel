package dev.vinciguerra.adrsentinel.web.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.TunnelRestriction;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment;
import dev.vinciguerra.adrsentinel.db.shipment.ShipmentService;
import dev.vinciguerra.adrsentinel.db.shipmentroute.ShipmentRoute;
import dev.vinciguerra.adrsentinel.db.shipmentroute.ShipmentRouteService;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;
import dev.vinciguerra.adrsentinel.web.annotation.ValidatorUUID;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.ShipmentRouteRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.ShipmentRouteResponseDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.ShipmentRouteUpdateDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.SingleShipmentRouteResponseDTO;
import jakarta.validation.Valid;

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
	 * Espone l'endpoint di lettura per recuperare i dettagli spaziali e metrici 
	 * di un singolo segmento di rotta (Leg).
	 * <p>
	 * <b>Sicurezza (Zero-Trust & Anti-IDOR):</b><br>
	 * L'endpoint è blindato in ingresso dal vincolo custom {@code @ValidatorUUID}, 
	 * che respinge preventivamente formati non conformi con un errore 400 Bad Request. 
	 * Inoltre, la ricerca avviene tramite {@code routeUUID} (Business Key) anziché 
	 * tramite chiave primaria del database, annullando il rischio di enumerazione 
	 * delle risorse (Insecure Direct Object Reference).
	 * </p>
	 * <p>
	 * <b>Isolamento del Dominio:</b><br>
	 * L'entità JPA recuperata dal Service Layer viene incapsulata e trasformata 
	 * nel {@link SingleShipmentRouteResponseDTO} prima di essere serializzata in JSON. 
	 * Questo garantisce che i dati sensibili del database o le logiche di persistenza non vengano 
	 * mai esposti accidentalmente al client (Presentation Layer).
	 * </p>
	 * @param routeUUID L'identificativo alfanumerico pubblico che punta in modo univoco al segmento desiderato.
	 * @return Una {@link ResponseEntity} contenente il DTO della rotta con HTTP Status 200 (OK).
	 * @throws ResourceNotFoundException (Gestita globalmente) se l'UUID non corrisponde ad alcun record.
	 */
	@GetMapping("/{routeUUID}")
	public ResponseEntity<SingleShipmentRouteResponseDTO> getByRouteUUID(@PathVariable @ValidatorUUID String routeUUID) {
		ShipmentRoute route = shipmentRouteService.getByRouteUUID(routeUUID);
		return ResponseEntity.ok(SingleShipmentRouteResponseDTO.fromEntity(route));
	}
	
	/**
	 * Recupera l'intero ecosistema di navigazione (viaggio multi-tappa) associato 
	 * a una specifica spedizione, interrogando il sistema tramite il suo Tracking Number.
	 * <p>
	 * <b>Aggregazione del Payload:</b><br>
	 * L'endpoint non si limita a restituire un array piatto di segmenti, ma costruisce 
	 * una struttura gerarchica (tramite {@link ShipmentRouteResponseDTO}) che include 
	 * la sequenza ordinata delle tratte.
	 * </p>
	 * <p>
	 * <b>Architettura e Performance:</b><br>
	 * Delega l'estrazione al Service Layer, il quale sfrutta il livello di caching (Caffeine) 
	 * per servire la lista in tempi prossimi allo zero se la spedizione è stata interrogata di recente, 
	 * riducendo drasticamente il carico sul database relazionale.
	 * </p>
	 * @param trackingNumber Il codice pubblico di tracciamento della spedizione, sottoposto 
	 * a rigorosa validazione sintattica per bloccare payload malevoli prima dell'elaborazione.
	 * @return Una {@link ResponseEntity} con HTTP Status 200 (OK) e il payload gerarchico del viaggio.
	 */
	@GetMapping("/shipment/{trackingNumber}")
	public ResponseEntity<ShipmentRouteResponseDTO> getByShipmentTrackingNumber(@PathVariable @ValidatorUUID String trackingNumber) {
		List<ShipmentRoute> routes = shipmentRouteService.getByShipmentTrackingNumber(trackingNumber);
		if(routes.isEmpty())
			return ResponseEntity.ok(ShipmentRouteResponseDTO.fromEntity(routes, TunnelRestriction.NONE));
		Shipment shipment = routes.get(0).getShipment();
		return ResponseEntity.ok(ShipmentRouteResponseDTO.fromEntity(routes, shipment.getTunnelRestriction()));
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
	
	/**
	 * Endpoint REST per la creazione e il consolidamento batch dei segmenti di rotta.
	 * <p>
	 * <b>Pattern Operativo (Fase 2: Conferma della Persistenza Differita):</b><br>
	 * Questo metodo rappresenta l'anello finale dell'architettura "Calcola-e-Conferma".
	 * Riceve dal client il set di rotte precedentemente elaborate dal motore cartografico in modalità 
	 * <i>stateless</i> e procede alla loro memorizzazione fisica.
	 * </p>
	 * <p>
	 * <b>Flusso Architetturale:</b>
	 * <ol>
	 * <li><b>Lookup del Parent:</b> Recupera l'entità madre {@link Shipment} tramite il Tracking Number, 
	 * garantendo l'integrità referenziale.</li>
	 * <li><b>Hydration & Mapping:</b> Sfrutta le Stream API e il mapper statico per convertire i DTO 
	 * in entità JPA, iniettando contestualmente l'istanza della spedizione per popolare la Foreign Key 
	 * (lato proprietario della relazione).</li>
	 * <li><b>Persistenza:</b> Delega al Service Layer il salvataggio transazionale in batch.</li>
	 * <li><b>RESTful Compliance:</b> Restituisce rigorosamente uno status code HTTP 201 (CREATED) 
	 * allegando nel body la rappresentazione gerarchica della risorsa appena generata.</li>
	 * </ol>
	 * </p>
	 * @param shipmentRouteRequestDTO Il payload validato contenente la collezione dei segmenti 
	 * confermati dal client e il puntatore (Tracking Number) alla spedizione di appartenenza.
	 * @return Una {@link ResponseEntity} con HTTP Status 201 (CREATED) contenente il DTO completo 
	 * del viaggio multi-tappa.
	 */
	@PostMapping
	public ResponseEntity<ShipmentRouteResponseDTO> create(@RequestBody @Valid ShipmentRouteRequestDTO shipmentRouteRequestDTO) {
		Shipment shipment = shipmentService.getByTrackingNumber(shipmentRouteRequestDTO.shipmentTrackingNumber());
		List<ShipmentRoute> shipmentRoutesToSave = shipmentRouteRequestDTO.routeDetails().stream()
			.map(routeDetailDTO -> shipmentRouteService.mapToEntity(routeDetailDTO, shipment))
			.toList();
		List<ShipmentRoute> savedShipmentRoutes = shipmentRouteService.save(shipmentRoutesToSave);
		return ResponseEntity.status(HttpStatus.CREATED).body(ShipmentRouteResponseDTO.fromEntity(savedShipmentRoutes, shipment.getTunnelRestriction()));
	}
	
	/**
	 * Endpoint REST per l'aggiornamento puntuale delle metriche e della geometria di uno specifico segmento.
	 * <p>
	 * <b>Design Pattern (Thin Controller & Clean Architecture):</b><br>
	 * Questo metodo funge da puro "Passacarte" (Facade) verso il livello di business. 
	 * Non contiene logica di dominio (es. verifiche sullo stato della spedizione madre). Il controllo 
	 * sulle mutazioni illegali (es. modifica di una spedizione non più in stato {@code PLANNED}) 
	 * è centralizzato e blindato all'interno del Service Layer transazionale, che solleverà le 
	 * opportune eccezioni (es. HTTP 409 Conflict) se i vincoli legali vengono violati.
	 * </p>
	 * <p>
	 * <b>Sicurezza (Fail-Fast & Anti-IDOR):</b>
	 * <ul>
	 * <li><b>@ValidatorUUID:</b> Ispeziona e blocca la request a monte se l'identificativo in URL è malformato o tenta attacchi di iniezione.</li>
	 * <li><b>@Valid:</b> Attiva la catena di validatori custom sul JSON in ingresso (es. {@code @ValidatorLatitude}, {@code @ValidatorETA}), 
	 * rigettando payload fisicamente o logicamente impossibili (HTTP 400).</li>
	 * <li><b>UUID come Business Key:</b> Maschera completamente la Primary Key del database verso l'esterno.</li>
	 * </ul>
	 * </p>
	 * @param routeUUID Il parametro di path (identificativo pubblico) che individua univocamente la tratta da mutare.
	 * @param updateDto Il payload (Request Body) contenente i nuovi valori spaziali e temporali della tratta.
	 * @return Una {@link ResponseEntity} con HTTP Status 200 (OK) che incapsula la vista aggiornata del singolo segmento.
	 */
	@PutMapping("/{routeUUID}")
	public ResponseEntity<SingleShipmentRouteResponseDTO> updateByRouteUUID(@PathVariable @ValidatorUUID String routeUUID, @RequestBody @Valid ShipmentRouteUpdateDTO updateDto) {
		ShipmentRoute updatedShipmentRoute = shipmentRouteService.updateByRouteUUID(routeUUID, updateDto);
		return ResponseEntity.ok(SingleShipmentRouteResponseDTO.fromEntity(updatedShipmentRoute));
	}
}
