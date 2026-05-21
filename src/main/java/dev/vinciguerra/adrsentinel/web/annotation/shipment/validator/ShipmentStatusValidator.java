package dev.vinciguerra.adrsentinel.web.annotation.shipment.validator;

import dev.vinciguerra.adrsentinel.db.shipment.Shipment.ShipmentStatus;
import dev.vinciguerra.adrsentinel.web.annotation.shipment.ValidatorShipmentStatus;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale {@link ValidatorShipmentStatus}.
 * Agisce come barriera di controllo (Edge Validation) per lo Stato della Spedizione 
 * (Shipment Status), garantendo che i flussi di transizione di stato richiesti dal client 
 * siano obbligatori, sicuri e semanticamente riconosciuti dal sistema.
 * <p><b>Design Architetturale (Strict Validation & Type-Safety):</b></p>
 * A protezione dell'integrità del Dominio, questa classe adotta un approccio restrittivo. 
 * Rifiutando i valori mancanti, fonde il controllo di obbligatorietà (comportandosi 
 * a tutti gli effetti come un {@code @NotNull}) con un rigoroso matching a dizionario (Enum). 
 * Questo design pattern, comunemente noto come Anti-Corruption Layer, assicura che il 
 * Service Layer non debba mai gestire stati inventati dal client o eccezioni di conversione 
 * a runtime, operando esclusivamente su dati Type-Safe.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorShipmentStatus
 */
public class ShipmentStatusValidator implements ConstraintValidator<ValidatorShipmentStatus, String> {
	
	/**
	 * Esegue l'ispezione profonda della stringa in ingresso per certificarne l'obbligatorietà 
	 * e l'esatta corrispondenza con le costanti di sistema.
	 * <p><b>Flusso di Esecuzione (Fail-Fast & Defense in Depth):</b></p>
	 * <ol>
	 * <li><b>Guard Clause (Strict Presence Check):</b> Intercetta e respinge istantaneamente 
	 * i valori {@code null}. Questo passaggio impone l'assoluta obbligatorietà del dato, 
	 * trattando l'assenza dello stato della spedizione come un payload intrinsecamente malformato.</li>
	 * <li><b>Risoluzione a Dizionario (Enum Matching):</b> Tenta il casting della stringa 
	 * fornita (inclusi tentativi di invio di stringhe vuote {@code ""}) verso l'enumerazione 
	 * {@code ShipmentStatus}. Se il valore non corrisponde esattamente a uno stato legale 
	 * (es. PENDING, SHIPPED, DELIVERED), l'infrastruttura solleva una {@code IllegalArgumentException}, 
	 * che il blocco catch traduce elegantemente in un fallimento di validazione (HTTP 400).</li>
	 * <li><b>Boundary Check (Defense in Depth):</b> Come ultimo strato di sicurezza, certifica 
	 * matematicamente che la lunghezza della stringa non ecceda i 255 caratteri. Sebbene 
	 * le costanti Enum siano nativamente brevi, questa istruzione blinda la transazione 
	 * garantendo l'assoluta compatibilità con i limiti fisici di archiviazione del database.</li>
	 * </ol>
	 * @param value La stringa rappresentante lo stato della spedizione, estratta dal Request Payload.
	 * @param context Il contesto di validazione iniettato dal framework (Spring/Hibernate Validator).
	 * @return {@code true} esclusivamente se la stringa è presente e corrisponde esattamente 
	 * a una costante valida dell'Enum; {@code false} in caso di assenza del dato, formato non 
	 * riconosciuto a dizionario o violazione dimensionale.
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null)
			return false;
		try {
			Enum.valueOf(ShipmentStatus.class, value);
		} catch(IllegalArgumentException error) {
			return false;
		}
		return value.length() <= 255;
	}
}
