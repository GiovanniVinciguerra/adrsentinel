package dev.vinciguerra.adrsentinel.web.controller;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import dev.vinciguerra.adrsentinel.db.shipment.Shipment;
import dev.vinciguerra.adrsentinel.db.shipment.ShipmentService;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment.ShipmentStatus;
import dev.vinciguerra.adrsentinel.web.annotation.shipment.ValidatorLocalDate;
import dev.vinciguerra.adrsentinel.web.annotation.shipment.ValidatorShipmentStatus;
import dev.vinciguerra.adrsentinel.web.annotation.shipment.ValidatorTrackingNumber;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorLicensePlate;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentResponseDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentUpdateDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentUpdateStatusDTO;
import jakarta.validation.Valid;

/**
 * Controller REST primario (Presentation Layer) per la gestione del ciclo di vita 
 * delle spedizioni di merci pericolose (ADR-Sentinel).
 * <p><b>Contesto Architetturale e Design API:</b></p>
 * Questa classe funge da punto di ingresso (Entrypoint) per tutti i client esterni 
 * (Frontend, API di terze parti, dispositivi mobili) che necessitano di interagire 
 * con l'aggregato {@code Shipment}. Espone un'interfaccia HTTP pienamente conforme 
 * ai principi RESTful, implementando la separazione delle responsabilità (Separation 
 * of Concerns) delegando la logica di business al {@code ShipmentService}.
 * <p><b>Edge Validation e Sicurezza:</b></p>
 * L'annotazione {@code @Validated} a livello di classe abilita l'ispezione AOP 
 * (Aspect-Oriented Programming) di Spring sui parametri dei metodi. Questo permette 
 * alle annotazioni custom (es. {@code @ValidatorTrackingNumber}, {@code @ValidatorLocalDate}) 
 * applicate sui {@code @PathVariable} di scattare ancor prima che il metodo venga eseguito, 
 * bloccando payload malevoli o malformati al perimetro dell'applicazione (Anti-Corruption Layer).
 * <p><b>Pattern DTO (Data Transfer Object):</b></p>
 * Nessuna entità di database (Entity) viene mai esposta direttamente all'esterno. 
 * Il Controller agisce da traduttore bidirezionale, consumando {@code RequestDTO} 
 * e producendo {@code ResponseDTO}, schermando così la struttura relazionale del 
 * database dai contratti API (API Contract).
 * @author Giovanni Vinciguerra
 * @version 1.0 (REST Interface & Validation Boundary)
 * @since 1.0
 */
@RestController
@RequestMapping("/adr-sentinel/shipments")
@Validated
public class ShipmentController {
	private final ShipmentService shipmentService;
	
	/**
	 * Inietta le dipendenze necessarie tramite costruttore (Constructor Injection).
	 * Questa è la best practice raccomandata da Spring per garantire l'immutabilità 
	 * e facilitare i test di unità.
	 */
	public ShipmentController(ShipmentService shipmentService) {
		this.shipmentService = shipmentService;
	}
	
	/**
	 * Recupera l'elenco globale e paginato di tutte le spedizioni presenti a sistema.
	 * @param pageable Configurazione di paginazione e ordinamento (es. size, page, sort) 
	 * iniettata automaticamente da Spring MVC tramite query parameters.
	 * @return Un payload HTTP 200 (OK) contenente la pagina di {@link ShipmentResponseDTO}.
	 */
	@GetMapping
	public ResponseEntity<Page<ShipmentResponseDTO>> getAll(Pageable pageable) {
		Page<Shipment> page = shipmentService.getAllShipment(pageable);
		Page<ShipmentResponseDTO> response = page.map(this::mapToDTO);
		return ResponseEntity.ok(response);
	}
	
	/**
	 * Esegue una ricerca filtrata delle spedizioni in base al loro stato logistico corrente.
	 * <p><b>Conversione e Validazione:</b></p>
	 * Lo stato viene ricevuto come Stringa (sanitizzata da {@code @ValidatorShipmentStatus}) 
	 * e convertito rigorosamente nell'Enum {@link ShipmentStatus} prima di interrogare il Service.
	 * @param status La stringa rappresentante lo stato (es. "IN_VIAGGIO").
	 * @param pageable Configurazione di paginazione e ordinamento.
	 * @return Un payload HTTP 200 (OK) contenente i risultati filtrati.
	 */
	@GetMapping("/status/{status}")
	public ResponseEntity<Page<ShipmentResponseDTO>> getByStatus(@PathVariable @ValidatorShipmentStatus String status, Pageable pageable) {
		Page<Shipment> page = shipmentService.getByShipmentStatus(Enum.valueOf(ShipmentStatus.class, status), pageable);
		Page<ShipmentResponseDTO> response = page.map(this::mapToDTO);
		return ResponseEntity.ok(response);
	}
	
	/**
	 * Ricerca tutte le spedizioni assegnate a un determinato veicolo della flotta.
	 * @param licensePlate La targa alfanumerica del veicolo, normalizzata e validata da 
	 * {@code @ValidatorLicensePlate} (es. nessuna spaziatura, solo maiuscole).
	 * @param pageable Configurazione di paginazione.
	 * @return Un payload HTTP 200 (OK) con le spedizioni associate al mezzo.
	 */
	@GetMapping("/vehicle/{licensePlate}")
	public ResponseEntity<Page<ShipmentResponseDTO>> getByVehicle(@PathVariable @ValidatorLicensePlate String licensePlate, Pageable pageable) {
		Page<Shipment> page = shipmentService.getByVehicle(licensePlate, pageable);
		Page<ShipmentResponseDTO> response = page.map(this::mapToDTO);
		return ResponseEntity.ok(response);
	}
	
	/**
	 * Esegue un lookup puntuale e ad altissime prestazioni (O(1) in caso di Cache Hit) 
	 * utilizzando la Business Key identificativa della spedizione.
	 * @param tracking Il codice UUID univoco generato a sistema, validato da {@code @ValidatorTrackingNumber}.
	 * @return Un payload HTTP 200 (OK) con il DTO della spedizione richiesta.
	 */
	@GetMapping("/{tracking}")
	public ResponseEntity<ShipmentResponseDTO> getByTrackingNumber(@PathVariable @ValidatorTrackingNumber String tracking) {
		Shipment shipment = shipmentService.getByTrackingNumber(tracking);
		return ResponseEntity.ok(mapToDTO(shipment));
	}
	
	/**
	 * Estrae l'intera lista di spedizioni programmate o partite in una specifica data.
	 * <p><b>Gestione Temporale:</b></p>
	 * Riceve la data in formato stringa ISO 8601 (sanitizzata da {@code @ValidatorLocalDate}) 
	 * ed esegue il parsing in {@link LocalDate} solo al momento dell'ingresso nella logica.
	 * @param date La stringa della data nel formato YYYY-MM-DD.
	 * @return Un payload HTTP 200 (OK) con la lista completa delle spedizioni giornaliere.
	 */
	@GetMapping("/date/{date}")
	public ResponseEntity<List<ShipmentResponseDTO>> getByShipmentDate(@PathVariable @ValidatorLocalDate String date) {
		LocalDate parsedDate = LocalDate.parse(date);
		List<Shipment> shipments = shipmentService.getByShipmentDate(parsedDate);
		List<ShipmentResponseDTO> response = shipments.stream().map(this::mapToDTO).toList();
		return ResponseEntity.ok(response);
	}
	
	/**
	 * Endpoint di mutazione (Write) per la registrazione di una nuova spedizione ADR.
	 * @param shipmentRequestDTO Il payload JSON in ingresso contenente i dati logistici, 
	 * validato in modo ricorsivo grazie a {@code @Valid}.
	 * @return Un payload HTTP 201 (CREATED) contenente i dati della spedizione appena salvata 
	 * (incluso l'ID generato e il Tracking Number).
	 */
	@PostMapping
	public ResponseEntity<ShipmentResponseDTO> create(@RequestBody @Valid ShipmentRequestDTO shipmentRequestDTO) {
		Shipment shipmentToSave = shipmentService.mapToEntity(shipmentRequestDTO);
		Shipment savedShipment = shipmentService.save(shipmentToSave);
		return ResponseEntity.status(HttpStatus.CREATED).body(mapToDTO(savedShipment));
	}
	
	/**
	 * Esegue un aggiornamento idempotente (PUT) sui dettagli operativi di una spedizione esistente.
	 * @param tracking Il tracking number della spedizione da modificare.
	 * @param updateDto Il payload contenente le nuove specifiche (origini, destinazioni, distanze).
	 * @return Un payload HTTP 200 (OK) con l'entità aggiornata e riallineata in cache.
	 */
	@PutMapping("/{tracking}")
	public ResponseEntity<ShipmentResponseDTO> updateShipmentDetails(@PathVariable @ValidatorTrackingNumber String tracking,
			@RequestBody @Valid ShipmentUpdateDTO updateDto) {
		Shipment updatedShipment = shipmentService.updateDetailsByTrackingNumber(tracking, updateDto);
		return ResponseEntity.ok(mapToDTO(updatedShipment));
	}
	
	/**
	 * Aggiorna esclusivamente lo stato del ciclo di vita della spedizione.
	 * Pur agendo come un aggiornamento parziale (PATCH-like), utilizza il verbo PUT per 
	 * sostituire in toto la risorsa logica di "Stato".
	 * @param tracking Il tracking number della spedizione.
	 * @param updateStatusDTO Il payload snello contenente solo il nuovo stato logistico.
	 * @return Un payload HTTP 200 (OK) a conferma dell'avvenuta transizione di stato.
	 */
	@PutMapping("/status/{tracking}")
	public ResponseEntity<ShipmentResponseDTO> updateShipmentStatus(@PathVariable @ValidatorTrackingNumber String tracking,
			@RequestBody @Valid ShipmentUpdateStatusDTO updateStatusDTO) {
		Shipment updatedShipment = shipmentService.updateStatusByTrackingNumber(tracking, updateStatusDTO);
		return ResponseEntity.ok(mapToDTO(updatedShipment));
	}
	
	/**
	 * Metodo di utilità per isolare la logica di mappatura Entity -> DTO.
	 * Converte il modello di dominio complesso in un oggetto piatto e sicuro per la serializzazione JSON.
	 */
	private ShipmentResponseDTO mapToDTO(Shipment entity) {
		return new ShipmentResponseDTO(
			entity.getId(),
			entity.getTrackingNumber(),
			entity.getShipmentDate().toString(),
			entity.getShipmentStatus(),
			entity.getOriginAddress(),
			entity.getDestinationAddress(),
			entity.getDistancekm(),
			entity.getVehicle()
		);
	}
}
