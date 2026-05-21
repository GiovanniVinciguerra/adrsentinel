package dev.vinciguerra.adrsentinel.web.annotation.shipment.validator;

import dev.vinciguerra.adrsentinel.web.annotation.shipment.ValidatorDistance;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale e matematico {@link ValidatorDistance}.
 * Agisce come scudo architetturale (Edge Validation) per le grandezze fisiche di distanza 
 * (es. raggio di trasporto, chilometraggio), garantendo che i valori in ingresso siano 
 * obbligatori, matematicamente sicuri e semanticamente coerenti con il dominio applicativo.
 * <p><b>Design Architetturale (Strict Validation & Mathematical Sanitization):</b></p>
 * Questa classe implementa una difesa in profondità a livello algebrico. Rifiutando 
 * nativamente l'assenza del dato (valori {@code null}), fonde l'obbligatorietà del parametro 
 * con una rigorosa sanitizzazione matematica. Blocca proattivamente valori anomali come 
 * {@code NaN} (Not a Number) o {@code Infinity}, proteggendo il Service Layer e il 
 * database da calcoli corrotti o eccezioni silenziose durante l'elaborazione delle rotte 
 * o delle tariffe di trasporto.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see ValidatorDistance
 */
public class DistanceValidator implements ConstraintValidator<ValidatorDistance, Float> {
	/**
	 * Esegue l'ispezione profonda del valore numerico applicando tre livelli di sicurezza matematica.
	 * <p><b>Flusso di Esecuzione (Fail-Fast & Triple Check):</b></p>
	 * <ol>
	 * <li><b>Guard Clause (Strict Presence Check):</b> Intercetta e respinge immediatamente 
	 * i valori {@code null}. Questo certifica l'assoluta obbligatorietà del parametro nel payload 
	 * e previene le subdole {@code NullPointerException} che si verificherebbero a valle 
	 * durante l'unboxing (da {@code Float} a {@code float}).</li>
	 * <li><b>Sanitizzazione Algebrica (Finite Math Check):</b> Verifica che il numero a virgola 
	 * mobile sia finito e reale. Intercettando {@code Float.isNaN()} e {@code Float.isInfinite()}, 
	 * sventa potenziali iniezioni di costanti matematiche non elaborabili che i comuni parser 
	 * JSON (es. Jackson) lascerebbero passare.</li>
	 * <li><b>Domain Rule (Strictly Positive):</b> Come ultimo step, impone che il valore sia 
	 * strettamente maggiore di zero ({@code > 0}). Trattandosi di una distanza fisica, 
	 * coordinate o vettori nulli (zero) o negativi rappresentano un'incongruenza logica 
	 * nel dominio dei trasporti.</li>
	 * </ol>
	 * @param value Il valore numerico della distanza estratto dal Request Payload.
	 * @param context Il contesto di validazione iniettato dal framework (Spring/Hibernate Validator).
	 * @return {@code true} esclusivamente se il valore è presente, finito, reale e strettamente 
	 * maggiore di zero; {@code false} in caso di assenza, aberrazione matematica (NaN/Infinity) 
	 * o valore nullo/negativo.
	 */
	@Override
	public boolean isValid(Float value, ConstraintValidatorContext context) {
		if(value == null)
			return false;
		if(value.isInfinite() || value.isNaN())
			return false;
		return value > 0;
	}
}
