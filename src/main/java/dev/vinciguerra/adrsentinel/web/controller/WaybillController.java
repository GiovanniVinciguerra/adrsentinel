package dev.vinciguerra.adrsentinel.web.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment;
import dev.vinciguerra.adrsentinel.db.shipment.ShipmentService;
import dev.vinciguerra.adrsentinel.db.waybill.Waybill;
import dev.vinciguerra.adrsentinel.db.waybill.WaybillService;
import dev.vinciguerra.adrsentinel.web.annotation.ValidatorUUID;
import dev.vinciguerra.adrsentinel.web.dto.waybill.WaybillResponseDTO;

/**
 * Controller REST (Presentation/Boundary Layer) responsabile dell'esposizione e della 
 * gestione dei Documenti di Trasporto (D.D.T. / Waybill) verso i client esterni.
 * <p><b>Contesto Architetturale (Separation of Concerns):</b></p>
 * Questa API di facciata implementa una rigida segregazione delle operazioni di lettura 
 * per garantire scalabilità e performance:
 * <ul>
 * <li><b>Endpoint Pesante (Payload):</b> Dedicato esclusivamente all'erogazione dello stream binario (PDF) su richiesta esplicita dell'utente.</li>
 * <li><b>Endpoint Leggero (Metadata):</b> Dedicato all'estrazione delle informazioni anagrafiche per le interfacce grafiche, sfruttando le proiezioni JPA per minimizzare il consumo di memoria RAM.</li>
 * </ul>
 * <p><b>Sicurezza Strutturale (OWASP &amp; Input Validation):</b></p>
 * L'annotazione {@code @Validated} a livello di classe abilita la validazione AOP nativa di Spring. 
 * Abbinata al presidio {@code @ValidatorUUID} sui path parameter, funge da scudo contro vettori 
 * di attacco come l'Enumerazione IDOR (Insecure Direct Object Reference) e blocca payload malformati 
 * con un Fail-Fast HTTP 400 (Bad Request).
 * @author Giovanni Vinciguerra
 * @version 1.0 (REST Interface & Validation Boundary)
 * @since 1.0
 */
@RestController
@RequestMapping("/adr-sentinel/waybill")
@Validated
public class WaybillController {
	private final WaybillService waybillService;
	private final ShipmentService shipmentService;
	
	/** Costruttore per la Dependency Injection (DI) raccomandata da Spring. */
	public WaybillController(WaybillService waybillService, ShipmentService shipmentService) {
		this.waybillService = waybillService;
		this.shipmentService = shipmentService;
	}
	
	/**
	 * Eroga il payload binario (PDF) del Documento di Trasporto istruendo il browser client 
	 * ad avviare il download del file sul disco locale.
	 * <p><b>Dettagli Implementativi (HTTP Headers):</b></p>
	 * <ul>
	 * <li>{@code Content-Type}: Impostato dinamicamente (es. "application/pdf") per informare 
	 * il client sulla corretta natura del payload decodificato.</li>
	 * <li>{@code Content-Disposition}: Formattato come "attachment" con nome file esplicito, 
	 * forza la finestra di dialogo "Salva con nome" nei browser standard, impedendo la 
	 * visualizzazione inline forzata che potrebbe dipendere dalle configurazioni locali dell'utente.</li>
	 * </ul>
	 * @param trackingNumber Il codice identificativo univoco (UUID) della spedizione 
	 * (protetto da validazione formale {@code @ValidatorUUID}).
	 * @return Un contenitore {@link ResponseEntity} configurato con gli header di scaricamento 
	 * e il corpo del messaggio formato dallo stream di byte ({@code LONGBLOB}) del file.
	 */
	@GetMapping("/{trackingNumber}")
	public ResponseEntity<byte []> download(@PathVariable @ValidatorUUID String trackingNumber) {
		Shipment shipment = shipmentService.getByTrackingNumber(trackingNumber);
		Waybill waybill = waybillService.getWaybillByShipmentId(shipment.getId());
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType(waybill.getContentType()));
		headers.setContentDisposition(
			ContentDisposition.builder("attachment")
				.filename(waybill.getFilename())
				.build()
		);
		return ResponseEntity.ok().headers(headers).body(waybill.getPdfData());
	}
	
	/**
	 * Recupera in modo ultra-leggero esclusivamente i metadati di dettaglio del Documento 
	 * di Trasporto (D.D.T.) ignorando il file fisico sul database.
	 * <p><b>Ottimizzazione Architetturale (Memory Footprint):</b></p>
	 * Endpoint ottimizzato per cruscotti informativi (Dashboards) o griglie dati (Data Tables) lato Frontend. 
	 * Mappa l'entità distaccata (Detached) sul Presentation Object {@link WaybillResponseDTO}, 
	 * proteggendo il server da cali di performance o eccezioni {@code OutOfMemoryError} derivanti 
	 * da interrogazioni di massa.
	 * @param trackingNumber Il codice identificativo univoco (UUID) della spedizione 
	 * (protetto da validazione formale {@code @ValidatorUUID}).
	 * @return Un contenitore HTTP 200 (OK) con un payload JSON rappresentante il Data Transfer Object 
	 * dei metadati legati al D.D.T.
	 */
	@GetMapping("/detail/{trackingNumber}")
	public ResponseEntity<WaybillResponseDTO> getDetail(@PathVariable @ValidatorUUID String trackingNumber) {
		Shipment shipment = shipmentService.getByTrackingNumber(trackingNumber);
		Waybill waybill = waybillService.getWaybillDetailByShipmentId(shipment.getId());
		return ResponseEntity.ok(WaybillResponseDTO.fromEntity(waybill));
	}
}
