package dev.vinciguerra.adrsentinel.web.annotation.onunumber.validator;

import java.util.regex.Pattern;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorKemlerCode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale {@link ValidatorKemlerCode}.
 * Agisce come scudo architetturale (Edge Validation) per garantire che i codici Kemler 
 * (numeri di identificazione del pericolo ADR) forniti in ingresso rispettino la rigida 
 * nomenclatura internazionale.
 * <p><b>Design Architetturale (Single Responsibility & Optionality):</b></p>
 * Questa classe rispetta il <i>Single Responsibility Principle</i> gestendo unicamente 
 * la coerenza formale del dato. Essendo il codice Kemler un parametro che tollera 
 * nativamente l'assenza o l'omissione nel payload, il validatore restituisce esito 
 * positivo in caso di valori nulli o vuoti. L'obbligatorietà, qualora imposta dal 
 * caso d'uso, deve essere garantita tramite l'affiancamento di annotazioni strutturali 
 * (es. {@code @NotBlank}).
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorKemlerCode
 */
public class KemlerCodeValidator implements ConstraintValidator<ValidatorKemlerCode, String> {
	/**
	 * Espressione Regolare (Regex) pre-compilata per l'analisi formale del Codice Kemler.
	 * <p><b>Ottimizzazione delle Performance:</b></p>
	 * L'istanziazione come costante {@code static final} assicura che il framework compili 
	 * l'automa a stati finiti una singola volta durante il Classloading, azzerando l'overhead 
	 * computazionale per le successive richieste HTTP.
	 * <p><b>Specifiche del Pattern {@code ^(NONE|X?\d{2,3})$}:</b></p>
	 * <ul>
	 * <li>{@code NONE} : Costante letterale accettata per indicare esplicitamente l'assenza di classificazione.</li>
	 * <li>{@code X?} : Prefisso opzionale 'X', che nella normativa ADR denota una sostanza che reagisce pericolosamente con l'acqua.</li>
	 * <li>{@code \d{2,3}} : Nucleo del codice di pericolo, composto strettamente da due o tre cifre numeriche (es. 33, 338, 22).</li>
	 * </ul>
	 */
	private final static Pattern KEMLER_CODE_PATTERN = Pattern.compile("^(NONE|X?\\d{2,3})$");
	
	/**
	 * Esegue la validazione effettiva sul payload in ingresso (Data Flow).
	 * <p><b>Flusso di Esecuzione (Fail-Fast):</b></p>
	 * <ol>
	 * <li><b>Guard Clause (Tolleranza all'Assenza):</b> Intercetta valori {@code null}, stringhe 
	 * vuote o composte solo da spazi bianchi (tramite {@code isBlank()}). In questi scenari restituisce 
	 * immediatamente {@code true}, prevenendo valutazioni Regex superflue e onorando l'opzionalità del campo.</li>
	 * <li><b>Pattern Matching:</b> Sottopone la stringa valorizzata e pulita al motore Regex 
	 * pre-compilato per certificarne l'esatta corrispondenza semantica con il dominio ADR.</li>
	 * </ol>
	 * @param value La stringa rappresentante il codice Kemler estratta dal Request Payload.
	 * @param context Il contesto di validazione iniettato dal framework (Spring/Hibernate Validator).
	 * @return {@code true} se la stringa è assente, vuota, oppure se è presente e rispetta il pattern Kemler; 
	 * {@code false} se il dato è presente ma sintatticamente malformato.
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null || value.isBlank())
			return true;
		return KEMLER_CODE_PATTERN.matcher(value).matches();
	}
}
