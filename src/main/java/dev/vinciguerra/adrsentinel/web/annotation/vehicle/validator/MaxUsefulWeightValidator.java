package dev.vinciguerra.adrsentinel.web.annotation.vehicle.validator;

import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorMaxUsefulWeight;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale e matematico {@link ValidatorMaxUsefulWeight}.
 * Agisce come scudo architetturale (Edge Validation) per la Portata Utile Massima (Max Useful Weight) 
 * dei veicoli o degli imballaggi nel dominio dei trasporti (ADR), garantendo che la capacità 
 * di carico dichiarata sia obbligatoria e fisicamente coerente.
 * <p><b>Design Architetturale (Strict Validation & Domain-Driven Design):</b></p>
 * Questa classe traduce un concetto logistico fondamentale in una regola software invalicabile. 
 * Rifiutando nativamente l'assenza del dato (valori {@code null}), garantisce la presenza del 
 * parametro e impone che la portata utile sia strettamente maggiore di zero. Un veicolo o 
 * un contenitore con portata utile nulla o negativa rappresenta un paradosso logistico che 
 * corromperebbe i calcoli per la ripartizione del carico e l'assegnazione delle spedizioni.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorMaxUsefulWeight
 */
public class MaxUsefulWeightValidator implements ConstraintValidator<ValidatorMaxUsefulWeight, Integer> {
	/**
	 * Esegue l'ispezione profonda del valore numerico intero per certificarne l'obbligatorietà 
	 * e la conformità alle leggi della fisica logistica.
	 * <p><b>Flusso di Esecuzione (Fail-Fast & Domain Rule):</b></p>
	 * <ol>
	 * <li><b>Guard Clause (Strict Presence Check):</b> Intercetta e respinge istantaneamente 
	 * i valori {@code null}. Questo certifica l'assoluta obbligatorietà del parametro nel payload 
	 * e previene le fatali {@code NullPointerException} a valle, durante la fase di unboxing 
	 * (da {@code Integer} a {@code int}) all'interno dei motori di calcolo.</li>
	 * <li><b>Regola di Dominio (Strictly Positive):</b> Come ultimo step, impone che il valore 
	 * sia strettamente maggiore di zero ({@code > 0}). Elimina alla radice l'inserimento 
	 * di grandezze nulle o negative: non è operativamente possibile né legalmente ammesso 
	 * immatricolare a sistema un mezzo commerciale sprovvisto di capacità di carico utile.</li>
	 * </ol>
	 * @param value Il valore numerico intero (portata utile) estratto dal Request Payload.
	 * @param context Il contesto di validazione iniettato dal framework (Spring/Hibernate Validator).
	 * @return {@code true} esclusivamente se il valore è presente e strettamente maggiore 
	 * di zero; {@code false} in caso di assenza del dato o di portata nulla/negativa.
	 */
	@Override
	public boolean isValid(Integer value, ConstraintValidatorContext context) {
		if(value == null)
			return false;
		return value > 0;
	}
}
