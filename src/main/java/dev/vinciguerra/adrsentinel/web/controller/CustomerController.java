package dev.vinciguerra.adrsentinel.web.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import dev.vinciguerra.adrsentinel.db.customer.Customer;
import dev.vinciguerra.adrsentinel.db.customer.CustomerService;
import dev.vinciguerra.adrsentinel.db.shipment.ShipmentService;
import dev.vinciguerra.adrsentinel.web.dto.customer.CustomerRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.customer.CustomerResponseDTO;
import dev.vinciguerra.adrsentinel.web.dto.customer.CustomerSearchByNameRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.customer.CustomerSearchByVatRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.customer.CustomerUpdateActiveStatusDTO;
import dev.vinciguerra.adrsentinel.web.dto.customer.CustomerUpdateDTO;
import jakarta.validation.Valid;

/**
 * Controller REST di frontiera (Boundary Layer) per l'esposizione delle API relative al dominio {@link Customer}.
 * <p><b>Ruolo Architetturale e Sicurezza (Security by Design):</b></p>
 * Questa classe gestisce il routing HTTP per il perimetro logistico di AdrSentinel, agendo come scudo protettivo 
 * per i servizi transazionali sottostanti. Tramite l'annotazione {@code @Validated}, il controller attiva il motore 
 * di validazione JSR-380 a livello di classe, garantendo che ogni DTO in ingresso venga rigorosamente ispezionato 
 * prima di allocare risorse elaborative. Il design degli endpoint riflette precise scelte di tutela della privacy: 
 * i parametri di ricerca potenzialmente assimilabili a Dati Personali (PII, es. P.IVA di ditte individuali o ragioni sociali) 
 * vengono sistematicamente incapsulati nel Request Body, evadendo l'esposizione in chiaro nei log di routing (URL logging) 
 * e operando in totale sicurezza sotto tunnel crittografico TLS.
 * @author Giovanni Vinciguerra
 * @version 1.0 (REST Interface & Validation Boundary)
 * @since 1.0
 */
@RestController
@RequestMapping("/adr-sentinel/customers")
@Validated
public class CustomerController {
	private final ShipmentService shipmentService;
	private final CustomerService customerService;
	
	/**
	 * Inietta le dipendenze necessarie tramite costruttore (Constructor Injection).
	 * Questa pratica garantisce l'immutabilità del controller (thread-safety) e ne 
	 * agevola i test unitari isolati (Mocking).
	 */
	public CustomerController(ShipmentService shipmentService, CustomerService customerService) {
		this.shipmentService = shipmentService;
		this.customerService = customerService;
	}
	
	/**
	 * Recupera l'elenco onnicomprensivo dei clienti attivi e inattivi censiti a sistema.
	 * L'operazione estrae il dataset e lo proietta nelle rispettive maschere di uscita (DTO), assicurando che nessun 
	 * metadato interno sensibile o legato al framework ORM (es. timestamp, ID surrogati) venga esposto al frontend.
	 * @return Una {@link ResponseEntity} contenente la lista di {@link CustomerResponseDTO} con HTTP status 200 (OK).
	 */
	@GetMapping
	public ResponseEntity<List<CustomerResponseDTO>> getAllCustomer() {
		List<Customer> customers = customerService.getAllCustomer();
		List<CustomerResponseDTO> response = customers.stream().map(CustomerResponseDTO::fromEntity).toList();
		return ResponseEntity.ok(response);
	}
	
	/**
	 * Esegue una ricerca per corrispondenza esatta (Exact Match) sulla Ragione Sociale.
	 * <p><b>Peculiarità Architetturale:</b></p>
	 * L'endpoint sfrutta un payload strutturato (Body) in combinazione con un verbo GET. Sebbene atipica nello standard REST, 
	 * questa direttiva architetturale garantisce che il dato di ricerca, potenzialmente assimilabile a PII, non transiti mai 
	 * nei parametri URI, risultando di fatto invisibile a sistemi di monitoraggio passivo, Web Application Firewall e Proxy.
	 * @param searchDto Il payload validato contenente la Ragione Sociale da ricercare.
	 * @return Una {@link ResponseEntity} contenente una lista di DTO (gestione strutturale delle omonimie) con HTTP status 200 (OK).
	 */
	@GetMapping("/search-name")
	public ResponseEntity<List<CustomerResponseDTO>> getByCompanyName(@RequestBody @Valid CustomerSearchByNameRequestDTO searchDto) {
		List<Customer> customers = customerService.getByCompanyName(searchDto.companyName());
		List<CustomerResponseDTO> response = customers.stream().map(CustomerResponseDTO::fromEntity).toList();
		return ResponseEntity.ok(response);
	}
	
	/**
	 * Recupera univocamente un cliente avvalendosi della Partita IVA (Business Key).
	 * Mappata intenzionalmente come richiesta POST per abilitare il trasferimento sicuro dell'identificativo fiscale 
	 * tramite Request Body, blindando la frontiera contro attacchi di data leakage sui log di rete.
	 * @param searchDto Il payload validato contenente l'esatta Partita IVA.
	 * @return Una {@link ResponseEntity} contenente il singolo {@link CustomerResponseDTO} individuato, con HTTP status 200 (OK).
	 */
	@PostMapping("/search-vat")
	public ResponseEntity<CustomerResponseDTO> getByVatNumber(@RequestBody @Valid CustomerSearchByVatRequestDTO searchDto) {
		Customer customer = customerService.getByVatNumber(searchDto.vatNumber());
		return ResponseEntity.ok(CustomerResponseDTO.fromEntity(customer));
	}
	
	/**
	 * Avvia il processo di creazione e censimento di una nuova entità societaria (Customer).
	 * Il controller agisce da orchestratore primario: converte il DTO in ingresso in un'entità transiente, inietta 
	 * l'assunzione di dominio di default (forzando il flag di operatività a {@code true}) e demanda la persistenza al service.
	 * @param customerRequestDto Il DTO (Inbound Payload) esaminato dal validatore e contenente i dati anagrafici d'ingresso.
	 * @return Una {@link ResponseEntity} contenente il DTO del cliente reidratato dal database, con HTTP status 201 (CREATED).
	 */
	@PostMapping
	public ResponseEntity<CustomerResponseDTO> create(@RequestBody @Valid CustomerRequestDTO customerRequestDto) {
		Customer customerToSave = customerService.mapToEntity(customerRequestDto);
		customerToSave.setActive(true);
		Customer savedCustomer = customerService.save(customerToSave);
		return ResponseEntity.status(HttpStatus.CREATED).body(CustomerResponseDTO.fromEntity(savedCustomer));
	}
	
	/**
	 * Gestisce l'aggiornamento chirurgico delle informazioni anagrafiche non-chiave (Ragione Sociale e Indirizzo Legale).
	 * L'operazione delega al service il recupero e la mutazione parziale dei dati, operando nella garanzia che la Business Key 
	 * (Partita IVA) non venga mai esposta ad alterazioni accidentali grazie alla specifica struttura del DTO in ingresso.
	 * @param updateDto Il DTO validato contenente i nuovi valori testuali da sovrascrivere.
	 * @return Una {@link ResponseEntity} contenente il DTO aggiornato post-commit, con HTTP status 200 (OK).
	 */
	@PutMapping
	public ResponseEntity<CustomerResponseDTO> updateCustomerDetails(@RequestBody @Valid CustomerUpdateDTO updateDto) {
		Customer updatedCustomer = customerService.updateDetailsByVatNumber(updateDto);
		return ResponseEntity.ok(CustomerResponseDTO.fromEntity(updatedCustomer));
	}
	
	/**
	 * Esegue una mutazione isolata dello stato vitale del cliente (operazioni di abilitazione o sospensione cautelare).
	 * L'implementazione di un endpoint dedicato a questa singola responsabilità previene scenari di Mass Assignment, 
	 * assicurando che il toggle logico del ciclo di vita non comprometta la stabilità dei dati anagrafici sottostanti.
	 * @param updateDto Il DTO validato contenente unicamente il nuovo flag operativo (booleano).
	 * @return Una {@link ResponseEntity} esprimente lo stato finale e consolidato dell'entità, con HTTP status 200 (OK).
	 */
	@PutMapping("/active-status")
	public ResponseEntity<CustomerResponseDTO> updateCustomerActiveStatus(@RequestBody @Valid CustomerUpdateActiveStatusDTO updateDto) {
		Customer updatedCustomer = customerService.updateActiveStatusByVatNumber(updateDto);
		return ResponseEntity.ok(CustomerResponseDTO.fromEntity(updatedCustomer));
	}
}
