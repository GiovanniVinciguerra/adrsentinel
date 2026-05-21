package dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.validator;

import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.ValidatorQuantity;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale e matematico {@link ValidatorQuantity}.
 * Agisce come scudo architetturale (Edge Validation) per le grandezze fisiche di quantità 
 * nel dominio ADR (es. peso netto, massa lorda, volume nominale), garantendo che i dati in 
 * ingresso siano obbligatori, matematicamente sicuri e semanticamente coerenti con la realtà fisica.
 * <p><b>Design Architetturale (Strict Validation & Mathematical Sanitization):</b></p>
 * Questa classe implementa una difesa in profondità a livello algebrico. Rifiutando 
 * nativamente l'assenza del dato (valori {@code null}), fonde l'obbligatorietà del parametro 
 * con una rigorosa sanitizzazione matematica. Blocca proattivamente le anomalie strutturali 
 * come {@code NaN} (Not a Number) o {@code Infinity}, proteggendo il motore di calcolo del 
 * Service Layer (es. l'algoritmo per l'esenzione dei 1000 punti ADR 1.1.3.6) da alterazioni 
 * silenziose e corruzione dei dati a database.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorQuantity
 */
public class QuantityValidator implements ConstraintValidator<ValidatorQuantity, Float> {
	/**
	 * Esegue l'ispezione profonda del valore numerico applicando tre livelli di sicurezza matematica.
	 * <p><b>Flusso di Esecuzione (Fail-Fast & Triple Check):</b></p>
	 * <ol>
	 * <li><b>Guard Clause (Strict Presence Check):</b> Intercetta e respinge istantaneamente 
	 * i valori {@code null}. Questo certifica l'assoluta obbligatorietà del parametro nel payload 
	 * e previene disastrose {@code NullPointerException} durante il processo di unboxing 
	 * (da {@code Float} a {@code float}) nelle formule a valle.</li>
	 * <li><b>Sanitizzazione Algebrica (Finite Math Check):</b> Verifica che il numero a virgola 
	 * mobile sia finito e reale. Intercettando {@code Float.isNaN()} e {@code Float.isInfinite()}, 
	 * respinge payload ingannevoli che i comuni parser JSON deserializzerebbero senza errori, 
	 * impedendo che si propaghino nel sistema.</li>
	 * <li><b>Domain Rule (Strictly Positive):</b> Come ultimo step, impone che il valore sia 
	 * strettamente maggiore di zero ({@code > 0}). Trattandosi di materia fisica trasportata, 
	 * una quantità nulla o negativa rappresenta un paradosso logico e normativo inaccettabile.</li>
	 * </ol>
	 * @param value Il valore numerico della quantità estratto dal Request Payload.
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
