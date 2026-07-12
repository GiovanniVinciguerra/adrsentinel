package dev.vinciguerra.adrsentinel.web.dto.shipment;

import java.util.List;
import java.util.Set;

import dev.vinciguerra.adrsentinel.web.annotation.ValidatorAddress;
import dev.vinciguerra.adrsentinel.web.annotation.ValidatorLocalDateTime;
import dev.vinciguerra.adrsentinel.web.annotation.customer.ValidatorCustomerContainer;
import dev.vinciguerra.adrsentinel.web.annotation.customer.ValidatorCustomerRole;
import dev.vinciguerra.adrsentinel.web.annotation.customer.ValidatorVatNumber;
import dev.vinciguerra.adrsentinel.web.annotation.driver.ValidatorLicense;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorTunnelRestriction;
import dev.vinciguerra.adrsentinel.web.annotation.shipment.ValidatorShipmentStatus;
import dev.vinciguerra.adrsentinel.web.annotation.shipment.ValidatorShipmentReason;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorLicensePlate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * Data Transfer Object (DTO) in ingresso (Request Payload) per l'istanziazione 
 * o la mutazione strutturale di una spedizione di merci pericolose ADR.
 * <p><b>Contesto Architetturale (Boundary Layer e Edge Validation):</b></p>
 * Questo record rappresenta il punto di contatto esposto al mondo esterno (Frontend, 
 * dispositivi mobili, integrazioni B2B). Funge da scudo perimetrale primario dell'API: 
 * ogni singolo campo è blindato da validatori custom ({@code @Validator...}) che agiscono 
 * come Anti-Corruption Layer. Questo garantisce che nessun dato sporco, formattato 
 * in modo malevolo (es. XSS) o fisicamente impossibile possa mai superare il Controller 
 * e infettare la logica di business nel {@code ShipmentService}.
 * <p><b>Scelta Architetturale (Java Record & Thread-Safety):</b></p>
 * L'implementazione tramite costrutto {@code record} assicura la totale immutabilità 
 * del payload una volta conclusa la deserializzazione da parte di Jackson. Questo 
 * previene alterazioni accidentali dello stato della richiesta ("Side Effects") 
 * durante il transito attraverso i vari layer architetturali (Controller -> Mapper -> Service).
 * @param date Il marcatore temporale (Data e Ora) programmato per la transazione logistica. 
 * (Nota tecnica: richiede attenzione in fase di deserializzazione per compatibilità con i validatori regex).
 * @param status Lo stato logistico iniziale o di transizione (es. "CREATA"). 
 * Protetto contro l'overflow (Data Truncation) dalla base dati.
 * @param origin L'indirizzo toponomastico del sito di carico. Sottoposto a sanificazione 
 * anti-XSS e blocco di caratteri speciali non ammessi.
 * @param destinations Gli indirizzi toponomastici dei siti di scarico. Sanificati analogamente all'origine.
 * @param tunnelRestriction La restrizione dei tunnel associata a questa spedizione in base al percorso seguito dal veicolo e 
 * dalle merci ADR trasportate sottoposta a rigido controllo.
 * @param transportReason La ragione per cui è stato effettuato il trasporto sottoposto a rigido controllo.
 * @param vehicleLicensePlate L'identificativo legale del mezzo assegnato. Sottoposto a 
 * rigida normalizzazione e standardizzazione europea (assenza di spazi, solo maiuscole).
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record ShipmentRequestDTO(@ValidatorLocalDateTime String date, @ValidatorShipmentStatus String status, @ValidatorAddress String origin,  
		@NotEmpty(message = "Malformed payload: destinations list is required") List<@ValidatorAddress String> destinations,
		@ValidatorTunnelRestriction String tunnelRestriction, @ValidatorShipmentReason String shipmentReason,
		@ValidatorLicensePlate String vehicleLicensePlate,
		@NotEmpty(message = "Malformed payload: invalid parameter. Drivers is empy.") Set<@ValidatorLicense String> drivers,
		@ValidatorCustomerContainer List<@Valid CustomerContainerDTO> customers) {
	/**
	 * Data Transfer Object (DTO) immutabile che incapsula la definizione di un singolo attore 
	 * logistico (Customer) all'interno del payload di una spedizione (Shipment).
	 * <p>
	 * <b>Scelte Architetturali (Flat DTO & Anti-Corruption Layer):</b><br>
	 * In linea con le best practice per la progettazione di API REST resilienti, questo record 
	 * è volutamente mantenuto "piatto" (flat) e utilizza tipi base ({@code String}) per accogliere 
	 * l'input grezzo del client. Anziché forzare il deserializzatore JSON (es. Jackson) a 
	 * convertire immediatamente i dati in tipi complessi (come gli Enum), il DTO accetta il dato 
	 * testuale e delega la validazione semantica alle annotazioni custom JSR-380. 
	 * Questo approccio previene crash a basso livello (es. {@code HttpMessageNotReadableException}) 
	 * in caso di input malformato, garantendo la restituzione di risposte HTTP 400 chiare, 
	 * uniformi e gestite dal ControllerAdvice.
	 * </p>
	 * <p>
	 * <b>Immutabilità e Sicurezza:</b><br>
	 * L'impiego del costrutto {@code record} nativo di Java garantisce che il payload, una volta 
	 * istanziato dal framework, sia intrinsecamente <i>read-only</i> e thread-safe. Funge da 
	 * perfetto e sicuro veicolo di trasporto dati verso la logica di business (Service Layer).
	 * </p>
	 * @param role La stringa che definisce la qualifica operativa dell'azienda nel trasporto 
	 * (es. "SENDER", "RECEIVER", "CARRIER"). L'annotazione {@link ValidatorCustomerRole} 
	 * assicura che il valore testuale corrisponda esattamente a una delle costanti previste 
	 * dal dominio applicativo, scartando ruoli inesistenti prima dell'elaborazione.
	 * @param vatNumber La stringa che rappresenta la Partita IVA (VAT Number) transfrontaliera. 
	 * L'annotazione {@link ValidatorVatNumber} garantisce il <i>cleansing</i> del dato 
	 * (ignorando spazi o formattazioni visive) e ne verifica la rigida conformità matematica 
	 * rispetto ai pattern ufficiali del sistema europeo VIES (VAT Information Exchange System).
	 * @author Giovanni Vinciguerra
	 * @version 1.0 (Strict Validated Input Payload)
	 * @since 1.0
	 */
	public record CustomerContainerDTO(@ValidatorCustomerRole String role, @ValidatorVatNumber String vatNumber) {}
}
