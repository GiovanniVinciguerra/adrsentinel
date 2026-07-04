package dev.vinciguerra.adrsentinel.web.controller;

import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import dev.vinciguerra.adrsentinel.db.driver.Driver;
import dev.vinciguerra.adrsentinel.db.driver.DriverService;
import dev.vinciguerra.adrsentinel.db.driver.DriverSnapshot;
import dev.vinciguerra.adrsentinel.db.driver.DriverSnapshotService;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment.ShipmentStatus;
import dev.vinciguerra.adrsentinel.db.shipment.ShipmentService;
import dev.vinciguerra.adrsentinel.web.annotation.ValidatorUUID;
import dev.vinciguerra.adrsentinel.web.dto.driver.DriverRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.driver.DriverResponseDTO;
import dev.vinciguerra.adrsentinel.web.dto.driver.DriverSearchRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.driver.DriverUpdateActiveStatusDTO;
import dev.vinciguerra.adrsentinel.web.dto.driver.DriverUpdateAdrApprovalDTO;
import dev.vinciguerra.adrsentinel.web.dto.driver.DriverUpdateDTO;
import jakarta.validation.Valid;

/**
 * Controller REST (Presentation Layer) che gestisce il ciclo di vita e le operazioni 
 * sulle risorse di tipo Conducente (Driver) all'interno del modulo ADR-Sentinel.
 * <p><b>Dettagli Architetturali:</b></p>
 * <ul>
 * <li><b>Endpoint Base:</b> Tutte le rotte esposte da questa classe sono relative al path {@code /adr-sentinel/drivers}.</li>
 * <li><b>Validazione a cascata:</b> L'annotazione di classe {@code @Validated} abilita il motore 
 * di validazione di Spring (Method Validation) direttamente sui parametri dei metodi (es. {@code @PathVariable}). 
 * Questo garantisce che payload malformati vengano scartati con un HTTP 400 Bad Request prima ancora 
 * di invocare la logica di business.</li>
 * <li><b>DTO Pattern:</b> Il controller non espone mai direttamente le entità JPA al client. Tutte le 
 * risposte sono mascherate tramite {@link DriverResponseDTO}, mentre le richieste in ingresso 
 * passano per DTO di comando specifici (es. {@link DriverRequestDTO}).</li>
 * </ul>
 * @author Giovanni Vinciguerra
 * @version 1.0 (REST Interface & Validation Boundary)
 * @since 1.0
 */
@RestController
@RequestMapping("/adr-sentinel/drivers")
@Validated
public class DriverController {
	private final ShipmentService shipmentService;
	private final DriverService driverService;
	private final DriverSnapshotService driverSnapshotService;
	
	/**
	 * Costruttore per la Dependency Injection (IoC) del servizio di dominio.
	 * @param driverService Il servizio contenente la logica di business per le entità Driver.
	 */
	public DriverController(ShipmentService shipmentService, DriverService driverService, DriverSnapshotService driverSnapshotService) {
		this.shipmentService = shipmentService;
		this.driverService = driverService;
		this.driverSnapshotService = driverSnapshotService;
	}
	
	/**
	 * Recupera l'elenco completo di tutti i conducenti registrati a sistema.
	 * <p><b>Comportamento:</b> Il metodo interroga il database, estrae le entità 
	 * e mappa ciascuna di esse nel corrispondente contratto di uscita tramite Stream API.</p>
	 * @return Una {@link ResponseEntity} contenente HTTP 200 (OK) e una lista di {@link DriverResponseDTO}. 
	 * Restituisce una lista vuota se non sono presenti record.
	 */
	@GetMapping
	public ResponseEntity<List<DriverResponseDTO>> getAllDriver() {
		List<Driver> drivers = driverService.getAllDriver();
		List<DriverResponseDTO> response = drivers.stream().map(DriverResponseDTO::fromEntity).toList();
		return ResponseEntity.ok(response);
	}
	
	/**
	 * Recupera i dettagli anagrafici e documentali di un singolo conducente ricercandolo per patente.
	 * <p><b>Scelta Architetturale (POST for Search):</b></p>
	 * <p>Sebbene questa sia logicamente un'operazione di sola lettura, l'endpoint è esposto 
	 * intenzionalmente tramite metodo HTTP {@code POST} (rotta {@code /search}). Questa scelta 
	 * previene l'esposizione di Dati Personali Identificabili (PII), come il numero di patente, 
	 * all'interno degli URL (tipico delle richieste GET). In questo modo, i dati sensibili 
	 * viaggiano cifrati nel Body della richiesta grazie al protocollo HTTPS, evitando di essere 
	 * registrati in chiaro nei log dei web server (es. Nginx/Tomcat), nei proxy o nelle cronologie 
	 * dei client.</p>
	 * <p><b>Sicurezza e Validazione:</b></p>
	 * <p>Il payload di ricerca è protetto dall'annotazione {@code @Valid}. Qualsiasi vincolo 
	 * di validazione applicato all'interno del {@link DriverSearchRequestDTO} (come ad esempio 
	 * {@code @ValidatorLicense}) verrà scatenato automaticamente a livello di framework. 
	 * Se il client inietta una stringa non conforme, l'architettura respingerà la richiesta 
	 * con un HTTP 400 (Bad Request) prima di eseguire query inutili verso il database.</p>
	 * @param searchDto Il payload in formato JSON contenente i parametri di ricerca (es. il numero 
	 * di patente da interrogare), rigorosamente validato.
	 * @return Una {@link ResponseEntity} contenente HTTP 200 (OK) e il payload {@link DriverResponseDTO}.
	 */
	@PostMapping("/search")
	public ResponseEntity<DriverResponseDTO> getByLicense(@RequestBody @Valid DriverSearchRequestDTO searchDto) {
		Driver driver = driverService.getByLicense(searchDto.license());
		return ResponseEntity.ok(DriverResponseDTO.fromEntity(driver));
	}
	
	/**
	 * Recupera la lista degli autisti assegnati a una specifica spedizione, interrogando 
	 * dinamicamente la corretta fonte di verità in base allo stato logistico attuale.
	 * <p><b>Contesto Architetturale (Router dei Dati):</b></p>
	 * Questo endpoint funge da "Smart Router" per l'estrazione dei dati. In accordo con le regole 
	 * della macchina a stati del dominio logistico, l'entità master (Autisti) viene 
	 * fisicamente scollegata dalla spedizione non appena questa abbandona lo stato iniziale. 
	 * Pertanto, il metodo adatta la sua strategia di fetch per garantire sempre la coerenza del dato:
	 * <ul>
	 * <li><b>Stato Attivo ({@code PLANNED}):</b> La spedizione è ancora in fase di pianificazione. 
	 * La risorsa master è fisicamente collegata all'entità {@link Shipment}. I dati vengono estratti 
	 * navigando direttamente le relazioni JPA ({@code shipment.getDrivers()}).</li>
	 * <li><b>Stati Storici ({@code TRANSIT}, {@code DELIVERED}, {@code CANCELED}):</b> La spedizione 
	 * ha lasciato la fase di pianificazione, i legami con il master originale sono stati recisi 
	 * e il "manifest" è stato congelato. I dati vengono recuperati interrogando lo storico 
	 * immutabile ({@link DriverSnapshot}) tramite il {@code driverSnapshotService}.</li>
	 * </ul>
	 * <p><b>Flusso Operativo:</b></p>
	 * <ol>
	 * <li>Lookup della spedizione tramite la sua Business Key ({@code trackingNumber}).</li>
	 * <li>Valutazione condizionale dello stato ({@link ShipmentStatus}).</li>
	 * <li>Estrazione delle entità (Master o Snapshot) e proiezione dei dati nel 
	 * Data Transfer Object ({@link DriverResponseDTO}) tramite method reference ({@code fromEntity}).</li>
	 * </ol>
	 * @param trackingNumber Identificativo univoco (validato come UUID tramite {@code @ValidatorUUID}) 
	 * della spedizione di cui si richiedono gli autisti.
	 * @return Una {@link ResponseEntity} contenente la lista di {@link DriverResponseDTO}. La lista può 
	 * essere vuota se non ci sono autisti assegnati, ma restituisce sempre lo status 200 (OK) 
	 * in caso di esecuzione corretta.
	 * @throws ResourceNotFoundException (o eccezione analoga sollevata da {@code shipmentService}) 
	 * se il {@code trackingNumber} non esiste a sistema.
	 * @throws ConstraintViolationException (sollevata a monte a livello di dispatcher) se il 
	 * {@code trackingNumber} non rispetta il formato UUID richiesto.
	 */
	@GetMapping("/{trackingNumber}")
	public ResponseEntity<List<DriverResponseDTO>> getByShipmentTrackingNumber(@ValidatorUUID String trackingNumber) {
		Shipment shipment = shipmentService.getByTrackingNumber(trackingNumber);
		List<DriverResponseDTO> response;
		if(shipment.getShipmentStatus() != ShipmentStatus.PLANNED) {
			List<DriverSnapshot> snaps = driverSnapshotService.getByShipmentId(shipment.getId());
			response = snaps.stream()
				.map(DriverResponseDTO::fromEntity)
				.toList();
		} else {
			Set<Driver> drivers = shipment.getDrivers();
			response = drivers.stream()
				.map(DriverResponseDTO::fromEntity)
				.toList();
		}
		return ResponseEntity.ok(response);
	}
	
	/**
	 * Registra un nuovo conducente a sistema elaborando il payload di richiesta del client.
	 * <p><b>Regole di Business Applicate:</b></p>
	 * <ul>
	 * <li>Igienizzazione e validazione formale del payload garantita da {@code @Valid}.</li>
	 * <li>Impostazione dello stato di default: Il nuovo conducente viene forzato come "Attivo" 
	 * ({@code active = true}) e "Non in transito" ({@code inTransit = false}) a prescindere 
	 * dal contenuto del DTO di ingresso.</li>
	 * </ul>
	 * @param driverRequestDto Il payload di creazione rigorosamente validato.
	 * @return Una {@link ResponseEntity} contenente HTTP 201 (CREATED) e la rappresentazione del conducente appena salvato.
	 */
	@PostMapping
	public ResponseEntity<DriverResponseDTO> create(@RequestBody @Valid DriverRequestDTO driverRequestDto) {
		Driver driverToSave = driverService.mapToEntity(driverRequestDto);
		driverToSave.setActive(true);
		driverToSave.setInTransit(false);
		Driver savedDriver = driverService.save(driverToSave);
		return ResponseEntity.status(HttpStatus.CREATED).body(DriverResponseDTO.fromEntity(savedDriver));
	}
	
	/**
	 * Aggiorna i dati anagrafici e documentali di un conducente preesistente, identificato dalla sua patente.
	 * <p>La logica di aggiornamento (aggiornamento completo o parziale) viene delegata al {@link DriverService}. 
	 * Entrambi i parametri di input (path variable e body) sono rigorosamente validati.</p>
	 * @param updateDto Il payload contenente le informazioni aggiornate (es. nuovo telefono, rinnovo patente) e il 
	 * numero patente. 
	 * @return Una {@link ResponseEntity} contenente HTTP 200 (OK) e il profilo del conducente aggiornato.
	 */
	@PutMapping
	public ResponseEntity<DriverResponseDTO> updateDriverDetails(@RequestBody @Valid DriverUpdateDTO updateDto) {
		Driver updatedDriver = driverService.updateDetailsByLicense(updateDto);
		return ResponseEntity.ok(DriverResponseDTO.fromEntity(updatedDriver));
	}
	
	/**
	 * Endpoint granulare per l'aggiornamento esclusivo dello stato operativo del conducente (es. Sospensione / Riattivazione).
	 * <p>Questo endpoint favorisce un design REST orientato ai task (Task-Based API), permettendo 
	 * modifiche di stato rapide senza dover reinviare l'intera anagrafica del driver.</p>
	 * @param updateDto Il DTO mirato contenente esclusivamente i nuovi flag di stato operativo e il numero patente.
	 * @return Una {@link ResponseEntity} contenente HTTP 200 (OK) e il profilo del conducente con lo stato modificato.
	 */
	@PutMapping("/active-status")
	public ResponseEntity<DriverResponseDTO> updateDriverActiveStatus(@RequestBody @Valid DriverUpdateActiveStatusDTO updateDto) {
		Driver updatedDriver = driverService.updateActiveStatusByLicense(updateDto);
		return ResponseEntity.ok(DriverResponseDTO.fromEntity(updatedDriver));
	}
	
	/**
	 * Endpoint granulare per la gestione e l'aggiornamento delle certificazioni/abilitazioni (es. rinnovo CQC o certificati ADR).
	 * @param license La patente del conducente bersaglio.
	 * @param updateDto Il DTO contenente il set di certificazioni ADR o abilitazioni da applicare. 
	 * (Nota architetturale: se necessario, assicurarsi che il payload sia convalidato tramite annotazioni).
	 * @return Una {@link ResponseEntity} contenente HTTP 200 (OK) e il profilo del conducente aggiornato.
	 */
	@PutMapping("/adr-certified/{license}")
	public ResponseEntity<DriverResponseDTO> updateDriverAdrCertified(@RequestBody DriverUpdateAdrApprovalDTO updateDto) {
		Driver updatedDriver = driverService.updateAdrCertifiedByLicense(updateDto);
		return ResponseEntity.ok(DriverResponseDTO.fromEntity(updatedDriver));
	}
}
