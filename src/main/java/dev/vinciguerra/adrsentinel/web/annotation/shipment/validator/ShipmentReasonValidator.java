package dev.vinciguerra.adrsentinel.web.annotation.shipment.validator;

import dev.vinciguerra.adrsentinel.db.shipment.Shipment.ShipmentReason;
import dev.vinciguerra.adrsentinel.web.annotation.shipment.ValidatorShipmentReason;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validatore personalizzato per l'annotazione {@link ValidatorShipmentReason}.
 * <p>
 * Questa classe definisce la logica di validazione applicata da Spring/Hibernate Validator
 * per assicurare che una stringa in ingresso (tipicamente ricevuta da un payload JSON
 * o da una richiesta REST) sia semanticamente corretta e corrisponda a una causale di 
 * spedizione riconosciuta dal sistema.
 * </p>
 * <p>
 * L'utilizzo di questo validatore garantisce l'integrità dei dati al livello dei DTO,
 * intercettando input malevoli o non conformi prima che questi possano causare errori 
 * di business logic o eccezioni di parsing (es. {@link IllegalArgumentException}).
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see 
 */
public class ShipmentReasonValidator implements ConstraintValidator<ValidatorShipmentReason, String> {
	/**
     * Esegue la validazione del campo verificando il rispetto di tre criteri fondamentali.
     * <p>
     * Il metodo garantisce che il valore in ingresso:
     * <ol>
     * <li>Non sia nullo (fail-fast preventivo).</li>
     * <li>Sia esplicitamente mappabile su uno dei valori definiti nell'enumerazione {@link ShipmentReason}.</li>
     * <li>Risulti inferiore o uguale a 255 caratteri (limite standard per colonne VARCHAR di database).</li>
     * </ol>
     * </p>
     * @param value la stringa da validare, rappresentante la causale di spedizione ricevuta dal client
     * @param context il contesto di validazione in cui viene valutato il vincolo, utile per eventuali
     * sovrascritture dinamiche del messaggio di errore
     * @return {@code true} se la stringa è valida e sicura per il sistema, {@code false} altrimenti
     */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null)
			return false;
		try {
			Enum.valueOf(ShipmentReason.class, value);
		} catch(IllegalArgumentException error) {
			return false;
		}
		return value.length() <= 255;
	}
}
