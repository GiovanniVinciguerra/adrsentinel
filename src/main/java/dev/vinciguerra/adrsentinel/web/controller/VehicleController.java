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
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle;
import dev.vinciguerra.adrsentinel.db.vehicle.VehicleService;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorLicensePlate;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorMaxUsefulWeight;
import dev.vinciguerra.adrsentinel.web.dto.UpdateActiveStatusDTO;
import dev.vinciguerra.adrsentinel.web.dto.vehicle.VehicleRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.vehicle.VehicleResponseDTO;
import dev.vinciguerra.adrsentinel.web.dto.vehicle.VehicleUpdateAdrApprovalDTO;
import dev.vinciguerra.adrsentinel.web.dto.vehicle.VehicleUpdateDTO;
import jakarta.validation.Valid;

/**
 * Controller REST (Presentation Layer) dedicato alla gestione dell'anagrafica 
 * e del ciclo di vita della flotta veicoli all'interno del sistema ADR Sentinel.
 * <p><b>Contesto Architetturale (Separation of Concerns & REST API):</b></p>
 * Questa classe rappresenta l'interfaccia di comunicazione esclusiva tra i client esterni 
 * (es. Web App, dispositivi mobili degli autisti) e il dominio logistico. Il suo mandato 
 * è strettamente limitato all'instradamento del protocollo HTTP, all'orchestrazione 
 * della sicurezza perimetrale (AOP Validation) e alla traduzione dei payload (DTO). 
 * Tutta la logica transazionale e di dominio è delegata rigorosamente al {@link VehicleService}.
 * <p><b>Scudo di Validazione Perimetrale (Edge Validation):</b></p>
 * L'annotazione {@code @Validated} a livello di classe istruisce il framework (Spring AOP) 
 * a intercettare e validare in tempo reale i parametri nativi dei metodi (come i {@code @PathVariable}). 
 * Questo garantisce che payload malevoli o URL malformati (es. targhe non conformi) vengano 
 * respinti con un HTTP 400 Bad Request prima ancora di consumare cicli applicativi.
 * @author Giovanni Vinciguerra
 * @version 1.0 (REST Interface & Validation Boundary)
 * @since 1.0
 */
@RestController
@RequestMapping("/adr-sentinel/vehicles")
@Validated
public class VehicleController {
	private final VehicleService vehicleService;
	
	/**
	 * Inietta le dipendenze necessarie tramite costruttore (Constructor Injection).
	 * Questa pratica garantisce l'immutabilità del controller (thread-safety) e ne 
	 * agevola i test unitari isolati (Mocking).
	 */
	public VehicleController(VehicleService vehicleService) {
		this.vehicleService = vehicleService;
	}
	
	/**
	 * Endpoint di aggregazione globale per recuperare l'intera flotta aziendale.
	 *
	 * <p><b>Design Pattern:</b> Utilizza le Stream API di Java per una proiezione 
	 * funzionale ed efficiente (Mapping) dalla lista di Entity JPA alla lista di DTO 
	 * in uscita, garantendo l'Information Hiding.</p>
	 * @return HTTP {@code 200 OK} contenente la lista completa dei veicoli (Array JSON).
	 */
	@GetMapping
	public ResponseEntity<List<VehicleResponseDTO>> getAllVehicle() {
		List<Vehicle> vehicles = vehicleService.getAllVehicle();
		List<VehicleResponseDTO> response = vehicles.stream().map(VehicleResponseDTO::fromEntity).toList();
		return ResponseEntity.ok(response);
	}
	
	/**
	 * Endpoint di lettura puntuale (Point Lookup) per recuperare la scheda tecnica 
	 * e normativa di un singolo veicolo tramite la sua Targa (Business Key).
	 * @param licensePlate La targa del veicolo. L'input è protetto e sanificato 
	 * dalla macro-annotazione di dominio {@code @ValidatorLicensePlate}.
	 * @return HTTP {@code 200 OK} contenente il DTO del veicolo, oppure HTTP {@code 404 Not Found} 
	 * se la risorsa non è presente a sistema.
	 */
	@GetMapping("/{licensePlate}")
	public ResponseEntity<VehicleResponseDTO> getByLicensePlate(@PathVariable @ValidatorLicensePlate String licensePlate) {
		Vehicle vehicle = vehicleService.getByLicensePlate(licensePlate);
		return ResponseEntity.ok(VehicleResponseDTO.fromEntity(vehicle));
	}
	
	/**
	 * Endpoint di Business Intelligence e Routing Logistico. Recupera una sotto-flotta 
	 * di veicoli che possiedono una portata utile uguale o superiore alla soglia richiesta.
	 * @param maxUsefulWeight La soglia minima di portata utile richiesta (in kg). 
	 * Protetta da injection tramite {@code @ValidatorRequiredNumber}.
	 * @return HTTP {@code 200 OK} contenente i veicoli idonei al carico.
	 */
	@GetMapping("/weight/{maxUsefulWeight}")
	public ResponseEntity<List<VehicleResponseDTO>> getByMaxUsefulWeight(@PathVariable @ValidatorMaxUsefulWeight Integer maxUsefulWeight) {
		List<Vehicle> vehicles = vehicleService.getByMaxUsefulWeightGreaterThanEqual(maxUsefulWeight);
		List<VehicleResponseDTO> response = vehicles.stream().map(VehicleResponseDTO::fromEntity).toList();
		return ResponseEntity.ok(response);
	}
	
	/**
	 * Endpoint di mutazione (Creazione) per censire un nuovo veicolo all'interno della flotta.
	 * <p><b>Contesto REST:</b> Rispetta lo standard HTTP restituendo uno status code 
	 * {@code 201 CREATED} per confermare l'avvenuta genesi della risorsa.</p>
	 * @param vehicleRequestDTO Il payload piatto contenente i dati di immatricolazione. 
	 * Ispezionato dal validatore JSR-380 tramite {@code @Valid} prima dell'elaborazione.
	 * @return HTTP {@code 201 CREATED} con il DTO rappresentante la risorsa appena persistita.
	 */
	@PostMapping
	public ResponseEntity<VehicleResponseDTO> create(@RequestBody @Valid VehicleRequestDTO vehicleRequestDTO) {
		Vehicle vehicleToSave = vehicleService.mapToEntity(vehicleRequestDTO);
		vehicleToSave.setActive(true);
		vehicleToSave.setInTransit(false);
		Vehicle savedVehicle = vehicleService.save(vehicleToSave);
		return ResponseEntity.status(HttpStatus.CREATED).body(VehicleResponseDTO.fromEntity(savedVehicle));
	}
	
	/**
	 * Endpoint di mutazione standard per l'aggiornamento massivo dei parametri fisici 
	 * e omologativi di un veicolo esistente.
	 * @param licensePlate La targa bersaglio (Validata strutturalmente nel path).
	 * @param updateDto Il payload (Validato via {@code @Valid}) contenente il nuovo stato desiderato.
	 * @return HTTP {@code 200 OK} con il DTO sincronizzato post-transazione.
	 */
	@PutMapping("/{licensePlate}")
	public ResponseEntity<VehicleResponseDTO> updateVehicleDetails(@PathVariable @ValidatorLicensePlate String licensePlate, @RequestBody @Valid VehicleUpdateDTO updateDto) {
		Vehicle updatedVehicle = vehicleService.updateDetailsByLicensePlate(licensePlate, updateDto);
		return ResponseEntity.ok(VehicleResponseDTO.fromEntity(updatedVehicle));
	}
	
	/**
	 * Endpoint REST (HTTP PUT) dedicato all'aggiornamento dello stato operativo di un veicolo.
	 * <p>
	 * <b>Semantica RESTful e Idempotenza:</b><br>
	 * L'operazione è mappata sul verbo {@code PUT} in quanto garantisce l'idempotenza: invocazioni 
	 * multiple e identiche di questo endpoint produrranno il medesimo stato finale nel sistema, 
	 * senza effetti collaterali indesiderati. La risorsa da modificare è identificata tramite URI 
	 * utilizzando la sua chiave logica di business (Targa) anziché la chiave surrogata (Database ID), 
	 * esponendo un'API sicura e agnostica rispetto al livello di persistenza.
	 * </p>
	 * <p>
	 * <b>Boundary Validation (Barriera Protettiva Fail-Fast):</b><br>
	 * Il metodo agisce da scudo per il Service Layer delegando al framework due rigidi controlli preliminari:
	 * <ul>
	 * <li><b>URI Validation:</b> L'annotazione custom {@code @ValidatorLicensePlate} ispeziona la Path Variable, 
	 * bloccando istantaneamente (HTTP 400) formati di targa errati o tentativi di iniezione di caratteri non ammessi.</li>
	 * <li><b>Payload Validation:</b> L'annotazione {@code @Valid} innesca il motore di validazione (Hibernate Validator) 
	 * sul corpo della richiesta JSON, garantendo che l'oggetto {@link UpdateActiveStatusDTO} 
	 * non sia malformato e contenga effettivamente il campo booleano richiesto.</li>
	 * </ul>
	 * </p>
	 * @param licensePlate La targa alfanumerica univoca del veicolo, estratta direttamente dal path dell'URI.
	 * @param updateDto Il payload (Data Transfer Object) contenente il nuovo stato desiderato ({@code active}).
	 * @return HTTP {@code 200 OK} con l'oggetto veicolo aggiornato.
	 */
	@PutMapping("/active-status/{licensePlate}")
	public ResponseEntity<VehicleResponseDTO> updateVehicleActiveStatus(@PathVariable @ValidatorLicensePlate String licensePlate,
			@RequestBody @Valid UpdateActiveStatusDTO updateDto) {
		Vehicle updatedVehicle = vehicleService.updateActiveStatusByLicensePlate(licensePlate, updateDto);
		return ResponseEntity.ok(VehicleResponseDTO.fromEntity(updatedVehicle));
	}
	
	/**
	 * Endpoint atomico di State Toggle per l'abilitazione o la revoca istantanea 
	 * delle certificazioni ADR di un veicolo.
	 * <p><b>Design del Payload (Fallback Architetturale):</b></p>
	 * L'assenza dell'annotazione {@code @Valid} su {@code updateDto} è una scelta 
	 * architetturale deliberata. Sfruttando i tipi primitivi (boolean) all'interno del Record, 
	 * il sistema delega al motore JSON (Jackson) un fallback nativo: se il client invia un 
	 * body vuoto ({@code {}}), il sistema assumerà in sicurezza il valore {@code false}, 
	 * revocando le autorizzazioni di default senza generare eccezioni a runtime.
	 * @param licensePlate La targa del veicolo da aggiornare.
	 * @param updateDto Il payload ultraleggero contenente esclusivamente il nuovo stato legale.
	 * @return HTTP {@code 200 OK} con l'oggetto veicolo aggiornato.
	 */
	@PutMapping("/adr-certified/{licensePlate}")
	public ResponseEntity<VehicleResponseDTO> updateVehicleAdrCertified(@PathVariable @ValidatorLicensePlate String licensePlate, @RequestBody VehicleUpdateAdrApprovalDTO updateDto) {
		Vehicle updatedVehicle = vehicleService.updateAdrCertifiedByLicensePlate(licensePlate, updateDto);
		return ResponseEntity.ok(VehicleResponseDTO.fromEntity(updatedVehicle));
	}
}
