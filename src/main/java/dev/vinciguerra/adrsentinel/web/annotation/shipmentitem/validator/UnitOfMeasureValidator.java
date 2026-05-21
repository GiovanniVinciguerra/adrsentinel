package dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.validator;

import dev.vinciguerra.adrsentinel.db.shipmentitem.ShipmentItem.UnitOfMeasure;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.ValidatorUnitOfMeasure;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale {@link ValidatorUnitOfMeasure}.
 * Agisce come scudo architetturale (Edge Validation) per garantire che l'unità di misura 
 * associata alle grandezze fisiche dell'ADR (es. KG, L, T) sia obbligatoria e semanticamente 
 * inequivocabile.
 * <p><b>Design Architetturale (Strict Validation & Type-Safety):</b></p>
 * Questa classe agisce in stretta sinergia con i validatori quantitativi (es. {@code ValidatorQuantity}), 
 * applicando i rigorosi principi del Domain-Driven Design (DDD). Rifiuta nativamente l'assenza 
 * del dato e impone una ferrea risoluzione a dizionario (Enum Matching). Questo design pattern 
 * garantisce che il Service Layer non operi mai su "numeri crudi" privi di contesto, ma 
 * esclusivamente su misurazioni fisicamente inquadrate e normativamente standardizzate.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorUnitOfMeasure
 */
public class UnitOfMeasureValidator implements ConstraintValidator<ValidatorUnitOfMeasure, String> {
	/**
	 * Esegue l'ispezione profonda della stringa in ingresso per certificarne l'obbligatorietà 
	 * e l'esatta corrispondenza con le costanti di sistema.
	 * <p><b>Flusso di Esecuzione (Fail-Fast & Defense in Depth):</b></p>
	 * <ol>
	 * <li><b>Guard Clause (Strict Presence Check):</b> Intercetta e respinge istantaneamente 
	 * i valori {@code null}. Questo certifica l'obbligatorietà assoluta del parametro: una 
	 * quantità fisica priva della sua unità di misura rende il payload intrinsecamente malformato.</li>
	 * <li><b>Risoluzione a Dizionario (Enum Matching):</b> Tenta il casting della stringa 
	 * (inclusi tentativi malevoli di invio di stringhe vuote {@code ""}) verso l'enumerazione 
	 * {@code UnitOfMeasure}. Se la sigla non corrisponde a una costante legale del dominio (es. KG, L), 
	 * l'infrastruttura solleva una {@code IllegalArgumentException}, tradotta elegantemente 
	 * in un pulito fallimento di validazione (HTTP 400).</li>
	 * <li><b>Boundary Check (Defense in Depth):</b> Certifica matematicamente che la lunghezza 
	 * della stringa non ecceda i 255 caratteri. Sebbene le costanti Enum siano per loro natura 
	 * molto brevi, questa istruzione blinda la transazione garantendo la totale compatibilità 
	 * con i limiti fisici di archiviazione del database.</li>
	 * </ol>
	 * @param value La stringa rappresentante l'unità di misura estratta dal Request Payload.
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
			Enum.valueOf(UnitOfMeasure.class, value);
		} catch(IllegalArgumentException error) {
			return false;
		}
		return value.length() <= 255;
	}
}
