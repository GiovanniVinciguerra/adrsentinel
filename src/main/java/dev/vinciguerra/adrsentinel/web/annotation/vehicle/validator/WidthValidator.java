package dev.vinciguerra.adrsentinel.web.annotation.vehicle.validator;

import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorWidth;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale e fisico {@link ValidatorWidth}.
 * Agisce come scudo architetturale (Edge Validation) per l'ingombro trasversale (larghezza in metri) 
 * di veicoli o unità di carico nel dominio dei trasporti commerciali (es. normativa ADR e Codice della Strada).
 * <p><b>Design Architetturale (Strict Validation & Domain-Driven Boundaries):</b></p>
 * Questa classe traduce i limiti di sagoma stradale in regole software inossidabili. 
 * Garantendo la presenza del dato e sanificando le aberrazioni algebriche (NaN/Infinity), 
 * impone un rigoroso Boundary Check (Controllo di Confine). L'intervallo [1.5 - 2.6] metri 
 * rappresenta lo spettro operativo reale dei trasporti su gomma: partendo dalla base di 1.5 metri 
 * per escludere tricicli o veicoli leggeri non idonei, fino ad arrivare al limite massimo 
 * legale assoluto di 2.6 metri, tolleranza concessa dalle direttive europee esclusivamente 
 * per i veicoli a temperatura controllata (isotermici/ATP).
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorWidth
 */
public class WidthValidator implements ConstraintValidator<ValidatorWidth, Float> {
	/**
	 * Esegue l'ispezione profonda del valore a virgola mobile per certificarne l'obbligatorietà, 
	 * la stabilità matematica e la conformità ai limiti fisici stradali.
	 * <p><b>Flusso di Esecuzione (Fail-Fast & Triple Check):</b></p>
	 * <ol>
	 * <li><b>Guard Clause (Strict Presence Check):</b> Intercetta e respinge istantaneamente 
	 * i valori {@code null}. Questo certifica l'assoluta obbligatorietà del parametro nel payload, 
	 * garantendo che le logiche di instradamento abbiano sempre le dimensioni a disposizione.</li>
	 * <li><b>Sanitizzazione Algebrica (Finite Math Check):</b> Verifica che il numero sia 
	 * finito e reale. Intercettando {@code Float.isNaN()} e {@code Float.isInfinite()}, 
	 * respinge payload ingannevoli o corrotti che supererebbero i normali controlli di deserializzazione JSON.</li>
	 * <li><b>Boundary Check (Range [1.5, 2.6]):</b> Come ultimo step, impone che il valore 
	 * rientri nel range consentito. <i>Nota Ingegneristica: Il confronto diretto ({@code >=} e {@code <=}) 
	 * senza margine di tolleranza (Epsilon) è architetturalmente sicuro in questa fase (Edge Layer), 
	 * poiché la stringa del client è stata appena decodificata in binario senza subire ulteriori 
	 * operazioni aritmetiche intermedie.</i></li>
	 * </ol>
	 * @param value Il valore numerico (larghezza in metri) estratto dal Request Payload.
	 * @param context Il contesto di validazione iniettato dal framework (Spring/Hibernate Validator).
	 * @return {@code true} esclusivamente se il valore è presente, finito, reale e compreso 
	 * nel range chiuso [1.5, 2.6]; {@code false} in caso di assenza, aberrazione matematica 
	 * (NaN/Infinity) o violazione dei limiti dimensionali europei.
	 */
	@Override
	public boolean isValid(Float value, ConstraintValidatorContext context) {
		if(value == null)
			return false;
		if(value.isInfinite() || value.isNaN())
			return false;
		return value >= 1.5f && value <= 2.6f;
	}
}
