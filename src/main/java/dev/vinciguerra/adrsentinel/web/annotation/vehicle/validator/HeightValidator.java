package dev.vinciguerra.adrsentinel.web.annotation.vehicle.validator;

import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorHeight;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale e fisico {@link ValidatorHeight}.
 * Agisce come scudo architetturale (Edge Validation) per l'ingombro verticale (altezza in metri) 
 * di veicoli o unità di carico nel dominio dei trasporti (es. normativa ADR), garantendo 
 * che i dati in ingresso siano obbligatori, matematicamente sicuri e fisicamente realistici.
 * <p><b>Design Architetturale (Strict Validation & Domain-Driven Boundaries):</b></p>
 * Questa classe traduce i vincoli del mondo fisico in regole software inossidabili. 
 * Rifiutando nativamente l'assenza del dato e sanificando le anomalie algebriche (NaN/Infinity), 
 * impone un rigoroso Boundary Check (Controllo di Confine). L'intervallo [1.7 - 4.3] metri 
 * rappresenta lo spettro operativo reale dei trasporti commerciali su gomma: partendo 
 * dai veicoli leggeri (1.7m) fino al limite massimo stradale europeo per rimorchi "Mega" 
 * o carichi eccezionali standard (4.3m).
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorHeight
 */
public class HeightValidator implements ConstraintValidator<ValidatorHeight, Float> {
	/**
	 * Esegue l'ispezione profonda del valore a virgola mobile per certificarne l'obbligatorietà, 
	 * la stabilità matematica e la conformità ai limiti fisici stradali.
	 * <p><b>Flusso di Esecuzione (Fail-Fast & Triple Check):</b></p>
	 * <ol>
	 * <li><b>Guard Clause (Strict Presence Check):</b> Intercetta e respinge istantaneamente 
	 * i valori {@code null}. Questo certifica l'assoluta obbligatorietà del parametro nel payload, 
	 * prevenendo {@code NullPointerException} a valle.</li>
	 * <li><b>Sanitizzazione Algebrica (Finite Math Check):</b> Verifica che il numero sia 
	 * finito e reale. Intercettando {@code Float.isNaN()} e {@code Float.isInfinite()}, 
	 * respinge payload ingannevoli che corromperebbero i calcoli di routing (es. verifica 
	 * dei percorsi sotto i ponti).</li>
	 * <li><b>Boundary Check (Range [1.7, 4.3]):</b> Come ultimo step, impone che il valore 
	 * rientri nel range consentito. <i>Nota Ingegneristica: Il confronto diretto ({@code >=} e {@code <=}) 
	 * senza Epsilon è qui architetturalmente sicuro poiché operiamo sull'Anti-Corruption Layer, 
	 * dove la rappresentazione binaria del float inviato dal client coincide esattamente 
	 * con la costante letterale compilata, prima che subisca alterazioni da calcoli di business.</i></li>
	 * </ol>
	 * @param value Il valore numerico (altezza in metri) estratto dal Request Payload.
	 * @param context Il contesto di validazione iniettato dal framework (Spring/Hibernate Validator).
	 * @return {@code true} esclusivamente se il valore è presente, finito, reale e compreso 
	 * nel range chiuso [1.7, 4.3]; {@code false} in caso di assenza, aberrazione matematica 
	 * (NaN/Infinity) o violazione dei limiti dimensionali.
	 */
	@Override
	public boolean isValid(Float value, ConstraintValidatorContext context) {
		if(value == null)
			return false;
		if(value.isInfinite() || value.isNaN())
			return false;
		return value >= 1.7f && value <= 4.3f;
	}
}
