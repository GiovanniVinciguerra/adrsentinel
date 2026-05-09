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
import dev.vinciguerra.adrsentinel.db.shipmentitem.ShipmentItem;
import dev.vinciguerra.adrsentinel.db.shipmentitem.ShipmentItemService;
import dev.vinciguerra.adrsentinel.web.annotation.ValidatorUUID;
import dev.vinciguerra.adrsentinel.web.dto.shipmentitem.ShipmentItemRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipmentitem.ShipmentItemResponseDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipmentitem.ShipmentItemUpdateDTO;
import jakarta.validation.Valid;

/**
 * Controller REST (Presentation Layer) dedicato alla gestione del ciclo di vita 
 * delle singole righe di carico (Shipment Items) all'interno del dominio logistico ADR.
 * <p><b>Contesto Architetturale (RESTful API & Separation of Concerns):</b></p>
 * Questa classe agisce come punto di ingresso esclusivo per i client esterni (Frontend, 
 * Mobile App o servizi di terze parti). Il suo mandato architetturale è limitato e preciso: 
 * instradare le richieste HTTP, orchestrare la validazione perimetrale (Edge Validation) 
 * e gestire la serializzazione/deserializzazione dei payload (DTO). Delega rigorosamente 
 * l'intera logica di business, le regole di dominio e il perimetro transazionale al 
 * {@link ShipmentItemService}, rispettando il principio della singola responsabilità (SRP).
 * <p><b>Motore di Validazione Perimetrale (AOP):</b></p>
 * L'annotazione {@code @Validated} a livello di classe abilita l'ispezione proxy AOP 
 * (Aspect-Oriented Programming). Questo garantisce che le validazioni custom 
 * (es. {@code @ValidatorUUID}) dichiarate direttamente sui parametri dei metodi ({@code @PathVariable}) 
 * vengano innescate prima ancora che l'esecuzione raggiunga il corpo del metodo, 
 * bloccando istantaneamente payload o URL malevoli.
 * @author Giovanni Vinciguerra
 * @version 1.0 (REST Interface & Validation Boundary)
 * @since 1.0
 */
@RestController
@RequestMapping("/adr-sentinel/shipment-items")
@Validated
public class ShipmentItemController {
	private final ShipmentItemService shipmentItemService;
	
	/**
	 * Inietta le dipendenze necessarie tramite costruttore (Constructor Injection).
	 * Questa è la best practice raccomandata da Spring per garantire l'immutabilità 
	 * e facilitare i test di unità.
	 */
	public ShipmentItemController(ShipmentItemService shipmentItemService) {
		this.shipmentItemService = shipmentItemService;
	}
	
	/**
	 * Endpoint di lettura puntuale (Point Lookup) per recuperare l'anagrafica completa 
	 * di una specifica riga di carico tramite la sua Business Key.
	 * <p><b>Design API:</b> Operazione sicura e idempotente (HTTP GET).</p>
	 * @param itemUUID L'identificatore univoco universale dell'articolo. 
	 * L'input è sanificato e validato strutturalmente da {@code @ValidatorUUID}.
	 * @return HTTP {@code 200 OK} contenente il {@link ShipmentItemResponseDTO} idratato. 
	 * (In caso di assenza, il gestore globale delle eccezioni intercetterà la 
	 * {@code ResourceNotFoundException} e restituirà HTTP {@code 404 Not Found}).
	 */
	@GetMapping("/{itemUUID}")
	public ResponseEntity<ShipmentItemResponseDTO> getByItemUUID(@PathVariable @ValidatorUUID String itemUUID) {
		ShipmentItem entity = shipmentItemService.getByItemUUID(itemUUID);
		return ResponseEntity.ok(ShipmentItemResponseDTO.fromEntity(entity));
	}
	
	/**
	 * Endpoint di aggregazione per recuperare l'intera distinta di carico (lista di articoli) 
	 * associata a una specifica spedizione "padre".
	 * <p><b>Design Pattern e Performance (Stream API):</b> L'utilizzo delle Stream API di Java 
	 * garantisce una trasformazione funzionale rapida ed efficiente dalla lista di Entity JPA 
	 * alla lista di DTO, schermando il motore JSON (Jackson) dalle insidie del database 
	 * (es. cicli infiniti o proxy non inizializzati).</p>
	 * @param shipmentTracking Il Tracking Number della spedizione. Validato da {@code @ValidatorUUID}.
	 * @return HTTP {@code 200 OK} contenente la lista degli articoli. Se la spedizione esiste 
	 * ma non possiede articoli, restituirà coerentemente un array vuoto {@code []}.
	 */
	@GetMapping("/shipment/{shipmentTracking}")
	public ResponseEntity<List<ShipmentItemResponseDTO>> getByShipmentTrackingNumber(@PathVariable @ValidatorUUID String shipmentTracking) {
		List<ShipmentItem> items = shipmentItemService.getByShipment(shipmentTracking);
		List<ShipmentItemResponseDTO> response = items.stream().map(ShipmentItemResponseDTO::fromEntity).toList();
		return ResponseEntity.ok(response);
	}
	
	/**
	 * Endpoint di mutazione (Creazione) per l'inserimento di un nuovo articolo ADR 
	 * all'interno del sistema e l'associazione a una spedizione.
	 * <p><b>Contesto REST e HTTP Status:</b></p>
	 * L'endpoint rispetta rigorosamente la semantica REST restituendo uno status code 
	 * {@code 201 CREATED} al termine di una transazione di successo, segnalando ai client 
	 * l'avvenuta creazione fisica di una nuova risorsa.
	 * <p><b>Scudo di Validazione ({@code @Valid}):</b> Il payload è ispezionato dal validatore 
	 * JSR-380, che analizza tutte le Macro-Annotazioni interne al DTO prima di concedere 
	 * l'accesso al Service.</p>
	 * @param shipmentItemRequestDTO Il payload piatto contenente le grandezze fisiche e 
	 * i puntatori logici (es. Codice ONU) necessari alla creazione.
	 * @return HTTP {@code 201 CREATED} con il DTO rappresentante la risorsa appena generata, 
	 * arricchito con le chiavi di sistema (ID/UUID) generate dal database.
	 */
	@PostMapping
	public ResponseEntity<ShipmentItemResponseDTO> create(@RequestBody @Valid ShipmentItemRequestDTO shipmentItemRequestDTO) {
		ShipmentItem shipmentItemToSave = shipmentItemService.mapToEntity(shipmentItemRequestDTO);
		ShipmentItem savedShipmentItem = shipmentItemService.save(shipmentItemToSave);
		return ResponseEntity.status(HttpStatus.CREATED).body(ShipmentItemResponseDTO.fromEntity(savedShipmentItem));
	}
	
	/**
	 * Endpoint di mutazione (Aggiornamento Completo/Parziale) per la modifica 
	 * delle grandezze fisiche e dei riferimenti normativi di una riga di carico esistente.
	 * <p><b>Design API:</b> Implementato tramite il verbo HTTP {@code PUT}. L'identificatore 
	 * immutabile della risorsa bersaglio è passato nel Path (Best Practice REST), mentre 
	 * l'intero stato mutabile è contenuto nel Body della richiesta.</p>
	 * @param itemUUID L'identificatore inalterabile dell'articolo bersaglio (Validato strutturalmente).
	 * @param updateDto Il payload validato ({@code @Valid}) contenente i nuovi valori operativi.
	 * @return HTTP {@code 200 OK} con il DTO aggiornato, riflettendo lo stato esatto post-transazione 
	 * del database e delle cache in memoria.
	 */
	@PutMapping("/{itemUUID}")
	public ResponseEntity<ShipmentItemResponseDTO> updateShipmentItemDetails(@PathVariable @ValidatorUUID String itemUUID,
			@RequestBody @Valid ShipmentItemUpdateDTO updateDto) {
		ShipmentItem updatedShipmentItem = shipmentItemService.updateDetailsByItemUUID(itemUUID, updateDto);
		return ResponseEntity.ok(ShipmentItemResponseDTO.fromEntity(updatedShipmentItem));
	}
}
