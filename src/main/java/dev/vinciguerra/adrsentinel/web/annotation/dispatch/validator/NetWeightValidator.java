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
	 * Valuta la conformità del peso netto fornito in input rispetto alle rigide regole 
	 * del dominio logistico e matematico.
	 * <p>
	 * L'algoritmo di validazione procede per "imbuti" di sicurezza successivi:
	 * <ol>
	 * <li><b>Controllo di Esistenza (Null-Safety):</b> Rifiuta esplicitamente i payload privi di valore. 
	 * Questo rende il vincolo autosufficiente: un campo annotato con {@code @ValidatorNetWeight} 
	 * è implicitamente considerato obbligatorio (comportandosi anche come un {@code @NotNull}).</li>
	 * <li><b>Controllo di Integrità Fisica:</b> Assicura che il peso rappresenti una grandezza 
	 * nel mondo reale, dovendo essere strettamente maggiore di zero.</li>
	 * </ol>
	 * </p>
	 * @param value l'oggetto {@link Float} che rappresenta il peso netto da validare 
	 * (estratto tipicamente dalla deserializzazione del JSON).
	 * @param context il contesto operativo in cui il vincolo viene valutato; fornisce API 
	 * avanzate per manipolare dinamicamente i template dei messaggi di errore 
	 * qualora si volessero disabilitare quelli di default.
	 * @return {@code true} se e solo se il valore è presente, finito e strettamente positivo; 
	 * {@code false} in tutti gli altri casi.
	 */
	@Override
	public boolean isValid(Integer value, ConstraintValidatorContext context) {
		if(value == null)
			return false;
		return value > 0;
	}
}
