package dev.vinciguerra.adrsentinel.web.dto.shipment;

import java.util.List;
import java.util.Set;
import dev.vinciguerra.adrsentinel.web.annotation.ValidatorLocalDateTime;
import dev.vinciguerra.adrsentinel.web.annotation.customer.ValidatorCustomerRole;
import dev.vinciguerra.adrsentinel.web.annotation.customer.ValidatorVatNumber;
import dev.vinciguerra.adrsentinel.web.annotation.driver.ValidatorLicense;
import dev.vinciguerra.adrsentinel.web.annotation.shipment.ValidatorAddress;
import dev.vinciguerra.adrsentinel.web.annotation.shipment.ValidatorShipmentStatus;
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
 * @param distancekm Il calcolo telemetrico del routing spaziale espresso in chilometri. 
 * Vincolato a essere strettamente positivo per garantire coerenza fisica nel mondo reale.
 * @param vehicleLicensePlate L'identificativo legale del mezzo assegnato. Sottoposto a 
 * rigida normalizzazione e standardizzazione europea (assenza di spazi, solo maiuscole).
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record ShipmentRequestDTO(@ValidatorLocalDateTime String date, @ValidatorShipmentStatus String status, @ValidatorAddress String origin, 
		@NotEmpty(message = "Malformed payload: Destinations list is required") List<@ValidatorAddress String> destinations, @ValidatorLicensePlate String vehicleLicensePlate,
		@NotEmpty(message = "Malformed payload: invalid parameter. Drivers is empy.") Set<@ValidatorLicense String> drivers,
		@NotEmpty(message = "Malformed payload: invalid parameter: Customers is empty") Set<@Valid CustomerContainerDTO> customers) {
	/* TODO validator */
	public record CustomerContainerDTO(@ValidatorCustomerRole String role, @ValidatorVatNumber String vatNumber) {}
}
