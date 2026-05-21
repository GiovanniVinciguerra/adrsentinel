package dev.vinciguerra.adrsentinel.web.annotation.vehicle.validator;

import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorWheelbase;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale e fisico {@link ValidatorWheelbase}.
 * Agisce come scudo architetturale (Edge Validation) per il passo del veicolo (distanza 
 * tra l'asse anteriore e posteriore in metri) nel dominio dei trasporti commerciali e ADR.
 * <p><b>Design Architetturale (Strict Validation & Domain-Driven Boundaries):</b></p>
 * Questa classe arricchisce la matrice geometrica del veicolo traducendo i limiti strutturali 
 * degli autotelai in regole software inossidabili. Garantendo la presenza del dato e sanificando 
 * le aberrazioni algebriche (NaN/Infinity), impone un rigoroso Boundary Check (Controllo di Confine). 
 * L'intervallo [1.9 - 7.0] metri rappresenta lo spettro operativo reale dell'ingegneria dei trasporti: 
 * partendo da una base di 1.9 metri (tipica delle micro-vetture compatte cittadine), fino ad 
 * arrivare al limite estremo di 7.0 metri, caratteristico degli autotelai rigidi per mezzi pesanti 
 * a più assi.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorWheelbase
 */
public class WheelbaseValidtor implements ConstraintValidator<ValidatorWheelbase, Float> {
	/**
	 * Esegue l'ispezione profonda del valore a virgola mobile per certificarne l'obbligatorietà, 
	 * la stabilità matematica e la conformità ai limiti fisici ingegneristici.
	 * <p><b>Flusso di Esecuzione (Fail-Fast & Triple Check):</b></p>
	 * <ol>
	 * <li><b>Guard Clause (Strict Presence Check):</b> Intercetta e respinge istantaneamente 
	 * i valori {@code null}. L'obbligatorietà di questo parametro è cruciale: il passo è il 
	 * fulcro per i calcoli di ripartizione del carico sugli assi e per la determinazione 
	 * del raggio di sterzata nei software di routing logistico.</li>
	 * <li><b>Sanitizzazione Algebrica (Finite Math Check):</b> Verifica che il numero sia 
	 * finito e reale. Intercettando {@code Float.isNaN()} e {@code Float.isInfinite()}, 
	 * respinge payload ingannevoli che innescherebbero difetti di calcolo fatali nel Service Layer.</li>
	 * <li><b>Boundary Check (Range [1.9, 7.0]):</b> Come ultimo step, impone che il valore 
	 * rientri nel range ingegneristico consentito. Questo sbarramento previene alla radice errori 
	 * di inserimento della magnitudo (es. millimetri al posto di metri) che falserebbero 
	 * completamente la fisica del veicolo simulato a sistema.</li>
	 * </ol>
	 * @param value Il valore numerico (passo in metri) estratto dal Request Payload.
	 * @param context Il contesto di validazione iniettato dal framework (Spring/Hibernate Validator).
	 * @return {@code true} esclusivamente se il valore è presente, finito, reale e compreso 
	 * nel range chiuso [1.9, 7.0]; {@code false} in caso di assenza, aberrazione matematica 
	 * (NaN/Infinity) o violazione dei limiti strutturali.
	 */
	@Override
	public boolean isValid(Float value, ConstraintValidatorContext context) {
		if(value == null)
			return false;
		if(value.isInfinite() || value.isNaN())
			return false;
		return value >= 1.9f && value <= 7;
	}
}
