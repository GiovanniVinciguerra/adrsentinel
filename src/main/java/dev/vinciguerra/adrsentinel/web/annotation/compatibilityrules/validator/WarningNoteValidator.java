package dev.vinciguerra.adrsentinel.web.annotation.compatibilityrules.validator;

import java.util.regex.Pattern;
import dev.vinciguerra.adrsentinel.web.annotation.compatibilityrules.ValidatorWarningNote;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale {@link ValidatorWarningNote}.
 * Funge da barriera di controllo (Edge Validation) per le note di avvertenza, garantendo che, 
 * qualora il dato venga fornito dal client, rispetti rigorosamente i vincoli dimensionali di sistema.
 * <p><b>Design Architetturale (Tolleranza all'Assenza):</b></p>
 * A differenza di validatori compositi o restrittivi, questa implementazione riconosce che la nota 
 * di avvertenza è un campo tipicamente opzionale all'interno del Dominio. Pertanto, il validatore 
 * tollera l'assenza del dato, delegando un eventuale controllo di obbligatorietà ad annotazioni 
 * esterne (es. {@code @NotBlank}), e concentrandosi esclusivamente sulla coerenza strutturale 
 * (Length/Size).
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorWarningNote
 */
public class WarningNoteValidator implements ConstraintValidator<ValidatorWarningNote, String> {
	/**
	 * Whitelist di sicurezza per il campo warningNote di CompatibilityRule:
	 * <ul>
	 * <li><b>\p{L}:</b> Qualsiasi lettera (inclusi accenti) </li>
	 * <li><b>0-9:</b> Numeri </li>
	 * <li><b>\s:</b> Spazi, tab, ritorni a capo </li>
	 * <li><b>\-',.;:!?:</b> Punteggiatura base per frasi di senso compiuto </li>
	 * <li><b>/()</b> Alternative e precisazioni </li>
	 * <li><b>°%+&</b> Gradi, percentuali, addizioni (es. 1.4+1.5) e congiunzioni </li>
	 * <li><b>Esclusi:</b> <, >, =, {, }, $, [, ], * </li>
	 * </ul>
	 * <p>
	 * <b>N.B</b> la stringa è valida esclusivamente se possiede solo e solo questi caratteri consentiti.
	 * </p>
	 */
	Pattern WHITELIST_WARNING_NOTE_PATTERN = Pattern.compile("^[\\p{L}0-9\\s\\-',.;:!?/()°%+&]+$");
	
	/**
	 * Esegue l'ispezione della stringa in ingresso per certificarne l'allineamento ai limiti di archiviazione.
	 * <p><b>Flusso di Esecuzione (Fail-Fast & Optionality):</b></p>
	 * <ol>
	 * <li><b>Tolleranza e Bypass (Guard Clause):</b> Intercetta valori {@code null}, stringhe vuote 
	 * o composte esclusivamente da spazi bianchi (tramite l'uso ottimizzato di {@code isBlank()}). 
	 * In questi scenari l'esecuzione si interrompe istantaneamente restituendo {@code true}, 
	 * trattando l'assenza del dato come uno stato lecito e prevenendo {@code NullPointerException}.</li>
	 * <li><b>Boundary Check (Limiti Dimensionali):</b> Se la stringa è effettivamente valorizzata, 
	 * il motore di validazione verifica che la sua lunghezza sia matematicamente compresa 
	 * nel range prestabilito [3, 255]. Questo assicura che il payload non provochi troncamenti 
	 * o errori di overflow durante la scrittura sul database (es. colonna VARCHAR).</li>
	 * <li><b>Whitelisting</b>: Intercetta e respinge qualunque stringa che non corrisponde al pattern del campo 
	 * warningNote di una regola di compatibilità ADR.
	 * </ol>
	 * @param value La nota di avvertenza estratta dal Data Transfer Object (Request Payload).
	 * @param context Il contesto di validazione iniettato dal framework (Spring/Hibernate Validator).
	 * @return {@code true} se la stringa è assente, vuota, oppure se è presente e rientra nei limiti 
	 * di lunghezza prestabiliti; {@code false} esclusivamente se la stringa è presente ma viola 
	 * i confini dimensionali.
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null || value.isBlank())
			return true;
		if(value.length() < 3 || value.length() > 255)
			return false;
		return WHITELIST_WARNING_NOTE_PATTERN.matcher(value).matches();
	}
}
