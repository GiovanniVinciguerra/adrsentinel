package dev.vinciguerra.adrsentinel.db.waybill;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import dev.vinciguerra.adrsentinel.db.AbstractGenericService;
import dev.vinciguerra.adrsentinel.db.customer.Customer.CustomerRole;
import dev.vinciguerra.adrsentinel.db.customer.CustomerSnapshot;
import dev.vinciguerra.adrsentinel.db.customer.CustomerSnapshotService;
import dev.vinciguerra.adrsentinel.db.driver.DriverSnapshot;
import dev.vinciguerra.adrsentinel.db.driver.DriverSnapshotService;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment;
import dev.vinciguerra.adrsentinel.db.shipment.ShipmentService;
import dev.vinciguerra.adrsentinel.db.shipmentitem.ShipmentItem;
import dev.vinciguerra.adrsentinel.db.shipmentitem.ShipmentItemService;
import dev.vinciguerra.adrsentinel.db.vehicle.VehicleSnapshot;
import dev.vinciguerra.adrsentinel.db.vehicle.VehicleSnapshotService;
import dev.vinciguerra.adrsentinel.exception.IllegalShipmentStateException;

/**
 * Servizio applicativo (Business Service) dedicato all'orchestrazione e alla generazione 
 * del Documento di Trasporto (D.D.T. / Waybill).
 * <p><b>Contesto Architetturale (Orchestrator &amp; Snapshot Pattern):</b></p>
 * Questa classe agisce come punto di aggregazione (Facade) per molteplici domini applicativi. 
 * Per garantire l'integrità storico-legale del documento ADR, il servizio non interroga le 
 * anagrafiche "vive", ma si avvale di <i>Snapshot Services</i> (es. {@code DriverSnapshotService}). 
 * Questo assicura che il D.D.T. generato rifletta l'esatta fotografia dei dati (autisti, veicoli, 
 * clienti) al momento della presa in carico, rendendo il documento immune ad alterazioni anagrafiche future.
 * <p><b>Sicurezza e Consistenza:</b></p>
 * L'intero processo di salvataggio è protetto da rigidi controlli di stato (Fail-Fast) e 
 * barriere transazionali ({@code @Transactional}), impedendo la generazione di documenti orfani, 
 * duplicati o privi delle entità logistiche obbligatorie per legge (Mittente, Vettore, Destinatario).
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Service
public class WaybillService extends AbstractGenericService  {
	private final WaybillRepository waybillRepository;
	private final TemplateEngine templateEngine;
	private final ShipmentService shipmentService;
	private final VehicleSnapshotService vehicleSnapshotService;
	private final DriverSnapshotService driverSnapshotService;
	private final CustomerSnapshotService customerSnapshotService;
	private final ShipmentItemService shipmentItemService;
	
	/** Costruttore per l'iniezione delle dipendenze (Dependency Injection). */
	protected WaybillService(WaybillRepository waybillRepository, TemplateEngine templateEngine, ShipmentService shipmentService,
			VehicleSnapshotService vehicleSnapshotService, DriverSnapshotService driverSnapshotService, CustomerSnapshotService customerSnapshotService,
			ShipmentItemService shipmentItemService, CacheManager cacheManager) {
		super(cacheManager);
		this.waybillRepository = Objects.requireNonNull(waybillRepository, "waybillRepository must not be null.");
		this.templateEngine = Objects.requireNonNull(templateEngine, "templateEngine must not be null.");
		this.shipmentService = Objects.requireNonNull(shipmentService, "shipmentService must not be null.");
		this.vehicleSnapshotService = Objects.requireNonNull(vehicleSnapshotService, "vehicleSnapshotService must not be null.");
		this.driverSnapshotService = Objects.requireNonNull(driverSnapshotService, "driverSnapshotService must not be null.");
		this.customerSnapshotService = Objects.requireNonNull(customerSnapshotService, "customerSnapshotService must not be null.");
		this.shipmentItemService = Objects.requireNonNull(shipmentItemService, "shipmentItemService must not be null.");
	}
	
	/**
	 * Recupera il documento di trasporto associato a uno specifico identificativo di spedizione.
	 * @param id L'identificativo interno della spedizione (Surrogate Key).
	 * @return L'entità immutabile {@link Waybill} contenente i dati e il payload PDF.
	 * @throws RuntimeException se nessun D.D.T. risulta associato alla spedizione richiesta.
	 */
	@Transactional(readOnly = true)
	public Waybill getWaybillByShipmentId(Long id) {
		logger.info("[DataBase CALL] Searching for the Waybill by shipment id: {}", id);
		return waybillRepository.findByShipment_Id(id)
			.orElseThrow(() -> new RuntimeException("Waybill not found for shipment ID:: " + id));
	}
	
	/**
	 * Recupera esclusivamente i metadati anagrafici e di dettaglio del Documento di Trasporto (D.D.T.) 
	 * associato a una specifica spedizione, omettendo intenzionalmente il caricamento del file PDF.
	 * <p><b>Contesto Architetturale (Memory Safety &amp; Performance):</b></p>
	 * Questo metodo espone al Presentation Layer (es. per popolare tabelle, cruscotti o griglie dati frontend) 
	 * i dettagli del D.D.T. senza scatenare il download del payload binario (LONGBLOB) dal database. 
	 * Questa segregazione previene il saturamento della Heap Memory della JVM, garantendo la 
	 * massima scalabilità anche in scenari di interrogazioni massive (Bulk Queries).
	 * <p><b>ATTENZIONE (Entity State Warning):</b></p>
	 * L'entità {@link Waybill} restituita da questo metodo è istanziata tramite <i>Query Projection</i>. 
	 * Di conseguenza:
	 * <ul>
	 * <li>Si trova in stato <b>Detached</b> (non è monitorata dal Persistence Context).</li>
	 * <li>Il metodo {@link Waybill#getPdfData()} restituirà <b>sempre {@code null}</b>.</li>
	 * <li>La relazione {@link Waybill#getShipment()} non viene inizializzata.</li>
	 * </ul>
	 * Per ottenere il documento scaricabile, invocare l'alternativa {@link #getWaybillByShipmentId(Long)}.
	 * @param id L'identificativo primario (Surrogate Key) della spedizione di cui si richiedono i metadati del D.D.T.
	 * @return L'entità {@code Waybill} parzialmente popolata, destinata alla conversione in DTO.
	 * @throws RuntimeException (Fail-Fast) se il database non rileva alcun documento generato per l'ID spedizione fornito.
	 */
	@Transactional(readOnly = true)
	public Waybill getWaybillDetailByShipmentId(Long id) {
		logger.info("[DataBase CALL] Searching for the Waybill detail by shipment id: {}", id);
		return waybillRepository.findMetadataByShipment_Id(id)
			.orElseThrow(() -> new RuntimeException("Waybill not found for shipment ID:: " + id));
	}
	
	/**
	 * Verifica l'esistenza di un D.D.T. bypassando il caricamento in memoria del file PDF.
	 * Sfrutta l'ottimizzazione tramite Query Projection del repository subalterno.
	 * @param id L'identificativo interno della spedizione.
	 * @return {@code true} se il documento esiste, {@code false} altrimenti.
	 */
	@Transactional(readOnly = true)
	public boolean isPresentByShipment_Id(Long id) {
		logger.info("[DataBase CALL] Checking existence of Waybill for Shipment ID: {}", id);
		return waybillRepository.existsByShipment_Id(id);
	}
	
	/**
	 * Orchestra la validazione, l'aggregazione dei dati, il rendering grafico e la persistenza 
	 * di un nuovo Documento di Trasporto.
	 * <p><b>Pipeline Operativa (Idempotenza e Validazione):</b></p>
	 * <ol>
	 * <li><i>Pre-Condition Check:</i> Verifica che la spedizione non possegga già un D.D.T. per evitare duplicati legali.</li>
	 * <li><i>Snapshot Aggregation:</i> Raccoglie la storicizzazione di veicoli, autisti, e attori logistici (Sender, Carrier, Receiver).</li>
	 * <li><i>Integrity Check:</i> Se manca anche un solo attore logistico (es. spedizione senza autista o senza merce), il processo collassa immediatamente (Fail-Fast).</li>
	 * <li><i>Rendering:</i> Impacchetta i dati nel contesto di Thymeleaf e invoca il motore OpenHTMLtoPDF.</li>
	 * <li><i>Persistenza:</i> Salva il payload binario associandolo in modo irreversibile alla spedizione.</li>
	 * </ol>
	 * @param waybillRequestDto Il payload di richiesta, validato per prevenire manipolazioni del nome file e attacchi OS.
	 * @return Il documento {@link Waybill} appena generato, salvato e cristallizzato nel database.
	 * @throws IllegalShipmentStateException qualora la spedizione risulti carente di dati obbligatori 
	 * o qualora il motore PDF incontri un errore critico durante la renderizzazione.
	 */
	@Transactional
	public Waybill save(String shipmentTrackingNumber) throws IllegalShipmentStateException {
		String filename = shipmentTrackingNumber + ".pdf";
		logger.info("[DataBase CALL] Saving new Waybill with filename: {}", filename);
		Shipment shipment = shipmentService.getByTrackingNumber(shipmentTrackingNumber);
		boolean isPresent = waybillRepository.existsByShipment_Id(shipment.getId());
		if(isPresent)
			throw new IllegalShipmentStateException("A waybill already exists for shipment tracking number: " + shipment.getTrackingNumber());
		// Recupera tutte le risorse collegate a Shipment
		VehicleSnapshot vehicle = vehicleSnapshotService.getByShipmentId(shipment.getId());
		List<DriverSnapshot> drivers = driverSnapshotService.getByShipmentId(shipment.getId());
		if(drivers.isEmpty())
			throw new IllegalShipmentStateException("No drivers found in the history for the shipment with tracking number: " + shipment.getTrackingNumber());
		List<CustomerSnapshot> customers = customerSnapshotService.getByShipmentId(shipment.getId());
		if(customers.isEmpty())
			throw new IllegalShipmentStateException("No customers found in the history for the shipment with tracking number: " + shipment.getTrackingNumber());
		CustomerSnapshot sender = customers.stream()
			.filter(customer -> customer.getRoleSnap() == CustomerRole.SENDER)
			.findFirst()
			.orElseThrow(() -> new IllegalShipmentStateException("Incomplete Snapshot: Missing SENDER"));
		CustomerSnapshot carrier = customers.stream()
			.filter(customer -> customer.getRoleSnap() == CustomerRole.CARRIER)
			.findFirst()
			.orElseThrow(() -> new IllegalShipmentStateException("Incomplete Snapshot: Missing CARRIER"));
		List<CustomerSnapshot> receivers = customers.stream()
			.filter(customer -> customer.getRoleSnap() == CustomerRole.RECEIVER)
			.toList();
		if(receivers.isEmpty())
			throw new IllegalShipmentStateException("Incomplete Snapshot: Missing RECEIVER");
		List<ShipmentItem> items = shipmentItemService.getByShipment(shipment.getTrackingNumber());
		if(items.isEmpty())
			throw new IllegalShipmentStateException("No items found for the shipment with tracking number: " + shipment.getTrackingNumber());
		Map<String, Object> context = new HashMap<String, Object>();
		context.put("shipment", shipment);
		context.put("vehicle", vehicle);
		context.put("drivers", drivers);
		context.put("sender", sender);
		context.put("carrier", carrier);
		context.put("receivers", receivers);
		context.put("items", items);
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("context", context);
		String ddtNumber = "DDT-" + shipment.getTrackingNumber();
		data.put("documentNumber", ddtNumber);
		LocalDate date = LocalDate.now();
		data.put("date", date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
		byte[] bytes;
		try {
			bytes = generate("waybill", data);
		} catch(Exception error) {
			logger.error("Critical error while rendering PDF for shipping {}" , shipment.getTrackingNumber(), error);
			throw new IllegalShipmentStateException("Internal error while generating PDF document.");
		}
		Waybill waybillToSave = new Waybill(ddtNumber, filename, "application/pdf", bytes, date, shipment);
		Waybill savedWaybill = waybillRepository.save(waybillToSave);
		return savedWaybill;
	}
	
	/**
	 * Motore di renderizzazione interno che trasforma il template HTML e i dati aggregati in uno stream binario PDF.
	 * @param templateName Il nome del file Thymeleaf (senza percorso o estensione) situato in {@code src/main/resources/templates/}.
	 * @param data La mappa delle variabili iniettate nel contesto di generazione.
	 * @return L'array di byte rappresentante il documento PDF finale.
	 * @throws Exception se il template non viene trovato o se il processore PDF rileva markup malformato.
	 */
	private byte[] generate(String templateName, Map<String, Object> data) throws Exception {
		// 1. Inserisce i dati nel contesto di Thymeleaf
		Context context = new Context();
		context.setVariables(data);
		// 2. Processa il template HTML (cerca in src/main/resources/templates/)
		String htmlContent = templateEngine.process(templateName, context);
		// 3. Converte l'HTML in PDF (Stream di byte)
		try(ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			// Passa l'HTML e imposta l'URL di base (utile per immagini o font locali)
			builder.withHtmlContent(htmlContent, "classpath:/");
			// Scrive il risultato nello stream
			builder.toStream(output);
			builder.run();
			return output.toByteArray();
		}
	}
}
