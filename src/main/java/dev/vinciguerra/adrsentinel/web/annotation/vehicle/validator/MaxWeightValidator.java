package dev.vinciguerra.adrsentinel.web.annotation.vehicle.validator;

import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorMaxWeight;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale {@link ValidatorMaxWeight}.
 * Agisce come barriera architetturale (Edge Validation) per il controllo del peso lordo 
 * o della massa massima autorizzata nel dominio dei trasporti (es. normativa ADR).
 * <p><b>Design Architetturale (Strict Validation & Domain-Driven Boundaries):</b></p>
 * Questa classe traduce i vincoli del mondo fisico e legislativo in regole di validazione 
 * software (Domain-Driven Design). Rifiuta nativamente l'assenza del dato e impone un 
 * rigoroso Boundary Check (Controllo di Confine). I limiti [1500 - 44000] riflettono la 
 * realtà operativa dei trasporti commerciali europei su gomma: partendo dai veicoli 
 * commerciali leggeri (1.5 tonnellate) fino al limite massimo legale consentito per 
 * gli autoarticolati pesanti (44 tonnellate). Questo previene errori di input critici, 
 * come l'inserimento di pesi in grammi anziché chilogrammi.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorMaxWeight
 */
public class MaxWeightValidator implements ConstraintValidator<ValidatorMaxWeight, Integer> {
	/**
	 * Esegue l'ispezione profonda del valore numerico intero per certificarne 
	 * l'obbligatorietà e la conformità ai limiti fisici e normativi del trasporto.
	 * <p><b>Flusso di Esecuzione (Fail-Fast & Boundary Check):</b></p>
	 * <ol>
	 * <li><b>Guard Clause (Strict Presence Check):</b> Intercetta e respinge istantaneamente 
	 * i valori {@code null}. L'obbligatorietà di questo parametro previene {@code NullPointerException} 
	 * a valle (fase di unboxing) e garantisce che nessuna spedizione o veicolo venga registrato 
	 * senza la dichiarazione del proprio peso.</li>
	 * <li><b>Boundary Check (Range [1500, 44000]):</b> Utilizzando un operatore logico 
	 * inclusivo (AND ristretto), certifica che il valore rientri esattamente nel range consentito. 
	 * Lo sbarramento inferiore (1500) blocca l'inserimento di valori incompatibili con i 
	 * trasporti pesanti o eventuali refusi (es. inserire "44" intendendo le tonnellate, 
	 * quando il sistema si aspetta i chilogrammi). Lo sbarramento superiore (44000) agisce 
	 * come salvavita normativo, bloccando trasporti fuori sagoma/peso eccezionali non previsti 
	 * dalla logica di business.</li>
	 * </ol>
	 * @param value Il valore numerico intero (peso in kg) estratto dal Request Payload.
	 * @param context Il contesto di validazione iniettato dal framework (Spring/Hibernate Validator).
	 * @return {@code true} se il valore è presente e rientra matematicamente nel range 
	 * chiuso [1500, 44000]; {@code false} in caso di assenza del dato o violazione dei limiti di confine.
	 */
	@Override
	public boolean isValid(Integer value, ConstraintValidatorContext context) {
		if(value == null)
			return false;
		return value >= 1500 && value <= 44000;
	}
}
