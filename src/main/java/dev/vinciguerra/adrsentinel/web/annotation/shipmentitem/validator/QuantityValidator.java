package dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.validator;

import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.ValidatorQuantity;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del validatore custom legato all'annotazione {@link ValidatorQuantity}.
 * <p><b>Contesto Architetturale (Boundary Protection &amp; Anti-Corruption):</b></p>
 * Questa classe agisce come barriera protettiva allo strato di ingresso delle API (REST/DTO). 
 * Il suo compito è intercettare i valori quantitativi interi dichiarati nei payload, 
 * garantendo che siano conformi ai limiti strutturali e logistici prima di propagarsi 
 * verso la logica di business o la persistenza sul database.
 * <p><b>Invarianti di Dominio (Limiti Fisici e Logistici):</b></p>
 * Il range di validazione matematico (0, 40000] è progettato per allinearsi 
 * ai limiti operativi standard del trasporto merci:
 * <ul>
 * <li><i>Limite Inferiore (Rigidamente Positivo):</i> La quantità deve essere strettamente 
 * maggiore di zero. L'assenza di quantità logistica (zero) renderebbe logicamente invalida 
 * la riga del Documento di Trasporto.</li>
 * <li><i>Limite Superiore (40.000):</i> Il tetto massimo di 40.000 rappresenta una 
 * soglia di sicurezza invalicabile (es. limite fisico corrispondente alla portata massima di 40 tonnellate). 
 * Questo vincolo funge primariamente da "Typo Preventer", bloccando sul nascere errori macroscopici 
 * di digitazione da parte dell'operatore (es. l'aggiunta accidentale di zeri extra).</li>
 * </ul>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorQuantity
 */
public class QuantityValidator implements ConstraintValidator<ValidatorQuantity, Integer> {
	/**
	 * Esegue la validazione formale e logistica sulla quantità intera in ingresso.
	 * <p><b>Pipeline di Validazione (Fail-Fast):</b></p>
	 * <ol>
	 * <li><i>Null-Safety:</i> Rifiuta immediatamente valori nulli, garantendo che il dato 
	 * sia sempre presente per l'elaborazione del D.D.T.</li>
	 * <li><i>Domain Rule Check:</i> Verifica che il valore sia maggiore di zero ({@code > 0}) 
	 * e non ecceda il limite logistico di sicurezza ({@code 44000}).</li>
	 * </ol>
	 * @param value Il valore quantitativo intero da sottoporre a verifica.
	 * @param context Il contesto strutturale fornito dal motore di validazione Jakarta.
	 * @return {@code true} se la quantità è valorizzata e rientra nel range logistico consentito, {@code false} altrimenti.
	 */
	@Override
	public boolean isValid(Integer value, ConstraintValidatorContext context) {
		if(value == null)
			return false;
		return value > 0 && value <= 44000;
	}
}
