package dev.vinciguerra.adrsentinel.web.annotation.dispatch.validator;

import dev.vinciguerra.adrsentinel.web.annotation.dispatch.ValidatorNetWeight;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta (Validator Engine) per il vincolo custom {@link ValidatorNetWeight}.
 * <p>
 * Questa classe funge da barriera difensiva primaria (Fail-Fast pattern) a livello di 
 * Presentation/Controller. Il suo scopo è garantire che i dati in ingresso relativi 
 * ai pesi netti delle merci ADR siano fisicamente e matematicamente impeccabili prima 
 * che raggiungano il Service Layer, proteggendo la logica di business e il Database.
 * </p>
 * <p>
 * <b>Dettaglio Architetturale:</b> Essendo istanziato e gestito dal framework di validazione 
 * (Hibernate Validator / Spring), questo componente è implicitamente un Singleton e deve 
 * rimanere rigorosamente <b>Thread-Safe</b>. Non contiene né deve contenere alcuno stato 
 * mutabile a livello di istanza.
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorNetWeight
 */
public class NetWeightValidator implements ConstraintValidator<ValidatorNetWeight, Integer> {
	/**
	 * Esegue la validazione formale e logistica sulla quantità intera in ingresso.
	 * <p><b>Pipeline di Validazione (Approccio Fail-Fast):</b></p>
	 * <ol>
	 * <li><i>Null-Safety:</i> Rifiuta immediatamente valori nulli ({@code value == null}). 
	 * Garantisce che la quantità sia sempre esplicitamente dichiarata prima di consentire 
	 * la persistenza o l'elaborazione del Documento di Trasporto.</li>
	 * <li><i>Invariante di Dominio (Limite Inferiore):</i> Verifica che il valore sia 
	 * strettamente maggiore di zero ({@code value > 0}). Una riga di spedizione con 
	 * quantità nulla o negativa è semanticamente e fisicamente invalida.</li>
	 * <li><i>Invariante di Dominio (Limite Superiore):</i> Verifica che la quantità non ecceda 
	 * la soglia massima di sicurezza fissata a 44.000 ({@code value <= 44000}). Questo 
	 * valore riflette la portata limite legale per il trasporto intermodale/combinato 
	 * (44 tonnellate). Il vincolo agisce primariamente come "Typo Preventer", bloccando sul 
	 * nascere errori macroscopici di digitazione nell'inserimento dati.</li>
	 * </ol>
	 * @param value Il valore quantitativo intero da sottoporre a verifica (es. chili o litri discreti).
	 * @param context Il contesto strutturale fornito dal motore di validazione Jakarta, 
	 * utilizzabile per l'eventuale costrutto di messaggi di violazione dinamici.
	 * @return {@code true} se il valore è presente e rientra nel range operativo ammesso (da 1 a 44000 inclusi), 
	 * {@code false} in caso di valore nullo o fuori dai limiti.
	 */
	@Override
	public boolean isValid(Integer value, ConstraintValidatorContext context) {
		if(value == null)
			return false;
		return value > 0 && value <= 44000;
	}
}
